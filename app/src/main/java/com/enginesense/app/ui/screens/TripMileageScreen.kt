package com.enginesense.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.TripMileageState
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.MileageTrendResult
import com.enginesense.app.vehicle.TripMileageResult

private const val V3CARS_REAL_WORLD_MILEAGE_URL =
    "https://www.v3cars.com/car-guide/all-v3-cars-real-world-mileage-test-arai-claimed-fuel-efficiency"

/**
 * Fuel economy (km/L) estimated from MAF + vehicle speed over a real drive — no direct fuel-rate
 * PID exists on this hardware, so this is the same MAF-based estimate budget OBD apps use, with
 * the same no-GPS caveat as [com.enginesense.app.vehicle.PerformanceAnalyzer]'s distance figures.
 * Needs a live drive, not a stationary scan — idle MAF readings alone can't produce a km/L figure.
 * See [com.enginesense.app.vehicle.MileageAnalyzer] and [com.enginesense.app.vehicle.MileageTrendAnalyzer].
 */
@Composable
fun TripMileageScreen(viewModel: GetAutoSureViewModel, onStop: () -> Unit) {
    val state by viewModel.tripMileageState.collectAsState()
    val liveKmPerLitre by viewModel.tripMileageLiveKmPerLitre.collectAsState()
    val claimedMileage by viewModel.vehicleClaimedMileageKmPerLitre.collectAsState()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { viewModel.stopTripMileage() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Trip mileage", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            "Drive normally for a few minutes, then stop to see your estimated km/L for this trip.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            is TripMileageState.Idle, is TripMileageState.Connecting -> LoadingRow("Connecting…")
            is TripMileageState.Error -> Text("Stopped: ${s.message}", color = GetAutoSureColors.Attention, style = GetAutoSureType.Body)
            is TripMileageState.Recording -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(liveKmPerLitre?.let { "%.1f".format(it) } ?: "—", style = GetAutoSureType.ScoreNumber)
                    Text("km/L so far — recording", style = GetAutoSureType.Caption)
                }
            }
            is TripMileageState.Finished -> TripResultCard(
                result = s.result, signal = s.signal, trend = s.trend,
                claimedMileage = claimedMileage,
                onClaimedMileageChange = { viewModel.setVehicleClaimedMileage(it) },
                onOpenV3Cars = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(V3CARS_REAL_WORLD_MILEAGE_URL))) }
            )
        }

        Spacer(Modifier.weight(1f))

        if (state is TripMileageState.Recording) {
            Button(onClick = { viewModel.stopTripMileage() }, modifier = Modifier.fillMaxWidth()) { Text("Stop & see results") }
        } else {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun LoadingRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = GetAutoSureColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = GetAutoSureType.Body)
    }
}

@Composable
private fun TripResultCard(
    result: TripMileageResult,
    signal: String?,
    trend: MileageTrendResult?,
    claimedMileage: Double?,
    onClaimedMileageChange: (Double?) -> Unit,
    onOpenV3Cars: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (result.unsupportedFuelType) {
            Text(
                "Mileage estimate isn't available for this fuel type — km/L only applies to petrol and diesel engines. CNG is measured in km/kg, and electric vehicles don't burn fuel at all.",
                style = GetAutoSureType.Body
            )
            return@Column
        }

        val avg = result.averageKmPerLitre
        if (avg == null) {
            Text(
                "Trip too short to estimate mileage — drive at least a few hundred metres, then stop.",
                style = GetAutoSureType.Body
            )
            return@Column
        }

        Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("%.1f km/L".format(avg), style = GetAutoSureType.ScoreNumber)
                Text(
                    "%.1f km · %.2f L estimated this trip".format(result.distanceKm ?: 0.0, result.fuelLitres ?: 0.0),
                    style = GetAutoSureType.Caption
                )
            }
        }

        // Independent of trip history — can show on trip 1, no baseline needed.
        if (signal != null) {
            Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Could be affecting your mileage", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Watch))
                    Spacer(Modifier.height(4.dp))
                    Text(signal, style = GetAutoSureType.Caption)
                }
            }
        }

        // Needs a real personal baseline — see MileageTrendAnalyzer's class doc for why this
        // never compares against a lab figure or a flat percentage.
        when {
            trend == null -> Text(
                "Need at least 2 prior trips to compare against — this is your baseline so far.",
                style = GetAutoSureType.Caption
            )
            trend.isDropSignificant -> Text(
                "%.0f%% below your recent average (%.1f km/L).%s".format(
                    -(trend.percentChange ?: 0.0), trend.baselineKmPerLitre ?: 0.0,
                    if (signal == null) " Nothing in your last scan explains this — could be driving conditions, traffic, or something this scan can't check, like tyre pressure." else ""
                ),
                style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch)
            )
            else -> Text(
                "In line with your recent average (%.1f km/L).".format(trend.baselineKmPerLitre ?: 0.0),
                style = GetAutoSureType.Caption
            )
        }

        Spacer(Modifier.height(4.dp))
        ClaimedMileageReference(claimedMileage, onClaimedMileageChange, onOpenV3Cars)
    }
}

/**
 * Pure reference, never a verdict input — this app has no vehicle spec catalog, so the figure is
 * always typed in by the user, never looked up. Deliberately not used anywhere to judge a "drop":
 * real-world-vs-ARAI deviation ranges from about -11% to -55% across models (per V3Cars' own
 * testing, linked below), nowhere near a reliable flat number to compare against.
 */
@Composable
private fun ClaimedMileageReference(claimedMileage: Double?, onChange: (Double?) -> Unit, onOpenV3Cars: () -> Unit) {
    var input by remember(claimedMileage) { mutableStateOf(claimedMileage?.toString() ?: "") }

    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Manufacturer-claimed mileage (ARAI)", style = GetAutoSureType.Body)
            Spacer(Modifier.height(2.dp))
            Text(
                "Lab-test figure, for reference only — real-world mileage is typically meaningfully lower, by a different amount for every car. Not used to judge your trips.",
                style = GetAutoSureType.Caption
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it.filter { c -> c.isDigit() || c == '.' }
                    onChange(input.toDoubleOrNull())
                },
                label = { Text("km/L") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.TextButton(onClick = onOpenV3Cars, modifier = Modifier.fillMaxWidth()) {
                Text("Check your model's real-world tested mileage →")
            }
        }
    }
}
