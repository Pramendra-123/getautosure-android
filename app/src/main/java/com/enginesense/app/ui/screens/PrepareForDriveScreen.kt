package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.obd.Severity
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

private val PHYSICAL_CHECKLIST = listOf(
    "Tyres" to "Check tread depth and pressure on all 4 + spare — an OBD scan can't see tyre condition at all.",
    "Brakes" to "Listen for grinding/squealing and check pedal feel — the scan can show ABS faults, but not pad thickness.",
    "Lights" to "Walk around and check headlights, brake lights, indicators — not something OBD reports on.",
    "Fluids" to "Visually check engine oil and coolant levels — the scan reads sensor values, not a dipstick.",
    "Wipers & washer fluid" to "Quick visual/functional check — unrelated to OBD data entirely.",
    "Spare tyre & tools" to "Confirm the spare is inflated and the jack/tools are actually in the car."
)

/**
 * A real pre-drive journey, not an empty AI Mechanic screen. Combines what an OBD scan
 * actually can tell you (findings from the latest scan, if one exists) with what it
 * fundamentally can't (tyres, brakes, lights, fluids — all physical checks), so the
 * customer doesn't mistake "no fault codes" for "the car is 100% road-ready".
 */
@Composable
fun PrepareForDriveScreen(viewModel: GetAutoSureViewModel, onRunScan: () -> Unit, onAskAiMechanic: () -> Unit) {
    val result by viewModel.latestResult.collectAsState()
    val findings = result?.findings.orEmpty().filter { it.severity != Severity.INFO }
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("Prepare for a Long Drive", style = GetAutoSureType.ScreenTitle)
                Spacer(Modifier.height(4.dp))
                Text(
                    "An OBD scan checks engine, emissions and electronics — it can't see tyres, brake pads, lights or fluid levels. Use both.",
                    style = GetAutoSureType.Caption
                )
            }
        }

        item {
            if (result == null) {
                Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("No recent scan", style = GetAutoSureType.Body)
                        Spacer(Modifier.height(4.dp))
                        Text("Run one to check for engine/electrical issues before you go.", style = GetAutoSureType.Caption)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onRunScan, modifier = Modifier.fillMaxWidth()) { Text("Scan My Car") }
                    }
                }
            } else if (findings.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("✅ Last scan found nothing on the engine/electronics side", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Good))
                        Spacer(Modifier.height(4.dp))
                        Text("That covers what OBD can check — still run the physical checklist below.", style = GetAutoSureType.Caption)
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.SurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("From your last scan", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
                        Spacer(Modifier.height(4.dp))
                        findings.forEach { f -> Text("• ${f.title}", style = GetAutoSureType.Body) }
                        Spacer(Modifier.height(10.dp))
                        Text("Worth resolving before a long drive, not just noting.", style = GetAutoSureType.Caption)
                    }
                }
            }
        }

        item { Text("Physical checklist (OBD can't check these)", style = GetAutoSureType.SectionTitle) }

        items(PHYSICAL_CHECKLIST) { (title, desc) ->
            Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(title, style = GetAutoSureType.Body)
                    Spacer(Modifier.height(2.dp))
                    Text(desc, style = GetAutoSureType.Caption)
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    viewModel.askAiMechanic("Is my car ok for a long trip?", context)
                    onAskAiMechanic()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ask AI Mechanic about this scan") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
