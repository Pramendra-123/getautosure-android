package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.enginesense.app.LiveDataState
import com.enginesense.app.ui.components.StatTile
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidStatus

/**
 * The continuous "watch it change as you drive" screen — directly answers the feedback
 * that a one-shot scan can't show RPM climbing on acceleration. Re-reads a short, fixed
 * set of PIDs every ~600ms for as long as this screen is open; see
 * [GetAutoSureViewModel.startLiveData] for why it's a short list, not all 31 PIDs.
 *
 * The transport is already connecting/connected by the time this screen opens — the
 * Connect screen starts it (reusing the same device/profile pickers as a normal scan)
 * before navigating here, so this screen only needs to observe state and stop it on exit.
 */
@Composable
fun LiveDataScreen(viewModel: GetAutoSureViewModel, onStop: () -> Unit) {
    val state by viewModel.liveState.collectAsState()
    val readings by viewModel.liveReadings.collectAsState()

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
                    items(GetAutoSureViewModel.LIVE_DATA_PIDS) { pid ->
                        val reading = readings[pid]
                        StatTile(tileLabel(pid), formatLive(pid, reading?.status, reading?.value), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
    }
}

@Composable
private fun LiveLoadingRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = GetAutoSureColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = GetAutoSureType.Body)
    }
}

/** [PidId.O2_SENSOR_B1S1_VOLTAGE]'s canonical display name is too long for a 2-column grid tile. */
private fun tileLabel(pid: PidId): String =
    if (pid == PidId.O2_SENSOR_B1S1_VOLTAGE) "O2 Sensor Voltage" else pid.displayName

private fun formatLive(pid: PidId, status: PidStatus?, value: Double?): String {
    if (status != PidStatus.VERIFIED || value == null) return "—"
    val formatted = if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
    return if (pid.unit.isEmpty()) formatted else "$formatted ${pid.unit}"
}
