package com.enginesense.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.enginesense.app.BuildConfig
import androidx.compose.ui.unit.dp
import com.enginesense.app.ConnectionFlowState
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.obd.Elm327Client
import com.enginesense.app.obd.SimulatedObdTransport
import com.enginesense.app.obd.SimulatedProfile
import com.enginesense.app.ui.components.StatusDot
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

/** India-market makes, most common first. */
val VEHICLE_MAKES = listOf(
    "Other / not sure",
    "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Kia", "Honda", "Toyota",
    "Volkswagen", "Skoda", "Renault", "Nissan", "Ford", "MG Motor", "Jeep",
    "Citroen", "BYD", "Mercedes-Benz", "BMW", "Audi", "Volvo", "Land Rover",
    "Jaguar", "Lexus", "Isuzu", "Fiat", "Mitsubishi", "Datsun"
)

/** Common current/recent models per make — a dropdown instead of free text, per feedback. Not exhaustive; "Other / not sure" always available. */
val MODELS_BY_MAKE: Map<String, List<String>> = mapOf(
    "Maruti Suzuki" to listOf("Swift", "Baleno", "Dzire", "WagonR", "Alto", "Celerio", "Ertiga", "Brezza", "Ignis", "S-Presso", "XL6", "Fronx", "Jimny", "Grand Vitara", "Invicto"),
    "Hyundai" to listOf("Grand i10 Nios", "i20", "Venue", "Creta", "Verna", "Alcazar", "Exter", "Aura", "Tucson", "Ioniq 5"),
    "Tata" to listOf("Nexon", "Punch", "Tiago", "Tigor", "Altroz", "Harrier", "Safari", "Curvv"),
    "Mahindra" to listOf("XUV300", "XUV700", "Scorpio Classic", "Scorpio-N", "Thar", "Bolero", "XUV400", "Marazzo"),
    "Kia" to listOf("Seltos", "Sonet", "Carens", "Carnival", "EV6"),
    "Honda" to listOf("City", "Amaze", "Elevate", "WR-V", "Jazz"),
    "Toyota" to listOf("Innova Crysta", "Fortuner", "Glanza", "Urban Cruiser Hyryder", "Camry", "Rumion", "Hilux"),
    "Volkswagen" to listOf("Polo", "Vento", "Taigun", "Virtus", "Tiguan"),
    "Skoda" to listOf("Slavia", "Kushaq", "Octavia", "Superb", "Kodiaq"),
    "Renault" to listOf("Kwid", "Triber", "Kiger"),
    "Nissan" to listOf("Magnite", "Kicks"),
    "Ford" to listOf("EcoSport", "Figo", "Endeavour", "Aspire"),
    "MG Motor" to listOf("Hector", "Astor", "ZS EV", "Comet", "Gloster"),
    "Jeep" to listOf("Compass", "Meridian", "Wrangler"),
    "Citroen" to listOf("C3", "C3 Aircross", "eC3", "Basalt"),
    "BYD" to listOf("Atto 3", "Seal", "e6"),
    "Mercedes-Benz" to listOf("C-Class", "E-Class", "GLA", "GLC", "S-Class"),
    "BMW" to listOf("3 Series", "5 Series", "X1", "X3", "X5"),
    "Audi" to listOf("A4", "A6", "Q3", "Q5", "Q7"),
    "Volvo" to listOf("XC40", "XC60", "XC90", "S90"),
    "Land Rover" to listOf("Range Rover", "Range Rover Evoque", "Discovery Sport", "Defender"),
    "Jaguar" to listOf("XF", "F-Pace"),
    "Lexus" to listOf("ES", "NX", "RX"),
    "Isuzu" to listOf("D-Max", "MU-X", "V-Cross"),
    "Fiat" to listOf("Punto", "Linea"),
    "Mitsubishi" to listOf("Pajero Sport"),
    "Datsun" to listOf("GO", "GO+")
)

const val OTHER_MODEL = "Other / not sure"

fun modelsFor(make: String): List<String> = listOf(OTHER_MODEL) + (MODELS_BY_MAKE[make] ?: emptyList())

/** What picking a device/test-profile in [ConnectScreen] actually does — one sheet pair reused for all four flows. */
private enum class ConnectIntent { SCAN, LIVE_DATA, PERFORMANCE, TRIP_MILEAGE }

/** What Android needs before the app can see or talk to a paired scanner. Asked for only when the user first connects. */
internal fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasBluetoothPermission(context: Context): Boolean =
    bluetoothPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@Composable
fun ConnectScreen(
    viewModel: GetAutoSureViewModel,
    onScanStarted: () -> Unit,
    onLiveDataStarted: () -> Unit,
    onPerformanceTestStarted: () -> Unit,
    onTripMileageStarted: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val vehicleBrand by viewModel.vehicleBrand.collectAsState()
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showSimSheet by remember { mutableStateOf(false) }
    var intent by remember { mutableStateOf(ConnectIntent.SCAN) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionDenied = !grants.values.all { it }
        if (!permissionDenied) showDeviceSheet = true
    }
    fun connectWithScanner(next: ConnectIntent) {
        intent = next
        if (hasBluetoothPermission(context)) showDeviceSheet = true else permissionLauncher.launch(bluetoothPermissions())
    }
    fun testMode(next: ConnectIntent) {
        intent = next
        showSimSheet = true
    }

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionFlowState.Connecting || connectionState is ConnectionFlowState.Scanning) {
            onScanStarted()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Scan your car", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(16.dp))
        listOf(
            "Plug the GetAutoSure scanner into the port under the steering wheel.",
            "Turn the ignition on. The engine can stay off.",
            "First time? Pair \"GetAutoSure\" in your phone's Bluetooth settings (PIN 1234)."
        ).forEachIndexed { i, step ->
            Row(modifier = Modifier.padding(bottom = 10.dp)) {
                Text("${i + 1}", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Accent), modifier = Modifier.width(24.dp))
                Text(step, style = GetAutoSureType.Body)
            }
        }
        Spacer(Modifier.height(16.dp))

        Button(onClick = { connectWithScanner(ConnectIntent.SCAN) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Scan my car")
        }
        if (permissionDenied) {
            Spacer(Modifier.height(8.dp))
            Text(
                "GetAutoSure needs the Nearby devices (Bluetooth) permission to talk to your scanner. Tap Scan my car to allow it.",
                style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention)
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("More with your scanner", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { connectWithScanner(ConnectIntent.LIVE_DATA) }, modifier = Modifier.fillMaxWidth()) {
            Text("Live readings while driving")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { connectWithScanner(ConnectIntent.PERFORMANCE) }, modifier = Modifier.fillMaxWidth()) {
            Text("0-60, braking & 100 m timer")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { connectWithScanner(ConnectIntent.TRIP_MILEAGE) }, modifier = Modifier.fillMaxWidth()) {
            Text("Mileage (km/L) for a drive")
        }

        if (connectionState is ConnectionFlowState.Error) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Couldn't connect: ${(connectionState as ConnectionFlowState.Error).message}",
                color = GetAutoSureColors.Attention,
                style = GetAutoSureType.Body
            )
        }

        // Simulated cars for testing without a scanner. Debug builds only, so customers never see it.
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(28.dp))
            Text("Test mode (debug builds only)", style = GetAutoSureType.Caption)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { testMode(ConnectIntent.SCAN) }, modifier = Modifier.weight(1f)) { Text("Scan") }
                TextButton(onClick = { testMode(ConnectIntent.LIVE_DATA) }, modifier = Modifier.weight(1f)) { Text("Live") }
                TextButton(onClick = { testMode(ConnectIntent.PERFORMANCE) }, modifier = Modifier.weight(1f)) { Text("0-60") }
                TextButton(onClick = { testMode(ConnectIntent.TRIP_MILEAGE) }, modifier = Modifier.weight(1f)) { Text("Mileage") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDeviceSheet) {
        // The GetAutoSure scanner (or any OBD adapter) first, so new users don't have to guess.
        val devices = viewModel.pairedDevices().sortedBy { device ->
            val name = deviceLabel(device).lowercase()
            when {
                "getautosure" in name -> 0
                "obd" in name || "elm" in name || "vlink" in name -> 1
                else -> 2
            }
        }
        PickerBottomSheet(
            title = "Choose your scanner",
            items = devices,
            itemLabel = { deviceLabel(it) },
            emptyText = "No paired scanner found. Open your phone's Bluetooth settings, pair \"GetAutoSure\" (PIN 1234), then come back.",
            onDismiss = { showDeviceSheet = false },
            onPick = { device ->
                showDeviceSheet = false
                when (intent) {
                    ConnectIntent.SCAN -> viewModel.scanWithDevice(device, vehicleBrand)
                    ConnectIntent.LIVE_DATA -> { viewModel.startLiveData { Elm327Client(device) }; onLiveDataStarted() }
                    ConnectIntent.PERFORMANCE -> { viewModel.startPerformanceTest { Elm327Client(device) }; onPerformanceTestStarted() }
                    ConnectIntent.TRIP_MILEAGE -> { viewModel.startTripMileage { Elm327Client(device) }; onTripMileageStarted() }
                }
            }
        )
    }

    if (showSimSheet) {
        PickerBottomSheet(
            title = "Choose a test vehicle",
            items = SimulatedProfile.entries,
            itemLabel = { profileLabel(it) },
            itemSubtitle = { profileDescription(it) },
            onDismiss = { showSimSheet = false },
            onPick = { profile ->
                showSimSheet = false
                when (intent) {
                    ConnectIntent.SCAN -> viewModel.scanSimulated(profile, vehicleBrand)
                    ConnectIntent.LIVE_DATA -> { viewModel.startLiveData { SimulatedObdTransport(profile) }; onLiveDataStarted() }
                    ConnectIntent.PERFORMANCE -> { viewModel.startPerformanceTest { SimulatedObdTransport(profile) }; onPerformanceTestStarted() }
                    ConnectIntent.TRIP_MILEAGE -> { viewModel.startTripMileage { SimulatedObdTransport(profile) }; onTripMileageStarted() }
                }
            }
        )
    }
}

@SuppressLint("MissingPermission") // only called after ConnectScreen has the Bluetooth permission
private fun deviceLabel(device: BluetoothDevice): String = device.name ?: device.address

/**
 * One styled bottom sheet used for every "pick one of these" moment in the app (brand,
 * paired device, test profile) — replaces the earlier plain AlertDialog+LazyColumn, which
 * rendered with broken sizing (a LazyColumn inside an AlertDialog's content slot has no
 * height constraint to measure against, so the dialog ballooned and overlapped the screen
 * behind it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickerBottomSheet(
    title: String,
    items: List<T>,
    itemLabel: (T) -> String,
    itemSubtitle: ((T) -> String)? = null,
    emptyText: String? = null,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GetAutoSureColors.Surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, style = GetAutoSureType.SectionTitle)
            Spacer(Modifier.height(12.dp))

            if (items.isEmpty() && emptyText != null) {
                Text(emptyText, style = GetAutoSureType.Body.copy(color = GetAutoSureColors.TextSecondary))
            } else {
                items.forEach { item ->
                    PickerRow(itemLabel(item), itemSubtitle?.invoke(item)) { onPick(item) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GetAutoSureColors.SurfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(GetAutoSureColors.Accent)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = GetAutoSureType.Body)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = GetAutoSureType.Caption)
            }
        }
    }
}

/**
 * How a scan's source reads on screen: nothing for a real scanner (that's the normal case), and a
 * plain "Test: …" name for simulated ones instead of the internal profile name.
 */
fun scanSourceLabel(profileLabel: String): String? =
    if (profileLabel == "Real dongle") null
    else SimulatedProfile.entries.firstOrNull { it.name == profileLabel }?.let { "Test: ${profileLabel(it)}" } ?: profileLabel

fun profileLabel(profile: SimulatedProfile): String = when (profile) {
    SimulatedProfile.HEALTHY -> "Healthy car"
    SimulatedProfile.MISFIRE_DTC -> "Cylinder misfire (DTC)"
    SimulatedProfile.OVERHEATING -> "Overheating engine"
    SimulatedProfile.LOW_BATTERY_VOLTAGE -> "Low battery voltage"
    SimulatedProfile.VOLTAGE_PID_UNSUPPORTED -> "Older car (voltage PID unsupported)"
    SimulatedProfile.SELLER_CLEARED_CODES -> "Used car, codes cleared by seller"
}

private fun profileDescription(profile: SimulatedProfile): String = when (profile) {
    SimulatedProfile.HEALTHY -> "Everything verified, no issues"
    SimulatedProfile.MISFIRE_DTC -> "Stored DTC P0301 — critical"
    SimulatedProfile.OVERHEATING -> "Live coolant reading over 110°C"
    SimulatedProfile.LOW_BATTERY_VOLTAGE -> "Live voltage reading under 11.5V"
    SimulatedProfile.VOLTAGE_PID_UNSUPPORTED -> "Battery category shows 'No data', not fake-good"
    SimulatedProfile.SELLER_CLEARED_CODES -> "Permanent P0420, pending P0171, pre-warmed engine"
}

@Composable
fun ScanningScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = GetAutoSureColors.Accent, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text("Scanning your car…", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        Text("Reading engine, battery, and cooling data", style = GetAutoSureType.Caption)
    }
}
