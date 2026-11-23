package com.enginesense.app.drivelab

import com.enginesense.app.owner.O2Check
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.enginesense.app.MainActivity
import com.enginesense.app.R
import com.enginesense.app.obd.Elm327Client
import com.enginesense.app.obd.ObdRawResult
import com.enginesense.app.obd.ObdResponseParser
import com.enginesense.app.obd.ObdTransport
import com.enginesense.app.storage.VehicleProfileStore
import com.enginesense.app.vehicle.LiveDerivedCalculator
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidReader
import com.enginesense.app.vehicle.PidReading
import com.enginesense.app.vehicle.PidStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the recorder is doing right now, for the Drive lab screen and the notification. */
data class DriveRecorderStatus(
    val running: Boolean = false,
    val phase: Phase = Phase.STOPPED,
    val deviceName: String? = null,
    val lastSpeedKmh: Double? = null,
    val lastVolts: Double? = null,
    val lastContactMillis: Long? = null,
    val samplesThisRun: Int = 0,
    val lastError: String? = null
) {
    enum class Phase(val label: String) {
        STOPPED("Stopped"),
        CONNECTING("Connecting to the scanner"),
        WAITING("Scanner not reachable, retrying"),
        ENGINE_OFF("Connected, engine off"),
        IGNITION_ON("Ignition on, engine not running"),
        DRIVING("Engine running, recording")
    }
}

/**
 * Stays connected to the scanner in the background and logs drives, for the Fuel chip, fill-up
 * detection, the odometer and Battery. Kept light on the phone: the CPU is only held awake while
 * the engine runs, about one reading a second while driving, and a check every minute or so when
 * parked (Android may stretch that while the phone sleeps).
 *
 * - Engine running: fast PIDs (rpm, speed, air flow, throttle, load) every cycle, slow ones
 *   (coolant, voltages, fuel level) every [SLOW_EVERY_MS], odometer-type counters every
 *   [VERY_SLOW_EVERY_MS].
 * - Ignition on, engine stopped (rpm 0): ATRV as fast as the adapter answers, to catch the
 *   voltage dip while cranking.
 * - Engine off: ATRV every 15 seconds, then every minute — this is what tells us whether the
 *   dongle stays awake (and drains the car battery) or goes to sleep.
 * - Connection lost: retries forever with a backoff until stopped.
 *
 * RPM is read raw rather than through [PidReader] while the engine is off: PidReader caches PID
 * support per connection, and asking with the ECU asleep would cache "nothing supported" for good.
 */
class DriveRecorderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var transport: ObdTransport? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var store: DriveLabStore

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = DriveLabStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: savedAddress(this)
        if (address == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        saveAddress(this, address)
        startInForeground("Starting…")
        if (loopJob?.isActive != true) loopJob = scope.launch { recordLoop(address) }
        // Restarted by Android after being killed: pick up the saved scanner and carry on.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        transport?.close()
        releaseWakeLock()
        _status.update { it.copy(running = false, phase = DriveRecorderStatus.Phase.STOPPED) }
        super.onDestroy()
    }

    private fun stopRecording() {
        store.appendEvent(DriveEvent(System.currentTimeMillis(), "stopped", "by user"))
        clearAddress(this)
        loopJob?.cancel()
        transport?.close()
        releaseWakeLock()
        _status.update { DriveRecorderStatus() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission") // started only from the Drive lab screen after the Bluetooth permission is granted
    private suspend fun recordLoop(address: String) {
        val device = BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
        if (device == null) {
            setPhase(DriveRecorderStatus.Phase.WAITING, error = "Bluetooth is off or the scanner isn't paired")
            return
        }
        _status.update { it.copy(running = true, deviceName = device.name ?: address) }
        store.appendEvent(DriveEvent(System.currentTimeMillis(), "started", device.name ?: address))
        // A Bluetooth read has no timeout: if the dongle goes silent without dropping the link,
        // sendCommand would block forever. Closing the socket unblocks it with an IOException.
        scope.launch {
            while (isActive) {
                delay(WATCHDOG_EVERY_MS)
                val t = transport ?: continue
                // Parked, the phone may sleep through several polls without the link being stuck.
                val limit = if (_status.value.phase == DriveRecorderStatus.Phase.DRIVING) WATCHDOG_SILENCE_MS else PARKED_WATCHDOG_SILENCE_MS
                if (System.currentTimeMillis() - lastIoAt > limit) {
                    store.appendEvent(DriveEvent(System.currentTimeMillis(), "watchdog", "no reply for ${limit / 1000}s"))
                    t.close()
                }
            }
        }
        var backoffMs = RETRY_MIN_MS
        while (scope.isActive) {
            setPhase(DriveRecorderStatus.Phase.CONNECTING)
            val client = Elm327Client(device)
            try {
                client.connect()
                lastIoAt = System.currentTimeMillis()
                transport = client
                backoffMs = RETRY_MIN_MS
                store.appendEvent(DriveEvent(System.currentTimeMillis(), "connected"))
                runSession(client)
            } catch (e: CancellationException) {
                client.close()
                throw e
            } catch (e: Exception) {
                val lost = transport === client
                store.appendEvent(DriveEvent(System.currentTimeMillis(), if (lost) "disconnected" else "connect_failed", e.message ?: e.javaClass.simpleName))
                setPhase(DriveRecorderStatus.Phase.WAITING, error = e.message)
            }
            releaseWakeLock() // a link lost mid-drive mustn't keep the phone awake while it retries
            client.close()
            transport = null
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(RETRY_MAX_MS)
        }
    }

    /** Runs until the connection drops (throws IOException) or the service stops. */
    private suspend fun runSession(t: ObdTransport) {
        val profile = VehicleProfileStore(this).load()
        var reader = PidReader(t)
        var engineOn = false
        var supportSaved = false
        var lastEngineOnAt = 0L
        var engineOffAt = System.currentTimeMillis()
        var lastSlowAt = 0L
        var lastVerySlowAt = 0L
        var lastO2At = 0L
        var hasMaf = true
        val slow = mutableMapOf<PidId, PidReading>()

        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val rpm = readRpmRaw(t)
            if (rpm != null && rpm > 0.0) {
                if (!engineOn) {
                    engineOn = true
                    reader = PidReader(t) // fresh support cache now the ECU is awake
                    acquireWakeLock()
                    store.appendEvent(DriveEvent(now, "engine_on"))
                }
                lastEngineOnAt = now
                if (!supportSaved) {
                    saveSupport(reader)
                    supportSaved = true
                }
                val r = mutableMapOf<PidId, PidReading>()
                r[PidId.RPM] = PidReading(PidId.RPM, PidStatus.VERIFIED, rpm, null)
                r[PidId.SPEED_KMH] = reader.read(PidId.SPEED_KMH)
                if (hasMaf) {
                    val maf = reader.read(PidId.MAF_AIR_FLOW_RATE)
                    r[PidId.MAF_AIR_FLOW_RATE] = maf
                    if (maf.status == PidStatus.UNSUPPORTED) hasMaf = false
                }
                if (!hasMaf) r[PidId.INTAKE_MANIFOLD_PRESSURE] = reader.read(PidId.INTAKE_MANIFOLD_PRESSURE)
                r[PidId.THROTTLE_POSITION] = reader.read(PidId.THROTTLE_POSITION)
                r[PidId.ENGINE_LOAD] = reader.read(PidId.ENGINE_LOAD)
                var adapterV: Double? = null
                val slowNow = now - lastSlowAt >= SLOW_EVERY_MS
                if (slowNow) {
                    lastSlowAt = now
                    listOf(PidId.COOLANT_TEMP_C, PidId.CONTROL_MODULE_VOLTAGE, PidId.FUEL_LEVEL, PidId.INTAKE_AIR_TEMP_C, PidId.SHORT_TERM_FUEL_TRIM_1, PidId.LONG_TERM_FUEL_TRIM_1)
                        .forEach { slow[it] = reader.read(it) }
                    adapterV = readAdapterVolts(t)
                }
                var distCleared: Double? = null
                var odometer: Double? = null
                if (now - lastVerySlowAt >= VERY_SLOW_EVERY_MS) {
                    lastVerySlowAt = now
                    distCleared = reader.read(PidId.DISTANCE_SINCE_CODES_CLEARED).verified()
                    odometer = reader.read(PidId.ODOMETER).verified()
                }
                r.putAll(slow)
                val air = LiveDerivedCalculator.airFlow(r, profile?.knownEngineCc)
                val sample = DriveSample(
                    timeMillis = now, rpm = rpm,
                    speedKmh = r[PidId.SPEED_KMH].verified(),
                    airGramsPerSec = air?.gramsPerSec,
                    throttlePercent = r[PidId.THROTTLE_POSITION].verified(),
                    engineLoadPercent = r[PidId.ENGINE_LOAD].verified(),
                    coolantC = if (slowNow) slow[PidId.COOLANT_TEMP_C].verified() else null,
                    moduleVolts = if (slowNow) slow[PidId.CONTROL_MODULE_VOLTAGE].verified() else null,
                    adapterVolts = adapterV,
                    fuelLevelPercent = if (slowNow) slow[PidId.FUEL_LEVEL].verified() else null,
                    distanceSinceClearedKm = distCleared,
                    odometerKm = odometer,
                    fuelTrimPercent = if (slowNow) fuelTrim(slow) else null
                )
                record(sample, DriveRecorderStatus.Phase.DRIVING)
                if (now - lastO2At >= O2_EVERY_MS && (slow[PidId.COOLANT_TEMP_C].verified() ?: 0.0) >= 70.0) {
                    lastO2At = now
                    o2Check(reader)?.let { store.appendEvent(DriveEvent(now, O2Check.EVENT, it.toEventDetail())) }
                }
                delay(DRIVING_PAUSE_MS)
            } else {
                if (engineOn && now - lastEngineOnAt >= ENGINE_OFF_AFTER_MS) {
                    engineOn = false
                    engineOffAt = now
                    store.appendEvent(DriveEvent(now, "engine_off"))
                    releaseWakeLock()
                }
                if (engineOn) {
                    // A dropped reply mid-drive, not the engine stopping — try again straight away.
                    delay(DRIVING_PAUSE_MS)
                    continue
                }
                val volts = readAdapterVolts(t)
                val ignitionOn = rpm == 0.0
                record(
                    DriveSample(now, rpm, null, null, null, null, null, null, volts, null, null, null),
                    if (ignitionOn) DriveRecorderStatus.Phase.IGNITION_ON else DriveRecorderStatus.Phase.ENGINE_OFF
                )
                delay(
                    when {
                        ignitionOn -> CRANK_WATCH_PAUSE_MS
                        now - engineOffAt < RECENT_OFF_WINDOW_MS -> RECENT_OFF_PAUSE_MS
                        else -> PARKED_PAUSE_MS
                    }
                )
            }
        }
    }

    @Volatile private var lastIoAt = 0L

    private fun record(sample: DriveSample, phase: DriveRecorderStatus.Phase) {
        lastIoAt = sample.timeMillis
        store.appendSample(sample)
        _status.update {
            it.copy(
                running = true, phase = phase, lastError = null,
                lastSpeedKmh = sample.speedKmh ?: it.lastSpeedKmh.takeIf { phase == DriveRecorderStatus.Phase.DRIVING },
                lastVolts = sample.adapterVolts ?: sample.moduleVolts ?: it.lastVolts,
                lastContactMillis = sample.timeMillis,
                samplesThisRun = it.samplesThisRun + 1
            )
        }
        if (_status.value.samplesThisRun % NOTIFY_EVERY_SAMPLES == 1 || phase != lastNotifiedPhase) {
            lastNotifiedPhase = phase
            updateNotification(notificationText())
        }
    }

    private var lastNotifiedPhase: DriveRecorderStatus.Phase? = null

    private fun notificationText(): String {
        val s = _status.value
        val bits = listOfNotNull(
            s.phase.label,
            s.lastSpeedKmh?.takeIf { s.phase == DriveRecorderStatus.Phase.DRIVING }?.let { "%.0f km/h".format(it) },
            s.lastVolts?.let { "%.1f V".format(it) }
        )
        return bits.joinToString(" · ")
    }

    private fun setPhase(phase: DriveRecorderStatus.Phase, error: String? = null) {
        _status.update { it.copy(running = true, phase = phase, lastError = error ?: it.lastError) }
        updateNotification(listOfNotNull(phase.label, error).joinToString(" · "))
    }

    private fun saveSupport(reader: PidReader) {
        val rows = PidId.entries.map { pid ->
            val reading = reader.read(pid)
            listOf("0x%02X".format(pid.pid), pid.displayName.replace(",", " "), reading.status.name, reading.value?.let { "%.2f".format(java.util.Locale.US, it) } ?: "")
                .joinToString(",")
        }
        store.saveSupport(rows)
        store.appendEvent(DriveEvent(System.currentTimeMillis(), "pids_checked", "${rows.count { "VERIFIED" in it }} answered"))
    }

    /**
     * A few seconds of front and rear oxygen sensor readings on a warm engine, for the catalytic
     * converter check. Null when the car has no rear sensor PID or nothing switched.
     */
    private fun o2Check(reader: PidReader): O2Check? {
        if (reader.read(PidId.O2_SENSOR_B1S2_VOLTAGE).status != PidStatus.VERIFIED) return null
        val wideband = reader.read(PidId.O2_SENSOR_B1S1_VOLTAGE).status != PidStatus.VERIFIED
        val front = if (wideband) PidId.O2_WIDEBAND_B1S1_LAMBDA else PidId.O2_SENSOR_B1S1_VOLTAGE
        val fronts = mutableListOf<Double>()
        val rears = mutableListOf<Double>()
        repeat(O2_READINGS) {
            reader.read(front).verified()?.let { fronts += it }
            reader.read(PidId.O2_SENSOR_B1S2_VOLTAGE).verified()?.let { rears += it }
        }
        if (fronts.size < O2_READINGS / 2 || rears.size < O2_READINGS / 2) return null
        return O2Check(System.currentTimeMillis(), O2Check.switches(fronts, if (wideband) 1.0 else 0.45), O2Check.switches(rears, 0.45))
    }

    /** Short + long-term trim when the car reports either; most diesels report neither. */
    private fun fuelTrim(slow: Map<PidId, PidReading>): Double? {
        val st = slow[PidId.SHORT_TERM_FUEL_TRIM_1].verified()
        val lt = slow[PidId.LONG_TERM_FUEL_TRIM_1].verified()
        return if (st == null && lt == null) null else (st ?: 0.0) + (lt ?: 0.0)
    }

    /** RPM straight from "010C": null when the ECU doesn't answer (asleep), 0.0 with the ignition on but engine stopped. */
    private fun readRpmRaw(t: ObdTransport): Double? {
        val raw = t.sendCommand("010C")
        return when (val parsed = ObdResponseParser.parse(raw, expectedModeEcho = "41")) {
            is ObdRawResult.Ok -> {
                val b = if (parsed.bytes.firstOrNull() == 0x0C) parsed.bytes.drop(1) else parsed.bytes
                if (b.size >= 2) ((b[0] * 256.0) + b[1]) / 4.0 else null
            }
            else -> null
        }
    }

    /** "12.4V" from ATRV, or null if the adapter answers something else. Throws if the link is gone. */
    private fun readAdapterVolts(t: ObdTransport): Double? =
        Regex("""(\d{1,2}\.\d)""").find(t.sendCommand("ATRV"))?.value?.toDoubleOrNull()?.takeIf { it in 5.0..20.0 }

    private fun PidReading?.verified(): Double? = this?.takeIf { it.status == PidStatus.VERIFIED }?.value

    // --- Foreground notification -------------------------------------------------------------

    private fun startInForeground(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Drive recorder (test)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while GetAutoSure stays connected to your scanner"
            }
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(text), type)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("GetAutoSure drive recorder")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        .addAction(
            0, "Stop",
            PendingIntent.getService(this, 1, Intent(this, DriveRecorderService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        )
        .build()

    @SuppressLint("WakelockTimeout") // held only while the engine runs; released on engine off, a lost link or Stop
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GetAutoSure:DriveRecorder")
            ?.apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "drive_recorder"
        private const val NOTIFICATION_ID = 7_301
        private const val ACTION_STOP = "com.enginesense.app.drivelab.STOP"
        private const val EXTRA_ADDRESS = "address"
        private const val PREFS = "drive_lab"
        private const val KEY_ADDRESS = "scanner_address"

        private const val DRIVING_PAUSE_MS = 1_000L
        private const val SLOW_EVERY_MS = 10_000L
        /** The converter check runs every few minutes of warm driving: a few seconds each. */
        private const val O2_EVERY_MS = 5 * 60_000L
        private const val O2_READINGS = 20
        private const val VERY_SLOW_EVERY_MS = 120_000L
        /** No RPM for this long while it was running = engine switched off (not a dropped reply). */
        private const val ENGINE_OFF_AFTER_MS = 20_000L
        private const val CRANK_WATCH_PAUSE_MS = 50L
        private const val RECENT_OFF_WINDOW_MS = 15 * 60_000L
        private const val RECENT_OFF_PAUSE_MS = 15_000L
        private const val PARKED_PAUSE_MS = 60_000L
        private const val RETRY_MIN_MS = 15_000L
        private const val RETRY_MAX_MS = 120_000L
        private const val NOTIFY_EVERY_SAMPLES = 20
        private const val WATCHDOG_EVERY_MS = 15_000L
        /** Longer than [PARKED_PAUSE_MS] plus a slow "SEARCHING…" reply, so a parked car never trips it. */
        private const val WATCHDOG_SILENCE_MS = 150_000L
        private const val PARKED_WATCHDOG_SILENCE_MS = 15 * 60_000L

        private val _status = MutableStateFlow(DriveRecorderStatus())
        val status: StateFlow<DriveRecorderStatus> = _status.asStateFlow()

        fun start(context: Context, address: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DriveRecorderService::class.java).putExtra(EXTRA_ADDRESS, address)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DriveRecorderService::class.java).setAction(ACTION_STOP))
        }

        fun savedAddress(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ADDRESS, null)

        private fun saveAddress(context: Context, address: String) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ADDRESS, address).apply()

        private fun clearAddress(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ADDRESS).apply()
    }
}
