package com.enginesense.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ScanResult
import com.enginesense.app.drivelab.DriveLabStore
import com.enginesense.app.drivelab.DriveRecorderService
import com.enginesense.app.owner.Level
import com.enginesense.app.owner.O2Check
import com.enginesense.app.owner.ScanChange
import com.enginesense.app.owner.ScanChanges
import com.enginesense.app.owner.ScanComparison
import com.enginesense.app.owner.ScanFacts
import com.enginesense.app.ui.components.OwnerText
import com.enginesense.app.ui.components.levelColor
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.vehicle.DtcSource
import com.enginesense.app.vehicle.PidId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FMT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private fun dayText(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY_FMT)

fun scanFacts(r: ScanResult): ScanFacts {
    val s = r.snapshot
    return ScanFacts(
        timeMillis = r.timestampMillis,
        score = r.health.score,
        storedCodes = s.dtcs.filter { it.source != DtcSource.PENDING }.map { it.code.uppercase() }.toSet(),
        pendingCodes = s.dtcs.filter { it.source == DtcSource.PENDING }.map { it.code.uppercase() }.toSet(),
        kmSinceCleared = s.verifiedValue(PidId.DISTANCE_SINCE_CODES_CLEARED),
        minutesSinceCleared = s.verifiedValue(PidId.TIME_SINCE_CODES_CLEARED),
        readyMonitors = r.readiness?.monitors?.count { it.supported && it.ready },
        supportedMonitors = r.readiness?.monitors?.count { it.supported },
        odometerKm = s.verifiedValue(PidId.ODOMETER),
        cvns = r.vehicleId.cvns,
        calibrationId = r.vehicleId.calibrationId,
        catalystPassed = r.selfTests?.catalystPassed,
        misfireCylinders = r.selfTests?.misfires?.takeIf { it.isNotEmpty() }?.filter { (it.recent ?: 0) > 0 }?.map { it.cylinder }?.toSet(),
        volts = s.verifiedValue(PidId.CONTROL_MODULE_VOLTAGE)
    )
}

/** The picked car's newest scan against the one before it; null with fewer than two scans. */
@Composable
fun rememberScanComparison(viewModel: GetAutoSureViewModel): ScanComparison? {
    val context = LocalContext.current
    val allHistory by viewModel.history.collectAsState()
    val garage by viewModel.garage.collectAsState()
    val recorder by DriveRecorderService.status.collectAsState()
    val drives = rememberCarDrives(viewModel)
    val activeId = garage.active?.id
    val real = allHistory.filter { !it.isDemo }
    val scans = (if (real.isNotEmpty()) real else allHistory)
        .filter { it.isDemo || garage.carOfScan(it.timestampMillis) == activeId }
        .sortedBy { it.timestampMillis }
    if (scans.size < 2) return null
    val (prev, curr) = scans.takeLast(2)
    val checks by produceState(emptyList<O2Check>(), activeId, recorder.samplesThisRun / 200) {
        value = withContext(Dispatchers.IO) {
            DriveLabStore(context).loadEvents()
                .filter { it.type == O2Check.EVENT && garage.carAt(it.timeMillis) == activeId }
                .mapNotNull { O2Check.fromEvent(it.timeMillis, it.detail) }
        }
    }
    val before = checks.lastOrNull { it.timeMillis <= prev.timestampMillis }
    val after = checks.lastOrNull { it.timeMillis > prev.timestampMillis }
    val recorded = drives?.trips?.filter { it.startMillis in prev.timestampMillis..curr.timestampMillis }?.sumOf { it.distanceKm }
    return remember(prev, curr, before, after, recorded) { ScanChanges.compare(scanFacts(prev), scanFacts(curr), before, after, recorded) }
}

private fun seenPrefs(context: Context) = context.getSharedPreferences("scan_changes", Context.MODE_PRIVATE)

/** The "Got it" on the Garage block: remembered per car until the next scan. */
@Composable
fun rememberChangesSeen(viewModel: GetAutoSureViewModel, comparisonFor: Long?): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val garage by viewModel.garage.collectAsState()
    val latest by viewModel.latestResult.collectAsState()
    val key = "seen_${garage.active?.id}"
    val stamp = latest?.timestampMillis ?: comparisonFor
    var seen by remember(key, stamp) { mutableStateOf(stamp != null && seenPrefs(context).getLong(key, 0L) == stamp) }
    return seen to { stamp?.let { seenPrefs(context).edit().putLong(key, it).apply() }; seen = true }
}

@Composable
private fun Dot(level: Level) {
    Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(if (level == Level.ATTENTION) GetAutoSureColors.Attention else levelColor(level)))
}

@Composable
private fun ChangeRow(change: ScanChange, showDetail: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Dot(change.level)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(change.title, style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary, fontSize = 15.sp, fontWeight = if (showDetail) FontWeight.SemiBold else FontWeight.Normal))
            if (change.detail.isNotBlank()) Text(change.detail, style = OwnerText.RowSub.copy(fontSize = 13.sp))
        }
        Text("›", style = OwnerText.RowSub)
    }
}

@Composable
private fun SameRow(text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, null, tint = GetAutoSureColors.Good, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary))
    }
}

/** The block at the top of "Needs your attention" until the next scan or "Got it". */
@Composable
fun SinceLastScanBlock(c: ScanComparison, onOpen: () -> Unit, onGotIt: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GetAutoSureColors.AccentSoft).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Since your last scan (${dayText(c.previousMillis)})", style = OwnerText.RowTitle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
            Text("Got it", style = OwnerText.RowSub.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold), modifier = Modifier.clickable(onClick = onGotIt))
        }
        c.changes.forEach { ChangeRow(it, showDetail = false, onClick = onOpen) }
    }
}

/** The Garage's one line for the last two scans, once any new changes have been seen; opens the full comparison. */
@Composable
fun SinceLastScanLine(c: ScanComparison, onOpen: () -> Unit) {
    val n = c.changes.size
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Since your last scan", style = OwnerText.RowTitle)
            Text(
                if (n == 0) "Nothing changed since ${dayText(c.previousMillis)}" else "$n change${if (n == 1) "" else "s"} since ${dayText(c.previousMillis)}",
                style = OwnerText.RowSub
            )
        }
        Text("›", style = OwnerText.RowSub.copy(color = GetAutoSureColors.Accent, fontSize = 20.sp))
    }
}

/** "Nothing changed since 12 Nov" — the reassurance line when every comparison came back the same. */
@Composable
fun NothingChangedLine(c: ScanComparison) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.CheckCircle, null, tint = GetAutoSureColors.Good, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("Nothing changed since ${dayText(c.previousMillis)}", style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary, fontWeight = FontWeight.SemiBold))
            c.same.filter { !it.startsWith("Battery") }.takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(", ") { s -> lowerFirst(s) }.replaceFirstChar { ch -> ch.uppercase() }, style = OwnerText.RowSub.copy(fontSize = 13.sp))
            }
        }
    }
}

private fun kmLine(c: ScanComparison): String? {
    val since = c.kmSince ?: return null
    val recorded = c.kmRecorded?.takeIf { it >= 1 }
    return "About %,.0f km since then".format(since) + (recorded?.let { ", %,.0f km of it recorded".format(minOf(it, since)) } ?: "")
}

/** On the scan results screen, right under the score: what happened since the last scan. */
@Composable
fun ScanChangesCard(c: ScanComparison, onOpen: () -> Unit, onAskAi: () -> Unit, onExpert: () -> Unit) {
    val alert = c.changes.isNotEmpty()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface)
            .then(if (alert) Modifier.border(1.5.dp, GetAutoSureColors.AccentBright, RoundedCornerShape(16.dp)) else Modifier)
            .padding(16.dp)
    ) {
        if (!alert) {
            NothingChangedLine(c)
            return@Column
        }
        Text("${c.changes.size} thing${if (c.changes.size == 1) "" else "s"} changed since ${dayText(c.previousMillis)}", style = OwnerText.RowTitle)
        kmLine(c)?.let { Text(it, style = OwnerText.RowSub.copy(fontSize = 13.sp)) }
        c.previousScore?.let { Text("Health score was $it then", style = OwnerText.RowSub.copy(fontSize = 13.sp)) }
        Spacer(Modifier.height(4.dp))
        c.changes.forEach { ChangeRow(it, showDetail = true, onClick = onOpen) }
        if (c.same.isNotEmpty()) {
            HorizontalDivider(color = GetAutoSureColors.SurfaceRaised, modifier = Modifier.padding(vertical = 6.dp))
            Text("Same as last time", style = OwnerText.RowSub.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            c.same.take(3).forEach { SameRow(it) }
        }
        Spacer(Modifier.height(10.dp))
        HelpButtons(onAskAi, onExpert)
    }
}

@Composable
private fun HelpButtons(onAskAi: () -> Unit, onExpert: () -> Unit) {
    Button(onClick = onAskAi, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GetAutoSureColors.Accent)) {
        Text("Ask the AI Mechanic what to do")
    }
    Spacer(Modifier.height(6.dp))
    OutlinedButton(onClick = onExpert, modifier = Modifier.fillMaxWidth()) {
        Text("Talk to a real expert · 15-min video call", color = GetAutoSureColors.Accent)
    }
}

/** The question the AI Mechanic gets from the changes, so it can explain them without a mechanic. */
fun aiQuestionFor(c: ScanComparison): String =
    "Since my last scan on ${dayText(c.previousMillis)}, the app found: " +
        c.changes.joinToString("; ") { "${it.title} (${it.detail})" } +
        ". What does this mean, is it safe to drive, and what should I do?"

/** Every change with its before → after and what to do. */
@Composable
fun SinceLastScanScreen(viewModel: GetAutoSureViewModel, onAskAi: () -> Unit, onExpert: () -> Unit) {
    val c = rememberScanComparison(viewModel)
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Since your last scan", style = OwnerText.Section.copy(fontSize = 22.sp))
            if (c != null) {
                Text("Compared with your scan on ${dayText(c.previousMillis)}", style = OwnerText.RowSub)
                kmLine(c)?.let { Text(it, style = OwnerText.RowSub) }
            }
        }
        if (c == null) {
            item { Text("Scan your car twice and this shows what changed in between.", style = OwnerText.RowSub) }
            return@LazyColumn
        }
        if (c.changes.isEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(16.dp)) { NothingChangedLine(c) }
        }
        items(c.changes) { change ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Dot(change.level)
                    Spacer(Modifier.width(10.dp))
                    Text(change.title, style = OwnerText.RowTitle)
                }
                change.evidence.forEachIndexed { i, (label, before, after) ->
                    if (i > 0) HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary), modifier = Modifier.weight(1f))
                        Text("$before → ", style = OwnerText.RowSub)
                        Text(after, style = OwnerText.RowSub.copy(color = if (change.level == Level.ATTENTION) GetAutoSureColors.Attention else GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(change.advice, style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary))
            }
        }
        if (c.same.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(16.dp)) {
                Text("Same as last time", style = OwnerText.RowTitle)
                Spacer(Modifier.height(4.dp))
                c.same.forEach { SameRow(it) }
            }
        }
        if (c.changes.isNotEmpty()) item {
            HelpButtons(onAskAi = { viewModel.askAiMechanic(aiQuestionFor(c), context); onAskAi() }, onExpert = onExpert)
        }
    }
}

/**
 * The owner report's history sections for [result]'s car: changes since the scan before it,
 * car integrity across every scan, and vital signs over time. Null outside "I own a car" mode —
 * a dealer's or buyer's scans aren't one car's history.
 */
suspend fun reportHistoryFor(context: Context, viewModel: GetAutoSureViewModel, result: ScanResult): com.enginesense.app.owner.ReportHistory? {
    if (com.enginesense.app.storage.AppModePrefs.get(context) != com.enginesense.app.storage.AppMode.OWN_CAR) return null
    val garage = viewModel.garage.value
    val carId = garage.carOfScan(result.timestampMillis)
    val scans = viewModel.history.value
        .filter { it.isDemo == result.isDemo && (it.isDemo || garage.carOfScan(it.timestampMillis) == carId) && it.timestampMillis <= result.timestampMillis }
        .sortedBy { it.timestampMillis }
    if (scans.isEmpty()) return null
    val checks = withContext(Dispatchers.IO) {
        DriveLabStore(context).loadEvents()
            .filter { it.type == O2Check.EVENT && garage.carAt(it.timeMillis) == carId }
            .mapNotNull { O2Check.fromEvent(it.timeMillis, it.detail) }
    }
    val facts = scans.map(::scanFacts)
    val changes = if (scans.size >= 2) {
        val prev = scans[scans.size - 2]
        ScanChanges.compare(
            facts[facts.size - 2], facts.last(),
            checks.lastOrNull { it.timeMillis <= prev.timestampMillis }, checks.lastOrNull { it.timeMillis > prev.timestampMillis }, null
        )
    } else null
    val vitals = scans.map(::vitalScanOf).takeIf { it.size >= 3 }?.let { com.enginesense.app.owner.VitalTrends.trends(it.takeLast(12)) }.orEmpty()
    return com.enginesense.app.owner.ReportHistory(
        changes = changes,
        integrity = com.enginesense.app.owner.CarIntegrity.rows(facts, checks.lastOrNull(), viewModel.vehicleOdometerKm.value),
        vitals = vitals,
        scanCount = scans.size,
        firstScanMillis = scans.first().timestampMillis
    )
}
