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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.enginesense.app.owner.CheckupKind
import com.enginesense.app.owner.FirstCheckup
import androidx.compose.material.icons.filled.LocalGasStation
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
private const val CARD_ITEMS = 4

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
    val markChangesSeen: () -> Unit = {},
    /** For the Service and Renewals tiles' one-line summaries. */
    val nextService: com.enginesense.app.owner.NextService? = null,
    val renewals: List<com.enginesense.app.owner.RenewalItem> = emptyList(),
    /** This car's real scans (or the demo ones when there are no real ones). */
    val scanCount: Int = 0,
    /** "Your car's first check-up" lines; shown on the Garage while [scanCount] is 1. */
    val checkup: List<com.enginesense.app.owner.CheckupLine> = emptyList(),
    val checkupSubtitle: String = "",
    /** Real mileage, said once as news on the "Since your last check-up" card. */
    val mileageKmpl: Double? = null,
    val mileageTrips: Int = 0,
    /** Owners so far per the RTO record; 2+ means a second-hand car. */
    val ownerCount: Int? = null
) {
    val needsAction: Boolean get() = CarAttention.needsAction(items)
    val comingUp: List<AttentionItem> get() = items.filter { !it.now }
}

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
    // This car's scans only (the same rule as the "since last scan" comparison), so a second car starts fresh.
    val garage by viewModel.garage.collectAsState()
    val realScans = allHistory.filter { !it.isDemo }
    val carScans = (if (realScans.isNotEmpty()) realScans else allHistory)
        .count { it.isDemo || garage.carOfScan(it.timestampMillis) == garage.active?.id }
    val firstSeen = listOfNotNull(history.minOfOrNull { it.timestampMillis }, trips.minOfOrNull { it.startMillis }).minOrNull()?.let(::dayOf)
    val lastSeen = listOfNotNull(history.maxOfOrNull { it.timestampMillis }, trips.maxOfOrNull { it.endMillis }).maxOrNull()?.let(::dayOf)
    val learning = CarAttention.learning(
        scans = carScans,
        tripsWithFuel = trips.count { it.fuelLitres != null },
        driveDays = trips.minOfOrNull { it.startMillis }?.let { (now - it) / DAY_MS } ?: 0,
        tanksCompared = fuel.tanks.count { it.verdict != TankVerdict.NOT_ENOUGH },
        firstSeen = firstSeen,
        today = today
    )
    val changes = rememberScanComparison(viewModel)
    val (seen, markSeen) = rememberChangesSeen(viewModel, changes?.let { result?.timestampMillis })
    val checkupInputs = com.enginesense.app.owner.CheckupInputs(
        attention = inputs,
        kmSinceCleared = result?.snapshot?.verifiedValue(com.enginesense.app.vehicle.PidId.DISTANCE_SINCE_CODES_CLEARED),
        warmUpsSinceCleared = result?.snapshot?.verifiedValue(com.enginesense.app.vehicle.PidId.WARM_UPS_SINCE_CODES_CLEARED),
        ownerCount = rc?.ownerCount,
        financed = rc?.financed,
        serviceAnswered = owner.services.isNotEmpty() || owner.lastServiceQuestionSkipped
    )
    return AttentionState(
        CarAttention.items(inputs), CarAttention.allGoodLine(inputs), learning, CarAttention.staleLine(lastSeen, today), changes, seen, markSeen,
        nextService = inputs.nextService, renewals = inputs.renewals,
        scanCount = carScans,
        checkup = if (carScans == 1) com.enginesense.app.owner.FirstCheckup.lines(checkupInputs) else emptyList(),
        checkupSubtitle = com.enginesense.app.owner.FirstCheckup.subtitle(inputs.checks, rc?.ownerCount),
        mileageKmpl = fuel.mileage?.kmPerLitre,
        mileageTrips = fuel.mileage?.trips ?: 0,
        ownerCount = rc?.ownerCount
    )
}

/** Garage chips an attention line can open instead of a screen. */
val GARAGE_CHIP_ROUTES = mapOf("health" to 0, "fuel" to 1, "service" to 2, "renewals" to 3)

private fun routeIcon(route: String?): ImageVector = when (route) {
    "issues" -> Icons.Filled.Warning
    "battery" -> Icons.Filled.BatteryChargingFull
    "fuel" -> Icons.Filled.LocalGasStation
    "renewals" -> Icons.Filled.Description
    "service" -> Icons.Filled.Build
    else -> Icons.Filled.Favorite
}

/** A square icon tile tinted by how serious the line is. */
@Composable
private fun LineIcon(icon: ImageVector, tint: Color, background: Color) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

private fun levelTint(level: Level): Pair<Color, Color> = when (level) {
    Level.ATTENTION -> GetAutoSureColors.Attention to Color(0xFFFDECEA)
    Level.WATCH -> GetAutoSureColors.Watch to Color(0xFFFFF4E0)
    else -> GetAutoSureColors.TextSecondary to GetAutoSureColors.SurfaceRaised
}

/** One line: what it means (bold), why (grey), and one action link. */
@Composable
private fun CardLine(icon: ImageVector, tint: Color, background: Color, title: String, detail: String?, action: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        LineIcon(icon, tint, background)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = OwnerText.RowTitle.copy(fontSize = 15.sp))
            detail?.let { Text(it, style = OwnerText.RowSub.copy(fontSize = 13.sp)) }
            if (action != null && onClick != null) {
                Text("$action ›", style = OwnerText.RowSub.copy(fontSize = 13.sp, color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun AttentionRow(item: AttentionItem, onOpen: (String) -> Unit) {
    val (tint, bg) = levelTint(item.level)
    CardLine(routeIcon(item.route), tint, bg, item.title, item.detail, item.action) { onOpen(item.route ?: "health") }
}

@Composable
private fun AttentionList(items: List<AttentionItem>, onOpen: (String) -> Unit) {
    val (now, later) = items.partition { it.now }
    now.forEachIndexed { i, it -> if (i > 0) HorizontalDivider(color = GetAutoSureColors.SurfaceRaised); AttentionRow(it, onOpen) }
    if (later.isNotEmpty()) {
        if (now.isNotEmpty()) Spacer(Modifier.height(6.dp))
        Text("COMING UP", style = OwnerText.RowSub.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp))
        later.forEachIndexed { i, it -> if (i > 0) HorizontalDivider(color = GetAutoSureColors.SurfaceRaised); AttentionRow(it, onOpen) }
    }
}

/** A white rounded card; [highlight] adds the orange border used when something needs the owner. */
@Composable
private fun GarageCard(highlight: Boolean = false, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Surface)
            .then(if (highlight) Modifier.border(1.5.dp, GetAutoSureColors.AccentBright, RoundedCornerShape(16.dp)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) { content() }
}

/**
 * "Still learning your car · 2 of 6": one line with a progress bar and only the next step,
 * outside the warnings card so it isn't read as more problems. Gone once every step is done.
 */
@Composable
fun LearningLine(learning: Learning, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GetAutoSureColors.Surface)
            .border(1.dp, GetAutoSureColors.Border, RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Still learning your car · ${learning.doneCount} of ${learning.steps.size}", style = OwnerText.RowTitle.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
            Text("What's next ›", style = OwnerText.RowSub.copy(fontSize = 13.sp, color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(GetAutoSureColors.SurfaceRaised)) {
            Box(Modifier.fillMaxWidth(learning.doneCount.toFloat() / learning.steps.size).height(6.dp).clip(RoundedCornerShape(3.dp)).background(GetAutoSureColors.AccentBright))
        }
        learning.nextNudge?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = OwnerText.RowSub.copy(fontSize = 13.sp))
        }
    }
}

/** "What's next": every learning step in plain words — what the owner gets, and what to do. */
@Composable
fun LearningScreen(viewModel: GetAutoSureViewModel) {
    val learning = rememberAttention(viewModel).learning
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Learning your car", style = OwnerText.Section.copy(fontSize = 22.sp)) }
        if (learning == null) {
            item { Text("All done. GetAutoSure now knows your car's normal, so it can spot changes early.", style = OwnerText.RowSub) }
            return@LazyColumn
        }
        item {
            GarageCard {
                Text("${learning.doneCount} of ${learning.steps.size} done", style = OwnerText.RowTitle)
                Spacer(Modifier.height(4.dp))
                Text("The app gets smarter about your car the more you drive with the scanner plugged in.", style = OwnerText.RowSub.copy(fontSize = 13.sp))
            }
        }
        item {
            GarageCard {
                learning.steps.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (s.done) Color(0xFFE6F4F0) else GetAutoSureColors.AccentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            if (s.done) Icon(Icons.Filled.CheckCircle, null, tint = GetAutoSureColors.Good, modifier = Modifier.size(16.dp))
                            else Text("${i + 1}", style = OwnerText.RowTitle.copy(fontSize = 13.sp, color = GetAutoSureColors.Accent))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = OwnerText.RowTitle.copy(fontSize = 15.sp))
                            Text(if (s.done) s.doneNote else s.hint, style = OwnerText.RowSub.copy(fontSize = 13.sp))
                        }
                    }
                }
            }
        }
    }
}

/** "Needs your attention": only what the owner should act on now, worst first, each with one action. */
@Composable
fun AttentionCard(state: AttentionState, onOpen: (String) -> Unit, onSeeAll: () -> Unit) {
    val showChanges = state.changes?.changes?.isNotEmpty() == true && !state.changesSeen
    val now = state.items.filter { it.now }
    GarageCard(highlight = showChanges || state.needsAction) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Needs your attention", style = OwnerText.RowTitle, modifier = Modifier.weight(1f))
            if (now.size > CARD_ITEMS) {
                Text("See all ›", style = OwnerText.RowSub.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold), modifier = Modifier.clickable(onClick = onSeeAll))
            }
        }
        Spacer(Modifier.height(4.dp))
        state.stale?.let {
            Text(it, style = OwnerText.RowSub.copy(fontSize = 13.sp, color = GetAutoSureColors.Watch))
            Spacer(Modifier.height(6.dp))
        }
        // New changes lead the card until "Got it".
        if (showChanges) {
            SinceLastScanBlock(state.changes!!, onOpen = { onOpen("sinceLastScan") }, onGotIt = state.markChangesSeen)
            Spacer(Modifier.height(6.dp))
        }
        when {
            now.isNotEmpty() -> now.take(CARD_ITEMS).forEachIndexed { i, it -> if (i > 0) HorizontalDivider(color = GetAutoSureColors.SurfaceRaised); AttentionRow(it, onOpen) }
            showChanges -> {}
            else -> Text("Run your first scan and we'll tell you here what needs your attention.", style = OwnerText.RowSub)
        }
    }
}

/** "Coming up": papers and service, never mixed into the warnings. */
@Composable
fun ComingUpCard(items: List<AttentionItem>, onOpen: (String) -> Unit) {
    if (items.isEmpty()) return
    GarageCard {
        Text("Coming up", style = OwnerText.RowSub.copy(fontWeight = FontWeight.SemiBold))
        items.forEachIndexed { i, it -> if (i > 0) HorizontalDivider(color = GetAutoSureColors.SurfaceRaised); AttentionRow(it, onOpen) }
    }
}

/** "Your car's first check-up": what one scan found, the papers, and the one question we can't answer. */
@Composable
fun FirstCheckupCard(
    state: AttentionState,
    onOpen: (String) -> Unit,
    onServiceDate: () -> Unit,
    onNewCar: () -> Unit,
    onServiceUnknown: () -> Unit
) {
    GarageCard(highlight = FirstCheckup.hasProblems(state.checkup)) {
        Text("Your car's first check-up", style = OwnerText.RowTitle)
        Text(state.checkupSubtitle, style = OwnerText.RowSub.copy(fontSize = 12.sp))
        Spacer(Modifier.height(4.dp))
        state.checkup.forEachIndexed { i, line ->
            if (i > 0) HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
            val (icon, tint, bg) = when (line.kind) {
                CheckupKind.OK -> Triple(Icons.Filled.CheckCircle, GetAutoSureColors.Good, Color(0xFFE6F4F0))
                CheckupKind.WATCH -> Triple(routeIcon(line.route ?: "issues"), GetAutoSureColors.Watch, Color(0xFFFFF4E0))
                CheckupKind.PROBLEM -> Triple(routeIcon(line.route ?: "issues"), GetAutoSureColors.Attention, Color(0xFFFDECEA))
                CheckupKind.QUESTION -> Triple(Icons.Filled.HelpOutline, GetAutoSureColors.Accent, GetAutoSureColors.AccentSoft)
            }
            val isServiceQuestion = line.kind == CheckupKind.QUESTION && line.route == "service"
            CardLine(icon, tint, bg, line.title, line.detail, line.action, if (isServiceQuestion || line.route == null) null else ({ onOpen(line.route) }))
            if (isServiceQuestion) {
                Row(Modifier.padding(start = 42.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallChoice("Pick date", onServiceDate)
                    if ((state.ownerCount ?: 1) >= 2) SmallChoice("Don't know", onServiceUnknown)
                    else {
                        SmallChoice("New car, none yet", onNewCar)
                        SmallChoice("Skip", onServiceUnknown)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            FirstCheckup.FOOTER,
            style = OwnerText.RowSub.copy(fontSize = 12.5.sp, color = GetAutoSureColors.TextPrimary),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GetAutoSureColors.AccentSoft).padding(10.dp)
        )
    }
}

@Composable
private fun SmallChoice(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = OwnerText.RowSub.copy(fontSize = 12.sp, color = GetAutoSureColors.TextPrimary, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, GetAutoSureColors.Border, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/**
 * "Since your last check-up": the second scan onward when nothing needs action — what changed
 * (usually nothing), and the real mileage said once as news while it's new.
 */
@Composable
fun SinceCheckupCard(state: AttentionState, mileageIsNews: Boolean, onOpen: (String) -> Unit) {
    val c = state.changes
    GarageCard {
        Text("Since your last check-up", style = OwnerText.RowTitle)
        c?.let { comp ->
            val km = comp.kmSince?.let { "about %,.0f km".format(it) }
            Text(listOfNotNull(sinceDay(comp.previousMillis), km).joinToString(" · "), style = OwnerText.RowSub.copy(fontSize = 12.sp))
        }
        Spacer(Modifier.height(4.dp))
        val same = c?.same?.filter { !it.startsWith("Battery") }.orEmpty()
        CardLine(
            Icons.Filled.CheckCircle, GetAutoSureColors.Good, Color(0xFFE6F4F0),
            if (c == null || c.changes.isEmpty()) "Nothing new on the engine" else "${c.changes.size} change${if (c.changes.size == 1) "" else "s"} since last time",
            same.takeIf { it.isNotEmpty() }?.joinToString(", ") { lowerFirst(it) }?.replaceFirstChar { it.uppercase() } ?: "Same as last time. No faults, nothing cleared.",
            "See details"
        ) { onOpen("sinceLastScan") }
        if (mileageIsNews) state.mileageKmpl?.let { kmpl ->
            HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
            CardLine(
                Icons.Filled.LocalGasStation, GetAutoSureColors.Good, Color(0xFFE6F4F0),
                "Your real mileage: %.1f km/l".format(kmpl),
                "From your ${state.mileageTrips} drive${if (state.mileageTrips == 1) "" else "s"} with the scanner plugged in.",
                "See Fuel & mileage"
            ) { onOpen("fuel") }
        }
    }
}

/** Lower-cases the first letter for joining into a sentence, but not an acronym like "ECU". */
internal fun lowerFirst(s: String): String = if (s.length > 1 && s[1].isUpperCase()) s else s.replaceFirstChar { it.lowercase() }

private fun sinceDay(millis: Long): String = "Last check-up " + dayOf(millis).format(SHORT_DAY)

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
        state.learning?.let { item { LearningLine(it) { onOpen("learning") } } }
        item { GroupNote("Trends compare your car with its own earlier scans and drives, so they get sharper the longer the scanner stays plugged in.") }
    }
}
