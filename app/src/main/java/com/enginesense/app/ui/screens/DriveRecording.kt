package com.enginesense.app.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.drivelab.DriveAnalyzer
import com.enginesense.app.drivelab.DriveLabStore
import com.enginesense.app.drivelab.DriveLabSummary
import com.enginesense.app.drivelab.DriveRecorderService
import com.enginesense.app.ui.components.ListGroup
import com.enginesense.app.ui.components.ListRow
import com.enginesense.app.ui.theme.GetAutoSureColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picked car's drives, from the background recorder's samples. Samples belong to the car
 * picked in My Garage when they were recorded. Null while loading. There's no Trips screen: the
 * drives feed the Fuel chip, fill-up detection, the odometer and Battery.
 */
@Composable
fun rememberCarDrives(viewModel: GetAutoSureViewModel): DriveLabSummary? {
    val context = LocalContext.current
    val garage by viewModel.garage.collectAsState()
    val fuelType by viewModel.vehicleFuelType.collectAsState()
    val recorder by DriveRecorderService.status.collectAsState()
    val activeId = garage.active?.id
    // Reloads when the car changes and every 20 samples while recording.
    val value by produceState<DriveLabSummary?>(null, activeId, recorder.samplesThisRun / 20) {
        value = withContext(Dispatchers.IO) {
            val samples = DriveLabStore(context).loadSamples().filter { garage.carAt(it.timeMillis) == activeId }
            DriveAnalyzer.analyze(samples, fuelType)
        }
    }
    return value
}

fun fuelPricePerLitre(context: Context): Double =
    context.getSharedPreferences("drive_lab", Context.MODE_PRIVATE).getString("fuel_price", "100")?.toDoubleOrNull() ?: 100.0

/** The background recorder's on/off switch, wherever its data is shown (Battery, Fuel). */
@Composable
fun AutoRecordRow(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val recorder by DriveRecorderService.status.collectAsState()
    ListGroup {
        if (recorder.running) {
            ListRow(
                Icons.Filled.CheckCircle, "Auto-record is on",
                recorder.deviceName?.let { "${recorder.phase.label} · $it" } ?: recorder.phase.label,
                subtitleColor = GetAutoSureColors.Good,
                trailing = { TextButton(onClick = { DriveRecorderService.stop(context) }) { Text("Turn off", color = GetAutoSureColors.Accent) } }
            )
        } else {
            ListRow(
                Icons.Filled.Route, "Auto-record is off",
                "Leave the scanner plugged in and drives are recorded on their own, for mileage and battery.",
                trailing = {
                    TextButton(onClick = {
                        val address = DriveRecorderService.savedAddress(context)
                        if (address != null && hasBluetoothPermission(context)) DriveRecorderService.start(context, address)
                        else onNavigate("driveLab")
                    }) { Text("Turn on", color = GetAutoSureColors.Accent, fontWeight = FontWeight.Bold) }
                }
            )
        }
    }
}

/** A simple line through [points], scaled to [min]..[max] (or the data's own range). */
@Composable
fun Sparkline(points: List<Float>, color: Color, modifier: Modifier, min: Float? = null, max: Float? = null) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val lo = min ?: points.min()
        val hi = (max ?: points.max()).let { if (it - lo < 1f) lo + 1f else it }
        val step = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i * step
            val y = size.height - (p - lo) / (hi - lo) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
        val last = points.last()
        drawCircle(color, 4.dp.toPx(), Offset(size.width, size.height - (last - lo) / (hi - lo) * size.height))
    }
}
