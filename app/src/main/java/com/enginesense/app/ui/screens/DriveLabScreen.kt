package com.enginesense.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.enginesense.app.drivelab.BatteryFindings
import com.enginesense.app.drivelab.DriveAnalyzer
import com.enginesense.app.drivelab.DriveEvent
import com.enginesense.app.drivelab.DriveLabStore
import com.enginesense.app.drivelab.DriveLabSummary
import com.enginesense.app.drivelab.DriveRecorderService
import com.enginesense.app.drivelab.DriveRecorderStatus
import com.enginesense.app.drivelab.DriveTrip
import com.enginesense.app.storage.VehicleProfileStore
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drive lab — a test screen for the phase-2 "always connected" features: trips, driving score,
 * fuel and spend, battery (resting, cranking, charging), whether the scanner sleeps, and which
 * PIDs this car answers. Everything here comes from [DriveRecorderService]'s log; once the real
 * car trial shows what works, the parts worth keeping move into the owner's Garage.
 */
@Composable
fun DriveLabScreen() {
    val context = LocalContext.current
    val status by DriveRecorderService.status.collectAsState()
    val store = remember { DriveLabStore(context) }
    val fuelType = remember { VehicleProfileStore(context).load()?.fuelType }
    val prefs = remember { context.getSharedPreferences("drive_lab", Context.MODE_PRIVATE) }

    var summary by remember { mutableStateOf<DriveLabSummary?>(null) }
    var events by remember { mutableStateOf<List<DriveEvent>>(emptyList()) }
    var support by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var sizeKb by remember { mutableStateOf(0L) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var fuelPrice by remember { mutableStateOf(prefs.getString("fuel_price", "100") ?: "100") }
    var showDevices by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTick) {
        while (true) {
            withContext(Dispatchers.IO) {
                val samples = store.loadSamples()
                summary = DriveAnalyzer.analyze(samples, fuelType)
                events = store.loadEvents().takeLast(25).reversed()
                support = store.loadSupport()
                sizeKb = store.sizeBytes() / 1024
            }
            delay(15_000)
        }
    }

    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) showDevices = true
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        modifier = Modifier.fillMaxSize().background(GetAutoSureColors.Background).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Drive lab", style = GetAutoSureType.ScreenTitle)
            Text("Test: keep the scanner plugged in and see what we can record over 1–2 days.", style = GetAutoSureType.Caption)
        }

        // Recorder status and controls
        LabCard {
            Text(status.phase.label, style = GetAutoSureType.SectionTitle, color = phaseColor(status.phase))
            status.deviceName?.let { Text("Scanner: $it", style = GetAutoSureType.Caption) }
            status.lastContactMillis?.let { Text("Last reply: ${ago(it)}", style = GetAutoSureType.Caption) }
            val live = listOfNotNull(
                status.lastSpeedKmh?.takeIf { status.phase == DriveRecorderStatus.Phase.DRIVING }?.let { "%.0f km/h".format(it) },
                status.lastVolts?.let { "%.1f V".format(it) },
                "${status.samplesThisRun} readings this run"
            )
            Text(live.joinToString(" · "), style = GetAutoSureType.Body)
            status.lastError?.let { Text(it, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch)) }
            Spacer(Modifier.height(4.dp))
            if (status.running) {
                OutlinedButton(onClick = { DriveRecorderService.stop(context) }, modifier = Modifier.fillMaxWidth()) { Text("Stop recording") }
            } else {
                Button(
                    onClick = { if (hasBluetoothPermission(context)) showDevices = true else btLauncher.launch(bluetoothPermissions()) },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("Start recording") }
            }
            Text("While recording, the normal Scan can't use the scanner. Stop here first.", style = GetAutoSureType.Caption)
        }

        // What Android needs for an all-day connection
        LabCard {
            Text("Keep it running", style = GetAutoSureType.SectionTitle)
            val notificationsOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            CheckRow("Notifications allowed", notificationsOk) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val batteryOk = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
            CheckRow("Battery saver off for GetAutoSure", batteryOk) {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            Text(
                "On Samsung, Xiaomi, Oppo and Vivo also set GetAutoSure to \"Unrestricted\" / \"No restrictions\" in the app's battery settings, or the phone may stop the recorder.",
                style = GetAutoSureType.Caption
            )
        }

        val s = summary
        if (s == null) {
            Text("Loading…", style = GetAutoSureType.Caption)
        } else {
            SummaryCard(s, fuelPrice.toDoubleOrNull(), fuelPrice) {
                fuelPrice = it
                prefs.edit().putString("fuel_price", it).apply()
            }
            BatteryCard(s.battery)
            CarDataCard(s, support)
            Text("Trips", style = GetAutoSureType.SectionTitle)
            if (s.trips.isEmpty()) {
                Text("No trips yet. A trip is recorded when the engine runs for more than a minute.", style = GetAutoSureType.Caption)
            }
            s.trips.take(30).forEach { TripCard(it, fuelPrice.toDoubleOrNull()) }
        }

        LabCard {
            Text("Connection log", style = GetAutoSureType.SectionTitle)
            if (events.isEmpty()) Text("Nothing yet.", style = GetAutoSureType.Caption)
            events.forEach { e ->
                Text("${time(e.timeMillis)}  ${e.type.replace('_', ' ')}${if (e.detail.isNotBlank()) " · ${e.detail}" else ""}", style = GetAutoSureType.Caption)
            }
        }

        LabCard {
            Text("Data on this phone: $sizeKb KB", style = GetAutoSureType.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { shareExport(context, store) }, modifier = Modifier.weight(1f)) { Text("Export data") }
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
            if (confirmClear) {
                Text("Delete all recorded drive data on this phone? This can't be undone.", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { store.clear(); confirmClear = false; refreshTick++ }) { Text("Delete", color = GetAutoSureColors.Attention) }
                    TextButton(onClick = { confirmClear = false }) { Text("Keep") }
                }
            }
            Text("Export shares the raw CSV files (every reading, the connection log and the PID list) as a zip.", style = GetAutoSureType.Caption)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDevices) {
        val devices = remember { pairedScanners() }
        PickerBottomSheet(
            title = "Choose your scanner",
            items = devices,
            itemLabel = { label(it) },
            emptyText = "No paired scanner found. Pair \"GetAutoSure\" in Bluetooth settings (PIN 1234) first.",
            onDismiss = { showDevices = false },
            onPick = { device ->
                showDevices = false
                DriveRecorderService.start(context, device.address)
            }
        )
    }
}

@Composable
private fun SummaryCard(s: DriveLabSummary, price: Double?, priceText: String, onPrice: (String) -> Unit) = LabCard {
    Text("So far", style = GetAutoSureType.SectionTitle)
    val scores = s.trips.map { it.drivingScore }
    Stat("Trips", "${s.trips.size}")
    Stat("Distance", "%.1f km".format(s.totalKm))
    Stat("Fuel used (estimate)", s.totalFuelLitres?.let { "%.2f L".format(it) } ?: "Not measured")
    Stat("Fuel cost (estimate)", if (s.totalFuelLitres != null && price != null) "₹%.0f".format(s.totalFuelLitres * price) else "—")
    Stat("Average driving score", if (scores.isEmpty()) "—" else "${scores.average().toInt()} / 100")
    Stat("Readings", "${s.engineOnSamples} engine on · ${s.engineOffSamples} engine off")
    OutlinedTextField(
        value = priceText,
        onValueChange = { v -> onPrice(v.filter { it.isDigit() || it == '.' }.take(6)) },
        label = { Text("Fuel price, ₹ per litre") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BatteryCard(b: BatteryFindings) = LabCard {
    Text("Battery", style = GetAutoSureType.SectionTitle)
    Stat("Engine off (after 30+ min if we have it)", b.restingVolts?.let { "%.2f V".format(it) } ?: "Not yet")
    Stat("Lowest resting", b.lowestRestingVolts?.let { "%.2f V".format(it) } ?: "—")
    Stat("While driving (charging)", b.chargingVolts?.let { "%.2f V".format(it) } ?: "—")
    Stat("Dips while starting", if (b.crankingDips.isEmpty()) "Not caught yet" else b.crankingDips.takeLast(5).joinToString(", ") { "%.1f V".format(it) })
    Text(batteryNote(b), style = GetAutoSureType.Caption)
    Spacer(Modifier.height(6.dp))
    Text("Scanner sleep", style = GetAutoSureType.SectionTitle)
    val awake = b.longestAwakeAfterOffMinutes
    Text(
        when {
            awake == null -> "No engine-off contact yet. If the link drops every time the engine stops, the scanner is sleeping (good for the car battery)."
            awake >= 120 -> "Still answering $awake min after the engine stopped: it doesn't seem to sleep. Watch the resting voltage overnight for drain."
            else -> "Answered for up to $awake min after the engine stopped, then went quiet."
        },
        style = GetAutoSureType.Body
    )
}

private fun batteryNote(b: BatteryFindings): String {
    val rest = b.restingVolts
    val dip = b.crankingDips.lastOrNull()
    val parts = mutableListOf<String>()
    if (rest != null) parts += when {
        rest >= 12.6 -> "Resting voltage looks like a full battery."
        rest >= 12.4 -> "Resting voltage is a little low (about 75% charged)."
        else -> "Resting voltage is low: the battery may be weak or being drained."
    }
    if (dip != null) parts += if (dip >= 10.0) "Start-up dip is healthy (above 10 V)." else "Start-up dip below 10 V: the battery is getting weak."
    val charge = b.chargingVolts
    if (charge != null) parts += if (charge in 13.5..14.8) "Alternator charging looks normal." else "Charging voltage is outside 13.5–14.8 V: get the alternator checked."
    return if (parts.isEmpty()) "Rough guide only. Needs a night parked and a few starts." else parts.joinToString(" ")
}

@Composable
private fun CarDataCard(s: DriveLabSummary, support: List<List<String>>) = LabCard {
    Text("What this car reports", style = GetAutoSureType.SectionTitle)
    Stat("Odometer from the car", s.latestOdometerKm?.let { "%,.0f km".format(it) } ?: if (support.any { it.getOrNull(0) == "0xA6" && it.getOrNull(2) == "VERIFIED" }) "Supported" else "Not reported")
    Stat("Km since codes cleared", s.latestDistanceSinceClearedKm?.let { "%,.0f km".format(it) } ?: "—")
    Stat("Fuel level", if (s.fuelLevelLast != null) "%.0f%% (started at %.0f%%)".format(s.fuelLevelLast, s.fuelLevelFirst ?: s.fuelLevelLast) else "Not reported")
    if (support.isEmpty()) {
        Text("The full list appears after the first drive.", style = GetAutoSureType.Caption)
    } else {
        val answered = support.filter { it.getOrNull(2) == "VERIFIED" }
        Text("${answered.size} of ${support.size} readings answered:", style = GetAutoSureType.Caption)
        Text(answered.joinToString(", ") { it.getOrElse(1) { "" } }, style = GetAutoSureType.Caption)
    }
}

@Composable
private fun TripCard(t: DriveTrip, price: Double?) = LabCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${time(t.startMillis)} – ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t.endMillis))}", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
            Text("%.1f km · %d min".format(t.distanceKm, t.durationSeconds / 60), style = GetAutoSureType.Caption)
        }
        Text("${t.drivingScore}", style = GetAutoSureType.SectionTitle, color = scoreColor(t.drivingScore))
    }
    Stat("Harsh braking / acceleration", "${t.harshBrakes} / ${t.harshAccelerations}")
    Stat("Idling", "${t.idleSeconds / 60} min ${t.idleSeconds % 60} s")
    Stat("Top / average speed", "${t.maxSpeedKmh?.let { "%.0f".format(it) } ?: "—"} / ${t.avgMovingSpeedKmh?.let { "%.0f".format(it) } ?: "—"} km/h")
    if (t.overSpeedSeconds > 0) Stat("Above ${DriveAnalyzer.OVER_SPEED_KMH.toInt()} km/h", "${t.overSpeedSeconds / 60} min")
    if (t.highRpmSeconds > 0) Stat("Above ${DriveAnalyzer.HIGH_RPM.toInt()} rpm", "${t.highRpmSeconds} s")
    Stat("Mileage", t.kmPerLitre?.let { "%.1f km/L".format(it) } ?: "—")
    Stat("Fuel", t.fuelLitres?.let { l -> "%.2f L".format(l) + (price?.let { " · ₹%.0f".format(l * it) } ?: "") } ?: "—")
    Stat("Engine temp max", t.maxCoolantC?.let { "%.0f °C".format(it) } ?: "—")
    t.warmUpSeconds?.let { Stat("Warm-up time", "${it / 60} min ${it % 60} s") }
    t.crankingMinVolts?.let { Stat("Voltage at start", "%.1f V".format(it)) }
}

@Composable
private fun LabCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(GetAutoSureColors.Surface, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = GetAutoSureType.Caption, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = GetAutoSureType.Body)
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(if (ok) "✓" else "!", color = if (ok) GetAutoSureColors.Good else GetAutoSureColors.Watch, style = GetAutoSureType.SectionTitle, modifier = Modifier.width(24.dp))
        Text(label, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
        if (!ok) TextButton(onClick = onFix) { Text("Fix") }
    }
}

private fun phaseColor(p: DriveRecorderStatus.Phase): Color = when (p) {
    DriveRecorderStatus.Phase.DRIVING -> GetAutoSureColors.Good
    DriveRecorderStatus.Phase.ENGINE_OFF, DriveRecorderStatus.Phase.IGNITION_ON -> GetAutoSureColors.TextPrimary
    DriveRecorderStatus.Phase.CONNECTING, DriveRecorderStatus.Phase.WAITING -> GetAutoSureColors.Watch
    DriveRecorderStatus.Phase.STOPPED -> GetAutoSureColors.TextSecondary
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> GetAutoSureColors.Good
    score >= 60 -> GetAutoSureColors.Watch
    else -> GetAutoSureColors.Attention
}

private fun time(millis: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

private fun ago(millis: Long): String {
    val s = (System.currentTimeMillis() - millis) / 1000
    return when {
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60} min ago"
        else -> "${s / 3600} h ${(s % 3600) / 60} min ago"
    }
}

@SuppressLint("MissingPermission") // the picker only opens after the Bluetooth permission is granted
private fun pairedScanners(): List<BluetoothDevice> =
    (BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList()).sortedBy { d ->
        val name = (d.name ?: "").lowercase()
        when {
            "getautosure" in name -> 0
            "obd" in name || "elm" in name || "vlink" in name -> 1
            else -> 2
        }
    }

@SuppressLint("MissingPermission")
private fun label(d: BluetoothDevice): String = d.name ?: d.address

private fun shareExport(context: Context, store: DriveLabStore) {
    val zip = store.exportZip(context.cacheDir)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share drive data"))
}
