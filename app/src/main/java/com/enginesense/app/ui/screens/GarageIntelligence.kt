package com.enginesense.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.drivelab.DriveLabStore
import com.enginesense.app.drivelab.DriveLabSummary
import com.enginesense.app.drivelab.DriveRecorderService
import com.enginesense.app.drivelab.DriveSample
import com.enginesense.app.owner.AttentionInputs
import com.enginesense.app.owner.AttentionItem
import com.enginesense.app.owner.CarAttention
import com.enginesense.app.owner.CarWallet
import com.enginesense.app.owner.FuelAutomation
import com.enginesense.app.owner.FuelIntelligence
import com.enginesense.app.owner.FuelMix
import com.enginesense.app.owner.Learning
import com.enginesense.app.owner.Level
import com.enginesense.app.owner.MileageBasis
import com.enginesense.app.owner.MileageDrop
import com.enginesense.app.owner.MileageView
import com.enginesense.app.owner.OwnerCarData
import com.enginesense.app.owner.OwnerInsights
import com.enginesense.app.owner.ParkedDrop
import com.enginesense.app.owner.TankCheck
import com.enginesense.app.owner.TankVerdict
import com.enginesense.app.owner.VitalTrends
import com.enginesense.app.ui.components.GroupNote
import com.enginesense.app.ui.components.ListDivider
import com.enginesense.app.ui.components.ListGroup
import com.enginesense.app.ui.components.ListRow
import com.enginesense.app.ui.components.OwnerText
import com.enginesense.app.ui.components.SectionTitle
import com.enginesense.app.ui.components.levelColor
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.vehicle.DtcSource
import com.enginesense.app.vehicle.PidStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private const val DAY_MS = 86_400_000L
private const val RECENT_ALERT_DAYS = 14L
/** "Needs your attention" shows this many lines; the rest are behind See all. */
private const val CARD_ITEMS = 3

private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** Everything the Fuel chip shows for the picked car. */
class FuelIntelState(
    val mileage: MileageView?,
    val pricePerLitre: Double,
    val claimedKmpl: Double?,
    val mix: FuelMix?,
    val tanks: List<TankCheck>,
    val parkedDrops: List<ParkedDrop>,
    val drop: MileageDrop?,
    val reportsGauge: Boolean
)

/** The picked car's raw trip samples, reloaded as the recorder adds to them. */
@Composable
private fun rememberCarSamples(viewModel: GetAutoSureViewModel): List<DriveSample> {
    val context = LocalContext.current
    val garage by viewModel.garage.collectAsState()
    val recorder by DriveRecorderService.status.collectAsState()
    val activeId = garage.active?.id
    val samples by produceState(emptyList<DriveSample>(), activeId, recorder.samplesThisRun / 200) {
        value = withContext(Dispatchers.IO) { DriveLabStore(context).loadSamples().filter { garage.carAt(it.timeMillis) == activeId } }
    }
    return samples
}

@Composable
fun rememberFuelIntel(viewModel: GetAutoSureViewModel, drives: DriveLabSummary?, owner: OwnerCarData): FuelIntelState {
    val fuelType by viewModel.vehicleFuelType.collectAsState()
    val claimed by viewModel.vehicleClaimedMileageKmPerLitre.collectAsState()
    val result by viewModel.latestResult.collectAsState()
    val samples = rememberCarSamples(viewModel)
    val trips = drives?.trips.orEmpty()
    val price = CarWallet.fuelPrice(owner, fuelType)
    val fills = FuelAutomation.detectFills(samples).map { it.atMillis }
    val fillDays = fills.map(::dayOf).toSet()
    // Fill-ups the owner logged on days the gauge didn't catch count as tank starts too.
    val logged = owner.fuel.mapNotNull { f -> runCatching { LocalDate.parse(f.date) }.getOrNull() }
        .filter { it !in fillDays }.map { it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val now = System.currentTimeMillis()
    val scanTrim = result?.snapshot?.let { s ->
        val st = s.verifiedValue(com.enginesense.app.vehicle.PidId.SHORT_TERM_FUEL_TRIM_1)
        val lt = s.verifiedValue(com.enginesense.app.vehicle.PidId.LONG_TERM_FUEL_TRIM_1)
        if (st == null && lt == null) null else (st ?: 0.0) + (lt ?: 0.0)
    }
    return FuelIntelState(
        mileage = FuelIntelligence.mileage(trips, FuelAutomation.calibrationFactor(owner.fuel, trips), claimed),
        pricePerLitre = price,
        claimedKmpl = claimed,
        mix = FuelIntelligence.fuelMix(FuelIntelligence.recentTrim(samples) ?: scanTrim),
        tanks = FuelIntelligence.tanks(fills + logged, samples).sortedByDescending { it.filledMillis },
        parkedDrops = FuelIntelligence.parkedDrops(samples).sortedByDescending { it.atMillis },
        drop = FuelIntelligence.mileageDrop(trips, samples, now, price),
        reportsGauge = samples.any { it.fuelLevelPercent != null }
    )
}

class AttentionState(
    val items: List<AttentionItem>,
    val allGood: String?,
    val learning: Learning?,
    val stale: String?,
    /** The newest scan against the one before; null with fewer than two scans. */
    val changes: com.enginesense.app.owner.ScanComparison? = null,
    /** The owner tapped "Got it" on these changes. */
    val changesSeen: Boolean = false,
    val markChangesSeen: () -> Unit = {}
)

@Composable
fun rememberAttention(viewModel: GetAutoSureViewModel): AttentionState {
    val result by viewModel.latestResult.collectAsState()
    val allHistory by viewModel.history.collectAsState()
    val owner by viewModel.ownerData.collectAsState()
    val rc by viewModel.vehicleRc.collectAsState()
    val drives = rememberCarDrives(viewModel)
    val fuel = rememberFuelIntel(viewModel, drives, owner)
    val odometer = rememberOdometer(viewModel, owner, drives)
    val history = allHistory.filter { !it.isDemo || allHistory.all { h -> h.isDemo } }
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    val trips = drives?.trips.orEmpty()

    val dtcs = result?.snapshot?.dtcs.orEmpty()
    val bf = drives?.battery
    val vitals = history.sortedBy { it.timestampMillis }.map(::vitalScanOf).takeIf { it.size >= 3 }?.let { VitalTrends.trends(it.takeLast(12)) }.orEmpty()
    val inputs = AttentionInputs(
        scanned = result != null,
        storedCodes = dtcs.count { it.source != DtcSource.PENDING },
        pendingCodes = dtcs.count { it.source == DtcSource.PENDING },
        milOn = result?.readiness?.milOn == true,
        battery = result?.let { OwnerInsights.batteryHealth(it.snapshot.verifiedValue(com.enginesense.app.vehicle.PidId.CONTROL_MODULE_VOLTAGE), bf?.restingVolts, bf?.crankingDips?.minOrNull(), bf?.chargingVolts) },
        vitals = vitals,
        mileageDrop = fuel.drop,
        parkedDrop = fuel.parkedDrops.firstOrNull { it.atMillis >= now - RECENT_ALERT_DAYS * DAY_MS },
        nextService = OwnerInsights.nextService(owner, odometer, today),
        renewals = OwnerInsights.renewals(rc, owner, today),
        checks = (result?.snapshot?.readings?.values?.count { it.status == PidStatus.VERIFIED } ?: 0) + (if (result?.readiness != null) 1 else 0) + 1
    )
    val firstSeen = listOfNotNull(history.minOfOrNull { it.timestampMillis }, trips.minOfOrNull { it.startMillis }).minOrNull()?.let(::dayOf)
    val lastSeen = listOfNotNull(history.maxOfOrNull { it.timestampMillis }, trips.maxOfOrNull { it.endMillis }).maxOrNull()?.let(::dayOf)
    val learning = CarAttention.learning(
        scans = history.size,
        tripsWithFuel = trips.count { it.fuelLitres != null },
        driveDays = trips.minOfOrNull { it.startMillis }?.let { (now - it) / DAY_MS } ?: 0,
        tanksCompared = fuel.tanks.count { it.verdict != TankVerdict.NOT_ENOUGH },
        firstSeen = firstSeen,
        today = today
    )
    val changes = rememberScanComparison(viewModel)
    val (seen, markSeen) = rememberChangesSeen(viewModel, changes?.let { result?.timestampMillis })
    return AttentionState(CarAttention.items(inputs), CarAttention.allGoodLine(inputs), learning, CarAttention.staleLine(lastSeen, today), changes, seen, markSeen)
}

/** Garage chips an attention line can open instead of a screen. */
val GARAGE_CHIP_ROUTES = mapOf("health" to 0, "fuel" to 1, "service" to 2, "renewals" to 3)

@Composable
private fun AttentionRow(item: AttentionItem, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(item.route ?: "health") }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (item.level == Level.GOOD) GetAutoSureColors.Unavailable else levelColor(item.level)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary, fontSize = 15.sp))
            item.detail?.let { Text(it, style = OwnerText.RowSub.copy(fontSize = 13.sp)) }
        }
        Text("›", style = OwnerText.RowSub)
    }
}

@Composable
private fun AttentionList(items: List<AttentionItem>, onOpen: (String) -> Unit) {
    val (now, later) = items.partition { it.now }
    if (now.isNotEmpty()) {
        Text("Now", style = OwnerText.RowSub.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        now.forEach { AttentionRow(it, onOpen) }
    }
    if (later.isNotEmpty()) {
        if (now.isNotEmpty()) Spacer(Modifier.height(4.dp))
        Text("Coming up", style = OwnerText.RowSub.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        later.forEach { AttentionRow(it, onOpen) }
    }
}

@Composable
private fun LearningStrip(learning: Learning) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GetAutoSureColors.AccentSoft).padding(12.dp)) {
        Text("Getting to know your car · ${learning.doneCount} of ${learning.steps.size}", style = OwnerText.RowTitle.copy(fontSize = 15.sp))
        Spacer(Modifier.height(6.dp))
        learning.steps.forEach { s ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (s.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                    tint = if (s.done) GetAutoSureColors.Good else GetAutoSureColors.Unavailable, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (s.done) s.title else "${s.title}: ${s.hint.replaceFirstChar { it.lowercase() }}",
                    style = OwnerText.RowSub.copy(fontSize = 13.sp, color = if (s.done) GetAutoSureColors.TextPrimary else GetAutoSureColors.TextSecondary)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Keep the scanner plugged in. The more you drive with it, the more it learns about your car.", style = OwnerText.RowSub.copy(fontSize = 13.sp, color = GetAutoSureColors.Accent))
    }
}

/** The app's own conclusion, right under the Scan button: what needs you now and what's coming up. */
@Composable
fun AttentionCard(state: AttentionState, onOpen: (String) -> Unit, onSeeAll: () -> Unit) {
    val showChanges = state.changes?.changes?.isNotEmpty() == true && !state.changesSeen
    val urgent = showChanges || state.items.any { it.now && it.level != Level.GOOD }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface)
            .then(if (urgent) Modifier.border(1.5.dp, GetAutoSureColors.AccentBright, RoundedCornerShape(16.dp)) else Modifier)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Needs your attention", style = OwnerText.RowTitle, modifier = Modifier.weight(1f))
            if (state.items.size > CARD_ITEMS) {
                Text("See all ›", style = OwnerText.RowSub.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold), modifier = Modifier.clickable(onClick = onSeeAll))
            }
        }
        Spacer(Modifier.height(6.dp))
        state.stale?.let {
            Text(it, style = OwnerText.RowSub.copy(fontSize = 13.sp, color = GetAutoSureColors.Watch))
            Spacer(Modifier.height(6.dp))
        }
        if (showChanges) {
            SinceLastScanBlock(state.changes!!, onOpen = { onOpen("sinceLastScan") }, onGotIt = state.markChangesSeen)
            Spacer(Modifier.height(8.dp))
        } else if (state.changes != null && state.changes.changes.isEmpty()) {
            NothingChangedLine(state.changes)
            Spacer(Modifier.height(8.dp))
        }
        when {
            state.items.isNotEmpty() -> AttentionList(state.items.take(CARD_ITEMS), onOpen)
            showChanges -> {}
            state.allGood != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = GetAutoSureColors.Good, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(state.allGood, style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary))
            }
            else -> Text("Run your first scan and we'll tell you here what needs your attention.", style = OwnerText.RowSub)
        }
        state.learning?.let {
            Spacer(Modifier.height(10.dp))
            LearningStrip(it)
        }
    }
}

/** One line on the home for costs; the Wallet tab has the rest. */
@Composable
fun CostLine(viewModel: GetAutoSureViewModel, owner: OwnerCarData, onNavigate: (String) -> Unit) {
    val wallet = rememberWallet(viewModel)
    val month = OwnerInsights.monthSpend(owner, YearMonth.now()).total
    val perKm = wallet.summary.costPerKm?.let { " · ₹%.1f per km".format(it) } ?: ""
    // Same days of last month, so the 5th isn't set against a whole month.
    val lastMonth = YearMonth.now().minusMonths(1)
    val lastSoFar = OwnerInsights.monthSpend(owner, lastMonth, upToDay = LocalDate.now().dayOfMonth).total
    val name = lastMonth.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
    val vsLast = if (month > 0 && lastSoFar > 0 && month != lastSoFar) {
        " · ${OwnerInsights.inr(kotlin.math.abs(month - lastSoFar))} ${if (month < lastSoFar) "less" else "more"} than $name by this date"
    } else ""
    ListGroup {
        ListRow(
            Icons.Filled.AccountBalanceWallet,
            if (month > 0) "This month ${OwnerInsights.inr(month)}" else "About ${CarWallet.short(wallet.summary.yearlyTotal)} a year",
            "Your car costs$perKm$vsLast",
            subtitleColor = when {
                vsLast.isEmpty() -> GetAutoSureColors.TextSecondary
                month < lastSoFar -> GetAutoSureColors.Good
                else -> GetAutoSureColors.Watch
            },
            onClick = { onNavigate("wallet") }
        )
    }
}

// ---------- Fuel chip ----------

fun LazyListScope.fuelItems(fuel: FuelIntelState, onNavigate: (String) -> Unit) {
    item { MileageCard(fuel) }
    fuel.drop?.let { d ->
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.AccentSoft).padding(16.dp)) {
                Text("Mileage down ${d.percent}% for 2 weeks", style = OwnerText.RowTitle.copy(color = GetAutoSureColors.Watch))
                Text("%.1f km/l lately, against your usual %.1f.".format(d.recentKmpl, d.usualKmpl), style = OwnerText.RowSub)
                if (d.reasons.isNotEmpty()) Text("Likely: " + d.reasons.joinToString(", ") { it.replaceFirstChar { c -> c.lowercase() } } + ".", style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary))
                d.monthlyCostInr?.let { Text("Costing you about ${OwnerInsights.inr(it)} a month.", style = OwnerText.RowSub.copy(color = GetAutoSureColors.TextPrimary)) }
            }
        }
    }
    item { SectionTitle("Fuel mix") }
    item {
        ListGroup {
            val mix = fuel.mix
            if (mix == null) ListRow(Icons.Filled.LocalGasStation, "Not reported", "This car doesn't report its fuel correction (most diesels don't).")
            else ListRow(Icons.Filled.LocalGasStation, mix.headline, mix.detail, if (mix.level == Level.GOOD) GetAutoSureColors.TextSecondary else levelColor(mix.level))
        }
    }
    item { SectionTitle("Your tanks") }
    item {
        val shown = fuel.tanks.take(6)
        if (shown.isEmpty()) {
            GroupNote(if (fuel.reportsGauge || fuel.mix != null) "Each fill-up shows here, compared with your usual fuel, after 3 fill-ups." else "Add your fill-ups in the Wallet to see each tank here.")
        } else {
            ListGroup {
                shown.forEachIndexed { i, t ->
                    if (i > 0) ListDivider()
                    val (word, color) = when (t.verdict) {
                        TankVerdict.ODD -> "Odd" to GetAutoSureColors.Watch
                        TankVerdict.NORMAL -> "Normal" to GetAutoSureColors.Good
                        TankVerdict.NOT_ENOUGH -> "Learning" to GetAutoSureColors.TextSecondary
                    }
                    ListRow(
                        Icons.Filled.LocalGasStation, "Filled ${dayOf(t.filledMillis).format(SHORT_DAY)}",
                        if (t.verdict == TankVerdict.ODD) "The engine needed more fuel than with your usual tanks. Could be higher ethanol or poor fuel — try another pump next time." else null,
                        trailing = { Text(word, style = OwnerText.RowSub.copy(color = color, fontWeight = FontWeight.SemiBold)) }
                    )
                }
            }
        }
    }
    item { SectionTitle("Fuel alerts") }
    item {
        val parked = fuel.parkedDrops.take(3)
        if (parked.isEmpty() && fuel.drop == null) GroupNote(if (fuel.reportsGauge) "No fuel alerts. We'll tell you if mileage drops or fuel goes while the car is parked." else "We'll tell you if mileage drops for a couple of weeks.")
        else ListGroup {
            parked.forEachIndexed { i, p ->
                if (i > 0) ListDivider()
                ListRow(Icons.Filled.LocalGasStation, "Dropped %.0f%% while parked".format(p.droppedPercent), "Before the drive on ${dayOf(p.atMillis).format(SHORT_DAY)} · worth a look for a leak", GetAutoSureColors.Watch)
            }
            fuel.drop?.let {
                if (parked.isNotEmpty()) ListDivider()
                ListRow(Icons.Filled.LocalGasStation, "Mileage down ${it.percent}%", "Since ${dayOf(it.sinceMillis).format(SHORT_DAY)}", GetAutoSureColors.Watch)
            }
        }
    }
    item { AutoRecordRow(onNavigate) }
}

@Composable
private fun MileageCard(fuel: FuelIntelState) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Ink).padding(16.dp)) {
        Text("Your real mileage", style = OwnerText.InkLabel)
        val m = fuel.mileage
        if (m == null) {
            Text("Not yet", style = OwnerText.Big.copy(color = GetAutoSureColors.OnInk))
            Text("Real mileage appears after 3 drives with the scanner plugged in.", style = OwnerText.InkSub)
            return@Column
        }
        Text("%.1f km/l".format(m.kmPerLitre), style = OwnerText.Big.copy(color = GetAutoSureColors.OnInk))
        val perKm = fuel.pricePerLitre / m.kmPerLitre
        Text(
            listOfNotNull("₹%.1f per km".format(perKm), fuel.claimedKmpl?.takeIf { m.basis != MileageBasis.CLAIMED }?.let { "company claims %.1f".format(it) }).joinToString(" · "),
            style = OwnerText.InkSub
        )
        Spacer(Modifier.height(4.dp))
        Text(
            m.basis.label + if (m.trips > 0) " · ${m.trips} trip${if (m.trips == 1) "" else "s"}" else "",
            style = OwnerText.InkSub.copy(color = if (m.basis == MileageBasis.CORRECTED) GetAutoSureColors.AccentBright else GetAutoSureColors.OnInkMuted)
        )
    }
}

/** "See all" from the Garage card: everything that needs the owner, now and coming up. */
@Composable
fun AttentionScreen(viewModel: GetAutoSureViewModel, onOpen: (String) -> Unit) {
    val state = rememberAttention(viewModel)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Needs your attention", style = OwnerText.Section.copy(fontSize = 22.sp)) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface).padding(16.dp)) {
                state.stale?.let { Text(it, style = OwnerText.RowSub.copy(color = GetAutoSureColors.Watch)); HorizontalDivider(color = GetAutoSureColors.SurfaceRaised, modifier = Modifier.padding(vertical = 8.dp)) }
                state.changes?.takeIf { it.changes.isNotEmpty() }?.let { c ->
                    SinceLastScanBlock(c, onOpen = { onOpen("sinceLastScan") }, onGotIt = state.markChangesSeen)
                    Spacer(Modifier.height(8.dp))
                }
                if (state.items.isEmpty() && state.changes?.changes.isNullOrEmpty()) Text(state.allGood ?: "Run your first scan and we'll tell you here what needs your attention.", style = OwnerText.RowSub)
                else AttentionList(state.items, onOpen)
            }
        }
        state.learning?.let { item { LearningStrip(it) } }
        item { GroupNote("Trends compare your car with its own earlier scans and drives, so they get sharper the longer the scanner stays plugged in.") }
    }
}
