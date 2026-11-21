package com.enginesense.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.drivelab.DriveLabSummary
import com.enginesense.app.owner.CarWallet
import com.enginesense.app.owner.CostKind
import com.enginesense.app.owner.CostLine
import com.enginesense.app.owner.EntrySource
import com.enginesense.app.owner.ExpenseCategory
import com.enginesense.app.owner.ExpenseEntry
import com.enginesense.app.owner.FuelAutomation
import com.enginesense.app.owner.KmSource
import com.enginesense.app.owner.OwnerCarData
import com.enginesense.app.owner.OwnerInsights
import com.enginesense.app.owner.PaymentAlertListener
import com.enginesense.app.owner.RenewalKind
import com.enginesense.app.owner.WalletCar
import com.enginesense.app.owner.WalletSummary
import com.enginesense.app.ui.components.GroupNote
import com.enginesense.app.ui.components.InkCard
import com.enginesense.app.ui.components.ListDivider
import com.enginesense.app.ui.components.ListGroup
import com.enginesense.app.ui.components.ListRow
import com.enginesense.app.ui.components.OwnerText
import com.enginesense.app.ui.components.SectionTitle
import com.enginesense.app.ui.components.pickDate
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.pricing.ExShowroomPrices
import com.enginesense.app.vehicle.pricing.Variant
import com.enginesense.app.vehicle.pricing.priceFor
import com.enginesense.app.vehiclelookup.RtoRecord
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private val SLICE_COLORS = mapOf(
    CostKind.FUEL to Color(0xFFFF8A1F),
    CostKind.INSURANCE to Color(0xFFFFC078),
    CostKind.SERVICE to Color(0xFFFFFFFF),
    CostKind.OTHER to Color(0xFF8A8F98)
)

private fun iconOf(kind: CostKind): ImageVector = when (kind) {
    CostKind.FUEL -> Icons.Filled.LocalGasStation
    CostKind.INSURANCE -> Icons.Filled.Shield
    CostKind.SERVICE -> Icons.Filled.Build
    CostKind.OTHER -> Icons.Filled.Toll
}

/** The wallet's inputs for the picked car, gathered from the profile, RTO record, scanner and trips. */
class WalletState(val summary: WalletSummary, val car: WalletCar, val drives: DriveLabSummary?, val owner: OwnerCarData)

@Composable
fun rememberWallet(viewModel: GetAutoSureViewModel): WalletState {
    val owner by viewModel.ownerData.collectAsState()
    val brand by viewModel.vehicleBrand.collectAsState()
    val model by viewModel.vehicleModel.collectAsState()
    val year by viewModel.vehicleYear.collectAsState()
    val fuelType by viewModel.vehicleFuelType.collectAsState()
    val cc by viewModel.vehicleEngineCc.collectAsState()
    val claimed by viewModel.vehicleClaimedMileageKmPerLitre.collectAsState()
    val rc by viewModel.vehicleRc.collectAsState()
    val result by viewModel.latestResult.collectAsState()
    val drives = rememberCarDrives(viewModel)
    val odometer = rememberOdometer(viewModel, owner, drives)
    val trips = drives?.trips.orEmpty()
    val now = System.currentTimeMillis()
    // Km a month from trips only once two weeks or more were recorded — fewer days over-weight one long drive.
    val tripKmPerMonth = trips.minOfOrNull { it.startMillis }?.let { first ->
        val spanDays = ((now - first) / DAY_MILLIS).coerceAtMost(30)
        if (spanDays < 14) null else trips.filter { it.startMillis >= now - 30 * DAY_MILLIS }.sumOf { it.distanceKm } * 30 / spanDays
    }
    val car = WalletCar(
        newPrice = ExShowroomPrices.lookup(brand, model)?.priceFor(Variant.MID),
        registered = RtoRecord.parseDate(rc?.registrationDate) ?: year?.let { LocalDate.of(it, 7, 1) },
        odometerKm = odometer,
        engineCc = cc,
        fuelType = fuelType,
        claimedKmpl = claimed,
        measuredKmpl = FuelAutomation.liveMileage(trips, FuelAutomation.calibrationFactor(owner.fuel, trips))?.kmPerLitre,
        tripKmPerMonth = tripKmPerMonth,
        healthScore = result?.health?.score
    )
    val summary = remember(owner, car) { CarWallet.summary(owner, car, LocalDate.now()) }
    return WalletState(summary, car, drives, owner)
}

/** True while auto-capture's notification access is on; re-checked whenever the app comes back from Settings. */
@Composable
fun rememberAutoCaptureOn(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var on by remember { mutableStateOf(PaymentAlertListener.isEnabled(context)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) on = PaymentAlertListener.isEnabled(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return on
}

/**
 * My Car Wallet: what the car costs a year. On day one every group is an estimate (dashed in the
 * donut); each turns solid when real payments come in — typed, shared from an SMS, spotted from a
 * payment alert or a fill-up. Then insights: fuel a month, mileage and the next 12 months.
 */
@Composable
fun WalletScreen(viewModel: GetAutoSureViewModel, onNavigate: (String) -> Unit) {
    val wallet = rememberWallet(viewModel)
    val s = wallet.summary
    val owner = wallet.owner
    val garage by viewModel.garage.collectAsState()
    val rc by viewModel.vehicleRc.collectAsState()
    val result by viewModel.latestResult.collectAsState()
    val autoOn = rememberAutoCaptureOn()
    var askKm by rememberSaveable { mutableStateOf(false) }
    var addingFuel by remember { mutableStateOf(false) }
    var addSheet by remember { mutableStateOf(false) }
    var addingExpense by remember { mutableStateOf<ExpenseCategory?>(null) }
    var addingInsurance by remember { mutableStateOf(false) }
    var whyOpen by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val carTitle = garage.active?.title ?: "Your car"

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Car wallet", style = OwnerText.Section.copy(fontSize = 26.sp)) }
        item { YearCard(carTitle, s) }
        item { AutoCaptureStrip(autoOn, owner, today) { onNavigate("autoCapture") } }
        if (askKm || (owner.monthlyKm == null && s.km.source != KmSource.TRIPS)) {
            item { KmQuestion(s.km.km.takeIf { owner.monthlyKm != null }) { km -> askKm = false; viewModel.updateOwnerData { it.copy(monthlyKm = km) } } }
        }
        item {
            ListGroup {
                s.lines.forEachIndexed { i, line ->
                    if (i > 0) ListDivider()
                    val action = when {
                        line.kind == CostKind.FUEL && !line.confirmed -> "Fix km" to { askKm = true }
                        line.kind == CostKind.INSURANCE && !line.confirmed -> "Add actual" to { addingInsurance = true }
                        line.kind == CostKind.SERVICE && !line.confirmed -> "Add last bill" to { addingExpense = ExpenseCategory.SERVICE }
                        else -> null
                    }
                    CostRow(line, action?.first, action?.second)
                }
                val tax = OwnerInsights.renewals(rc, owner, today).first { it.kind == RenewalKind.ROAD_TAX }
                if (tax.date != null && tax.date.isAfter(today.plusYears(5)) || tax.display.startsWith("Lifetime")) {
                    ListDivider()
                    ListRow(Icons.Filled.EventNote, "Road tax", "Paid once · ${tax.display.replaceFirstChar { it.lowercase() }}") {
                        Text("₹0", style = OwnerText.RowTitle.copy(color = GetAutoSureColors.Good))
                    }
                }
            }
        }
        s.valueLostThisYear?.let { lost ->
            item {
                ListGroup {
                    ListRow(Icons.Filled.TrendingDown, "Value lost this year ≈ ${OwnerInsights.inr(roundTo(lost, 1000))}", "Not money you pay, but the biggest cost of owning a car")
                }
            }
        }

        item { SectionTitle("Insights") }
        item {
            val fuel = s.line(CostKind.FUEL)
            val claimed = wallet.car.claimedKmpl
            val mileage = wallet.car.measuredKmpl ?: OwnerInsights.fuelMileage(owner.fuel)
            val low = mileage != null && claimed != null && mileage < claimed * 0.9
            ListGroup {
                ListRow(Icons.Filled.LocalGasStation, "Fuel ≈ ${OwnerInsights.inr(roundTo(fuel.yearlyInr / 12, 50))} a month", if (fuel.confirmed) "From your fill-ups" else "Estimate · add fill-ups to make it exact")
                ListDivider()
                ListRow(
                    Icons.Filled.Speed,
                    if (mileage != null) "Mileage %.1f km/l".format(mileage) else "Mileage",
                    when {
                        mileage == null -> "Leave the scanner plugged in while you drive"
                        claimed != null -> "%s company's %.1f km/l%s".format(if (low) "Below the" else "Close to the", claimed, if (low) " · see why" else "")
                        else -> "From your drives"
                    },
                    if (low) GetAutoSureColors.Watch else GetAutoSureColors.TextSecondary,
                    onClick = if (mileage != null) ({ whyOpen = !whyOpen }) else null
                )
                ListDivider()
                ListRow(Icons.Filled.CalendarMonth, "Next 12 months ≈ ${CarWallet.short(roundTo(s.yearlyTotal, 1000))}", upcoming(owner, rc, wallet, today))
            }
        }
        if (whyOpen) item { WhyMileage(wallet, result) }

        // One small way in for payments: auto-capture and fill-up spotting cover the rest.
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = OwnerText.Section, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.AccentSoft).clickable { addSheet = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add payment", style = OwnerText.RowSub.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold))
                }
            }
        }
        val recent = recentEntries(owner)
        item {
            if (recent.isEmpty()) GroupNote("Your payments will show here.")
            else ListGroup {
                recent.forEachIndexed { i, r ->
                    if (i > 0) ListDivider()
                    ListRow(r.icon, r.title, r.sub)
                }
            }
        }
    }
    if (addSheet) {
        AddPaymentSheet(
            autoOn = autoOn,
            onDismiss = { addSheet = false },
            onFuel = { addSheet = false; addingFuel = true },
            onExpense = { addSheet = false; addingExpense = ExpenseCategory.SERVICE },
            onAutoCapture = { addSheet = false; onNavigate("autoCapture") }
        )
    }
    if (addingFuel) AddFuelDialog(onDismiss = { addingFuel = false }) { e -> addingFuel = false; viewModel.updateOwnerData { it.copy(fuel = it.fuel + e) } }
    addingExpense?.let { start ->
        AddExpenseDialog(initial = start, onDismiss = { addingExpense = null }) { e -> addingExpense = null; viewModel.updateOwnerData { it.copy(expenses = it.expenses + e) } }
    }
    if (addingInsurance) InsuranceDialog(onDismiss = { addingInsurance = false }) { e -> addingInsurance = false; viewModel.updateOwnerData { it.copy(expenses = it.expenses + e) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPaymentSheet(autoOn: Boolean, onDismiss: () -> Unit, onFuel: () -> Unit, onExpense: () -> Unit, onAutoCapture: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = GetAutoSureColors.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text("Add a payment", style = OwnerText.Section)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetChoice(Icons.Filled.LocalGasStation, "Fuel", "Amount, litres, km", onFuel, Modifier.weight(1f))
                SheetChoice(Icons.Filled.Receipt, "Other expense", "Service, insurance, toll, parking", onExpense, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GetAutoSureColors.AccentSoft).padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Filled.Sms, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Tip: in your SMS app, long-press a bank or UPI message and share it to GetAutoSure to add it in one tap.", style = OwnerText.RowSub.copy(fontSize = 13.sp))
            }
            if (!autoOn) {
                Text(
                    "Turn on auto-capture for fuel, toll and parking",
                    style = OwnerText.RowSub.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onAutoCapture).padding(top = 14.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SheetChoice(icon: ImageVector, title: String, sub: String, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(GetAutoSureColors.SurfaceRaised).clickable(onClick = onClick).padding(14.dp)) {
        Icon(icon, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, style = OwnerText.RowTitle)
        Text(sub, style = OwnerText.RowSub.copy(fontSize = 13.sp))
    }
}

private fun roundTo(amount: Int, step: Int): Int = ((amount + step / 2) / step) * step

@Composable
private fun YearCard(carTitle: String, s: WalletSummary) {
    InkCard {
        Text("$carTitle · yearly cost", style = OwnerText.InkLabel)
        Text((if (s.allConfirmed) "" else "≈ ") + CarWallet.short(roundTo(s.yearlyTotal, 1000)) + " a year", style = OwnerText.Big)
        Text(
            listOfNotNull(
                s.costPerKm?.let { "About ₹%.1f per km".format(it) },
                "${s.confirmedCount} of ${s.confirmableCount} confirmed"
            ).joinToString(" · "),
            style = OwnerText.InkSub
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Donut(s.lines, Modifier.size(116.dp))
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.lines.filter { it.yearlyInr > 0 }.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(SLICE_COLORS.getValue(line.kind)))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${line.kind.label.substringBefore(",").substringBefore(" and")} ${CarWallet.short(roundTo(line.yearlyInr, 100))}" + if (line.confirmed) "" else " est.",
                            style = OwnerText.InkSub.copy(color = GetAutoSureColors.OnInk, fontSize = 13.sp)
                        )
                    }
                }
            }
        }
        if (s.spentThisYear > 0) {
            Spacer(Modifier.height(10.dp))
            Text("Spent in ${LocalDate.now().year} so far: ${OwnerInsights.inr(s.spentThisYear)}", style = OwnerText.InkSub)
        }
        if (!s.allConfirmed) {
            Spacer(Modifier.height(6.dp))
            Text("Dashed = estimate. It turns solid as your real payments come in.", style = OwnerText.InkSub.copy(fontSize = 12.sp))
        }
    }
}

/** Yearly cost by group; estimated groups are dashed. */
@Composable
private fun Donut(lines: List<CostLine>, modifier: Modifier) {
    val total = lines.sumOf { it.yearlyInr }.coerceAtLeast(1)
    Canvas(modifier) {
        val stroke = size.minDimension * 0.16f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        var start = -90f
        lines.filter { it.yearlyInr > 0 }.forEach { line ->
            val sweep = 360f * line.yearlyInr / total
            drawArc(
                color = SLICE_COLORS.getValue(line.kind),
                startAngle = start, sweepAngle = (sweep - 2f).coerceAtLeast(1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Butt,
                    pathEffect = if (line.confirmed) null else PathEffect.dashPathEffect(floatArrayOf(stroke * 0.5f, stroke * 0.3f))
                )
            )
            start += sweep
        }
    }
}

@Composable
private fun CostRow(line: CostLine, actionLabel: String?, onAction: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(iconOf(line.kind), contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.kind.label, style = OwnerText.RowTitle)
                Spacer(Modifier.width(6.dp))
                if (line.kind != CostKind.OTHER || line.confirmed) Tag(if (line.confirmed) "real" else "estimate", line.confirmed)
            }
            Text(line.basis, style = OwnerText.RowSub.copy(fontSize = 13.sp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (line.yearlyInr == 0) "—" else OwnerInsights.inr(roundTo(line.yearlyInr, 100)), style = OwnerText.RowTitle)
            if (actionLabel != null && onAction != null) {
                Text(actionLabel, style = OwnerText.RowSub.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp), modifier = Modifier.clickable(onClick = onAction))
            }
        }
    }
}

@Composable
private fun Tag(text: String, good: Boolean) {
    Text(
        text,
        style = OwnerText.RowSub.copy(fontSize = 11.sp, color = if (good) GetAutoSureColors.Good else GetAutoSureColors.Watch, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (good) Color(0xFFDDF4EA) else Color(0xFFFFF3D6)).padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

@Composable
private fun AutoCaptureStrip(on: Boolean, owner: OwnerCarData, today: LocalDate, onOpen: () -> Unit) {
    val monthStart = today.withDayOfMonth(1)
    fun thisMonth(date: String) = runCatching { !LocalDate.parse(date).isBefore(monthStart) }.getOrDefault(false)
    val added = owner.fuel.count { it.source == EntrySource.ALERT && thisMonth(it.date) } + owner.expenses.count { it.source == EntrySource.ALERT && thisMonth(it.date) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GetAutoSureColors.AccentSoft).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Bolt, contentDescription = null, tint = GetAutoSureColors.Accent)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (on) "Auto-capture on" else "Track car costs automatically", style = OwnerText.RowTitle.copy(fontSize = 15.sp))
            Text(
                if (on) (if (added > 0) "$added payment${if (added == 1) "" else "s"} added this month" else "Fuel, toll and parking payments are added as they happen")
                else "Fuel, toll and parking payments, straight from your payment alerts",
                style = OwnerText.RowSub.copy(fontSize = 13.sp)
            )
        }
        if (!on) Text("Turn on", style = OwnerText.RowTitle.copy(color = GetAutoSureColors.Accent, fontSize = 15.sp))
    }
}

@Composable
private fun KmQuestion(current: Int?, onPick: (Int) -> Unit) {
    val options = listOf("Under 500 km" to 400, "~1,000" to 1_000, "~1,500" to 1_500, "2,000+" to 2_200)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GetAutoSureColors.AccentSoft).padding(14.dp)) {
        Text("One question makes this sharper", style = OwnerText.RowSub.copy(fontSize = 13.sp, color = GetAutoSureColors.Accent))
        Text("How much do you drive a month?", style = OwnerText.RowTitle)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (label, km) ->
                val on = km == current
                Text(
                    label,
                    style = OwnerText.RowSub.copy(fontSize = 13.sp, color = if (on) GetAutoSureColors.OnInk else GetAutoSureColors.TextPrimary),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) GetAutoSureColors.Ink else GetAutoSureColors.Surface)
                        .clickable { onPick(km) }.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun upcoming(owner: OwnerCarData, rc: com.enginesense.app.vehiclelookup.RcLookupResult?, wallet: WalletState, today: LocalDate): String {
    val bits = mutableListOf<String>()
    OwnerInsights.renewals(rc, owner, today).firstOrNull { it.kind == RenewalKind.INSURANCE }?.date
        ?.takeIf { !it.isBefore(today) && it.isBefore(today.plusYears(1)) }
        ?.let { bits += "the insurance renewal on ${it.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}" }
    OwnerInsights.nextService(owner, wallet.car.odometerKm, today)?.let { bits += "the service due in ${it.dueDate.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))}" }
    return if (bits.isEmpty()) "Fuel, insurance and service at today's rate" else "Includes " + bits.joinToString(", ")
}

@Composable
private fun WhyMileage(wallet: WalletState, result: com.enginesense.app.ScanResult?) {
    val trim = result?.snapshot?.let { s ->
        val st = s.verifiedValue(PidId.SHORT_TERM_FUEL_TRIM_1); val lt = s.verifiedValue(PidId.LONG_TERM_FUEL_TRIM_1)
        if (st == null && lt == null) null else (st ?: 0.0) + (lt ?: 0.0)
    }
    val reasons = OwnerInsights.mileageReasons(wallet.drives?.trips.orEmpty().takeLast(20), trim)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ListGroup {
            if (reasons.isEmpty()) ListRow(Icons.Filled.Speed, "Nothing in your driving or scan explains it", "Check the tips below")
            reasons.forEachIndexed { i, r ->
                if (i > 0) ListDivider()
                ListRow(Icons.Filled.Warning, r.substringBefore(":"), r.substringAfter(": ", r))
            }
        }
        OwnerInsights.MILEAGE_TIPS.forEach { GroupNote("Tip: $it") }
    }
}

private class RecentEntry(val date: String, val icon: ImageVector, val title: String, val sub: String)

private fun recentEntries(owner: OwnerCarData): List<RecentEntry> {
    fun day(d: String) = runCatching { LocalDate.parse(d).format(DAY) }.getOrDefault(d)
    val fuel = owner.fuel.map { f ->
        RecentEntry(f.date, Icons.Filled.LocalGasStation, "Fuel · ${OwnerInsights.inr(f.amountInr)}",
            listOfNotNull(day(f.date), f.litres?.let { "%.1f L".format(it) }, f.source.label).joinToString(" · "))
    }
    val expenses = owner.expenses.map { e ->
        RecentEntry(e.date, if (e.category == ExpenseCategory.INSURANCE) Icons.Filled.Shield else Icons.Filled.Receipt,
            "${e.category.label}${e.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""} · ${OwnerInsights.inr(e.amountInr)}",
            "${day(e.date)} · ${e.source.label}")
    }
    val services = owner.services.filter { it.costInr != null }.map { s ->
        RecentEntry(s.date, Icons.Filled.Build, "${s.work} · ${OwnerInsights.inr(s.costInr!!)}", "${day(s.date)} · Service log")
    }
    return (fuel + expenses + services).sortedByDescending { it.date }.take(12)
}

@Composable
private fun InsuranceDialog(onDismiss: () -> Unit, onSave: (ExpenseEntry) -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your insurance premium") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What you paid at your last renewal, including GST.", style = OwnerText.RowSub)
                Text("Paid on: ${date.format(DAY)}", style = OwnerText.RowTitle.copy(color = GetAutoSureColors.Accent), modifier = Modifier.clickable { pickDate(context, date) { date = it } })
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Premium (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(onClick = { amount.toIntOrNull()?.let { onSave(ExpenseEntry(System.currentTimeMillis(), date.toString(), ExpenseCategory.INSURANCE, it, "Premium")) } }, enabled = amount.toIntOrNull() != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- Auto-capture ----------

/** Explains auto-capture before sending the owner to phone Settings to turn on notification access. */
@Composable
fun AutoCaptureScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val on = rememberAutoCaptureOn()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(GetAutoSureColors.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Receipt, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(32.dp))
            }
        }
        item {
            Text(if (on) "Auto-capture is on" else "Track car costs automatically", style = OwnerText.Section.copy(fontSize = 24.sp))
            Spacer(Modifier.height(4.dp))
            Text(
                "When a payment alert for fuel, FASTag toll or parking arrives, we add it to your car wallet. Service, insurance and other bills you can share from the SMS.",
                style = OwnerText.RowSub
            )
        }
        item {
            ListGroup {
                ListRow(Icons.Filled.CheckCircle, "Reads payment alerts only", "From your SMS and UPI apps")
                ListDivider()
                ListRow(Icons.Filled.Lock, "Messages stay on your phone", "Only the car payment is saved")
                ListDivider()
                ListRow(Icons.Filled.VisibilityOff, "Ignores everything else", "Chats, OTPs and other spending")
                ListDivider()
                ListRow(Icons.Filled.ToggleOn, "Turn it off any time", "Phone Settings → Notification access → GetAutoSure")
            }
        }
        item {
            Text(
                if (on) "Turn off in phone Settings" else "Turn on",
                style = OwnerText.RowTitle.copy(color = if (on) GetAutoSureColors.Accent else GetAutoSureColors.OnInk),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(if (on) GetAutoSureColors.AccentSoft else GetAutoSureColors.Accent)
                    .clickable { runCatching { context.startActivity(PaymentAlertListener.settingsIntent(context)) } }
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (!on) {
                Spacer(Modifier.height(6.dp))
                Text("Opens phone Settings. Find GetAutoSure and switch it on.", style = OwnerText.RowSub.copy(fontSize = 13.sp), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        item {
            Text(
                if (on) "Done" else "Not now, I'll share messages",
                style = OwnerText.RowTitle.copy(color = GetAutoSureColors.Accent),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDone).padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
