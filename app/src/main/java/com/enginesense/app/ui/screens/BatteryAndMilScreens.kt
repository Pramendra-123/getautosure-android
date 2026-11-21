package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.components.StatusDot
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehicle.SensorRating
import com.enginesense.app.vehicle.benchmark
import com.enginesense.app.vehicle.rate

/** Quick standalone view of the check-engine light + stored DTC count from the last scan's readiness data. */
@Composable
fun MilStatusScreen(viewModel: GetAutoSureViewModel) {
    val result by viewModel.latestResult.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Check-engine light", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text("The check-engine light, as your car's computer reports it.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(20.dp))

        val readiness = result?.readiness
        if (readiness == null) {
            Text("Run a scan first to see this.", style = GetAutoSureType.Body)
            return@Column
        }

        val color = if (readiness.milOn) GetAutoSureColors.Attention else GetAutoSureColors.Good
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color)
            Spacer(Modifier.width(10.dp))
            Text(if (readiness.milOn) "Check-engine light is ON" else "Check-engine light is off", style = GetAutoSureType.SectionTitle)
        }
        Spacer(Modifier.height(8.dp))
        Text("Stored trouble codes: ${readiness.dtcCount}", style = GetAutoSureType.Body)
    }
}
