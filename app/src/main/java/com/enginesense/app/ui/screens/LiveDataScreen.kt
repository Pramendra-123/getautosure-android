package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.LiveDataState
import com.enginesense.app.ui.components.StatTile
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.AirFlowSource
import com.enginesense.app.vehicle.LiveDerived
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidReading
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehiclelookup.RcPrefillMapper

/**
 * The continuous "watch it change as you drive" screen. The top group is what reacts to the
 * accelerator plus what's calculated from it (mileage, trip average, boost); below are the rest
 * of the car's sensors in groups. Only PIDs this car actually supports get a tile — see
 * [GetAutoSureViewModel.startLiveData] for the polling (fast/slow tiers, unsupported dropped).
 *
 * The transport is already connecting/connected by the time this screen opens — the Connect
 * screen starts it before navigating here, so this screen only observes state and stops it on exit.
 */
@Composable
fun LiveDataScreen(viewModel: GetAutoSureViewModel, onStop: () -> Unit) {
    val state by viewModel.liveState.collectAsState()
    val readings by viewModel.liveReadings.collectAsState()
    val derived by viewModel.liveDerived.collectAsState()
    val fuelType by viewModel.vehicleFuelType.collectAsState()
    val engineCc by viewModel.vehicleEngineCc.collectAsState()
    var showDetailsDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopLiveData() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Live Data", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text("Updates continuously — watch it change as you drive.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            is LiveDataState.Connecting, LiveDataState.Idle -> LiveLoadingRow("Connecting…")
            is LiveDataState.Error -> Text("Stopped: ${s.message}", color = GetAutoSureColors.Attention, style = GetAutoSureType.Body)
            is LiveDataState.Running -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item { MileageNowTile(derived) }
                    item { TripAverageTile(derived) }
                    derived.mileageUnavailableReason?.let { reason ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(reason, style = GetAutoSureType.Caption)
                                if (fuelType == null || engineCc == null) {
                                    TextButton(onClick = { showDetailsDialog = true }, contentPadding = PaddingValues(0.dp)) {
                                        Text("Add car details")
                                    }
                                }
                            }
                        }
                    }
                    pidTiles(readings, TOP_PIDS)
                    val boost = derived.boostKpa
                    if (derived.turboSeen && boost != null) {
                        item { StatTile("Turbo boost", formatBoost(boost), Modifier.fillMaxWidth()) }
                    }
                    GROUPS.forEach { (title, pids) ->
                        if (pids.any { readings.isShown(it) }) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(title, style = GetAutoSureType.Caption, modifier = Modifier.padding(top = 8.dp))
                            }
                            pidTiles(readings, pids)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
    }

    if (showDetailsDialog) {
        CarDetailsDialog(
            initialFuel = fuelType,
            initialCc = engineCc,
            onDismiss = { showDetailsDialog = false },
            onSave = { fuel, cc ->
                viewModel.setVehicleFuelType(fuel)
                viewModel.setVehicleEngineCc(cc)
                showDetailsDialog = false
            }
        )
    }
}

/** Shown in this order at the top — the values that visibly react to the accelerator. */
private val TOP_PIDS = listOf(PidId.RPM, PidId.SPEED_KMH, PidId.ACCELERATOR_PEDAL_POSITION_D, PidId.THROTTLE_POSITION)

private val GROUPS: List<Pair<String, List<PidId>>> = listOf(
    "Engine" to listOf(
        PidId.ENGINE_LOAD, PidId.INTAKE_MANIFOLD_PRESSURE, PidId.MAF_AIR_FLOW_RATE, PidId.TIMING_ADVANCE,
        PidId.ABSOLUTE_LOAD_VALUE, PidId.ACTUAL_ENGINE_TORQUE_PERCENT, PidId.COMMANDED_THROTTLE_ACTUATOR,
        PidId.RUN_TIME_SINCE_START,
    ),
    "Temperatures" to listOf(
        PidId.COOLANT_TEMP_C, PidId.INTAKE_AIR_TEMP_C, PidId.ENGINE_OIL_TEMP_C, PidId.CATALYST_TEMP_B1S1, PidId.AMBIENT_AIR_TEMP_C,
    ),
    "Fuel and air" to listOf(
        PidId.SHORT_TERM_FUEL_TRIM_1, PidId.LONG_TERM_FUEL_TRIM_1, PidId.O2_SENSOR_B1S1_VOLTAGE, PidId.O2_WIDEBAND_B1S1_LAMBDA,
        PidId.FUEL_AIR_COMMANDED_RATIO, PidId.FUEL_RAIL_PRESSURE_ABS, PidId.FUEL_PRESSURE, PidId.FUEL_LEVEL, PidId.COMMANDED_EVAP_PURGE,
    ),
    "Electrical and outside" to listOf(PidId.CONTROL_MODULE_VOLTAGE, PidId.BAROMETRIC_PRESSURE),
)

/** A tile appears once the PID has been read at least once and the car didn't say it's unsupported. */
private fun Map<PidId, PidReading>.isShown(pid: PidId): Boolean =
    this[pid]?.let { it.status != PidStatus.UNSUPPORTED } ?: false

private fun LazyGridScope.pidTiles(readings: Map<PidId, PidReading>, pids: List<PidId>) {
    items(pids.filter { readings.isShown(it) }) { pid ->
        val reading = readings[pid]
        StatTile(tileLabel(pid), formatLive(pid, reading?.status, reading?.value), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MileageNowTile(derived: LiveDerived) {
    val instant = derived.instant
    val value = when {
        instant == null -> "—"
        instant.kmPerLitre != null -> "%.1f km/l".format(instant.kmPerLitre)
        else -> "%.1f L/h".format(instant.litresPerHour) // idling — km/l is meaningless at standstill
    }
    StatTile("Mileage now", value, Modifier.fillMaxWidth(), note = sourceNote(derived))
}

@Composable
private fun TripAverageTile(derived: LiveDerived) {
    val trip = derived.trip
    val value = trip?.averageKmPerLitre?.let { "%.1f km/l".format(it) } ?: "—"
    val note = trip?.distanceKm?.let { d -> "%.1f km · %.2f L".format(d, trip.fuelLitres ?: 0.0) }
    StatTile("Trip average", value, Modifier.fillMaxWidth(), note = note)
}

private fun sourceNote(derived: LiveDerived): String? = when (derived.airFlowSource) {
    AirFlowSource.ESTIMATED_FROM_MAP -> "Estimated"
    AirFlowSource.MEASURED -> "From air-flow sensor"
    null -> null
}

private fun formatBoost(kpa: Double): String =
    if (kpa >= 0) "+%.0f kPa".format(kpa) else "%.0f kPa (vacuum)".format(kpa)

@Composable
private fun CarDetailsDialog(initialFuel: String?, initialCc: Int?, onDismiss: () -> Unit, onSave: (String?, Int?) -> Unit) {
    var fuel by remember { mutableStateOf(initialFuel) }
    var cc by remember { mutableStateOf(initialCc?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Car details for mileage") },
        text = {
            Column {
                Text("Fuel type", style = GetAutoSureType.Caption)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Petrol", "Diesel").forEach { option ->
                        FilterChip(selected = fuel == option, onClick = { fuel = option }, label = { Text(option) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = cc,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) cc = it },
                    label = { Text("Engine size") },
                    placeholder = { Text("1197") },
                    suffix = { Text("cc") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(fuel, cc.toIntOrNull()?.takeIf { it in RcPrefillMapper.ENGINE_CC_RANGE }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LiveLoadingRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = GetAutoSureColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = GetAutoSureType.Body)
    }
}

/** Canonical display names that are too long for a 2-column grid tile. */
private fun tileLabel(pid: PidId): String = when (pid) {
    PidId.O2_SENSOR_B1S1_VOLTAGE -> "O2 Sensor Voltage"
    PidId.O2_WIDEBAND_B1S1_LAMBDA -> "O2 Sensor Lambda"
    PidId.ACCELERATOR_PEDAL_POSITION_D -> "Accelerator Pedal"
    PidId.COMMANDED_THROTTLE_ACTUATOR -> "Commanded Throttle"
    PidId.RUN_TIME_SINCE_START -> "Engine Run Time"
    PidId.CONTROL_MODULE_VOLTAGE -> "Battery Voltage"
    PidId.INTAKE_MANIFOLD_PRESSURE -> "Manifold Pressure"
    PidId.FUEL_AIR_COMMANDED_RATIO -> "Commanded Lambda"
    else -> pid.displayName
}

private fun formatLive(pid: PidId, status: PidStatus?, value: Double?): String {
    if (status != PidStatus.VERIFIED || value == null) return "—"
    val formatted = when (pid) {
        PidId.RPM -> "%.0f".format(value) // "1744 rpm", not "1743.5 rpm"
        PidId.O2_WIDEBAND_B1S1_LAMBDA, PidId.FUEL_AIR_COMMANDED_RATIO -> "%.2f".format(value)
        PidId.RUN_TIME_SINCE_START -> return "%d:%02d".format(value.toInt() / 60, value.toInt() % 60)
        else -> if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
    }
    return if (pid.unit.isEmpty()) formatted else "$formatted ${pid.unit}"
}
