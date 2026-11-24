package com.enginesense.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.CarPanel
import com.enginesense.app.vehicle.ExteriorItem
import com.enginesense.app.vehicle.ExteriorDamage
import com.enginesense.app.vehicle.LookAround
import com.enginesense.app.vehicle.PanelIssue
import com.enginesense.app.vehicle.QuickAnswer

/** Where each part sits on the top-down car, in a 200 × 330 box, front at the top. */
private val PANEL_RECTS: Map<String, Rect> = mapOf(
    "front_bumper" to Rect(46f, 14f, 154f, 34f),
    "bonnet" to Rect(46f, 36f, 154f, 92f),
    "front_left_fender" to Rect(26f, 40f, 44f, 96f),
    "front_right_fender" to Rect(156f, 40f, 174f, 96f),
    "windshield" to Rect(52f, 94f, 148f, 120f),
    "left_mirror" to Rect(10f, 100f, 24f, 114f),
    "right_mirror" to Rect(176f, 100f, 190f, 114f),
    "roof" to Rect(60f, 122f, 140f, 214f),
    "front_left_door" to Rect(26f, 98f, 56f, 160f),
    "front_right_door" to Rect(144f, 98f, 174f, 160f),
    "rear_left_door" to Rect(26f, 162f, 56f, 222f),
    "rear_right_door" to Rect(144f, 162f, 174f, 222f),
    "rear_glass" to Rect(52f, 216f, 148f, 238f),
    "rear_left_quarter" to Rect(26f, 224f, 50f, 288f),
    "rear_right_quarter" to Rect(150f, 224f, 174f, 288f),
    "boot" to Rect(52f, 240f, 148f, 290f),
    "rear_bumper" to Rect(46f, 292f, 154f, 312f)
)
private val WHEELS = listOf(Rect(14f, 52f, 24f, 86f), Rect(176f, 52f, 186f, 86f), Rect(14f, 234f, 24f, 268f), Rect(176f, 234f, 186f, 268f))
private const val BOX_W = 200f
private const val BOX_H = 330f

private fun rupees(v: Int) = "₹" + "%,d".format(v)

/**
 * Step 4 of the Used Car Check: tap the car where you see damage, then the quick checks and the
 * accident signs. All of it feeds the same fixed repair-cost table the report uses.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LookAroundStep(
    marks: SnapshotStateMap<String, Set<PanelIssue>>,
    answers: SnapshotStateMap<String, QuickAnswer>,
    accident: SnapshotStateMap<Int, Boolean>,
    onNext: () -> Unit
) {
    var selected by remember { mutableStateOf<CarPanel?>(null) }
    val counts = LookAround.counts(marks, answers)
    val (minCost, maxCost) = LookAround.costRange(counts)

    Text("Tap where you see damage", style = GetAutoSureType.ScreenTitle)
    Text("The scan can't see the body. Anything you mark comes off the price.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Body looks clean, skip") }
    Spacer(Modifier.height(10.dp))

    CarDiagram(marks = marks, selectedId = selected?.id, onTap = { selected = it })

    val panel = selected
    if (panel == null) {
        Text("Tap a part of the car to mark it.", style = GetAutoSureType.Caption, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    } else {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(panel.label, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("Pick all that apply", style = GetAutoSureType.Caption)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                panel.kind.issues.forEach { issue ->
                    val on = issue in marks[panel.id].orEmpty()
                    val item = ExteriorDamage.items.first { it.id == issue.itemId }
                    IssueChip(issue.label, item, on) {
                        val now = marks[panel.id].orEmpty()
                        val next = if (on) now - issue else now + issue
                        if (next.isEmpty()) marks.remove(panel.id) else marks[panel.id] = next
                    }
                }
            }
            if (PanelIssue.REPAINTED in marks[panel.id].orEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("A repainted panel often means a repair. Ask the seller why it was painted.", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch))
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    Text("A few quick checks", style = GetAutoSureType.SectionTitle)
    Text("Skip any you can't check.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(horizontal = 14.dp, vertical = 6.dp)) {
        LookAround.quickChecks.forEach { q ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q.label, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                    q.hint?.let { Text("  ·  $it", style = GetAutoSureType.Caption) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickAnswer.values().forEach { a ->
                        val on = answers[q.id] == a
                        val color = when (a) {
                            QuickAnswer.GOOD -> GetAutoSureColors.Good
                            QuickAnswer.OK -> GetAutoSureColors.Watch
                            QuickAnswer.NEEDS_WORK -> GetAutoSureColors.Attention
                        }
                        Text(
                            a.label,
                            style = GetAutoSureType.Caption.copy(color = if (on) color else GetAutoSureColors.TextSecondary, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (on) color.copy(alpha = 0.12f) else Color.Transparent)
                                .border(1.5.dp, if (on) color else GetAutoSureColors.Border, RoundedCornerShape(50))
                                .clickable { if (on) answers.remove(q.id) else answers[q.id] = a }
                                .padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                if (answers[q.id] == QuickAnswer.NEEDS_WORK) {
                    val item = ExteriorDamage.items.first { it.id == q.itemId }
                    Text(
                        "+ ${rupees(item.minInr * q.count)}–${"%,d".format(item.maxInr * q.count)}" + if (q.count > 1) " for ${q.count} ${item.unit}s" else "",
                        style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    Text("Has it been in an accident?", style = GetAutoSureType.SectionTitle)
    Text("Walk around in daylight. Any \"Yes\" goes into the report as a red flag.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Ink).padding(horizontal = 14.dp, vertical = 6.dp)) {
        LookAround.accidentSigns.forEachIndexed { i, sign ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sign, color = GetAutoSureColors.OnInk, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                YesNo("Yes", accident[i] == true, Color(0xFFE5483B)) { if (accident[i] == true) accident.remove(i) else accident[i] = true }
                Spacer(Modifier.width(4.dp))
                YesNo("No", accident[i] == false, Color(0xFF17A383)) { if (accident[i] == false) accident.remove(i) else accident[i] = false }
            }
        }
        if (accident.values.any { it }) {
            Text(
                "🚩 Possible accident repair. Ask: \"Has this car had any body work? Can I see the insurance claim history?\"",
                color = Color(0xFFFF8A7E), fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A1512)).padding(10.dp)
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GetAutoSureColors.Ink).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Body, tyres and cabin", color = GetAutoSureColors.OnInkMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            if (maxCost == 0) "Nothing marked" else "${rupees(minCost)} – ${"%,d".format(maxCost)}",
            color = GetAutoSureColors.AccentBright, fontSize = 16.sp, fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(14.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Next: price and report") }
}

@Composable
private fun IssueChip(label: String, item: ExteriorItem, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (on) GetAutoSureColors.AccentSoft else Color.Transparent)
            .border(1.5.dp, if (on) GetAutoSureColors.AccentBright else GetAutoSureColors.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text((if (on) "✓ " else "") + label, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextPrimary, fontWeight = FontWeight.SemiBold))
        Text("  ${rupees(item.minInr)}–${"%,d".format(item.maxInr)}", style = GetAutoSureType.Caption)
    }
}

@Composable
private fun YesNo(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) Color.White else GetAutoSureColors.OnInkMuted,
        fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) color else GetAutoSureColors.InkRaised)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** The top-down car. Marked parts are orange with their first problem written on them; the tapped one is outlined. */
@Composable
private fun CarDiagram(marks: Map<String, Set<PanelIssue>>, selectedId: String?, onTap: (CarPanel) -> Unit) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A2E07))
    val dirStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GetAutoSureColors.TextSecondary)
    val border = Color(0xFFC9CDD3)
    val bright = GetAutoSureColors.AccentBright
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.width(220.dp).aspectRatio(BOX_W / BOX_H)
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val s = size.width / BOX_W
                        val at = Offset(p.x / s, p.y / s)
                        // Mirrors first: they're small and sit beside the doors.
                        val hit = PANEL_RECTS.entries.sortedBy { it.value.width * it.value.height }
                            .firstOrNull { it.value.inflate(3f).contains(at) }?.key
                        LookAround.panels.firstOrNull { it.id == hit }?.let(onTap)
                    }
                }
        ) {
            val s = size.width / BOX_W
            fun r(rect: Rect) = Rect(rect.left * s, rect.top * s, rect.right * s, rect.bottom * s)
            WHEELS.forEach { w -> val q = r(w); drawRoundRect(Color(0xFF2B2B2B), q.topLeft, q.size, CornerRadius(4f * s)) }
            LookAround.panels.forEach { panel ->
                val q = r(PANEL_RECTS.getValue(panel.id))
                val marked = marks[panel.id].orEmpty()
                val glass = panel.kind == com.enginesense.app.vehicle.PanelKind.GLASS
                val fill = when {
                    marked.isNotEmpty() -> Color(0xFFFFE3CC)
                    glass -> Color(0xFFEEF2F6)
                    else -> Color.White
                }
                drawRoundRect(fill, q.topLeft, q.size, CornerRadius(5f * s))
                drawRoundRect(
                    if (marked.isNotEmpty() || panel.id == selectedId) bright else border, q.topLeft, q.size, CornerRadius(5f * s),
                    style = Stroke(width = (if (panel.id == selectedId) 3f else if (marked.isNotEmpty()) 2f else 1.2f) * s)
                )
                marked.firstOrNull()?.let { issue ->
                    val text = issue.label + if (marked.size > 1) " +${marked.size - 1}" else ""
                    val layout = measurer.measure(text, labelStyle)
                    if (layout.size.width < q.width + 30f * s) {
                        drawText(layout, topLeft = Offset(q.center.x - layout.size.width / 2f, q.center.y - layout.size.height / 2f))
                    }
                }
            }
            val front = measurer.measure("FRONT", dirStyle)
            drawText(front, topLeft = Offset(size.width / 2 - front.size.width / 2f, 0f))
            val rear = measurer.measure("REAR", dirStyle)
            drawText(rear, topLeft = Offset(size.width / 2 - rear.size.width / 2f, size.height - rear.size.height))
        }
    }
    Spacer(Modifier.height(6.dp))
}

private fun Rect.inflate(by: Float) = Rect(left - by, top - by, right + by, bottom + by)
