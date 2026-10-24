package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.vehicle.CoolingDiagnosisAnalyzer
import com.enginesense.app.vehicle.EngineDiagnosisAnalyzer
import com.enginesense.app.vehicle.HealthScoreCalculator
import com.enginesense.app.vehicle.HealthCategory
import com.enginesense.app.vehicle.SignalReading
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

/**
 * Premium's "Detailed Repair Diagnosis" — the cross-signal interpretation [com.enginesense.
 * app.vehicle.FuelCombustionAnalyzer] already does for Fuel & Combustion, extended to Engine
 * and Cooling too. Free's category detail screens show raw grouped readings; this is the
 * composite "here's what it actually means" read on all three in one place.
 */
@Composable
fun DetailedDiagnosisScreen(viewModel: GetAutoSureViewModel) {
    val result by viewModel.latestResult.collectAsState()
    val r = result
    if (r == null) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text("Run a scan first to see this.", style = GetAutoSureType.Body)
        }
        return
    }

    val engineFindings = r.findings.filter { HealthCategory.ENGINE in HealthScoreCalculator.categoriesOf(it) }
    val coolingFindings = r.findings.filter { HealthCategory.COOLING in HealthScoreCalculator.categoriesOf(it) }
    val engine = EngineDiagnosisAnalyzer.analyze(r.snapshot, engineFindings)
    val cooling = CoolingDiagnosisAnalyzer.analyze(r.snapshot, coolingFindings)
    val fuel = r.fuelCombustionInsight

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Text("Detailed repair diagnosis", style = GetAutoSureType.ScreenTitle)
                Spacer(Modifier.height(4.dp))
                Text("What this scan's signals mean together, not one reading at a time.", style = GetAutoSureType.Caption)
            }
        }
        item { DiagnosisCard("Engine", engine.primarySignal, engine.supportingSignals, engine.interpretation) }
        item { DiagnosisCard("Cooling", cooling.primarySignal, cooling.supportingSignals, cooling.interpretation) }
        item { DiagnosisCard("Fuel & Combustion", fuel.primarySignal, fuel.supportingSignals, fuel.interpretation) }
    }
}

@Composable
private fun DiagnosisCard(title: String, primary: SignalReading?, supporting: List<SignalReading>, interpretation: String) {
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = GetAutoSureType.SectionTitle)
            Spacer(Modifier.height(8.dp))
            Text(interpretation, style = GetAutoSureType.Body)
            if (primary != null || supporting.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                (listOfNotNull(primary) + supporting).forEach { s ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.label, style = GetAutoSureType.Caption)
                        Text(s.value, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextSecondary))
                    }
                }
            }
        }
    }
}
