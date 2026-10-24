package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.components.DemoDataBanner
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.ReadinessMonitor
import com.enginesense.app.vehicle.ReadinessStatus

/**
 * VIN/ECU identification (Mode 09) and emissions-readiness monitors (Mode 01 PID 01) —
 * the "more information" gap flagged against reference OBD2 apps. A separate screen from
 * "Vehicle info & challan" (that one's an RC/challan lookup placeholder by registration
 * number; this is what the car's own ECU actually reports).
 */
@Composable
fun VehicleDetailsScreen(viewModel: GetAutoSureViewModel) {
    val result by viewModel.latestResult.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("VIN, ECU & readiness", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))

        val r = result
        if (r == null) {
            Spacer(Modifier.height(8.dp))
            Text("Run a scan first to see this.", style = GetAutoSureType.Body)
            return@Column
        }

        Text("Straight from your car's ECU — a dash means it didn't answer, never a guess.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(12.dp))

        if (r.isDemo) {
            DemoDataBanner()
            Spacer(Modifier.height(12.dp))
        }

        InfoRow("VIN", r.vehicleId.vin ?: "—")
        InfoRow("ECU Name", r.vehicleId.ecuName ?: "—")
        InfoRow("Calibration ID", r.vehicleId.calibrationId ?: "—")

        Spacer(Modifier.height(20.dp))
        Text("Emissions readiness", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(4.dp))

        val readiness = r.readiness
        if (readiness == null) {
            Spacer(Modifier.height(8.dp))
            Text("Adapter didn't answer this request.", style = GetAutoSureType.Body)
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        val overallColor = if (readiness.readyForEmissionsTest) GetAutoSureColors.Good else GetAutoSureColors.Watch
        val overallText = if (readiness.readyForEmissionsTest) {
            "Ready — every monitor this car has has finished its self-test."
        } else {
            "Not ready — one or more monitors haven't finished running yet. Drive normally for a while and check again."
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(overallColor)
            Spacer(Modifier.width(8.dp))
            Text(overallText, style = GetAutoSureType.Body)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Check-engine light: ${if (readiness.milOn) "ON" else "off"}  ·  Stored DTCs: ${readiness.dtcCount}  ·  Engine type: ${readiness.ignitionType.name.lowercase().replaceFirstChar { it.uppercase() }}",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn {
            items(readiness.monitors) { monitor ->
                MonitorRow(monitor)
                HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = GetAutoSureType.Caption)
        Spacer(Modifier.height(2.dp))
        Text(value, style = GetAutoSureType.Body)
    }
}

@Composable
private fun MonitorRow(monitor: ReadinessMonitor) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(monitor.name, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
        Text(
            when {
                !monitor.supported -> "Not applicable"
                monitor.ready -> "Ready"
                else -> "Not ready"
            },
            style = GetAutoSureType.Body.copy(
                color = when {
                    !monitor.supported -> GetAutoSureColors.Unavailable
                    monitor.ready -> GetAutoSureColors.Good
                    else -> GetAutoSureColors.Watch
                }
            )
        )
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.size(8.dp).background(color, shape = CircleShape))
}
