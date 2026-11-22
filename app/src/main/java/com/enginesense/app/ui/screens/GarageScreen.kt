package com.enginesense.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ScanResult
import com.enginesense.app.drivelab.DriveLabSummary
import com.enginesense.app.obd.Severity
import com.enginesense.app.owner.ExpenseCategory
import com.enginesense.app.owner.FuelAutomation
import com.enginesense.app.owner.ExpenseEntry
import com.enginesense.app.owner.FuelEntry
import com.enginesense.app.owner.OwnerCarData
import com.enginesense.app.owner.OwnerInsights
import com.enginesense.app.owner.RenewalKind
import com.enginesense.app.owner.ServiceEntry
import com.enginesense.app.ui.components.CarImages
import com.enginesense.app.ui.components.FilterChipRow
import com.enginesense.app.ui.components.GroupNote
import com.enginesense.app.ui.components.HealthScoreRing
import com.enginesense.app.ui.components.InkCard
import com.enginesense.app.ui.components.ListDivider
import com.enginesense.app.ui.components.ListGroup
import com.enginesense.app.ui.components.ListRow
import com.enginesense.app.ui.components.OwnerText
import com.enginesense.app.ui.components.SectionTitle
import com.enginesense.app.ui.components.levelColor
import com.enginesense.app.ui.components.openNearby
import com.enginesense.app.ui.components.openWeb
import com.enginesense.app.ui.components.pickDate
import com.enginesense.app.ui.components.shareLatestReport
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.CategoryState
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehiclelookup.RtoRecord
import com.enginesense.app.vehiclelookup.ValidityStatus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val CHIPS = listOf("Health", "Fuel", "Service", "Renewals")

/** A chip another screen asked the Garage to open, e.g. "fuel" from Needs your attention → See all. */
val garageChipRequest = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

/**
 * The car owner's home ("Garage"): the car with its switcher and health score, the pulsing Scan
 * button, this week's driving score, "Needs your attention" and the month's cost line, then four
 * chips — Health, Fuel, Service and Renewals — each opening its own list on the same page. AI Mechanic is the floating button; Trips, Reports and
 * Profile are the bottom tabs.
 */
@Composable
fun GarageScreen(
    viewModel: GetAutoSureViewModel,
    onRunScan: () -> Unit,
    onAddCar: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val result by viewModel.latestResult.collectAsState()
    val history by viewModel.history.collectAsState()
    val owner by viewModel.ownerData.collectAsState()
    val drives = rememberCarDrives(viewModel)
    var chip by rememberSaveable { mutableStateOf(0) }
    val chipRequest by garageChipRequest.collectAsState()
    androidx.compose.runtime.LaunchedEffect(chipRequest) {
        chipRequest?.let { GARAGE_CHIP_ROUTES[it]?.let { c -> chip = c }; garageChipRequest.value = null }
    }
    val attention = rememberAttention(viewModel)
    val fuel = rememberFuelIntel(viewModel, drives, owner)
    val open: (String) -> Unit = { route -> GARAGE_CHIP_ROUTES[route]?.let { chip = it } ?: onNavigate(route) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { GarageHeader(onNavigate) }
        item { CarCard(viewModel, result, onAddCar, onNavigate) }
        item { FillUpPrompt(viewModel) }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RoundScanButton(
                    onRunScan, buttonSize = 92.dp, title = null,
                    subtitle = result?.let { lastScanned(it.timestampMillis) } ?: "Engine on, scanner plugged in"
                )
            }
        }
        item { DrivingLine(drives, onNavigate) }
        item { AttentionCard(attention, open, onSeeAll = { onNavigate("attention") }) }
        item { CostLine(viewModel, owner, onNavigate) }
        item { FilterChipRow(CHIPS, chip) { chip = it } }
        when (chip) {
            0 -> healthItems(viewModel, result, history, drives, onNavigate)
            1 -> fuelItems(fuel, onNavigate)
            2 -> serviceItems(viewModel, owner)
            else -> renewalItems(viewModel, owner)
        }
    }
}

private fun lastScanned(millis: Long): String =
    "Last scanned " + DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().replaceFirstChar { it.lowercase() }

@Composable
private fun GarageHeader(onNavigate: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("GetAuto", style = GetAutoSureType.SectionTitle.copy(fontSize = 22.sp))
        Text("Sure", style = GetAutoSureType.SectionTitle.copy(fontSize = 22.sp, color = GetAutoSureColors.Accent))
        Spacer(Modifier.weight(1f))
        Text(
            "👑 Pro",
            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.AccentGold, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(GetAutoSureColors.AccentGold.copy(alpha = 0.15f))
                .clickable { onNavigate("pro") }.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** The car: picture, name with the car switcher, plate, then the health score and tentative value. */
@Composable
private fun CarCard(viewModel: GetAutoSureViewModel, result: ScanResult?, onAddCar: () -> Unit, onNavigate: (String) -> Unit) {
    val garage by viewModel.garage.collectAsState()
    val brand by viewModel.vehicleBrand.collectAsState()
    val model by viewModel.vehicleModel.collectAsState()
    val nickname by viewModel.vehicleNickname.collectAsState()
    val year by viewModel.vehicleYear.collectAsState()
    val fuel by viewModel.vehicleFuelType.collectAsState()
    val plate by viewModel.vehiclePlate.collectAsState()
    val odo by viewModel.vehicleOdometerKm.collectAsState()
    val owner by viewModel.ownerData.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val title = nickname.ifBlank { listOfNotNull(brand, model.ifBlank { null }).joinToString(" ").ifBlank { "My car" } }
    val picture = CarImages.forCar(brand, model, fuel)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GetAutoSureColors.Surface).padding(16.dp)) {
        if (picture != null) {
            Image(painterResource(picture), contentDescription = title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(120.dp))
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Row(Modifier.clickable { menuOpen = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = OwnerText.Section.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch car", tint = GetAutoSureColors.TextPrimary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    garage.cars.forEach { car ->
                        val active = car.id == garage.active?.id
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(car.title, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                                    car.profile.plate?.let { Text(it, style = OwnerText.RowSub.copy(fontSize = 12.sp)) }
                                }
                            },
                            onClick = { menuOpen = false; viewModel.switchCar(car.id) }
                        )
                    }
                    DropdownMenuItem(text = { Text("Add a car", color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold) }, leadingIcon = { Icon(Icons.Filled.Add, null, tint = GetAutoSureColors.Accent) }, onClick = { menuOpen = false; onAddCar() })
                    DropdownMenuItem(text = { Text("Edit this car") }, onClick = { menuOpen = false; onNavigate("selectBrand") })
                    if (garage.cars.size > 1) {
                        DropdownMenuItem(text = { Text("Remove this car", color = GetAutoSureColors.Attention) }, onClick = { menuOpen = false; garage.active?.id?.let { viewModel.removeCar(it) } })
                    }
                }
            }
            plate?.let { Text(it, style = OwnerText.RowSub) }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = result != null) { onNavigate("healthBreakdown") }) {
            val score = result?.health?.score
            val state = result?.let { worstState(it) }
            val color = state?.let { GetAutoSureColors.forCategoryState(it) } ?: GetAutoSureColors.Unavailable
            if (score != null) HealthScoreRing(score, color, diameter = 88.dp)
            else Box(Modifier.size(88.dp).clip(RoundedCornerShape(44.dp)).background(GetAutoSureColors.SurfaceRaised), contentAlignment = Alignment.Center) {
                Text("–", style = OwnerText.Section)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Your car health", style = OwnerText.RowSub)
                if (result == null) {
                    Text("No scan yet", style = OwnerText.Section)
                    Text("Plug in the scanner and tap Scan", style = OwnerText.RowSub)
                } else {
                    Text(stateWord(state!!), style = OwnerText.Section.copy(color = color, fontSize = 22.sp))
                    val critical = result.findings.count { it.severity == Severity.CRITICAL }
                    val watch = result.findings.count { it.severity == Severity.MODERATE || it.severity == Severity.MINOR }
                    Text(if (result.findings.isEmpty()) "Nothing to report" else "$critical critical · $watch to watch", style = OwnerText.RowSub)
                    Text("See breakdown →", style = OwnerText.RowSub.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

private fun worstState(r: ScanResult): CategoryState = when {
    r.health.categories.any { it.state == CategoryState.ATTENTION } -> CategoryState.ATTENTION
    r.health.categories.any { it.state == CategoryState.WATCH } -> CategoryState.WATCH
    r.health.score == null -> CategoryState.UNAVAILABLE
    else -> CategoryState.GOOD
}

private fun stateWord(s: CategoryState) = when (s) {
    CategoryState.GOOD -> "Good"
    CategoryState.WATCH -> "Watch"
    CategoryState.ATTENTION -> "Needs attention"
    CategoryState.UNAVAILABLE -> "No data"
}

@Composable
private fun DrivingLine(drives: DriveLabSummary?, onNavigate: (String) -> Unit) {
    val week = drives?.trips.orEmpty().filter { it.startMillis >= System.currentTimeMillis() - WEEK_MILLIS }
    val score = averageScore(week)
    ListGroup {
        if (score != null) {
            ListRow(
                Icons.Filled.Speed, "Driving score $score",
                "This week · ${week.size} trip${if (week.size == 1) "" else "s"} · %.0f km".format(week.sumOf { it.distanceKm }),
                onClick = { onNavigate("trips") },
                trailing = { Text("›", style = OwnerText.Section.copy(color = tripScoreColor(score))) }
            )
        } else {
            ListRow(Icons.Filled.Route, "Driving score", "Leave the scanner plugged in to record your drives", onClick = { onNavigate("trips") })
        }
    }
}

// ---------- Health ----------

private fun LazyListScope.healthItems(
    viewModel: GetAutoSureViewModel,
    result: ScanResult?,
    history: List<ScanResult>,
    drives: DriveLabSummary?,
    onNavigate: (String) -> Unit
) {
    item { HealthOverTime(history.filter { !it.isDemo || history.all { h -> h.isDemo } }, drives) }
    item {
        val volts = result?.snapshot?.verifiedValue(PidId.CONTROL_MODULE_VOLTAGE)
        val bf = drives?.battery
        val battery = OwnerInsights.batteryHealth(volts, bf?.restingVolts, bf?.crankingDips?.minOrNull(), bf?.chargingVolts)
        val codes = result?.snapshot?.dtcs?.size
        val milOn = result?.readiness?.milOn
        val readiness = result?.readiness
        ListGroup {
            ListRow(Icons.Filled.BatteryChargingFull, "Battery", battery.headline + (battery.lines.firstOrNull()?.let { " · " + it.substringBefore(". ") } ?: ""), levelColor(battery.level), onClick = { onNavigate("battery") })
            ListDivider()
            ListRow(
                Icons.Filled.Warning, "Fault codes and warning light",
                if (codes == null) "Scan to check" else "${if (codes == 0) "No codes" else "$codes code${if (codes == 1) "" else "s"}"} · engine light ${if (milOn == true) "ON" else "off"}",
                if (codes == null) GetAutoSureColors.TextSecondary else if (codes > 0 || milOn == true) GetAutoSureColors.Attention else GetAutoSureColors.Good,
                onClick = { onNavigate(if (codes != null && codes > 0) "issues" else "milStatus") }
            )
            ListDivider()
            ListRow(
                Icons.Filled.ShowChart, "Emissions readiness",
                readiness?.let { r -> r.monitors.filter { it.supported }.let { m -> "${m.count { it.ready }} of ${m.size} self-tests ready" } } ?: "Scan to check",
                onClick = { onNavigate("emissionsTrend") }
            )
        }
    }
    item { SectionTitle("More") }
    item {
        ListGroup {
            ListRow(Icons.Filled.Assessment, "Full report", "View, save and share the PDF", onClick = { onNavigate("aiReport") })
            ListDivider()
            ListRow(Icons.Filled.Timeline, "Live data", "RPM, speed, temperature and voltage, live", onClick = { onNavigate("connect") })
            ListDivider()
            ListRow(Icons.Filled.Handyman, "Repair guidance", "Understand a repair and its cost", onClick = { onNavigate("understandRepair") })
            ListDivider()
            ListRow(Icons.Filled.SupportAgent, "Talk to a real expert", "15-minute video call", onClick = { onNavigate("expertList") })
            ListDivider()
            ListRow(Icons.Filled.FactCheck, "Check a used car", "Before you buy one", onClick = { onNavigate("prePurchaseCheck") })
            ListDivider()
            ListRow(Icons.Filled.MenuBook, "Guides and tools", "Warning light guide, tips and checklists", onClick = { onNavigate("guidesAndTools") })
        }
    }
}

// ---------- Service ----------

private fun LazyListScope.serviceItems(viewModel: GetAutoSureViewModel, owner: OwnerCarData) {
    item { ServiceSection(viewModel, owner) }
}

@Composable
private fun ServiceSection(viewModel: GetAutoSureViewModel, owner: OwnerCarData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    val current = rememberOdometer(viewModel, owner, rememberCarDrives(viewModel))
    val next = OwnerInsights.nextService(owner, current, LocalDate.now())

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (owner.services.isEmpty() && !owner.lastServiceQuestionSkipped) {
            LastServiceQuestion(
                onSave = { date, km -> viewModel.updateOwnerData { it.copy(services = it.services + ServiceEntry(System.currentTimeMillis(), date.toString(), km, "Last service", null)) } },
                onSkip = { viewModel.updateOwnerData { it.copy(lastServiceQuestionSkipped = true) } }
            )
        }
        if (next != null) {
            InkCard {
                Text("Next service", style = OwnerText.InkLabel)
                val main = when {
                    next.overdue -> "Overdue"
                    next.kmLeft != null && next.kmLeft < next.daysLeft * 40 -> "In ${"%,d".format(next.kmLeft)} km"
                    else -> "By ${next.dueDate.format(DAY)}"
                }
                Text(main, style = OwnerText.Big.copy(color = if (next.overdue) GetAutoSureColors.AccentBright else GetAutoSureColors.OnInk))
                Text(
                    listOfNotNull(next.dueKm?.let { "at ${"%,d".format(it)} km" }, "or by ${next.dueDate.format(DAY)}").joinToString(" ") + ", whichever comes first",
                    style = OwnerText.InkSub
                )
            }
        }
        ListGroup {
            ListRow(Icons.Filled.Add, "Add a service", "Date, km, work done and cost", onClick = { adding = true })
            owner.services.sortedByDescending { it.date }.take(8).forEach { s ->
                ListDivider()
                ListRow(
                    Icons.Filled.Build, s.work.ifBlank { "Service" },
                    listOfNotNull(runCatching { LocalDate.parse(s.date).format(DAY) }.getOrNull(), s.odometerKm?.let { "%,d km".format(it) }, s.costInr?.let { OwnerInsights.inr(it) }).joinToString(" · ")
                )
            }
        }
        GroupNote("Service every ${"%,d".format(owner.serviceIntervalKm)} km or ${owner.serviceIntervalMonths} months.")
        ListGroup {
            ListRow(Icons.Filled.CalendarMonth, "Book a service", "Garages near you", onClick = { openNearby(context, "car service centre") })
            ListDivider()
            ListRow(Icons.Filled.Share, "Share with my mechanic", "Send the latest report on WhatsApp", onClick = { scope.launch { shareLatestReport(context, viewModel) } })
        }
    }
    if (adding) {
        AddServiceDialog(onDismiss = { adding = false }) { entry ->
            adding = false
            viewModel.updateOwnerData { it.copy(services = it.services + entry) }
        }
    }
}

@Composable
private fun LastServiceQuestion(onSave: (LocalDate, Int?) -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var km by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.AccentSoft).padding(16.dp)) {
        Text("When was the last service?", style = OwnerText.RowTitle)
        Text("We'll tell you when the next one is due. Skip if you don't remember.", style = OwnerText.RowSub)
        Spacer(Modifier.height(10.dp))
        Text(
            date?.format(DAY) ?: "Pick a date",
            style = OwnerText.RowTitle.copy(color = GetAutoSureColors.Accent),
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(GetAutoSureColors.Surface).clickable { pickDate(context, date) { date = it } }.padding(horizontal = 14.dp, vertical = 10.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(km, { km = it.filter(Char::isDigit) }, label = { Text("Odometer then (km, optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSkip) { Text("Skip", color = GetAutoSureColors.TextSecondary) }
            TextButton(onClick = { date?.let { onSave(it, km.toIntOrNull()) } }, enabled = date != null) { Text("Save", fontWeight = FontWeight.Bold, color = GetAutoSureColors.Accent) }
        }
    }
}

@Composable
private fun AddServiceDialog(onDismiss: () -> Unit, onSave: (ServiceEntry) -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var work by remember { mutableStateOf("General service") }
    var km by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a service") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date: ${date.format(DAY)}", style = OwnerText.RowTitle.copy(color = GetAutoSureColors.Accent), modifier = Modifier.clickable { pickDate(context, date) { date = it } })
                OutlinedTextField(work, { work = it }, label = { Text("Work done") }, singleLine = true)
                OutlinedTextField(km, { km = it.filter(Char::isDigit) }, label = { Text("Odometer (km)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(cost, { cost = it.filter(Char::isDigit) }, label = { Text("Cost (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ServiceEntry(System.currentTimeMillis(), date.toString(), km.toIntOrNull(), work.trim(), cost.toIntOrNull())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- Renewals ----------

private fun LazyListScope.renewalItems(viewModel: GetAutoSureViewModel, owner: OwnerCarData) {
    item { RenewalSection(viewModel, owner) }
}

@Composable
private fun RenewalSection(viewModel: GetAutoSureViewModel, owner: OwnerCarData) {
    val context = LocalContext.current
    val rc by viewModel.vehicleRc.collectAsState()
    val items = OwnerInsights.renewals(rc, owner, LocalDate.now())
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.updateOwnerData { it.copy(remindersOn = granted) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ListGroup {
            items.forEachIndexed { i, item ->
                if (i > 0) ListDivider()
                val color = when (item.status) {
                    ValidityStatus.EXPIRED -> GetAutoSureColors.Attention
                    ValidityStatus.EXPIRES_SOON -> GetAutoSureColors.Watch
                    ValidityStatus.LIFETIME -> GetAutoSureColors.Good
                    ValidityStatus.UNKNOWN -> if (item.date == null) GetAutoSureColors.Accent else GetAutoSureColors.TextSecondary
                    ValidityStatus.VALID -> if ((item.daysLeft ?: 999) <= OwnerInsights.COUNTDOWN_DAYS) GetAutoSureColors.Watch else GetAutoSureColors.TextSecondary
                }
                ListRow(
                    when (item.kind) {
                        RenewalKind.INSURANCE -> Icons.Filled.Shield
                        RenewalKind.PUC -> Icons.Filled.Eco
                        RenewalKind.ROAD_TAX -> Icons.Filled.ReceiptLong
                        RenewalKind.FITNESS -> Icons.Filled.VerifiedUser
                        RenewalKind.LICENCE -> Icons.Filled.Badge
                    },
                    item.kind.label, item.display, color,
                    onClick = if (item.status == ValidityStatus.LIFETIME) null else ({
                        pickDate(context, item.date) { d -> viewModel.updateOwnerData { it.copy(renewalDates = it.renewalDates + (item.kind to d.toString())) } }
                    })
                )
            }
        }
        ListGroup {
            ListRow(
                Icons.Filled.Notifications, "Reminders", "30, 7 and 1 day before, free",
                trailing = {
                    Switch(
                        checked = owner.remindersOn,
                        onCheckedChange = { on ->
                            val needsAsk = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            if (needsAsk) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else viewModel.updateOwnerData { it.copy(remindersOn = on) }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = GetAutoSureColors.Ink, checkedThumbColor = GetAutoSureColors.AccentBright)
                    )
                }
            )
        }
        GroupNote(
            (rc?.let { RtoRecord.sourceLine(it) + " " } ?: "No RTO record saved for this car. ") +
                "We don't fetch it again — tap a row to change a date after you renew."
        )
        ListGroup {
            ListRow(Icons.Filled.LocationOn, "PUC centre near me", onClick = { openNearby(context, "PUC centre") })
            ListDivider()
            ListRow(Icons.Filled.Shield, "Renew insurance", "Compare and renew online", onClick = { openWeb(context, "https://www.google.com/search?q=renew+car+insurance+online") })
        }
    }
}

// ---------- Payment dialogs (used by the wallet) ----------

@Composable
internal fun AddFuelDialog(onDismiss: () -> Unit, onSave: (FuelEntry) -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var amount by remember { mutableStateOf("") }
    var litres by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add fuel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date: ${date.format(DAY)}", style = OwnerText.RowTitle.copy(color = GetAutoSureColors.Accent), modifier = Modifier.clickable { pickDate(context, date) { date = it } })
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Amount (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(litres, { litres = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Litres") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(km, { km = it.filter(Char::isDigit) }, label = { Text("Odometer (km)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(full, { full = it })
                    Text("Filled the tank full", style = OwnerText.RowSub)
                }
                Text("Mileage is worked out between two full tanks.", style = OwnerText.RowSub.copy(fontSize = 12.sp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount.toIntOrNull()?.let { onSave(FuelEntry(System.currentTimeMillis(), date.toString(), litres.toDoubleOrNull(), it, km.toIntOrNull(), full)) } },
                enabled = amount.toIntOrNull() != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun AddExpenseDialog(initial: ExpenseCategory = ExpenseCategory.TOLL, onDismiss: () -> Unit, onSave: (ExpenseEntry) -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var category by remember { mutableStateOf(initial) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date: ${date.format(DAY)}", style = OwnerText.RowTitle.copy(color = GetAutoSureColors.Accent), modifier = Modifier.clickable { pickDate(context, date) { date = it } })
                ExpenseCategory.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { c ->
                            val on = c == category
                            Text(
                                c.label,
                                color = if (on) GetAutoSureColors.OnInk else GetAutoSureColors.TextPrimary,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) GetAutoSureColors.Ink else GetAutoSureColors.SurfaceRaised)
                                    .clickable { category = c }.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Amount (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { amount.toIntOrNull()?.let { onSave(ExpenseEntry(System.currentTimeMillis(), date.toString(), category, it, note.trim())) } }, enabled = amount.toIntOrNull() != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

