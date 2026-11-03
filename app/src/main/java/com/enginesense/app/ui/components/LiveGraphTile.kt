package com.enginesense.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.LiveHistory
import com.enginesense.app.vehicle.LiveSample
import com.enginesense.app.vehicle.LiveStatus

/**
 * A Live Data sensor drawn like a heart monitor: the last minute of readings scrolling left, newest
 * at the right edge, with the healthy range shaded behind the line when the sensor has one.
 * [normalRange] is only passed for sensors judged on their own (not RPM/load/throttle, whose
 * "normal" depends on how the car is being driven).
 */
@Composable
fun LiveGraphTile(
    label: String,
    value: String,
    samples: List<LiveSample>,
    /** Shown with the last minute's low and high under the graph, so the line has a scale. */
    unit: String,
    nowMs: Long,
    status: LiveStatus?,
    normalRange: ClosedFloatingPointRange<Double>?,
    minSpan: Double,
    modifier: Modifier = Modifier,
    graphHeight: Dp = 56.dp,
    windowMs: Long = LiveHistory.DEFAULT_WINDOW_MS
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GetAutoSureColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = GetAutoSureType.Caption, maxLines = 1, modifier = Modifier.weight(1f))
            status?.let { StatusChip(it) }
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(6.dp))
        Graph(samples, nowMs, normalRange, minSpan, windowMs, Modifier.fillMaxWidth().height(graphHeight))
        if (samples.size > 1) {
            Spacer(Modifier.height(4.dp))
            Text("Last minute: ${rangeText(samples, unit)}", style = GetAutoSureType.Caption, maxLines = 1)
        }
    }
}

@Composable
private fun StatusChip(status: LiveStatus) {
    val (text, color) = when (status) {
        LiveStatus.NORMAL -> "Normal" to GetAutoSureColors.Good
        LiveStatus.TYPICAL -> "Typical" to GetAutoSureColors.Good
        LiveStatus.VARIES_WITH_DRIVING -> "Varies" to GetAutoSureColors.TextSecondary
        LiveStatus.WARMING_UP -> "Warming up" to GetAutoSureColors.Watch
        LiveStatus.WATCH -> "Watch" to GetAutoSureColors.Watch
        LiveStatus.CHECK -> "Check" to GetAutoSureColors.Attention
    }
    Text(
        text,
        style = GetAutoSureType.Caption.copy(color = color, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun Graph(
    samples: List<LiveSample>,
    nowMs: Long,
    normalRange: ClosedFloatingPointRange<Double>?,
    minSpan: Double,
    windowMs: Long,
    modifier: Modifier
) {
    val line = GetAutoSureColors.Accent
    val band = GetAutoSureColors.Good.copy(alpha = 0.2f)
    val grid = GetAutoSureColors.TextSecondary.copy(alpha = 0.12f)
    val (lo, hi) = graphBounds(samples, normalRange, minSpan)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()
        fun x(t: Long) = (w - (nowMs - t).toFloat() / windowMs * w)

        // Faint baseline grid, like monitor paper.
        for (i in 1..3) drawLine(grid, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), strokeWidth = 1f)

        normalRange?.let { r ->
            val top = y(r.endInclusive).coerceIn(0f, h)
            val bottom = y(r.start).coerceIn(0f, h)
            if (bottom > top) drawRect(band, topLeft = Offset(0f, top), size = Size(w, bottom - top))
        }

        if (samples.isEmpty()) return@Canvas
        val path = Path()
        samples.forEachIndexed { i, s ->
            val px = x(s.elapsedMs)
            val py = y(s.value)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = samples.last()
        drawCircle(line, radius = 3.5.dp.toPx(), center = Offset(x(last.elapsedMs), y(last.value)))
    }
}

/** "0–46 km/h": whole numbers unless the readings are small enough that decimals matter (battery volts). */
internal fun rangeText(samples: List<LiveSample>, unit: String): String {
    val lo = samples.minOf { it.value }
    val hi = samples.maxOf { it.value }
    fun f(v: Double) = if (hi < 20) "%.1f".format(v) else "%.0f".format(v)
    val range = if (f(lo) == f(hi)) f(lo) else "${f(lo)}–${f(hi)}"
    return "$range $unit".trim()
}

/**
 * The y-axis range: the readings on screen plus the normal band, padded so the line never touches
 * the edges, and at least [minSpan] tall so a steady reading doesn't turn sensor noise into
 * dramatic spikes.
 */
internal fun graphBounds(samples: List<LiveSample>, normalRange: ClosedFloatingPointRange<Double>?, minSpan: Double): Pair<Double, Double> {
    val values = samples.map { it.value } + listOfNotNull(normalRange?.start, normalRange?.endInclusive)
    if (values.isEmpty()) return 0.0 to 1.0
    var lo = values.min()
    var hi = values.max()
    if (hi - lo < minSpan) {
        val mid = (hi + lo) / 2
        lo = mid - minSpan / 2
        hi = mid + minSpan / 2
    }
    val pad = (hi - lo) * 0.12
    return (lo - pad) to (hi + pad)
}
