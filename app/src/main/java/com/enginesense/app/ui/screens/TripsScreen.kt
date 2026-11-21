package com.enginesense.app.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.drivelab.DriveAnalyzer
import com.enginesense.app.drivelab.DriveLabStore
import com.enginesense.app.drivelab.DriveLabSummary
import com.enginesense.app.drivelab.DriveRecorderService
import com.enginesense.app.drivelab.DriveTrip
import com.enginesense.app.owner.OwnerInsights
import com.enginesense.app.ui.components.GroupNote
import com.enginesense.app.ui.components.InkCard
import com.enginesense.app.ui.components.ListDivider
import com.enginesense.app.ui.components.ListGroup
import com.enginesense.app.ui.components.ListRow
import com.enginesense.app.ui.components.OwnerText
import com.enginesense.app.ui.components.SectionTitle
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/**
 * The picked car's drives, from the trip recorder's samples. Samples belong to the car picked in
 * My Garage when they were recorded. Null while loading.
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

/** Time-weighted average score of [trips]; null when there are none. */
fun averageScore(trips: List<DriveTrip>): Int? {
    if (trips.isEmpty()) return null
    val weight = trips.sumOf { it.durationSeconds.coerceAtLeast(1) }
    return (trips.sumOf { it.drivingScore * it.durationSeconds.coerceAtLeast(1) } / weight).toInt()
}

fun tripScoreColor(score: Int): Color = when {
    score >= 80 -> GetAutoSureColors.Good
    score >= 60 -> GetAutoSureColors.Watch
    else -> GetAutoSureColors.Attention
}

@Composable
fun TripsScreen(viewModel: GetAutoSureViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val drives = rememberCarDrives(viewModel)
    val recorder by DriveRecorderService.status.collectAsState()
    val now = System.currentTimeMillis()
    val trips = drives?.trips.orEmpty().sortedByDescending { it.startMillis }
    val week = trips.filter { it.startMillis >= now - WEEK_MS }
    val price = remember { fuelPricePerLitre(context) }
    var openTrip by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Trips", style = GetAutoSureType.ScreenTitle) }

        item {
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
                        "Leave the scanner plugged in and every drive is recorded on its own.",
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

        if (drives == null) {
            item { GroupNote("Loading your drives…") }
            return@LazyColumn
        }

        if (trips.isEmpty()) {
            item {
                InkCard {
                    Text("No trips yet", style = OwnerText.InkLabel)
                    Spacer(Modifier.height(4.dp))
                    Text("Turn on auto-record and drive with the scanner plugged in. Each drive shows up here with its distance, fuel and driving score.", style = OwnerText.InkSub)
                }
            }
            return@LazyColumn
        }

        item { WeekScoreCard(week, trips, price) }

        if (week.isNotEmpty()) {
            item { SectionTitle("This week") }
            item {
                ListGroup {
                    ListRow(Icons.Filled.Bolt, "Harsh acceleration", trailing = { Count("${week.sumOf { it.harshAccelerations }}") })
                    ListDivider()
                    ListRow(Icons.Filled.FrontHand, "Hard braking", trailing = { Count("${week.sumOf { it.harshBrakes }}") })
                    ListDivider()
                    ListRow(Icons.Filled.Speed, "Over 100 km/h", trailing = { Count("${week.sumOf { it.overSpeedSeconds } / 60} min") })
                    ListDivider()
                    ListRow(Icons.Filled.Warning, "Above 4,000 rpm", trailing = { Count("${week.sumOf { it.highRpmSeconds } / 60} min") })
                    ListDivider()
                    ListRow(Icons.Filled.HourglassBottom, "Idling", trailing = { Count("${week.sumOf { it.idleSeconds } / 60} min") })
                }
            }
            drivingTip(week)?.let { tip -> item { GroupNote(tip) } }
        }

        item { SectionTitle("All trips") }
        item {
            ListGroup {
                trips.take(30).forEachIndexed { i, t ->
                    if (i > 0) ListDivider()
                    TripRow(t, price, expanded = openTrip == t.startMillis) { openTrip = if (openTrip == t.startMillis) null else t.startMillis }
                }
            }
        }
        item {
            ListGroup { ListRow(Icons.Filled.Science, "Raw recordings (Drive lab)", "Every reading, for testing", onClick = { onNavigate("driveLab") }) }
        }
    }
}

@Composable
private fun Count(text: String) {
    Text(text, style = OwnerText.RowTitle.copy(color = GetAutoSureColors.TextSecondary))
}

@Composable
private fun WeekScoreCard(week: List<DriveTrip>, all: List<DriveTrip>, price: Double) {
    val score = averageScore(week)
    InkCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Driving score", style = OwnerText.InkLabel)
            Text(if (score != null) "this week" else "last trips", style = OwnerText.InkSub)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${score ?: averageScore(all.take(5)) ?: "–"}", style = OwnerText.Big.copy(fontSize = 34.sp))
            Text(" / 100", style = OwnerText.InkSub, modifier = Modifier.padding(bottom = 6.dp))
        }
        val points = all.take(12).reversed().map { it.drivingScore.toFloat() }
        if (points.size >= 2) {
            Spacer(Modifier.height(6.dp))
            Sparkline(points, GetAutoSureColors.AccentBright, Modifier.fillMaxWidth().height(36.dp), min = 0f, max = 100f)
        }
        Spacer(Modifier.height(6.dp))
        val km = week.sumOf { it.distanceKm }
        val litres = week.mapNotNull { it.fuelLitres }.sum()
        Text(
            "${week.size} trip${if (week.size == 1) "" else "s"} · %.0f km".format(km) +
                if (litres > 0) " · ${OwnerInsights.inr((litres * price).toInt())} fuel" else "",
            style = OwnerText.InkSub
        )
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

private fun drivingTip(week: List<DriveTrip>): String? {
    val brakes = week.sumOf { it.harshBrakes }
    val accel = week.sumOf { it.harshAccelerations }
    val idleMin = week.sumOf { it.idleSeconds } / 60
    val fastMin = week.sumOf { it.overSpeedSeconds } / 60
    return when {
        brakes >= 2 && brakes >= accel -> "Tip: $brakes hard stops this week. Look further ahead and brake earlier."
        accel >= 2 -> "Tip: $accel harsh starts this week. Easing off the pedal saves fuel and the clutch."
        idleMin >= 15 -> "Tip: $idleMin min idling this week. Switch off for stops longer than a minute."
        fastMin >= 10 -> "Tip: $fastMin min over 100 km/h this week. Mileage drops fast above 90 km/h."
        else -> null
    }
}

private fun tripWhen(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
    val sameDay = { a: Calendar, b: Calendar -> a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR) }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(c, today) -> "Today $time"
        sameDay(c, yesterday) -> "Yesterday $time"
        else -> SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(millis))
    }
}

private fun duration(seconds: Long): String = if (seconds >= 3600) "${seconds / 3600} h %02d min".format((seconds % 3600) / 60) else "${seconds / 60} min"

@Composable
private fun TripRow(t: DriveTrip, price: Double, expanded: Boolean, onClick: () -> Unit) {
    val parts = listOfNotNull(
        duration(t.durationSeconds),
        t.kmPerLitre?.let { "%.1f km/l".format(it) },
        t.fuelLitres?.let { OwnerInsights.inr((it * price).toInt()) }
    )
    ListRow(
        Icons.Filled.Route, "${tripWhen(t.startMillis)} · %.0f km".format(t.distanceKm), parts.joinToString(" · "),
        onClick = onClick,
        trailing = { Text("${t.drivingScore}", style = OwnerText.RowTitle.copy(color = tripScoreColor(t.drivingScore), fontSize = 18.sp)) }
    )
    if (expanded) {
        Column(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            listOfNotNull(
                t.maxSpeedKmh?.let { "Top speed %.0f km/h".format(it) },
                t.avgMovingSpeedKmh?.let { "Average moving speed %.0f km/h".format(it) },
                "Harsh starts ${t.harshAccelerations} · hard stops ${t.harshBrakes}",
                "Idling ${t.idleSeconds / 60} min · over 100 km/h ${t.overSpeedSeconds / 60} min",
                t.warmUpSeconds?.let { "Engine warmed up in ${duration(it)}" },
                t.maxCoolantC?.let { "Hottest engine temperature %.0f°C".format(it) },
                t.crankingMinVolts?.let { "Battery at start: lowest %.1f V".format(it) },
                t.chargingVolts?.let { "Charging while driving %.1f V".format(it) }
            ).forEach { Text(it, style = OwnerText.RowSub) }
        }
    }
}

