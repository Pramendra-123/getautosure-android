package com.enginesense.app

import android.app.Application
import android.content.Context
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enginesense.app.obd.DtcRepository
import com.enginesense.app.obd.DtcRepositoryGenericExplainer
import com.enginesense.app.obd.Elm327Client
import com.enginesense.app.obd.Mode09Reader
import com.enginesense.app.obd.ObdTransport
import com.enginesense.app.obd.SimulatedObdTransport
import com.enginesense.app.obd.SimulatedProfile
import com.enginesense.app.obd.VehicleIdInfo
import com.enginesense.app.auth.decodeJwtExpiry
import com.enginesense.app.auth.getFreshGoogleIdToken
import com.enginesense.app.vehicle.DiagnosticFinding
import com.enginesense.app.vehicle.DiagnosticRulesEngine
import com.enginesense.app.vehicle.DtcExplanationInput
import com.enginesense.app.vehicle.DtcReading
import com.enginesense.app.vehicle.DtcSource
import com.enginesense.app.vehicle.FuelCombustionInsight
import com.enginesense.app.vehicle.FuelSample
import com.enginesense.app.vehicle.HealthScoreCalculator
import com.enginesense.app.vehicle.HealthScoreResult
import com.enginesense.app.vehicle.LiveDerived
import com.enginesense.app.vehicle.LiveDerivedCalculator
import com.enginesense.app.vehicle.MileageAnalyzer
import com.enginesense.app.vehicle.MileageTrendAnalyzer
import com.enginesense.app.vehicle.MileageTrendResult
import com.enginesense.app.vehicle.PerformanceAnalyzer
import com.enginesense.app.vehicle.PerformanceResult
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidReader
import com.enginesense.app.vehicle.PidReading
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehicle.ReadinessReader
import com.enginesense.app.vehicle.ReadinessStatus
import com.enginesense.app.vehicle.SpeedSample
import com.enginesense.app.vehicle.TripMileageResult
import com.enginesense.app.vehicle.VehicleSnapshot
import com.enginesense.app.storage.PersistedTripMileage
import com.enginesense.app.storage.ScanHistoryStore
import com.enginesense.app.storage.TripMileageStore
import com.enginesense.app.storage.toPersistedScan
import com.enginesense.app.storage.toSnapshot
import com.enginesense.app.vehicle.ai.AiBackendConfig
import com.enginesense.app.vehicle.ai.AiCarHealthReport
import com.enginesense.app.vehicle.ai.AiCarHealthReportService
import com.enginesense.app.vehicle.ai.AiMechanicChatService
import com.enginesense.app.vehicle.ai.AiVehicleContext
import com.enginesense.app.vehicle.ai.ChatMessage
import com.enginesense.app.vehicle.ai.FallbackAiMechanicChatService
import com.enginesense.app.vehicle.ai.LocalAutomotiveKnowledgeRetriever
import com.enginesense.app.vehicle.ai.RemoteAiMechanicChatService
import com.enginesense.app.vehicle.ai.RemoteAiNotAuthenticatedException
import com.enginesense.app.vehicle.ai.RuleBasedAiMechanicChatService
import com.enginesense.app.vehicle.ai.TemplateAiCarHealthReportService
import com.enginesense.app.vehicle.integrations.PlaceholderVehicleInfoService
import com.enginesense.app.vehicle.integrations.PlaceholderWorkshopBookingService
import com.enginesense.app.vehicle.integrations.VehicleInfoService
import com.enginesense.app.vehicle.integrations.WorkshopBookingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One completed scan's full result — what Home/Reports/the detail screens all read from. */
data class ScanResult(
    val timestampMillis: Long,
    val profileLabel: String,
    val isDemo: Boolean,
    val snapshot: VehicleSnapshot,
    val findings: List<DiagnosticFinding>,
    val health: HealthScoreResult,
    val aiReport: AiCarHealthReport,
    val aiContext: AiVehicleContext,
    /** Mode 09 VIN/ECU name/calibration ID — null fields where the ECU didn't answer, never guessed. */
    val vehicleId: VehicleIdInfo,
    /** Mode 01 PID 01 emissions-readiness monitors — null if the adapter couldn't answer at all. */
    val readiness: ReadinessStatus?,
    /** The same breakdown behind the Fuel & Combustion category score — computed once here so the detail screen and the home ring never disagree. */
    val fuelCombustionInsight: FuelCombustionInsight
)

/** Transient state for the Connect -> Bluetooth -> Scanning sub-flow. Cleared once a [ScanResult] lands. */
sealed interface ConnectionFlowState {
    data object Idle : ConnectionFlowState
    data object Connecting : ConnectionFlowState
    data object Scanning : ConnectionFlowState
    data class Error(val message: String) : ConnectionFlowState
}

/** State for the continuous Live Data screen — separate from [ConnectionFlowState] since "Running" has no equivalent there. */
sealed interface LiveDataState {
    data object Idle : LiveDataState
    data object Connecting : LiveDataState
    data object Running : LiveDataState
    data class Error(val message: String) : LiveDataState
}

/** State for the Performance test screen (0-60, braking, 100m). */
sealed interface PerformanceState {
    data object Idle : PerformanceState
    data object Connecting : PerformanceState
    data object Recording : PerformanceState
    data class Finished(val result: PerformanceResult) : PerformanceState
    data class Error(val message: String) : PerformanceState
}

/** State for the Trip Mileage screen. [Finished.trend] is null when there's no km/L to compare (too short a trip, or an unsupported fuel type). */
sealed interface TripMileageState {
    data object Idle : TripMileageState
    data object Connecting : TripMileageState
    data object Recording : TripMileageState
    /** [signal] needs no trip history (works on trip 1); [trend] is null until there's a real personal baseline — see [MileageTrendAnalyzer]'s class doc for why those stay separate. */
    data class Finished(val result: TripMileageResult, val signal: String?, val trend: MileageTrendResult?) : TripMileageState
    data class Error(val message: String) : TripMileageState
}

/**
 * Orchestrates the existing 13-stage pipeline (transport -> PidReader -> rules engine ->
 * AI layer) and layers the premium-UI state on top: a persistent [latestResult] Home/Reports
 * read from, a [history] timeline (demo scans + whatever's run this session), and AI Mechanic
 * chat tied to the current result. None of the underlying OBD/PID/DTC/rules-engine code
 * changed to support this — this file only composes it differently for multi-screen nav.
 */
class GetAutoSureViewModel(application: Application) : AndroidViewModel(application) {

    private val dtcRepository = DtcRepository(application)
    private val scanHistoryStore = ScanHistoryStore(application)
    private val tripMileageStore = TripMileageStore(application)
    private val aiReportService: AiCarHealthReportService = TemplateAiCarHealthReportService()

    // In-memory only, never persisted. A real-device test showed getFreshGoogleIdToken's
    // Credential Manager call triggering an account-picker prompt on every single chat message
    // without this cache — fetching once and reusing until shortly before expiry (per the
    // token's own `exp` claim) cuts that down to roughly once an hour instead.
    private data class CachedIdToken(val token: String, val expiresAtEpochSeconds: Long)
    private var cachedIdToken: CachedIdToken? = null

    // AiBackendConfig.isConfigured is false until a real backend is deployed and its URL set
    // there — this stays local-only (unchanged default behavior) until that happens.
    private val aiChatService: AiMechanicChatService = run {
        val local = RuleBasedAiMechanicChatService(genericDtcExplainer = DtcRepositoryGenericExplainer(dtcRepository))
        if (AiBackendConfig.isConfigured) {
            FallbackAiMechanicChatService(
                remote = RemoteAiMechanicChatService(
                    endpointUrl = AiBackendConfig.ENDPOINT_URL,
                    // Fetched fresh per request (subject to cachedIdToken above), never captured
                    // once — see RemoteAiMechanicChatService's @param doc for why. Mapped to
                    // RemoteAiNotAuthenticatedException so FallbackAiMechanicChatService can
                    // tell "not signed in" apart from a network failure and surface it to the
                    // user instead of silently substituting a local answer.
                    //
                    // Deliberately reads authUiContext, NOT `application`, on a cache miss: a
                    // real-device test showed getFreshGoogleIdToken failing on every single call
                    // when given the ViewModel's Application context — Credential Manager needs
                    // an Activity-rooted context to host UI even for a "silent" request (verified
                    // against developer.android.com). See authUiContext's own doc for why that
                    // can't just be captured here the same way endpointUrl is.
                    idTokenProvider = {
                        val now = System.currentTimeMillis() / 1000
                        val cached = cachedIdToken
                        if (cached != null && cached.expiresAtEpochSeconds - TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS > now) {
                            cached.token
                        } else {
                            val uiContext = authUiContext
                                ?: throw RemoteAiNotAuthenticatedException(IllegalStateException("no UI context available for sign-in"))
                            val fresh = getFreshGoogleIdToken(uiContext).getOrElse { throw RemoteAiNotAuthenticatedException(it) }
                            val expiry = decodeJwtExpiry(fresh) ?: (now + ASSUMED_TOKEN_LIFETIME_SECONDS)
                            cachedIdToken = CachedIdToken(fresh, expiry)
                            fresh
                        }
                    },
                    knowledgeRetriever = LocalAutomotiveKnowledgeRetriever()
                ),
                local = local
            )
        } else {
            local
        }
    }

    // Set only for the duration of one askAiMechanic() call, then cleared — never held longer
    // than that single in-flight request. A ViewModel normally must never cache an Activity
    // context (it would leak across configuration changes), but Credential Manager's
    // getCredential() needs an Activity-rooted one, not `application` (see aiChatService's
    // idTokenProvider above). Bounding the lifetime to one request is the tradeoff: the window
    // for a stale reference is at most how long one token fetch takes, not the ViewModel's
    // lifetime, and it's nulled out in askAiMechanic's `finally` regardless of outcome.
    private var authUiContext: Context? = null
    val workshopService: WorkshopBookingService = PlaceholderWorkshopBookingService()
    val vehicleInfoService: VehicleInfoService = PlaceholderVehicleInfoService()

    private var transport: ObdTransport? = null

    private val _connectionState = MutableStateFlow<ConnectionFlowState>(ConnectionFlowState.Idle)
    val connectionState: StateFlow<ConnectionFlowState> = _connectionState.asStateFlow()

    private val _latestResult = MutableStateFlow<ScanResult?>(null)
    val latestResult: StateFlow<ScanResult?> = _latestResult.asStateFlow()

    private val _history = MutableStateFlow<List<ScanResult>>(emptyList())
    val history: StateFlow<List<ScanResult>> = _history.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    /** True while awaiting a reply — the rule-based service is effectively instant, but a future remote implementation won't be. */
    private val _aiMechanicLoading = MutableStateFlow(false)
    val aiMechanicLoading: StateFlow<Boolean> = _aiMechanicLoading.asStateFlow()

    /** Raw AT command/response pairs from the last real-dongle scan — see "View raw log" on the report screen. */
    private val _lastRawLog = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val lastRawLog: StateFlow<List<Pair<String, String>>> = _lastRawLog.asStateFlow()

    private val _vehicleBrand = MutableStateFlow<String?>(null)
    val vehicleBrand: StateFlow<String?> = _vehicleBrand.asStateFlow()

    private val _vehicleNickname = MutableStateFlow("")
    val vehicleNickname: StateFlow<String> = _vehicleNickname.asStateFlow()

    private val _vehicleModel = MutableStateFlow("")
    val vehicleModel: StateFlow<String> = _vehicleModel.asStateFlow()

    /** Captured during Add Vehicle but not consumed by the scan pipeline yet — year/fuel type could refine DTC explanations later, odometer could drive maintenance reminders. Nothing invents a use for them before that's actually built. */
    private val _vehicleYear = MutableStateFlow<Int?>(null)
    val vehicleYear: StateFlow<Int?> = _vehicleYear.asStateFlow()

    private val _vehicleFuelType = MutableStateFlow<String?>(null)
    val vehicleFuelType: StateFlow<String?> = _vehicleFuelType.asStateFlow()

    private val _vehicleOdometerKm = MutableStateFlow<Int?>(null)
    val vehicleOdometerKm: StateFlow<Int?> = _vehicleOdometerKm.asStateFlow()

    private val _vehiclePlate = MutableStateFlow<String?>(null)
    val vehiclePlate: StateFlow<String?> = _vehiclePlate.asStateFlow()

    /** Engine size — from the RTO lookup or typed in. Needed to estimate fuel use on cars without a MAF sensor (see [AirFlowEstimator]). */
    private val _vehicleEngineCc = MutableStateFlow<Int?>(null)
    val vehicleEngineCc: StateFlow<Int?> = _vehicleEngineCc.asStateFlow()

    fun setVehicleEngineCc(cc: Int?) {
        _vehicleEngineCc.value = cc
    }

    /** User-entered only — this app has no vehicle spec catalog, so the manufacturer-claimed (ARAI) figure is never looked up, only typed in. Shown on the Trip Mileage screen as a labeled lab-test reference, never used to judge a "drop". */
    private val _vehicleClaimedMileageKmPerLitre = MutableStateFlow<Double?>(null)
    val vehicleClaimedMileageKmPerLitre: StateFlow<Double?> = _vehicleClaimedMileageKmPerLitre.asStateFlow()

    fun setVehicleClaimedMileage(kmPerLitre: Double?) {
        _vehicleClaimedMileageKmPerLitre.value = kmPerLitre
    }

    /** Set right before navigating into a scan so the post-scan screen knows whether this is "my car" or a car being considered for purchase. Reset once the Pre-Purchase result screen is shown, so a later "Run a new scan" from Home doesn't redirect there by accident. */
    private val _scanIsPrePurchaseCheck = MutableStateFlow(false)
    val scanIsPrePurchaseCheck: StateFlow<Boolean> = _scanIsPrePurchaseCheck.asStateFlow()

    fun startPrePurchaseCheck() {
        _scanIsPrePurchaseCheck.value = true
    }

    fun clearPrePurchaseCheck() {
        _scanIsPrePurchaseCheck.value = false
    }

    private val _liveState = MutableStateFlow<LiveDataState>(LiveDataState.Idle)
    val liveState: StateFlow<LiveDataState> = _liveState.asStateFlow()

    private val _liveReadings = MutableStateFlow<Map<PidId, PidReading>>(emptyMap())
    val liveReadings: StateFlow<Map<PidId, PidReading>> = _liveReadings.asStateFlow()

    private val _liveDerived = MutableStateFlow(LiveDerived())
    val liveDerived: StateFlow<LiveDerived> = _liveDerived.asStateFlow()

    private var liveJob: Job? = null

    private val _performanceState = MutableStateFlow<PerformanceState>(PerformanceState.Idle)
    val performanceState: StateFlow<PerformanceState> = _performanceState.asStateFlow()

    private val _performanceLiveSpeed = MutableStateFlow(0)
    val performanceLiveSpeed: StateFlow<Int> = _performanceLiveSpeed.asStateFlow()

    private var performanceJob: Job? = null

    private val _tripMileageState = MutableStateFlow<TripMileageState>(TripMileageState.Idle)
    val tripMileageState: StateFlow<TripMileageState> = _tripMileageState.asStateFlow()

    private val _tripMileageLiveKmPerLitre = MutableStateFlow<Double?>(null)
    val tripMileageLiveKmPerLitre: StateFlow<Double?> = _tripMileageLiveKmPerLitre.asStateFlow()

    private var tripMileageJob: Job? = null
    private var persistedTripMileage: List<PersistedTripMileage> = emptyList()

    fun setVehicle(brand: String?, nickname: String, model: String = "") {
        _vehicleBrand.value = brand
        _vehicleNickname.value = nickname
        _vehicleModel.value = model
    }

    fun setVehicleDetails(year: Int?, fuelType: String?, odometerKm: Int?, plate: String? = null, engineCc: Int? = null) {
        _vehicleYear.value = year
        _vehicleFuelType.value = fuelType
        _vehicleOdometerKm.value = odometerKm
        _vehiclePlate.value = plate
        _vehicleEngineCc.value = engineCc
    }

    init {
        viewModelScope.launch {
            loadPersistedHistory()
            seedDemoHistory()
            persistedTripMileage = withContext(Dispatchers.IO) { tripMileageStore.load() }
        }
    }

    /** Real scans from previous app sessions — demo scans are never persisted, only the illustration they're meant to be. */
    private suspend fun loadPersistedHistory() {
        val persisted = withContext(Dispatchers.IO) { scanHistoryStore.load() }
        if (persisted.isEmpty()) return
        val results = persisted.map { p ->
            computeScanResult(p.toSnapshot(), p.vehicleId, p.readiness, p.profileLabel, p.isDemo, p.timestampMillis)
        }
        _history.update { results + it }
        _latestResult.update { results.first() }
    }

    /** Saves every real (non-demo) scan currently in [history] — called after any change to it, so disk always matches what's on screen. */
    private fun persistHistory() {
        val realScans = _history.value.filter { !it.isDemo }.map { it.toPersistedScan() }
        viewModelScope.launch(Dispatchers.IO) { scanHistoryStore.save(realScans) }
    }

    fun pairedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return try {
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun scanWithDevice(device: BluetoothDevice, vehicleBrand: String?) {
        runScan(profileLabel = "Real dongle", isDemo = false, vehicleBrand = vehicleBrand) { Elm327Client(device) }
    }

    /** Runs the full flow against the simulator — the "test mode" that needs no physical hardware. */
    fun scanSimulated(profile: SimulatedProfile, vehicleBrand: String?) {
        runScan(profileLabel = profile.name, isDemo = false, vehicleBrand = vehicleBrand) { SimulatedObdTransport(profile) }
    }

    fun resetConnectionState() {
        _connectionState.update { ConnectionFlowState.Idle }
    }

    private fun runScan(
        profileLabel: String,
        isDemo: Boolean,
        vehicleBrand: String?,
        makeTransport: () -> ObdTransport
    ) {
        // Guard against a second scan starting while one is already in flight — tapping
        // through the Connect flow again before the first scan's screen transition finishes
        // was firing two concurrent navigate("home") calls, which is what produced the
        // overlapping/garbled text seen on a real device (two crossfades stacking). Error
        // isn't "in flight" though — retrying after a failed scan must still be allowed.
        val current = _connectionState.value
        if (current is ConnectionFlowState.Connecting || current is ConnectionFlowState.Scanning) return

        viewModelScope.launch {
            _connectionState.update { ConnectionFlowState.Connecting }
            try {
                val result = buildScanResult(profileLabel, isDemo, vehicleBrand, System.currentTimeMillis(), makeTransport)
                _connectionState.update { ConnectionFlowState.Scanning }
                _latestResult.update { result }
                _history.update { listOf(result) + it }
                persistHistory()
                _chatMessages.update { emptyList() }
                _lastRawLog.update { transport?.commandLog() ?: emptyList() }
                _connectionState.update { ConnectionFlowState.Idle }
            } catch (e: Exception) {
                _connectionState.update { ConnectionFlowState.Error(e.message ?: "Couldn't complete the scan.") }
            }
        }
    }

    private suspend fun buildScanResult(
        profileLabel: String,
        isDemo: Boolean,
        vehicleBrand: String?,
        timestampMillis: Long,
        makeTransport: () -> ObdTransport
    ): ScanResult {
        val t = withContext(Dispatchers.IO) {
            val newTransport = makeTransport()
            newTransport.connect()
            newTransport
        }
        if (!isDemo) {
            transport?.close()
            transport = t
        }

        val result = readScanResult(t, profileLabel, isDemo, vehicleBrand, timestampMillis)

        if (isDemo) withContext(Dispatchers.IO) { t.close() }
        return result
    }

    /** Reads PIDs + DTCs and builds the full result from an already-connected transport — no (re)connect. */
    private suspend fun readScanResult(
        t: ObdTransport,
        profileLabel: String,
        isDemo: Boolean,
        vehicleBrand: String?,
        timestampMillis: Long
    ): ScanResult {
        val snapshot = withContext(Dispatchers.IO) {
            val reader = PidReader(t)
            val readings = reader.readAll()
            val dtcCodes = Elm327Client.parseDtcResponse(t.sendCommand("03"))

            VehicleSnapshot(
                timestampMillis = timestampMillis,
                vehicleBrand = vehicleBrand,
                readings = readings,
                dtcs = dtcCodes.map { DtcReading(it, DtcSource.STORED) }
            )
        }

        val vehicleId = withContext(Dispatchers.IO) { Mode09Reader.read(t) }
        val readiness = withContext(Dispatchers.IO) { ReadinessReader.read(t) }

        return computeScanResult(snapshot, vehicleId, readiness, profileLabel, isDemo, timestampMillis)
    }

    /**
     * The pure-compute half of a scan — everything after the transport reads. Pulled out so
     * [ScanHistoryStore]'s persisted scans (which only keep the raw snapshot/vehicleId/
     * readiness, not the derived findings/health/AI report — see that class's doc) can be
     * replayed through the exact same pipeline a live scan uses, without needing a transport.
     * This also means a historical scan always reflects current scoring logic, not whatever
     * it was the day it was taken.
     */
    private suspend fun computeScanResult(
        snapshot: VehicleSnapshot,
        vehicleId: VehicleIdInfo,
        readiness: ReadinessStatus?,
        profileLabel: String,
        isDemo: Boolean,
        timestampMillis: Long
    ): ScanResult {
        val explanations = snapshot.dtcs.associate { dtc ->
            val explained = dtcRepository.explain(dtc.code, snapshot.vehicleBrand)
            dtc.code.uppercase() to DtcExplanationInput(explained.plainLanguage, explained.severity)
        }

        val findings = DiagnosticRulesEngine.evaluate(snapshot, explanations)
        val health = HealthScoreCalculator.calculate(snapshot, findings)
        val aiContext = AiVehicleContext.from(snapshot, findings, vehicleId, readiness)
        val aiReport = aiReportService.generateReport(aiContext)

        return ScanResult(timestampMillis, profileLabel, isDemo, snapshot, findings, health, aiReport, aiContext, vehicleId, readiness, health.fuelCombustionInsight)
    }

    /**
     * Sends Mode 04 (clear stored DTCs/MIL) on the currently connected transport, then
     * re-reads everything so the UI reflects the cleared state immediately. Only available
     * after a real or test-mode scan (not a demo history entry, which has no live transport).
     * This changes the vehicle's actual ECU state — the UI gates this behind an explicit
     * confirmation dialog, same as every reference OBD2 app does.
     */
    fun clearDtcsAndRescan() {
        val t = transport ?: return
        val previous = _latestResult.value ?: return
        val current = _connectionState.value
        if (current is ConnectionFlowState.Connecting || current is ConnectionFlowState.Scanning) return

        viewModelScope.launch {
            _connectionState.update { ConnectionFlowState.Connecting }
            try {
                withContext(Dispatchers.IO) { t.sendCommand("04") }
                _connectionState.update { ConnectionFlowState.Scanning }
                val result = readScanResult(t, previous.profileLabel, isDemo = false, vehicleBrand = _vehicleBrand.value, timestampMillis = System.currentTimeMillis())
                _latestResult.update { result }
                _history.update { listOf(result) + it }
                persistHistory()
                _chatMessages.update { emptyList() }
                _lastRawLog.update { t.commandLog() }
                _connectionState.update { ConnectionFlowState.Idle }
            } catch (e: Exception) {
                _connectionState.update { ConnectionFlowState.Error(e.message ?: "Couldn't clear codes.") }
            }
        }
    }

    /** Demo/test data only, clearly labeled — illustrates the Car Health Timeline without needing five real scans. */
    private suspend fun seedDemoHistory() {
        val dayMillis = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val profiles = listOf(
            SimulatedProfile.HEALTHY to 2,
            SimulatedProfile.MISFIRE_DTC to 6,
            SimulatedProfile.OVERHEATING to 10,
            SimulatedProfile.LOW_BATTERY_VOLTAGE to 15,
            SimulatedProfile.VOLTAGE_PID_UNSUPPORTED to 21
        )
        val demoResults = profiles.map { (profile, daysAgo) ->
            buildScanResult(
                profileLabel = profile.name,
                isDemo = true,
                vehicleBrand = null,
                timestampMillis = now - daysAgo * dayMillis
            ) { SimulatedObdTransport(profile) }
        }
        _history.update { demoResults + it }
    }

    /**
     * Continuously re-reads PIDs for as long as the Live Data screen is open — a one-shot scan
     * can't show RPM climbing as you accelerate, only a repeated-read loop can. Every PID is a
     * real Bluetooth round-trip, so to stay "live" with a longer list:
     * - [LIVE_FAST_PIDS] (what moves with the pedal) are read every cycle; everything else only
     *   every [LIVE_SLOW_EVERY]th cycle — temperatures and fuel level don't change in 200ms.
     * - A PID the car reports as unsupported is never asked for again this session (and gets no
     *   tile), instead of costing a round-trip every cycle forever.
     * Mileage, trip average and boost are worked out from these readings — see [LiveDerivedCalculator].
     */
    fun startLiveData(makeTransport: () -> ObdTransport) {
        stopLiveData()
        liveJob = viewModelScope.launch {
            _liveState.update { LiveDataState.Connecting }
            try {
                val t = withContext(Dispatchers.IO) {
                    val newTransport = makeTransport()
                    newTransport.connect()
                    newTransport
                }
                transport?.close()
                transport = t

                val reader = PidReader(t)
                _liveState.update { LiveDataState.Running }

                val unsupported = mutableSetOf<PidId>()
                val latest = mutableMapOf<PidId, PidReading>()
                val tripSamples = mutableListOf<FuelSample>()
                val startNanos = System.nanoTime()
                var turboSeen = false
                var cycle = 0

                while (isActive) {
                    val toRead = LIVE_DATA_PIDS.filter { pid ->
                        pid !in unsupported && (pid in LIVE_FAST_PIDS || cycle % LIVE_SLOW_EVERY == 0)
                    }
                    val fresh = withContext(Dispatchers.IO) { toRead.associateWith { reader.read(it) } }
                    fresh.forEach { (pid, reading) ->
                        if (reading.status == PidStatus.UNSUPPORTED) unsupported += pid
                        latest[pid] = reading
                    }

                    val air = LiveDerivedCalculator.airFlow(latest, _vehicleEngineCc.value)
                    val speed = LiveDerivedCalculator.value(latest, PidId.SPEED_KMH)
                    if (air != null && speed != null) {
                        tripSamples.add(FuelSample((System.nanoTime() - startNanos) / 1_000_000, speed.toInt(), air.gramsPerSec))
                    }
                    val derived = LiveDerivedCalculator.compute(latest, _vehicleEngineCc.value, _vehicleFuelType.value, tripSamples, turboSeen)
                    turboSeen = derived.turboSeen

                    _liveReadings.update { latest.toMap() }
                    _liveDerived.update { derived }
                    cycle++
                    // A breather for the adapter, not a refresh-rate cap — the reads themselves set the pace.
                    delay(LIVE_LOOP_PAUSE_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _liveState.update { LiveDataState.Error(e.message ?: "Live data stopped unexpectedly.") }
            }
        }
    }

    fun stopLiveData() {
        liveJob?.cancel()
        liveJob = null
        _liveState.update { LiveDataState.Idle }
        _liveReadings.update { emptyMap() }
        _liveDerived.update { LiveDerived() }
    }

    /** From the Live Data screen when mileage needs details onboarding didn't collect (e.g. onboarding was skipped). */
    fun setVehicleFuelType(fuelType: String?) {
        _vehicleFuelType.value = fuelType
    }

    /**
     * Polls vehicle speed as fast as the adapter round-trip allows (no artificial delay,
     * unlike [startLiveData]'s 600ms pace) so the 0-60/braking/100m measurements in
     * [PerformanceAnalyzer] have enough time resolution to be meaningful. Stopping the test
     * (button tap, or leaving the screen) cancels the loop; the samples collected up to that
     * point are analyzed and published as [PerformanceState.Finished] rather than thrown
     * away, so a short/incomplete run still shows whatever it actually measured — nulls for
     * anything it didn't reach, never a guess.
     */
    fun startPerformanceTest(makeTransport: () -> ObdTransport) {
        stopPerformanceTest()
        val samples = mutableListOf<SpeedSample>()
        performanceJob = viewModelScope.launch {
            _performanceState.update { PerformanceState.Connecting }
            var recording = false
            try {
                val t = withContext(Dispatchers.IO) {
                    val newTransport = makeTransport()
                    newTransport.connect()
                    newTransport
                }
                transport?.close()
                transport = t

                val reader = PidReader(t)
                _performanceState.update { PerformanceState.Recording }
                recording = true
                val startNanos = System.nanoTime()

                while (isActive) {
                    val reading = withContext(Dispatchers.IO) { reader.read(PidId.SPEED_KMH) }
                    if (reading.status == PidStatus.VERIFIED && reading.value != null) {
                        val speed = reading.value.toInt()
                        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                        samples.add(SpeedSample(elapsedMs, speed))
                        _performanceLiveSpeed.update { speed }
                    }
                }
            } catch (e: CancellationException) {
                if (recording) {
                    _performanceState.update { PerformanceState.Finished(PerformanceAnalyzer.analyze(samples)) }
                }
                throw e
            } catch (e: Exception) {
                _performanceState.update { PerformanceState.Error(e.message ?: "Performance test stopped unexpectedly.") }
            }
        }
    }

    fun stopPerformanceTest() {
        performanceJob?.cancel()
        performanceJob = null
    }

    /**
     * Polls MAF + vehicle speed together (no artificial delay, same as [startPerformanceTest])
     * so [MileageAnalyzer] can integrate fuel economy over the trip. On stop, two independent
     * things are computed — see [MileageTrendAnalyzer]'s class doc for why they're kept separate:
     * a mileage-affecting signal from the latest scan (works on trip 1, no history needed), and
     * a trend vs this car's own prior trips (needs at least two to mean anything). This trip is
     * then persisted to [tripMileageStore] to extend that baseline for next time.
     */
    fun startTripMileage(makeTransport: () -> ObdTransport) {
        stopTripMileage()
        val samples = mutableListOf<FuelSample>()
        tripMileageJob = viewModelScope.launch {
            _tripMileageState.update { TripMileageState.Connecting }
            var recording = false
            try {
                val t = withContext(Dispatchers.IO) {
                    val newTransport = makeTransport()
                    newTransport.connect()
                    newTransport
                }
                transport?.close()
                transport = t

                val reader = PidReader(t)
                _tripMileageState.update { TripMileageState.Recording }
                recording = true
                val startNanos = System.nanoTime()

                // Cars without a MAF sensor (common in India) get air flow estimated from manifold
                // pressure instead — see AirFlowEstimator. Intake temperature barely moves, so it's
                // refreshed every 10th sample rather than costing a round-trip each time.
                var hasMaf = true
                var intakeAirTemp: PidReading? = null
                var sampleCount = 0
                while (isActive) {
                    val readings = withContext(Dispatchers.IO) {
                        val r = mutableMapOf(PidId.SPEED_KMH to reader.read(PidId.SPEED_KMH))
                        if (hasMaf) {
                            val maf = reader.read(PidId.MAF_AIR_FLOW_RATE)
                            r[PidId.MAF_AIR_FLOW_RATE] = maf
                            if (maf.status == PidStatus.UNSUPPORTED) hasMaf = false
                        }
                        if (!hasMaf) {
                            r[PidId.INTAKE_MANIFOLD_PRESSURE] = reader.read(PidId.INTAKE_MANIFOLD_PRESSURE)
                            r[PidId.RPM] = reader.read(PidId.RPM)
                            if (intakeAirTemp == null || sampleCount % 10 == 0) intakeAirTemp = reader.read(PidId.INTAKE_AIR_TEMP_C)
                            intakeAirTemp?.let { r[PidId.INTAKE_AIR_TEMP_C] = it }
                        }
                        r
                    }
                    sampleCount++
                    val air = LiveDerivedCalculator.airFlow(readings, _vehicleEngineCc.value)
                    val speed = LiveDerivedCalculator.value(readings, PidId.SPEED_KMH)
                    if (air != null && speed != null) {
                        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                        samples.add(FuelSample(elapsedMs, speed.toInt(), air.gramsPerSec))
                        _tripMileageLiveKmPerLitre.update { MileageAnalyzer.analyze(samples, _vehicleFuelType.value).averageKmPerLitre }
                    }
                }
            } catch (e: CancellationException) {
                if (recording) {
                    val result = MileageAnalyzer.analyze(samples, _vehicleFuelType.value)
                    val avg = result.averageKmPerLitre

                    // Needs no trip history at all — an elevated fuel trim or active misfire is
                    // a real signal on its own, so this can answer on trip 1.
                    val signal = _latestResult.value?.let { r ->
                        MileageTrendAnalyzer.currentScanSignal(
                            fuelCombustionState = r.fuelCombustionInsight.state,
                            fuelCombustionInterpretation = r.fuelCombustionInsight.interpretation,
                            activeFindings = r.findings
                        )
                    }

                    val trend = avg?.let {
                        val t = MileageTrendAnalyzer.analyze(currentKmPerLitre = it, priorTrips = persistedTripMileage)
                        val newTrip = PersistedTripMileage(System.currentTimeMillis(), result.distanceKm ?: 0.0, result.fuelLitres ?: 0.0, it)
                        persistedTripMileage = listOf(newTrip) + persistedTripMileage
                        val toSave = persistedTripMileage
                        // The coroutine is already cancelling at this point (we're in the
                        // CancellationException branch) — a plain launch/withContext here would
                        // be cancelled before it runs, so this save needs NonCancellable.
                        withContext(NonCancellable + Dispatchers.IO) { tripMileageStore.save(toSave) }
                        t
                    }
                    _tripMileageState.update { TripMileageState.Finished(result, signal, trend) }
                }
                throw e
            } catch (e: Exception) {
                _tripMileageState.update { TripMileageState.Error(e.message ?: "Trip mileage stopped unexpectedly.") }
            }
        }
    }

    fun stopTripMileage() {
        tripMileageJob?.cancel()
        tripMileageJob = null
    }

    fun askAiMechanic(question: String, uiContext: Context) {
        val context = _latestResult.value?.aiContext ?: AiVehicleContext.NO_SCAN
        // History must be the conversation BEFORE this question, not including it — passing
        // the just-asked question as its own "history" confused the follow-up/clarification
        // resolution logic, which walks history looking for what was already discussed.
        // Bounded to MAX_CHAT_MESSAGES below, same as the stored list — a remote call further
        // caps what it actually sends (see RemoteAiProtocol.MAX_HISTORY_TURNS), but the service
        // interface always receives a chronologically-ordered list, never the current question
        // appended to it twice.
        val priorHistory = _chatMessages.value
        viewModelScope.launch {
            _chatMessages.update { (it + ChatMessage(fromUser = true, text = question)).takeLast(MAX_CHAT_MESSAGES) }
            _aiMechanicLoading.value = true
            authUiContext = uiContext
            try {
                val reply = aiChatService.ask(context, priorHistory, question)
                _chatMessages.update { (it + reply).takeLast(MAX_CHAT_MESSAGES) }
            } catch (e: CancellationException) {
                // Must always propagate — e.g. this screen was navigated away from while a
                // remote call was in flight. Swallowing this here would both break structured
                // concurrency for whoever cancelled the parent coroutine, and push a confusing
                // "couldn't get an answer" message into a conversation nobody's looking at anymore.
                throw e
            } catch (e: Exception) {
                // The rule-based service can't throw by construction, but a remote implementation
                // can (network failure, timeout, malformed response) — degrade to an honest
                // message in the conversation rather than leaving the question hanging with no
                // reply, or crashing the screen via an uncaught coroutine exception. In practice
                // FallbackAiMechanicChatService already falls back to the local service before
                // this would ever fire; this is the last line of defense if it's ever bypassed.
                _chatMessages.update {
                    (it + ChatMessage(fromUser = false, text = "Sorry, I couldn't get an answer just now. Please try again.")).takeLast(MAX_CHAT_MESSAGES)
                }
            } finally {
                authUiContext = null
                _aiMechanicLoading.value = false
            }
        }
    }

    override fun onCleared() {
        liveJob?.cancel()
        performanceJob?.cancel()
        tripMileageJob?.cancel()
        transport?.close()
        super.onCleared()
    }

    companion object {
        /** Keeps the AI Mechanic chat's in-memory history from growing unbounded for the life of the ViewModel. */
        const val MAX_CHAT_MESSAGES = 40

        /** Re-fetch this long before a cached Google ID token's real expiry, so a request never starts with a token that expires mid-flight. */
        private const val TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS = 60L

        /** Used only when a token's own `exp` claim couldn't be read — observed real expiry is ~1 hour; this is deliberately shorter so a decode failure errs toward re-fetching too often, not too rarely. */
        private const val ASSUMED_TOKEN_LIFETIME_SECONDS = 15L * 60L

        /**
         * Everything Live Data reads. Deliberately the agreed short list (2026-10-27 mockup) — more
         * tiles were tried and cut as clutter. MAF and barometric pressure are read but have no
         * tile: they feed mileage and turbo boost. Unsupported ones are dropped at runtime.
         */
        val LIVE_DATA_PIDS = listOf(
            PidId.RPM,
            PidId.SPEED_KMH,
            PidId.ACCELERATOR_PEDAL_POSITION_D,
            PidId.THROTTLE_POSITION,
            PidId.ENGINE_LOAD,
            PidId.MAF_AIR_FLOW_RATE,
            PidId.INTAKE_MANIFOLD_PRESSURE,
            PidId.TIMING_ADVANCE,
            PidId.COOLANT_TEMP_C,
            PidId.INTAKE_AIR_TEMP_C,
            PidId.SHORT_TERM_FUEL_TRIM_1,
            PidId.LONG_TERM_FUEL_TRIM_1,
            PidId.FUEL_LEVEL,
            PidId.CONTROL_MODULE_VOLTAGE,
            PidId.BAROMETRIC_PRESSURE,
        )

        /** Read every cycle — the values that visibly react to the accelerator, plus what instant mileage needs. */
        val LIVE_FAST_PIDS = setOf(
            PidId.RPM,
            PidId.SPEED_KMH,
            PidId.ACCELERATOR_PEDAL_POSITION_D,
            PidId.THROTTLE_POSITION,
            PidId.ENGINE_LOAD,
            PidId.MAF_AIR_FLOW_RATE,
            PidId.INTAKE_MANIFOLD_PRESSURE,
        )

        /** Slow PIDs are read on every Nth cycle only. */
        const val LIVE_SLOW_EVERY = 5

        private const val LIVE_LOOP_PAUSE_MS = 50L
    }
}
