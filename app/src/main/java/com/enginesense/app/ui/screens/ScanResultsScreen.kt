package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ScanResult
import com.enginesense.app.obd.Severity
import com.enginesense.app.ui.components.DemoDataBanner
import com.enginesense.app.ui.components.HealthScoreRing
import com.enginesense.app.ui.components.SeverityBadge
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.CategoryState
import com.enginesense.app.vehicle.DiagnosticFinding

/**
 * The screen a scan actually lands on — "the most important screen after Home" per the
 * customer-outcomes brief. Structure is fixed, in this order: overall summary, what's
 * working normally, what needs attention (with severity + evidence), a next-step line, then
 * View Full Report / Ask AI Mechanic. Nothing here is invented — categories/findings come
 * straight from the same HealthScoreCalculator output Home and Reports already use.
 */
@Composable
fun ScanResultsScreen(
    viewModel: GetAutoSureViewModel,
    onOpenIssue: (String) -> Unit,
    onOpenFullReport: () -> Unit,
    onOpenAiMechanic: () -> Unit,
    onDone: () -> Unit
) {
    val result by viewModel.latestResult.collectAsState()
    val r = result

    if (r == null) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text("No scan result to show.", style = GetAutoSureType.Body)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to Home") }
        }
        return
    }

    val allUnavailable = r.health.score == null
    val ringColor = when {
        allUnavailable -> GetAutoSureColors.Unavailable
        r.health.categories.any { it.state == CategoryState.ATTENTION } -> GetAutoSureColors.Attention
        r.health.categories.any { it.state == CategoryState.WATCH } -> GetAutoSureColors.Watch
        else -> GetAutoSureColors.Good
    }
    val working = r.health.categories.filter { it.state == CategoryState.GOOD }
    val attention = r.findings.filter { it.severity != Severity.INFO }
    val topFinding = attention.minByOrNull { severityRank(it.severity) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (r.isDemo) {
            item { DemoDataBanner() }
        }

        // 1. Overall summary — only shown because r.health exists; nothing here is a guess.
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.health.score != null) {
                    HealthScoreRing(r.health.score, ringColor, diameter = 76.dp)
                } else {
                    Box(
                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(38.dp))
                            .background(GetAutoSureColors.Unavailable.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Text("No\ndata", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Unavailable), textAlign = TextAlign.Center) }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Scan complete", style = GetAutoSureType.Caption)
                    Text(r.health.label, style = GetAutoSureType.ScreenTitle)
                    Text(
                        "This reflects an OBD scan — it covers what the car's computer reports, not a full physical inspection.",
                        style = GetAutoSureType.Caption
                    )
                }
            }
        }

        // 2. What's working normally
        if (working.isNotEmpty()) {
            item {
                Column {
                    Text("Working normally", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Good))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        working.joinToString(" · ") { it.category.displayName },
                        style = GetAutoSureType.Body
                    )
                }
            }
        }

        // 3. What needs attention — severity + the same plain-language evidence used everywhere else
        if (attention.isNotEmpty()) {
            item { Text("Needs attention", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Attention)) }
            items(attention) { finding -> ResultFindingCard(finding, onClick = { onOpenIssue(finding.id) }) }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No issues found on this scan.",
                        style = GetAutoSureType.Body,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // 4+5. Recommended next step — the single highest-priority action, not a repeat of every finding
        if (topFinding != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.SurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Recommended next step", style = GetAutoSureType.Caption)
                        Spacer(Modifier.height(4.dp))
                        Text(topFinding.recommendedAction, style = GetAutoSureType.Body)
                    }
                }
            }
        }

        // 6. The two actions this screen exists to lead to
        item {
            Column {
                Button(onClick = onOpenFullReport, modifier = Modifier.fillMaxWidth()) { Text("View Full Report") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenAiMechanic, modifier = Modifier.fillMaxWidth()) { Text("Ask AI Mechanic") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to Home") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun severityRank(s: Severity) = when (s) { Severity.CRITICAL -> 0; Severity.MODERATE -> 1; Severity.MINOR -> 2; Severity.INFO -> 3 }

@Composable
private fun ResultFindingCard(finding: DiagnosticFinding, onClick: () -> Unit) {
    val color = when (finding.severity) {
        Severity.CRITICAL -> GetAutoSureColors.Attention
        Severity.MODERATE, Severity.MINOR -> GetAutoSureColors.Watch
        Severity.INFO -> GetAutoSureColors.Unavailable
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(finding.title, style = GetAutoSureType.Body.copy(color = color), modifier = Modifier.weight(1f))
                SeverityBadge(finding.severity.name, color)
            }
            Spacer(Modifier.height(4.dp))
            Text(finding.plainLanguageSummary, style = GetAutoSureType.Caption)
            if (finding.relatedDtcCodes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Code: ${finding.relatedDtcCodes.joinToString(", ")}", style = GetAutoSureType.Caption)
            }
        }
    }
}
