package com.enginesense.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.ScanResult
import com.enginesense.app.drivelab.DriveLabSummary
import com.enginesense.app.owner.Level
import com.enginesense.app.owner.Vital
import com.enginesense.app.owner.VitalScan
import com.enginesense.app.owner.VitalTrend
import com.enginesense.app.owner.VitalTrends
import com.enginesense.app.ui.components.OwnerText
import com.enginesense.app.ui.components.levelColor
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.vehicle.PidId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val NORMAL_BAND = Color(0xFFDDF4EA)
/** How often to suggest the next check after the first scan. */
private const val NEXT_CHECK_DAYS = 14L

/**
 * A scan's vitals, only when they mean something: charging voltage and fuel mix need the engine
 * running, and engine temperature needs it warmed up — a cold-start scan isn't a cooling problem.
 */
fun vitalScanOf(r: ScanResult): VitalScan {
    val s = r.snapshot
    val running = (s.verifiedValue(PidId.RPM) ?: 0.0) > 400
    val values = buildMap {
        if (running) s.verifiedValue(PidId.CONTROL_MODULE_VOLTAGE)?.let { put(Vital.CHARGING, it) }
        s.verifiedValue(PidId.COOLANT_TEMP_C)?.takeIf { it >= 70 }?.let { put(Vital.ENGINE_TEMP, it) }
        if (running) {
            val st = s.verifiedValue(PidId.SHORT_TERM_FUEL_TRIM_1)
            val lt = s.verifiedValue(PidId.LONG_TERM_FUEL_TRIM_1)
            if (st != null || lt != null) put(Vital.FUEL_MIX, (st ?: 0.0) + (lt ?: 0.0))
        }
    }
    return VitalScan(r.timestampMillis, values, s.dtcs.size)
}

private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY)

/**
 * Health over time as the car's vital signs. One scan: "baseline saved". Two: what changed since
 * the last one. Three or more: each vital against its normal band, with a word when one drifts.
 */
@Composable
fun HealthOverTime(scans: List<ScanResult>, drives: DriveLabSummary?) {
    if (scans.isEmpty()) return
    val sorted = scans.sortedBy { it.timestampMillis }
    val vitals = sorted.map(::vitalScanOf)
    val trends = if (vitals.size >= 3) VitalTrends.trends(vitals.takeLast(12)) else emptyList()
    when {
        sorted.size == 1 -> Baseline(sorted.first().timestampMillis)
        trends.isNotEmpty() -> VitalsCard(trends, vitals.size)
        else -> {
            val (prev, last) = sorted.takeLast(2)
            val km = drives?.trips?.filter { it.startMillis in prev.timestampMillis..last.timestampMillis }?.sumOf { it.distanceKm }
            VitalTrends.sinceLastScan(vitals, km)?.let { SinceLast(it) }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(16.dp)) { content() }
}

@Composable
private fun Baseline(firstMillis: Long) {
    Card {
        Text("Your car's baseline is saved", style = OwnerText.RowTitle)
        Text("First scan, ${day(firstMillis)}. Every next scan is compared with this one.", style = OwnerText.RowSub)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(GetAutoSureColors.Good))
            repeat(2) {
                Box(Modifier.weight(1f).height(2.dp).background(GetAutoSureColors.SurfaceRaised))
                Box(Modifier.size(14.dp).border(1.5.dp, GetAutoSureColors.Unavailable, CircleShape))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Next check suggested: ${day(firstMillis + NEXT_CHECK_DAYS * 86_400_000L)}, or before a long drive", style = OwnerText.RowSub.copy(fontSize = 13.sp))
    }
}

@Composable
private fun SinceLast(s: com.enginesense.app.owner.SinceLastScan) {
    Card {
        Text("Since your scan on ${day(s.previousMillis)}", style = OwnerText.RowTitle)
        Spacer(Modifier.height(4.dp))
        s.changes.forEachIndexed { i, (label, change) ->
            if (i > 0) HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(label, style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary), modifier = Modifier.weight(1f))
                val color = when {
                    label in s.worse -> GetAutoSureColors.Attention
                    change == "Same" || change.startsWith("None") -> GetAutoSureColors.Good
                    else -> GetAutoSureColors.TextSecondary
                }
                Text(change, style = OwnerText.RowSub.copy(color = color))
            }
        }
    }
}

@Composable
private fun VitalsCard(trends: List<VitalTrend>, scanCount: Int) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Your car's vital signs", style = OwnerText.RowTitle)
            Text("$scanCount scans", style = OwnerText.RowSub)
        }
        Text("Green band = normal for a healthy car", style = OwnerText.RowSub.copy(fontSize = 13.sp))
        trends.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.vital.label, style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary, fontSize = 15.sp))
                    Text(t.summary, style = OwnerText.RowSub.copy(fontSize = 13.sp, color = if (t.level == Level.GOOD) GetAutoSureColors.TextSecondary else levelColor(t.level)))
                }
                Spacer(Modifier.width(12.dp))
                BandSparkline(t, Modifier.width(110.dp).height(36.dp))
            }
        }
        trends.firstOrNull { it.warning != null }?.let { t ->
            Spacer(Modifier.height(4.dp))
            Text(
                t.warning!!,
                style = OwnerText.RowSub.copy(fontSize = 13.sp, color = if (t.level == Level.ATTENTION) GetAutoSureColors.Attention else GetAutoSureColors.Watch),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(GetAutoSureColors.AccentSoft).padding(10.dp)
            )
        }
    }
}

/** The vital's readings over the normal band; the line takes the vital's colour. */
@Composable
private fun BandSparkline(t: VitalTrend, modifier: Modifier) {
    val color = if (t.level == Level.GOOD) GetAutoSureColors.Good else levelColor(t.level)
    Canvas(modifier) {
        val range = t.plotRange
        fun y(v: Double) = (size.height * (1 - (v - range.start) / (range.endInclusive - range.start))).toFloat()
        val top = y(t.vital.normal.endInclusive)
        drawRect(NORMAL_BAND, topLeft = Offset(0f, top), size = Size(size.width, y(t.vital.normal.start) - top))
        val step = size.width / (t.points.size - 1).coerceAtLeast(1)
        val path = Path()
        t.points.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v)) }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color, radius = 3.5.dp.toPx(), center = Offset((t.points.size - 1) * step, y(t.points.last())))
    }
}
