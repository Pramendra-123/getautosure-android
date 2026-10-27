package com.enginesense.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.RepairGuidanceBuilder

/**
 * Six-step guidance for one finding — see [RepairGuidance] for where each section's content
 * comes from. Sections the reference library doesn't cover say so and hand off to AI Mechanic
 * with a question built for exactly that gap.
 */
@Composable
fun RepairGuidanceDetailScreen(
    viewModel: GetAutoSureViewModel,
    findingId: String,
    onAsk: () -> Unit,
    onSeeAllCosts: () -> Unit,
    onRunScan: () -> Unit,
) {
    val result by viewModel.latestResult.collectAsState()
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
    val subject = g.code?.let { "fault code $it (${g.title})" } ?: "\"${finding.title}\" (${finding.plainLanguageSummary})"
    fun ask(question: String) {
        viewModel.askAiMechanic(question, context)
        onAsk()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        SeverityChip(g.severity)
        Spacer(Modifier.height(8.dp))
        Text(g.code ?: finding.title, style = GetAutoSureType.ScreenTitle)
        if (g.code != null) Text(g.title, style = GetAutoSureType.Caption)
        Spacer(Modifier.height(20.dp))

        Section(1, "What the car detected") {
            Text(g.meaning, style = GetAutoSureType.Body)
            g.technicalDefinition?.let {
                Spacer(Modifier.height(6.dp))
                Text("Technical definition: $it", style = GetAutoSureType.Caption)
            }
        }

        Section(2, "Urgency") {
            Text(g.drivingAdvice, style = GetAutoSureType.Body.copy(color = guidanceSeverityColor(g.severity)))
            g.symptoms?.let {
                Spacer(Modifier.height(8.dp))
                Text("You might notice: $it", style = GetAutoSureType.Caption)
            }
            if (g.stopSigns.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Pull over and switch off if:", style = GetAutoSureType.Caption.copy(fontWeight = FontWeight.SemiBold))
                g.stopSigns.forEach { Bullet(it) }
            }
        }

        Section(3, "Likely causes") {
            val causes = g.possibleCauses
            if (causes != null) {
                Text("Possibilities, not a diagnosis — a mechanic's check confirms which one it is.", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(6.dp))
                causes.forEach { Bullet(it) }
            } else {
                NotInLibrary("likely causes")
                TextButton(onClick = { ask("What are the most likely causes of $subject on my car, from most to least common? Label them as possibilities.") }, contentPadding = PaddingValues(0.dp)) {
                    Text("Ask AI Mechanic for likely causes")
                }
            }
        }

        Section(4, "What to check next") {
            val checks = g.checks
            if (checks != null) {
                Text(checks, style = GetAutoSureType.Body)
            } else {
                NotInLibrary("checks for this code")
            }
            TextButton(
                onClick = { ask("For $subject: what can I safely check myself first? List the simple checks first, and for each say the tools needed and the skill level (beginner / some experience / mechanic only).") },
                contentPadding = PaddingValues(0.dp)
            ) { Text("Ask AI Mechanic: checks, tools and skill level") }
        }

        Section(5, "Repair paths") {
            Text("Do it yourself", style = GetAutoSureType.Caption.copy(fontWeight = FontWeight.SemiBold))
            TextButton(
                onClick = { ask("Give me a step-by-step DIY guide for $subject: tools and parts needed, skill level, safety precautions, and clearly say when it should be left to a mechanic instead.") },
                contentPadding = PaddingValues(0.dp)
            ) { Text("Get a DIY guide from AI Mechanic") }

            Spacer(Modifier.height(8.dp))
            Text("Typical cost (India)", style = GetAutoSureType.Caption.copy(fontWeight = FontWeight.SemiBold))
            if (g.costEstimates.isEmpty()) {
                Text("No cost range in our reference for this repair.", style = GetAutoSureType.Caption)
            } else {
                Text("Tentative ranges, not a quote — depends on the actual cause, city and workshop.", style = GetAutoSureType.Caption)
                g.costEstimates.forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("If it's: ${entry.category}", style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
                        Text("₹${"%,d".format(entry.minInr)}–${"%,d".format(entry.maxInr)}", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Good))
                    }
                }
            }
            TextButton(onClick = onSeeAllCosts, contentPadding = PaddingValues(0.dp)) { Text("See all typical costs") }

            Spacer(Modifier.height(4.dp))
            Text("What to tell your mechanic", style = GetAutoSureType.Caption.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(4.dp))
            Text(g.mechanicNote, style = GetAutoSureType.Body)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, g.mechanicNote)
                    }
                    context.startActivity(Intent.createChooser(send, "Send to your mechanic"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send to mechanic") }
        }

        Section(6, "After the repair") {
            g.afterRepair.forEachIndexed { i, step -> Bullet(step, marker = "${i + 1}.") }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRunScan, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
        }
    }
}

@Composable
private fun Section(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(GetAutoSureColors.Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Text("$number", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold)) }
        Spacer(Modifier.width(10.dp))
        Text(title, style = GetAutoSureType.SectionTitle)
    }
    Spacer(Modifier.height(8.dp))
    GuidanceCard(content)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Bullet(text: String, marker: String = "•") {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(marker, style = GetAutoSureType.Body, modifier = Modifier.width(20.dp))
        Text(text, style = GetAutoSureType.Body)
    }
}

@Composable
private fun NotInLibrary(what: String) {
    Text("Our reference library doesn't cover $what yet.", style = GetAutoSureType.Caption)
}
