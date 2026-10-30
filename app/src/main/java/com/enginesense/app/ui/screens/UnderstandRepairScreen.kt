package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.obd.Severity
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.DiagnosticFinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repair Guidance home: every finding from the latest scan, most urgent first, each opening
 * [RepairGuidanceDetailScreen]. Below that, the free-text "what did your mechanic say?" ask is
 * kept for repairs a scan can't see (brakes, suspension, a quotation in hand).
 */
@Composable
fun UnderstandRepairScreen(
    viewModel: GetAutoSureViewModel,
    onAsk: () -> Unit,
    onOpenFinding: (findingId: String) -> Unit,
    onRunScan: () -> Unit,
) {
    val result by viewModel.latestResult.collectAsState()
    var description by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Repair guidance", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))

        val r = result
        when {
            r == null -> {
                Text("Scan your car to get step-by-step guidance for any fault it finds.", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRunScan, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Scan my car") }
            }
            r.findings.isEmpty() -> {
                Text("From your scan on ${scanDateLabel(r.timestampMillis)}${if (r.isDemo) " (demo data)" else ""}.", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(16.dp))
                GuidanceCard {
                    Text("No faults found", style = GetAutoSureType.SectionTitle)
                    Spacer(Modifier.height(4.dp))
                    Text("Your last scan didn't find any fault codes or warning readings. Scan again if a warning light comes on.", style = GetAutoSureType.Caption)
                }
            }
            else -> {
                Text(
                    "From your scan on ${scanDateLabel(r.timestampMillis)}${if (r.isDemo) " (demo data)" else ""} — most urgent first. Tap one for what to do.",
                    style = GetAutoSureType.Caption
                )
                Spacer(Modifier.height(16.dp))
                r.findings.forEach { finding ->
                    FindingRow(finding, onClick = { onOpenFinding(finding.id) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Got a mechanic's recommendation?", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(4.dp))
        Text("Describe it or paste the quotation — AI Mechanic explains what it means and what to ask.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth().height(110.dp),
            placeholder = { Text("Mechanic said I need a new clutch plate and pressure plate") }
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val q = if (description.isBlank()) {
                    "A mechanic gave me a repair recommendation. What should I know, and what should I ask them?"
                } else {
                    "My mechanic recommended: \"$description\". What does this mean, what should I verify, and what should I ask them?"
                }
                viewModel.askAiMechanic(q, context)
                onAsk()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ask AI Mechanic") }
    }
}

@Composable
private fun FindingRow(finding: DiagnosticFinding, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GetAutoSureColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SeverityChip(finding.severity)
            Spacer(Modifier.height(6.dp))
            Text(finding.title, style = GetAutoSureType.SectionTitle)
            Text(finding.plainLanguageSummary, style = GetAutoSureType.Caption, maxLines = 2)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GetAutoSureColors.TextSecondary)
    }
}

@Composable
internal fun SeverityChip(severity: Severity) {
    val color = guidanceSeverityColor(severity)
    Text(
        severity.label.uppercase(),
        style = GetAutoSureType.Caption.copy(color = color),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

internal fun guidanceSeverityColor(severity: Severity): Color = when (severity) {
    Severity.CRITICAL -> GetAutoSureColors.Attention
    Severity.MODERATE -> GetAutoSureColors.Watch
    Severity.MINOR -> GetAutoSureColors.Good
    Severity.INFO -> GetAutoSureColors.TextSecondary
}

@Composable
internal fun GuidanceCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GetAutoSureColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content
    )
}

internal fun scanDateLabel(timestampMillis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(timestampMillis))
