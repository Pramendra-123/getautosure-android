package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ScanResult
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

/**
 * Premium's "Emissions Trend History" — per-monitor readiness tracked across scans, not just
 * this scan's pass/fail (that stays free, on the VIN & ECU Emission Readiness screen). Each
 * row is one monitor; each dot is one scan, oldest on the left, most recent on the right.
 */
@Composable
fun EmissionsTrendScreen(viewModel: GetAutoSureViewModel) {
    val history by viewModel.history.collectAsState()
    val chronological = history.reversed().takeLast(10) // oldest -> newest, cap the row width

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Emissions trend history", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text("How each readiness monitor has looked across your last ${chronological.size} scans.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(16.dp))

        val monitorNames = chronological.lastOrNull()?.readiness?.monitors?.map { it.name } ?: emptyList()
        if (monitorNames.isEmpty()) {
            Text("Run a scan that reports readiness monitors first.", style = GetAutoSureType.Body)
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(monitorNames) { name -> MonitorTrendRow(name, chronological) }
        }
    }
}

@Composable
private fun MonitorTrendRow(monitorName: String, chronological: List<ScanResult>) {
    Column {
        Text(monitorName, style = GetAutoSureType.Body)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            chronological.forEach { scan ->
                val monitor = scan.readiness?.monitors?.firstOrNull { it.name == monitorName }
                val color = when {
                    monitor == null -> GetAutoSureColors.Unavailable
                    !monitor.supported -> GetAutoSureColors.Unavailable
                    monitor.ready -> GetAutoSureColors.Good
                    else -> GetAutoSureColors.Watch
                }
                Box(modifier = Modifier.size(14.dp).background(color, CircleShape))
            }
        }
    }
}
