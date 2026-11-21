package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.drivelab.DriveRecorderService
import com.enginesense.app.owner.OwnerInsights
import com.enginesense.app.owner.RenewalKind
import com.enginesense.app.ui.components.GroupNote
import com.enginesense.app.ui.components.InkCard
import com.enginesense.app.ui.components.ListDivider
import com.enginesense.app.ui.components.ListGroup
import com.enginesense.app.ui.components.ListRow
import com.enginesense.app.ui.components.OwnerText
import com.enginesense.app.ui.components.SectionTitle
import com.enginesense.app.ui.components.levelColor
import com.enginesense.app.ui.components.openNearby
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.PidId
import java.time.LocalDate

/**
 * Battery and charging: the scan's running voltage, and from the trip recorder the parked
 * voltage, the start test (lowest voltage while the engine cranks) and charging while driving.
 */
@Composable
fun BatteryScreen(viewModel: GetAutoSureViewModel, onNavigate: (String) -> Unit) {
    val result by viewModel.latestResult.collectAsState()
    val history by viewModel.history.collectAsState()
    val drives = rememberCarDrives(viewModel)
    val recorder by DriveRecorderService.status.collectAsState()
    val volts = result?.snapshot?.verifiedValue(PidId.CONTROL_MODULE_VOLTAGE)
    val bf = drives?.battery
    val lastStart = drives?.trips?.filter { it.crankingMinVolts != null }?.maxByOrNull { it.startMillis }
    val health = OwnerInsights.batteryHealth(volts, bf?.restingVolts, lastStart?.crankingMinVolts ?: bf?.crankingDips?.minOrNull(), bf?.chargingVolts)
    val trend = history.sortedBy { it.timestampMillis }.mapNotNull { it.snapshot.verifiedValue(PidId.CONTROL_MODULE_VOLTAGE)?.toFloat() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Battery", style = GetAutoSureType.ScreenTitle) }
        item {
            InkCard {
                Text("Battery health", style = OwnerText.InkLabel)
                Text(health.headline, style = OwnerText.Big.copy(color = if (health.level == com.enginesense.app.owner.Level.GOOD) GetAutoSureColors.OnInk else GetAutoSureColors.AccentBright))
                volts?.let { Text("%.1f V at the last scan".format(it), style = OwnerText.InkSub) }
            }
        }
        item {
            ListGroup {
                health.lines.forEachIndexed { i, line ->
                    if (i > 0) ListDivider()
                    ListRow(Icons.Filled.BatteryChargingFull, line.substringBefore(":"), line.substringAfter(": ", line), levelColor(health.level))
                }
            }
        }
        if (trend.size >= 2) {
            item { SectionTitle("Voltage over your scans") }
            item {
                ListGroup {
                    Sparkline(trend.takeLast(12), GetAutoSureColors.Accent, Modifier.fillMaxWidth().height(64.dp).padding(16.dp))
                }
            }
        }
        item { SectionTitle("Start test") }
        item {
            ListGroup {
                ListRow(
                    Icons.Filled.PlayCircle,
                    if (lastStart != null) "Last start: lowest %.1f V".format(lastStart.crankingMinVolts) else "No start test yet",
                    if (recorder.running) "Auto-record is on. The next time you start the car, we measure how far the voltage drops."
                    else "Turn on auto-record in Trips, then start the car. A healthy battery stays above 10 V while starting.",
                    onClick = if (recorder.running) null else ({ onNavigate("trips") })
                )
            }
        }
        item { GroupNote("Parked voltage and the start test need the scanner left plugged in with auto-record on.") }
    }
}

/** "Will you pass PUC?" from the scan's readiness self-tests and the check-engine light, with the PUC expiry date. */
@Composable
fun PucCheckScreen(viewModel: GetAutoSureViewModel) {
    val context = LocalContext.current
    val result by viewModel.latestResult.collectAsState()
    val owner by viewModel.ownerData.collectAsState()
    val rc by viewModel.vehicleRc.collectAsState()
    val verdict = OwnerInsights.pucVerdict(result?.readiness, result?.snapshot?.dtcs?.size ?: 0)
    val puc = OwnerInsights.renewals(rc, owner, LocalDate.now()).first { it.kind == RenewalKind.PUC }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Will you pass PUC?", style = GetAutoSureType.ScreenTitle) }
        item {
            InkCard {
                Text("From your last scan", style = OwnerText.InkLabel)
                Text(verdict.headline, style = OwnerText.Big.copy(color = if (verdict.level == com.enginesense.app.owner.Level.GOOD) GetAutoSureColors.OnInk else GetAutoSureColors.AccentBright))
                Spacer(Modifier.height(4.dp))
                Text(verdict.detail, style = OwnerText.InkSub)
            }
        }
        result?.readiness?.let { r ->
            item { SectionTitle("Emission self-tests") }
            item {
                ListGroup {
                    r.monitors.filter { it.supported }.forEachIndexed { i, m ->
                        if (i > 0) ListDivider()
                        ListRow(
                            if (m.ready) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, m.name,
                            if (m.ready) "Ready" else "Not finished yet",
                            if (m.ready) GetAutoSureColors.Good else GetAutoSureColors.Watch
                        )
                    }
                }
            }
        }
        item {
            ListGroup {
                ListRow(Icons.Filled.Eco, "PUC certificate", puc.display, levelColor(if (puc.daysLeft != null && puc.daysLeft < 30) com.enginesense.app.owner.Level.WATCH else com.enginesense.app.owner.Level.UNKNOWN))
                ListDivider()
                ListRow(Icons.Filled.LocationOn, "PUC centre near me", onClick = { openNearby(context, "PUC centre") })
            }
        }
        item { GroupNote("PUC centres test the exhaust gas. Cars with the check-engine light off and every self-test ready usually pass.") }
    }
}

/**
 * "Is this your car?" — right after a scan of a car whose VIN isn't in My Garage. The scan stays
 * out of the picked car's history until the owner answers.
 */
@Composable
fun CarQuestionDialog(viewModel: GetAutoSureViewModel, onAddDetails: () -> Unit, onUsedCarCheck: () -> Unit) {
    val question by viewModel.carQuestion.collectAsState()
    val garage by viewModel.garage.collectAsState()
    val q = question ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text("Is this your car?") },
        text = {
            Text(
                "This scan is from a different car than your ${garage.active?.title ?: "car"} (chassis number ending ${q.vin.takeLast(5)}).",
                style = OwnerText.RowSub
            )
        },
        confirmButton = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.TextButton(onClick = { viewModel.answerCarIsMine(); onAddDetails() }) {
                    Text("Yes, add it to My Garage", color = GetAutoSureColors.Accent)
                }
                androidx.compose.material3.TextButton(onClick = { viewModel.answerCarIsForSale(); onUsedCarCheck() }) {
                    Text("No, I'm checking it before buying", color = GetAutoSureColors.TextPrimary)
                }
                androidx.compose.material3.TextButton(onClick = { viewModel.answerCarIsSame() }) {
                    Text("It's my ${garage.active?.title ?: "car"}", color = GetAutoSureColors.TextSecondary)
                }
            }
        }
    )
}
