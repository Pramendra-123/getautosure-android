package com.enginesense.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.ai.RepairCostEntry
import com.enginesense.app.vehicle.ai.RepairCostReference

/**
 * A real, distinct journey for "Understand a repair" — not a blank AI Mechanic chat. Asks
 * what the mechanic actually recommended before answering, since the app has no way to know
 * that on its own (no invented fault is assumed). If a current scan exists, its context is
 * already what AI Mechanic answers from — this screen's only job is turning what the
 * customer was told into a useful, specific first question.
 */
@Composable
fun UnderstandRepairScreen(viewModel: GetAutoSureViewModel, onAsk: () -> Unit, onSeeAllCosts: () -> Unit = {}) {
    val result by viewModel.latestResult.collectAsState()
    var description by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Understand a Repair", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            "What did the mechanic recommend, or what did the quotation say? Describe it in your own words.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("e.g. \"Mechanic said I need a new catalytic converter\"") }
        )
        Spacer(Modifier.height(12.dp))

        if (result != null) {
            Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Your last scan's findings will be included automatically, so the answer can connect this to what GetAutoSure actually found — not just general advice.",
                    style = GetAutoSureType.Caption,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No recent scan — the answer will be general, not tied to your car's actual readings. Scan first for a more specific answer.",
                    style = GetAutoSureType.Caption,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Button(
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

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
        Spacer(Modifier.height(20.dp))

        Text("Typical repair costs (India)", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tentative ranges, not a real quote — varies a lot by city, workshop and car segment. Always confirm before agreeing.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(14.dp))

        RepairCostReference.preview.forEach { entry -> RepairCostRow(entry) }

        Spacer(Modifier.height(14.dp))
        Text(
            "See all categories →",
            style = GetAutoSureType.Body.copy(color = GetAutoSureColors.AccentPurple, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.clickable(onClick = onSeeAllCosts)
        )
    }
}

@Composable
private fun RepairCostRow(entry: RepairCostEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(entry.category, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "₹${"%,d".format(entry.minInr)}–${"%,d".format(entry.maxInr)}",
                style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Good, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(entry.note, style = GetAutoSureType.Caption)
    }
    HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
}
