package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.ai.RepairCostEntry
import com.enginesense.app.vehicle.ai.RepairCostReference

/**
 * The full version of [RepairCostReference]'s list — the Repair Guidance screen only shows a
 * six-item preview ([RepairCostReference.preview]); this is where "See all categories" goes.
 * Same tentative-not-a-quote framing as the preview, stated once at the top rather than per row.
 */
@Composable
fun RepairCostReferenceScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Typical Repair Costs", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tentative ranges for India, not real quotes — actual pricing varies a lot by city, workshop, and car segment. Always confirm with your mechanic before agreeing to a repair.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(RepairCostReference.entries) { entry ->
                FullRepairCostRow(entry)
            }
        }
    }
}

@Composable
private fun FullRepairCostRow(entry: RepairCostEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
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
