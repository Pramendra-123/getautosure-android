package com.enginesense.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.storage.LanguagePrefs
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.DiyGuide
import com.enginesense.app.vehicle.DiyRating
import com.enginesense.app.vehicle.RepairGuidanceBuilder
import com.enginesense.app.vehicle.ai.CarSize
import com.enginesense.app.vehicle.ai.RepairCostEntry
import com.enginesense.app.vehicle.ai.forSize
import com.enginesense.app.vehicle.cards.FaultCard
import com.enginesense.app.vehicle.cards.FaultCardLabels
import com.enginesense.app.vehicle.cards.FaultCardRepository
import com.enginesense.app.vehicle.pricing.ExShowroomPrices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private fun inr(n: Int) = "₹" + NumberFormat.getIntegerInstance(Locale("en", "IN")).format(n)

/** One likely repair with its tentative price, already narrowed to the car's size. */
private data class LikelyRepair(val name: String, val minInr: Int, val maxInr: Int)

/**
 * From fault to fixed: what's wrong (the fault card, in the owner's language), the repair as three
 * steps cheapest first, a check of the workshop's quote, help, and "Mark as fixed" — which the next
 * scan confirms. Falls back to the reference library when a code has no card.
 */
@Composable
fun RepairGuidanceDetailScreen(
    viewModel: GetAutoSureViewModel,
    findingId: String,
    onAsk: () -> Unit,
    onSeeAllCosts: () -> Unit,
    onRunScan: () -> Unit,
    onExpert: () -> Unit = {},
) {
    val result by viewModel.latestResult.collectAsState()
    val garage by viewModel.garage.collectAsState()
    val context = LocalContext.current
    val r = result
    val finding = r?.findings?.firstOrNull { it.id == findingId }
    if (r == null || finding == null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("This finding is no longer in your latest scan.", style = GetAutoSureType.Body)
        }
        return
    }
    val g = RepairGuidanceBuilder.build(finding, scanDateLabel(r.timestampMillis))
    val profile = garage.active?.profile
    val carName = profile?.let { listOfNotNull(it.brand, it.model.ifBlank { null }).joinToString(" ") }?.ifBlank { null }
    val size = CarSize.fromExShowroom(ExShowroomPrices.lookup(profile?.brand, profile?.model)?.midInr)
    val language = remember { LanguagePrefs.get(context) }
    val card by produceState<FaultCard?>(null, g.code, profile?.brand) {
        val code = g.code ?: return@produceState
        value = withContext(Dispatchers.IO) { runCatching { FaultCardRepository.find(FaultCardRepository.load(context), code, profile?.brand) }.getOrNull() }
    }
    val c = card
    val lang = if (c != null && c.has(language.code)) language.code else "en"
    val text = c?.textIn(lang)
    val labels = FaultCardLabels.of(lang)

    // Step 2: the card's repairs in its own order, else the reference library's; priced for this car's size.
    val repairs = if (c != null && text != null) {
        text.costParts.zip(c.costs).map { (part, range) ->
            RepairCostEntry(part, range.first, range.last, "", emptyList()).forSize(size).let { LikelyRepair(part, it.minInr, it.maxInr) }
        }
    } else {
        g.costEstimates.map { it.forSize(size).let { e -> LikelyRepair(e.category, e.minInr, e.maxInr) } }
    }
    val diy = g.diyGuides.firstOrNull()
    var markingFixed by remember { mutableStateOf(false) }

    fun ask(question: String) {
        viewModel.askAiMechanic(question, context)
        onAsk()
    }
    val subject = g.code?.let { "fault code $it (${text?.title ?: g.title})" } ?: "\"${finding.title}\""

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // What's wrong, from the card when there is one.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GetAutoSureColors.Ink).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                g.code?.let {
                    Text(it, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(GetAutoSureColors.AccentBright).padding(horizontal = 7.dp, vertical = 1.dp))
                    Spacer(Modifier.width(6.dp))
                }
                if (c != null) {
                    Text(labels.urgency.getValue(c.urgency), color = Color(0xFF111111), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(GetAutoSureColors.AccentBright).padding(horizontal = 8.dp, vertical = 1.dp))
                } else {
                    SeverityChip(g.severity)
                }
                Spacer(Modifier.weight(1f))
                carName?.let { Text(it, color = GetAutoSureColors.OnInkMuted, fontSize = 11.sp) }
            }
            Text(text?.title ?: g.title, color = GetAutoSureColors.OnInk, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
            Text(text?.meaning ?: g.meaning, color = GetAutoSureColors.OnInk, fontSize = 14.sp, lineHeight = 20.sp)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(GetAutoSureColors.InkRaised).padding(10.dp)
            ) {
                Text(labels.drive, color = GetAutoSureColors.AccentBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text?.drive ?: g.drivingAdvice, color = GetAutoSureColors.OnInk, fontSize = 13.5.sp, lineHeight = 19.sp)
            }
            if (g.stopSigns.isNotEmpty()) {
                Text("Pull over and switch off if: " + g.stopSigns.joinToString("; ").replaceFirstChar(Char::lowercase) + ".",
                    color = GetAutoSureColors.OnInkMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        Text("Fix it in 3 steps", style = GetAutoSureType.SectionTitle)
        Step(1, "Cheapest first check") {
            Text(text?.firstCheck ?: g.checks ?: "Start with the simplest, cheapest check before replacing any part.", style = GetAutoSureType.Body)
            diy?.let { DiyLine(it) }
            if (text == null && g.checks == null) {
                TextButton(onClick = { ask("For $subject: what's the cheapest thing I can check or try first?") }, contentPadding = PaddingValues(0.dp)) {
                    Text("Ask AI Mechanic for the first check")
                }
            }
        }
        Step(2, "If that doesn't fix it: likely repairs") {
            repairs.forEachIndexed { i, rep ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("${i + 1}. ${rep.name}", style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
                    Text("${inr(rep.minInr)}–${inr(rep.maxInr)}", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                }
            }
            Text(
                (if (size != null) "Priced for a ${size.name.lowercase()} car. " else "") + "Tentative ranges, not a quote.",
                style = GetAutoSureType.Caption
            )
            TextButton(onClick = onSeeAllCosts, contentPadding = PaddingValues(0.dp)) { Text("See all typical costs") }
        }
        Step(3, "After the repair: confirm the fix", last = true) {
            Text("Scan again with GetAutoSure. We check the code is gone and the self-tests pass.", style = GetAutoSureType.Body)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onRunScan, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
        }

        if (repairs.isNotEmpty()) QuoteCheck(repairs, carName, onExpert)

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Not sure what to do?", style = GetAutoSureType.SectionTitle)
            Button(onClick = { ask("My car has $subject. What should I do first, and what will it likely cost?") }, modifier = Modifier.fillMaxWidth()) {
                Text("Ask AI Mechanic")
            }
            OutlinedButton(onClick = onExpert, modifier = Modifier.fillMaxWidth()) { Text("Talk to a real expert · 15 min video") }
        }

        g.code?.let { code ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.AccentSoft).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Done the repair?", style = GetAutoSureType.SectionTitle)
                Text("Save what you paid. It goes into your Wallet and service history, and your next scan confirms the fix.", style = GetAutoSureType.Caption)
                OutlinedButton(onClick = { markingFixed = true }, modifier = Modifier.fillMaxWidth()) { Text("Mark as fixed") }
            }
            if (markingFixed) {
                MarkFixedDialog(
                    onDismiss = { markingFixed = false },
                    onSave = { paid ->
                        viewModel.markRepairFixed(code, text?.title ?: g.title, paid)
                        markingFixed = false
                        android.widget.Toast.makeText(context, "Saved. Scan again after the repair to confirm the fix.", android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The owner types the workshop's quote and sees whether it's usual for this repair on this car. */
@Composable
private fun QuoteCheck(repairs: List<LikelyRepair>, carName: String?, onExpert: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf<Int?>(null) }
    val low = repairs.minOf { it.minInr }
    val high = repairs.maxOf { it.maxInr }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Got a quote from a workshop?", style = GetAutoSureType.SectionTitle)
        Text("Check it against usual prices${carName?.let { " for your $it" } ?: ""}.", style = GetAutoSureType.Caption)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { v -> input = v.filter(Char::isDigit).take(7); checked = null },
                prefix = { Text("₹ ") },
                placeholder = { Text("Amount quoted") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { checked = input.toIntOrNull() }, enabled = input.isNotEmpty()) { Text("Check") }
        }
        checked?.let { q ->
            val (bg, fg, verdict) = when {
                q > high -> Triple(Color(0xFFFDECEA), GetAutoSureColors.Attention,
                    "Higher than usual. For this fault most repairs cost ${inr(low)}–${inr(high)}. Ask the workshop which part they're replacing and why.")
                q < low / 2 -> Triple(Color(0xFFFDF0E1), GetAutoSureColors.Watch,
                    "Lower than usual. Check what's included, and whether genuine parts are used.")
                else -> Triple(Color(0xFFE3F4EE), GetAutoSureColors.Good,
                    "Within the usual range for this repair (${inr(low)}–${inr(high)}).")
            }
            Text(verdict, color = fg, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(10.dp))
            if (q > high) {
                Button(
                    onClick = onExpert, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GetAutoSureColors.AccentBright, contentColor = Color(0xFF111111))
                ) { Text("Get a second opinion: expert call", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun MarkFixedDialog(onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var paid by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark as fixed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How much did you pay? Leave it empty if you don't want to save the cost.", style = GetAutoSureType.Caption)
                OutlinedTextField(
                    value = paid, onValueChange = { v -> paid = v.filter(Char::isDigit).take(7) },
                    prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(paid.toIntOrNull()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Shown once after the scan that follows a "Mark as fixed": proof the fault is gone, or that it came back. */
@Composable
fun FixConfirmationDialog(viewModel: GetAutoSureViewModel, onExpert: () -> Unit) {
    val confirmation by viewModel.fixConfirmation.collectAsState()
    val c = confirmation ?: return
    AlertDialog(
        onDismissRequest = { viewModel.dismissFixConfirmation() },
        title = { Text(if (c.fixed) "✓ ${c.repair.code} is fixed" else "${c.repair.code} came back") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (c.fixed) {
                    Text("Confirmed by today's scan of your ${c.carTitle}.", style = GetAutoSureType.Body)
                    Text("✓ Code gone from the engine computer", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Good))
                    if (c.testsDone != null && c.testsTotal != null && c.testsTotal > 0) {
                        val all = c.testsDone == c.testsTotal
                        Text(
                            "✓ Self-tests: ${c.testsDone} of ${c.testsTotal} done" + if (all) "" else ". Drive about 50 km to finish them.",
                            style = GetAutoSureType.Body.copy(color = if (all) GetAutoSureColors.Good else GetAutoSureColors.Watch)
                        )
                    }
                    c.repair.paidInr?.let { Text("Saved: ${c.repair.title}, ${inr(it)}, in Wallet → Repair.", style = GetAutoSureType.Caption) }
                } else {
                    Text("Today's scan of your ${c.carTitle} still shows ${c.repair.code}, so the cause wasn't fixed. Don't pay for the same part twice: an expert can look at both scans.", style = GetAutoSureType.Body)
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.dismissFixConfirmation() }) { Text("OK") } },
        dismissButton = if (c.fixed) null else ({ TextButton(onClick = { viewModel.dismissFixConfirmation(); onExpert() }) { Text("Talk to an expert") } })
    )
}

@Composable
private fun Step(number: Int, title: String, last: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(26.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(GetAutoSureColors.Ink), contentAlignment = Alignment.Center) {
                Text("$number", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(GetAutoSureColors.Border))
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier.weight(1f).padding(bottom = if (last) 0.dp else 10.dp).clip(RoundedCornerShape(14.dp))
                .background(GetAutoSureColors.Surface).border(1.dp, GetAutoSureColors.Border, RoundedCornerShape(14.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.Bold))
            content()
        }
    }
}

/** How hard the first check is, how long, and its video once one is linked in [com.enginesense.app.vehicle.DiyGuides]. */
@Composable
private fun DiyLine(guide: DiyGuide) {
    val context = LocalContext.current
    val color = when (guide.rating) {
        DiyRating.EASY -> GetAutoSureColors.Good
        DiyRating.MEDIUM -> GetAutoSureColors.Watch
        DiyRating.WORKSHOP -> GetAutoSureColors.Attention
    }
    Text(
        "● ${guide.rating.label}" + (guide.time?.let { " · $it" } ?: "") + (guide.partCost?.let { " · $it" } ?: ""),
        color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
    )
    guide.videoUrl?.let { url ->
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }, contentPadding = PaddingValues(0.dp)) {
            Text("▶ Watch how")
        }
    }
}
