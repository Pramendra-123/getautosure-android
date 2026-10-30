package com.enginesense.app.ui.screens

import android.annotation.SuppressLint
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

@Composable
fun ConnectScreen(
    viewModel: GetAutoSureViewModel,
    onScanStarted: () -> Unit,
    onLiveDataStarted: () -> Unit,
    onPerformanceTestStarted: () -> Unit,
    onTripMileageStarted: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val vehicleBrand by viewModel.vehicleBrand.collectAsState()
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showSimSheet by remember { mutableStateOf(false) }
    var intent by remember { mutableStateOf(ConnectIntent.SCAN) }

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionFlowState.Connecting || connectionState is ConnectionFlowState.Scanning) {
            onScanStarted()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Connect your OBD2 adapter", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pair your ELM327 dongle in Android Bluetooth settings first, then connect here.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(32.dp))

        Button(onClick = { intent = ConnectIntent.SCAN; showDeviceSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Connect to dongle")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { intent = ConnectIntent.SCAN; showSimSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Run in test mode (no hardware)")
        }

        Spacer(Modifier.height(24.dp))
        Text("Or watch readings update live while driving:", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { intent = ConnectIntent.LIVE_DATA; showDeviceSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Live data from dongle")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { intent = ConnectIntent.LIVE_DATA; showSimSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Live data (test mode)")
        }

        Spacer(Modifier.height(24.dp))
        Text("Or time a 0-60, a stop, or a 100m run:", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { intent = ConnectIntent.PERFORMANCE; showDeviceSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Performance test from dongle")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { intent = ConnectIntent.PERFORMANCE; showSimSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Performance test (test mode)")
        }

        Spacer(Modifier.height(24.dp))
        Text("Or estimate fuel economy (km/L) for a drive:", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { intent = ConnectIntent.TRIP_MILEAGE; showDeviceSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Trip mileage from dongle")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { intent = ConnectIntent.TRIP_MILEAGE; showSimSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Trip mileage (test mode)")
        }

        if (connectionState is ConnectionFlowState.Error) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Couldn't connect: ${(connectionState as ConnectionFlowState.Error).message}",
                color = GetAutoSureColors.Attention,
                style = GetAutoSureType.Body
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDeviceSheet) {
        val devices = viewModel.pairedDevices()
        PickerBottomSheet(
            title = "Paired devices",
            items = devices,
            itemLabel = { deviceLabel(it) },
            emptyText = "No paired Bluetooth devices found. Pair your ELM327 dongle in system Bluetooth settings first, or use test mode.",
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

@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is already granted before this screen is reachable
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
