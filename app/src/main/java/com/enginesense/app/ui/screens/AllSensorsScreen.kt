package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.components.DemoDataBanner
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidReading
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehicle.SensorRating
import com.enginesense.app.vehicle.benchmark
import com.enginesense.app.vehicle.plainDescription
import com.enginesense.app.vehicle.rate

/**
 * The exhaustive "every PID we know how to read" list — the GetAutoSure equivalent of
 * Car Scanner's "All sensors" screen. Home deliberately only shows four curated tiles
 * (insights-first, per the product's design direction); this is where "more information"
 * actually lives, one tap away via "View all sensors" on the report screen.
 */
@Composable
fun AllSensorsScreen(viewModel: GetAutoSureViewModel) {
    val result by viewModel.latestResult.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("All sensors", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))

        if (result == null) {
            Text("Run a scan first to see live sensor data.", style = GetAutoSureType.Body)
            return@Column
        }

        Text("Every reading your car's computer can share. \"Not supported\" means this car doesn't report it — never a guess.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(12.dp))

        if (result!!.isDemo) {
            DemoDataBanner()
            Spacer(Modifier.height(12.dp))
        }

        // Readings this car actually gives come first; the ones it doesn't report sit folded at
        // the bottom instead of burying the useful ones.
        val readings = result!!.snapshot.readings
        val (reported, notReported) = PidId.entries.partition { readings[it] != null && readings[it]!!.status != PidStatus.UNSUPPORTED }
        var showNotReported by remember { mutableStateOf(false) }
        LazyColumn {
            items(reported) { pid ->
                SensorRow(pid, readings[pid])
                HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
            }
            if (notReported.isNotEmpty()) {
                item {
                    TextButton(onClick = { showNotReported = !showNotReported }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text((if (showNotReported) "Hide" else "Show") + " readings this car doesn't report (${notReported.size})")
                    }
                }
                if (showNotReported) {
                    items(notReported) { pid ->
                        Text(pid.displayName, style = GetAutoSureType.Caption, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorRow(pid: PidId, reading: PidReading?) {
    val rating = reading?.takeIf { it.status == PidStatus.VERIFIED }?.value?.let { pid.rate(it) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (rating != null && rating != SensorRating.NOT_RATED) {
                    RatingDot(rating)
                    Spacer(Modifier.width(8.dp))
                }
                Text(pid.displayName, style = GetAutoSureType.Body)
            }
            // A gap so a long sensor name never runs into its value.
            Spacer(Modifier.width(12.dp))
            Text(formatReading(pid, reading), style = GetAutoSureType.Body.copy(color = GetAutoSureColors.TextSecondary), maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(pid.plainDescription(), style = GetAutoSureType.Caption)
        val benchmark = pid.benchmark()
        if (benchmark != null) {
            Spacer(Modifier.height(2.dp))
            Text(benchmark.note, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextSecondary))
        }
    }
}

@Composable
private fun RatingDot(rating: SensorRating) {
    val color = when (rating) {
        SensorRating.GOOD -> GetAutoSureColors.Good
        SensorRating.WATCH -> GetAutoSureColors.Watch
        SensorRating.ATTENTION -> GetAutoSureColors.Attention
        SensorRating.NOT_RATED -> GetAutoSureColors.Unavailable
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
    )
}

private fun formatReading(pid: PidId, reading: PidReading?): String {
    if (reading == null) return "—"
    return when (reading.status) {
        PidStatus.VERIFIED -> {
            val value = reading.value ?: return "—"
            // One decimal like Live Data and the PDF; two only for small values like oxygen-sensor volts.
            val formatted = when {
                value == value.toLong().toDouble() -> value.toLong().toString()
                kotlin.math.abs(value) < 2 -> "%.2f".format(value)
                else -> "%.1f".format(value)
            }
            if (pid.unit.isEmpty()) formatted else "$formatted ${pid.unit}"
        }
        PidStatus.IMPLAUSIBLE -> "Unreliable reading"
        PidStatus.SUPPORTED_NO_DATA -> "No data this scan"
        PidStatus.UNSUPPORTED -> "Not supported"
        PidStatus.TRANSPORT_ERROR -> "Couldn't read"
    }
}
