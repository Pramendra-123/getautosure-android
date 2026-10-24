package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.PerformanceState
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.PerformanceResult

/**
 * Acceleration (0-60 km/h), braking (60-0 km/h) and a 100m run — the gap flagged against a
 * reference app with a dedicated performance tab. Distance/time come from integrating the
 * vehicle-speed PID over time (no GPS in this app), so this is a real but adapter-latency-
 * bounded measurement, not a dyno-grade one; any segment the run never reached shows a dash,
 * never a guessed number. See [com.enginesense.app.vehicle.PerformanceAnalyzer].
 */
@Composable
fun PerformanceScreen(viewModel: GetAutoSureViewModel, onStop: () -> Unit) {
    val state by viewModel.performanceState.collectAsState()
    val liveSpeed by viewModel.performanceLiveSpeed.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.stopPerformanceTest() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Performance", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            "Accelerate hard, then brake, to fill in 0-60, braking, and 100m below.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            is PerformanceState.Idle, is PerformanceState.Connecting -> LoadingRow("Connecting…")
            is PerformanceState.Error -> Text("Stopped: ${s.message}", color = GetAutoSureColors.Attention, style = GetAutoSureType.Body)
            is PerformanceState.Recording -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("$liveSpeed", style = GetAutoSureType.ScoreNumber)
                    Text("km/h — recording", style = GetAutoSureType.Caption)
                }
            }
            is PerformanceState.Finished -> ResultCard(s.result)
        }

        Spacer(Modifier.weight(1f))

        if (state is PerformanceState.Recording) {
            Button(onClick = { viewModel.stopPerformanceTest() }, modifier = Modifier.fillMaxWidth()) { Text("Stop & see results") }
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
private fun ResultCard(result: PerformanceResult) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricRow("0-60 km/h", formatSeconds(result.zeroToSixtyMs), formatMetres(result.zeroToSixtyDistanceM))
        MetricRow("60-0 km/h (braking)", formatSeconds(result.sixtyToZeroMs), formatMetres(result.sixtyToZeroDistanceM))
        MetricRow("100m", formatSeconds(result.hundredMetreMs), null)
        if (listOf(result.zeroToSixtyMs, result.sixtyToZeroMs, result.hundredMetreMs).all { it == null }) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Nothing measured yet — the run needs to start from a standstill and reach 60 km/h (or complete 100m) for a result to show here.",
                style = GetAutoSureType.Caption
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, time: String, distance: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(time, style = GetAutoSureType.SectionTitle)
                if (distance != null) Text(distance, style = GetAutoSureType.Caption)
            }
        }
    }
}

private fun formatSeconds(ms: Long?): String = if (ms == null) "—" else "%.2fs".format(ms / 1000.0)
private fun formatMetres(m: Double?): String = if (m == null) "—" else "%.0fm".format(m)
