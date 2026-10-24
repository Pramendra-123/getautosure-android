package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.components.HealthScoreRing
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.HealthCategory
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidReading
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehicle.SignalReading

/** "How is the engine behaving?" etc — a reader-friendly framing per the product spec, display-only groupings of which PIDs feed each question (not a separate computed sub-score; see [com.enginesense.app.vehicle.HealthScoreCalculator] for the actual scoring evidence sets, which legitimately overlap across categories). */
private data class SignalGroup(val label: String, val pids: List<PidId>)

private val ENGINE_QUESTION = "Is the engine operating normally and efficiently?"
private val ENGINE_GROUPS = listOf(
    SignalGroup("Idle behaviour", listOf(PidId.RPM, PidId.ENGINE_LOAD, PidId.THROTTLE_POSITION)),
    SignalGroup("Engine response", listOf(PidId.ACCELERATOR_PEDAL_POSITION_D, PidId.THROTTLE_POSITION, PidId.RPM, PidId.ENGINE_LOAD)),
    SignalGroup("Air intake behaviour", listOf(PidId.INTAKE_MANIFOLD_PRESSURE, PidId.RPM, PidId.ENGINE_LOAD)),
    SignalGroup("Combustion behaviour", listOf(PidId.TIMING_ADVANCE, PidId.ENGINE_LOAD)),
    SignalGroup("Torque behaviour", listOf(PidId.ACTUAL_ENGINE_TORQUE_PERCENT, PidId.ENGINE_REFERENCE_TORQUE))
)

private val COOLING_QUESTION = "Is the engine maintaining a healthy operating temperature?"
private val COOLING_GROUPS = listOf(
    SignalGroup("Primary signals", listOf(PidId.COOLANT_TEMP_C, PidId.INTAKE_AIR_TEMP_C, PidId.AMBIENT_AIR_TEMP_C, PidId.CATALYST_TEMP_B1S1)),
    SignalGroup("Operating context", listOf(PidId.RPM, PidId.ENGINE_LOAD, PidId.SPEED_KMH, PidId.RUN_TIME_SINCE_START))
)

@Composable
fun CategoryDetailScreen(viewModel: GetAutoSureViewModel, category: HealthCategory, onOpenAiMechanic: () -> Unit) {
    val result by viewModel.latestResult.collectAsState()
    val r = result
    if (r == null) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text("Run a scan first to see this.", style = GetAutoSureType.Body)
        }
        return
    }
    val health = r.health.categories.first { it.category == category }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HealthScoreRing(health.score ?: 0, GetAutoSureColors.forCategoryState(health.state), diameter = 72.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(category.displayName, style = GetAutoSureType.ScreenTitle)
                        Text(questionFor(category), style = GetAutoSureType.Caption)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(health.note, style = GetAutoSureType.Body)
            }
        }

        if (category == HealthCategory.FUEL_COMBUSTION) {
            val insight = r.fuelCombustionInsight
            val primary = insight.primarySignal
            if (primary != null) {
                item { SignalCard("Primary signal", listOf(primary)) }
            }
            if (insight.supportingSignals.isNotEmpty()) {
                item { SignalCard("Supporting signals", insight.supportingSignals) }
            }
            val coverageNote = insight.dataCoverageNote
            if (coverageNote != null) {
                item { Text(coverageNote, style = GetAutoSureType.Caption) }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.SurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AI interpretation", style = GetAutoSureType.Caption)
                        Spacer(Modifier.height(4.dp))
                        Text(insight.interpretation, style = GetAutoSureType.Body)
                    }
                }
            }
        } else {
            val groups = if (category == HealthCategory.ENGINE) ENGINE_GROUPS else if (category == HealthCategory.COOLING) COOLING_GROUPS else emptyList()
            items(groups) { group -> GroupCard(group, r.snapshot.readings) }
        }

        item {
            Column {
                Text(
                    "This reflects your last scan only — one reading isn't a trend.",
                    style = GetAutoSureType.Caption
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedButton(onClick = onOpenAiMechanic, modifier = Modifier.fillMaxWidth()) {
                    Text("Ask AI Mechanic about this")
                }
            }
        }
    }
}

private fun questionFor(category: HealthCategory): String = when (category) {
    HealthCategory.ENGINE -> ENGINE_QUESTION
    HealthCategory.COOLING -> COOLING_QUESTION
    HealthCategory.FUEL_COMBUSTION -> "Is the air/fuel mixture and combustion behaviour healthy?"
    else -> ""
}

@Composable
private fun SignalCard(title: String, signals: List<SignalReading>) {
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = GetAutoSureType.Caption)
            Spacer(Modifier.height(6.dp))
            signals.forEach { s ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.label, style = GetAutoSureType.Body)
                    Text(s.value, style = GetAutoSureType.Body.copy(color = GetAutoSureColors.TextSecondary))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun GroupCard(group: SignalGroup, readings: Map<PidId, PidReading>) {
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(group.label, style = GetAutoSureType.Caption)
            Spacer(Modifier.height(6.dp))
            group.pids.forEachIndexed { index, pid ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(pid.displayName, style = GetAutoSureType.Body)
                    Text(formatReading(pid, readings[pid]), style = GetAutoSureType.Body.copy(color = GetAutoSureColors.TextSecondary))
                }
                if (index != group.pids.lastIndex) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun formatReading(pid: PidId, reading: PidReading?): String {
    if (reading == null || reading.status != PidStatus.VERIFIED || reading.value == null) return "—"
    val value = reading.value
    val formatted = if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
    return if (pid.unit.isEmpty()) formatted else "$formatted ${pid.unit}"
}
