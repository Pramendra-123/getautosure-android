package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.components.HealthScoreRing
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.CategoryState
import com.enginesense.app.vehicle.HealthCategory

/**
 * The 6-category grid that used to live inline on Home — pulled out to its own screen so
 * Home can stay to "one hero number, tap for detail". Same data, same scoring (see
 * HealthScoreCalculator), just reached by tapping the ring instead of always being visible.
 * Each category here still taps through to the existing CategoryDetailScreen.
 */
@Composable
fun HealthBreakdownScreen(viewModel: GetAutoSureViewModel, onNavigate: (String) -> Unit) {
    val result by viewModel.latestResult.collectAsState()
    val r = result
    if (r == null) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text("Run a scan first to see this.", style = GetAutoSureType.Body)
        }
        return
    }

    val ringColor = when {
        r.health.score == null -> GetAutoSureColors.Unavailable
        r.health.categories.any { it.state == CategoryState.ATTENTION } -> GetAutoSureColors.Attention
        r.health.categories.any { it.state == CategoryState.WATCH } -> GetAutoSureColors.Watch
        else -> GetAutoSureColors.Good
    }
    val gridCategories = listOf(
        HealthCategory.ENGINE, HealthCategory.COOLING, HealthCategory.FUEL_COMBUSTION,
        HealthCategory.BATTERY, HealthCategory.DIAGNOSTICS, HealthCategory.EMISSIONS
    ).mapNotNull { cat -> r.health.categories.firstOrNull { it.category == cat } }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.health.score != null) {
                HealthScoreRing(r.health.score, ringColor, diameter = 72.dp)
            } else {
                Box(
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(GetAutoSureColors.Unavailable.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Text("No\ndata", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Unavailable), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Health Breakdown", style = GetAutoSureType.ScreenTitle)
                Text(r.health.label, style = GetAutoSureType.Caption)
            }
        }
        Spacer(Modifier.height(24.dp))

        gridCategories.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                pair.forEach { cat ->
                    BreakdownItem(
                        icon = iconFor(cat.category),
                        label = cat.category.displayName,
                        value = cat.score?.let { "$it" } ?: stateLabel(cat.state),
                        color = GetAutoSureColors.forCategoryState(cat.state),
                        onClick = { onNavigate("categoryDetail/${cat.category.name}") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Battery, Diagnostics and Emissions show a status, not a number — there's no honest 0-100 score for those yet.",
            style = GetAutoSureType.Caption
        )
    }
}

@Composable
private fun BreakdownItem(icon: ImageVector, label: String, value: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, style = GetAutoSureType.SectionTitle.copy(color = color))
            Text(label, style = GetAutoSureType.Caption)
        }
    }
}

private fun iconFor(category: HealthCategory) = when (category) {
    HealthCategory.ENGINE -> Icons.Filled.Settings
    HealthCategory.COOLING -> Icons.Filled.AcUnit
    HealthCategory.FUEL_COMBUSTION -> Icons.Filled.LocalGasStation
    HealthCategory.BATTERY -> Icons.Filled.BatteryFull
    HealthCategory.DIAGNOSTICS -> Icons.Filled.Troubleshoot
    HealthCategory.EMISSIONS -> Icons.Filled.Eco
}

private fun stateLabel(state: CategoryState): String = when (state) {
    CategoryState.GOOD -> "Good"
    CategoryState.WATCH -> "Watch"
    CategoryState.ATTENTION -> "Needs attention"
    CategoryState.UNAVAILABLE -> "No data"
}
