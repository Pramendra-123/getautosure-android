package com.enginesense.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import com.enginesense.app.vehicle.ExteriorDamage
import com.enginesense.app.vehicle.pricing.ExShowroomPrices
import com.enginesense.app.vehicle.pricing.Variant
import com.enginesense.app.vehicle.pricing.priceFor
import com.enginesense.app.vehiclelookup.RcPrefill
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ui.components.TalkToMechanicCard
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.FairPriceEstimator
import com.enginesense.app.vehicle.OdometerMatch
import com.enginesense.app.vehicle.PrePurchaseAnalyzer
import com.enginesense.app.vehicle.PurchaseVerdict
import com.enginesense.app.vehicle.BuyerSignal
import com.enginesense.app.vehicle.CheckOutcome
import com.enginesense.app.vehicle.PurchaseCheck
import com.enginesense.app.ui.components.StatusDot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.enginesense.app.vehiclelookup.PurchaseRcCheckViewModel
import com.enginesense.app.vehiclelookup.PurchaseRcState
import com.enginesense.app.vehiclelookup.RcLookupResult
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import com.enginesense.app.report.PrePurchaseReportPdfGenerator
import com.enginesense.app.report.sharePdfIntent
import com.enginesense.app.vehicle.PrePurchaseReport
import com.enginesense.app.vehicle.PrePurchaseReportBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Used Car Check's four steps — the same shape as Dealer Saathi's flow. */
private val BUYER_STEP_TITLES = listOf("Scan the car", "Car number", "Look around the car", "Price and report")

/** Step 1 — starts a scan same as any other, just flagged as a pre-purchase check (see GetAutoSureViewModel.startPrePurchaseCheck). */
@Composable
fun PrePurchaseCheckScreen(viewModel: GetAutoSureViewModel, onStartScan: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        StepHeader(0, BUYER_STEP_TITLES)
        Spacer(Modifier.height(8.dp))
        Text(
            "Buying a car? Scan it before you pay. You get a clear answer — good buy, buy with caution, or avoid — and a tentative price.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(16.dp))
        listOf(
            "Plug the scanner into the seller's car, engine on",
            "Spot faults that were hidden or cleared before the sale",
            "Check the odometer and the RTO (VAHAN) record"
        ).forEach { line ->
            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                Text("•  ", style = GetAutoSureType.Body)
                Text(line, style = GetAutoSureType.Body)
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RoundScanButton(onRunScan = { viewModel.startPrePurchaseCheck(); onStartScan() }, title = "Scan this car", subtitle = "At the seller's place, engine on")
        }
    }
}

/**
 * Our tentative price for the car: its new (ex-showroom) price from [ExShowroomPrices] for the
 * chosen make, model and variant, aged by [com.enginesense.app.vehicle.FairPriceEstimator], with
 * the repair costs above taken off. The make, model and year fill themselves in from the RTO
 * record or the chassis number when there's one; the buyer can change any of them, or type the
 * exact new price instead.
 */
@Composable
private fun PriceCard(
    report: PrePurchaseReport,
    make: String?, onMake: (String) -> Unit,
    model: String?, onModel: (String) -> Unit,
    variant: Variant, onVariant: (Variant) -> Unit,
    yearInput: String, onYear: (String) -> Unit, autoYear: Int?, autoYearSource: String?,
    newPriceUsed: Double?
) {
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Our tentative price", style = GetAutoSureType.SectionTitle)
            Spacer(Modifier.height(2.dp))
            Text("From the car's new price, its age and km, minus the repairs above. A rough guide, not a valuation.", style = GetAutoSureType.Caption)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerButton("Make", make, VEHICLE_MAKES.drop(1), onMake, Modifier.weight(1f))
                PickerButton("Model", model, make?.let { MODELS_BY_MAKE[it] }.orEmpty(), onModel, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Variant", style = GetAutoSureType.Caption)
                Variant.entries.forEach { v ->
                    FilterChip(selected = v == variant, onClick = { onVariant(v) }, label = { Text(v.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = yearInput,
                onValueChange = { onYear(it.filter(Char::isDigit).take(4)) },
                label = { Text("Year of manufacture") },
                placeholder = { autoYear?.let { Text("$it") } },
                supportingText = { autoYear?.let { Text("$it from the $autoYearSource — change it if the RC says otherwise") } },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            val value = report.value
            if (value == null) {
                Text(report.valueMissingReason ?: "", style = GetAutoSureType.Caption)
            } else {
                val est = value.fair
                Text(
                    "₹${formatRupees(est.lowEstimate)} – ₹${formatRupees(est.highEstimate)}",
                    style = GetAutoSureType.ScreenTitle.copy(color = GetAutoSureColors.Accent)
                )
                Text(
                    "${est.depreciationPercent}% below the new price after ${est.ageYears} year${if (est.ageYears == 1) "" else "s"}",
                    style = GetAutoSureType.Caption
                )
                if (report.repairMaxInr > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("After the repairs above", style = GetAutoSureType.Caption)
                    Text(
                        "₹${formatRupees(value.afterRepairsLow)} – ₹${formatRupees(value.afterRepairsHigh)}",
                        style = GetAutoSureType.SectionTitle
                    )
                }
                newPriceUsed?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "New price used: ₹${formatRupees(it)} (approx. ex-showroom, ${variant.label.lowercase()} variant)",
                        style = GetAutoSureType.Caption
                    )
                }
            }
        }
    }
}

/** A make or model picker: a button showing the choice that opens the list. */
@Composable
private fun PickerButton(label: String, value: String?, options: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(value ?: label, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onPick(option); open = false })
            }
        }
    }
}

/** What the scan can't see: the buyer counts damage on the body, glass, tyres and cabin, and each adds a tentative repair cost. */
@Composable
private fun ExteriorCard(counts: Map<String, Int>, onCount: (String, Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Walk around the car and count anything you see. Each adds a tentative repair cost.", style = GetAutoSureType.Caption)
            Spacer(Modifier.height(8.dp))
            ExteriorDamage.items.forEach { item ->
                val n = counts[item.id] ?: 0
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, style = GetAutoSureType.Body)
                        Text("₹${"%,d".format(item.minInr)}–${"%,d".format(item.maxInr)} per ${item.unit}", style = GetAutoSureType.Caption)
                    }
                    TextButton(onClick = { onCount(item.id, (n - 1).coerceAtLeast(0)) }, enabled = n > 0) { Text("−") }
                    Text("$n", style = GetAutoSureType.Body, modifier = Modifier.width(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { onCount(item.id, (n + 1).coerceAtMost(9)) }) { Text("+") }
                }
            }
            val lines = ExteriorDamage.lines(counts)
            if (lines.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Body and cabin repairs: ₹${"%,d".format(lines.sumOf { it.cost!!.minInr })}–${"%,d".format(lines.sumOf { it.cost!!.maxInr })}",
                    style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Watch)
                )
            }
        }
    }
}

private fun formatRupees(value: Double): String =
    if (value >= 100000) "%.2f L".format(value / 100000) else "%,.0f".format(value)

/**
 * Steps 2–4 of Used Car Check, after the scan: the car number and its RTO (VAHAN) record, the
 * buyer's own look-around, then the verdict, price and report. Everything the buyer adds lives
 * here, not in each step, because the verdict and the PDF need all of it.
 */
@Composable
fun PrePurchaseResultScreen(viewModel: GetAutoSureViewModel, onTalkToMechanic: () -> Unit, onDone: () -> Unit, onViewPdf: (java.io.File) -> Unit = {}) {
    val result by viewModel.prePurchaseResult.collectAsState()
    val r = result
    val rcViewModel: PurchaseRcCheckViewModel = viewModel()
    val rcState by rcViewModel.state.collectAsState()
    var step by remember { mutableStateOf(1) }
    var plate by remember { mutableStateOf("") }
    var dashboardInput by remember { mutableStateOf("") }
    var yearInput by remember { mutableStateOf("") }
    var make by remember { mutableStateOf<String?>(null) }
    var model by remember { mutableStateOf<String?>(null) }
    var variant by remember { mutableStateOf(Variant.MID) }
    val exteriorCounts = remember { mutableStateMapOf<String, Int>() }
    val ratings = remember { mutableStateMapOf<String, Int>() }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp)) {
        if (r == null) {
            Text("No scan result to show.", style = GetAutoSureType.Body)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { viewModel.clearPrePurchaseCheck(); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            return@Column
        }

        val insight = PrePurchaseAnalyzer.analyze(r.findings, r.health.score, r.readiness, r.snapshot)
        val rc = (rcState as? PurchaseRcState.Found)?.result
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        // Make, model and year come from the RTO (VAHAN) record only — never guessed from the VIN, which
        // mislabelled a Tata as a Hyundai — until the buyer picks their own.
        val rcPrefill = rc?.let { RcPrefill.from(it) }
        LaunchedEffect(rcPrefill) {
            if (make == null) make = rcPrefill?.brand?.takeIf { it in MODELS_BY_MAKE }
            if (model == null) model = rcPrefill?.model?.takeIf { m -> make?.let { MODELS_BY_MAKE[it]?.contains(m) } == true }
        }
        val autoYear = rcPrefill?.year
        val tableRange = ExShowroomPrices.lookup(make, model)
        val newPrice = tableRange?.priceFor(variant)

        val report = PrePurchaseReportBuilder.build(
            findings = r.findings, healthScore = r.health.score, readiness = r.readiness, snapshot = r.snapshot,
            vin = r.vehicleId.vin, dashboardKm = dashboardInput.toDoubleOrNull(), rc = rc, registration = rc?.registrationNumber,
            exShowroomPriceInr = newPrice, buyerEnteredYear = yearInput.toIntOrNull() ?: autoYear,
            currentYear = currentYear,
            exteriorCounts = exteriorCounts.toMap(),
            make = make, fuelType = rc?.fuelType
        )

        StepHeader(step, BUYER_STEP_TITLES)
        Spacer(Modifier.height(12.dp))
        when (step) {
            1 -> BuyerCarNumberStep(
                plate = plate, onPlate = { plate = it },
                rcViewModel = rcViewModel, rcState = rcState, isDemo = r.isDemo,
                ecuKm = insight.ecuOdometerKm, dashboardInput = dashboardInput, onDashboard = { dashboardInput = it },
                onNext = { step = 2 }
            )
            2 -> {
                Text("What the scan can't see. All optional — skip anything you can't check.", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(12.dp))
                ExteriorCard(exteriorCounts) { id, n -> exteriorCounts[id] = n }
                Spacer(Modifier.height(12.dp))
                BuyerRatingsCard(ratings)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Next: price and report") }
            }
            else -> BuyerPriceAndReportStep(
                viewModel = viewModel, r = r, insight = insight, report = report, rc = rc, ratings = ratings.toMap(),
                make = make, onMake = { make = it; model = null }, model = model, onModel = { model = it },
                variant = variant, onVariant = { variant = it },
                yearInput = yearInput, onYear = { yearInput = it }, autoYear = autoYear, newPrice = newPrice,
                onTalkToMechanic = onTalkToMechanic, onDone = onDone, onViewPdf = onViewPdf
            )
        }
        if (step > 1) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { step -= 1 }) { Text("← Back") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Step 2: the RTO (VAHAN) record is required — it names the car and feeds the price — and the dashboard km for the odometer check. */
@Composable
private fun BuyerCarNumberStep(
    plate: String, onPlate: (String) -> Unit,
    rcViewModel: PurchaseRcCheckViewModel, rcState: PurchaseRcState, isDemo: Boolean,
    ecuKm: Double?, dashboardInput: String, onDashboard: (String) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun check() { if (plate.isNotBlank()) rcViewModel.check(context, plate, null) }
    LaunchedEffect(rcState) {
        if (rcState is PurchaseRcState.Found) { focus.clearFocus(); keyboard?.hide() }
    }

    Text("We fetch the car's official record from VAHAN: make and model, year, owners, insurance, PUC, loan and more.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = plate,
        onValueChange = { onPlate(it.uppercase().take(13)) },
        label = { Text("Registration number, e.g. MH12AB1234") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { check() }),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { check() },
        enabled = rcState != PurchaseRcState.Looking && plate.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (rcState == PurchaseRcState.Looking) "Checking RTO records…" else "Get RTO record") }
    when (rcState) {
        PurchaseRcState.Looking -> RtoSearching()
        is PurchaseRcState.Found -> RcCheckResult(rcState.result)
        is PurchaseRcState.Failed -> {
            Spacer(Modifier.height(8.dp))
            Text(rcState.message, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
        }
        PurchaseRcState.Idle -> Unit
    }

    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = dashboardInput,
        onValueChange = { onDashboard(it.filter(Char::isDigit).take(7)) },
        label = { Text("Dashboard km") },
        supportingText = { Text("Read it off the dashboard. We compare it with the km stored in the car's computer to check the odometer hasn't been rolled back.") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    val dashboardKm = dashboardInput.toDoubleOrNull()
    if (ecuKm != null && dashboardKm != null) {
        Spacer(Modifier.height(4.dp))
        when (PrePurchaseAnalyzer.compareOdometer(ecuKm, dashboardKm)) {
            OdometerMatch.MATCHES -> Text("🟢 Matches the car's computer (${"%,d".format(ecuKm.toLong())} km). No odometer red flags from OBD.", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Good))
            OdometerMatch.MISMATCH -> Text(
                "🔴 The car's computer says ${"%,d".format(ecuKm.toLong())} km — ${"%,d".format(kotlin.math.abs(ecuKm - dashboardKm).toLong())} km different. Ask the seller why, and for the service records.",
                style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention)
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    val found = rcState is PurchaseRcState.Found || isDemo
    Button(onClick = onNext, enabled = found, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(if (found) "Next: look around the car" else "Get the RTO record to continue")
    }
}

/** Optional 1–5 stars for what a buyer can judge on a test drive; shown in the PDF as the buyer's own ratings. */
@Composable
private fun BuyerRatingsCard(ratings: MutableMap<String, Int>) {
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Your ratings", style = GetAutoSureType.Body)
            Text("Tap 1–5 stars. Tap the same star again to clear.", style = GetAutoSureType.Caption)
            Spacer(Modifier.height(8.dp))
            BUYER_RATING_ITEMS.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
                    val current = ratings[item] ?: 0
                    (1..5).forEach { n ->
                        Text(
                            if (n <= current) "★" else "☆",
                            style = GetAutoSureType.SectionTitle.copy(color = if (n <= current) Color(0xFFF59E0B) else GetAutoSureColors.TextSecondary),
                            modifier = Modifier.clickable { if (current == n) ratings.remove(item) else ratings[item] = n }.padding(horizontal = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

internal val BUYER_RATING_ITEMS = listOf("Tyres", "AC", "Interior and seats", "Test drive")

/** Step 4: verdict, what to raise with the seller, the price with how it was worked out, and the report. */
@Composable
private fun BuyerPriceAndReportStep(
    viewModel: GetAutoSureViewModel, r: com.enginesense.app.ScanResult, insight: com.enginesense.app.vehicle.PrePurchaseInsight,
    report: PrePurchaseReport, rc: RcLookupResult?, ratings: Map<String, Int>,
    make: String?, onMake: (String) -> Unit, model: String?, onModel: (String) -> Unit,
    variant: Variant, onVariant: (Variant) -> Unit,
    yearInput: String, onYear: (String) -> Unit, autoYear: Int?, newPrice: Double?,
    onTalkToMechanic: () -> Unit, onDone: () -> Unit, onViewPdf: (java.io.File) -> Unit
) {
    // The report's verdict also counts the buyer's own checks (odometer, RTO record), so the screen uses it too.
    val (color, emoji) = when (report.verdict) {
        PurchaseVerdict.GOOD_BUY -> GetAutoSureColors.Good to "🟢"
        PurchaseVerdict.BUY_WITH_CAUTION -> GetAutoSureColors.Watch to "🟡"
        PurchaseVerdict.AVOID -> GetAutoSureColors.Attention to "🔴"
    }
    val verdictLabel = when (report.verdict) {
        PurchaseVerdict.GOOD_BUY -> "Good buy"
        PurchaseVerdict.BUY_WITH_CAUTION -> "Buy with caution"
        PurchaseVerdict.AVOID -> "Avoid"
    }
    VerdictHeader(
        label = verdictLabel, emoji = emoji, color = color, headline = report.headline,
        redFlags = report.redFlags.size, toAsk = report.worthAsking.size,
        clear = report.checks.count { it.outcome == CheckOutcome.CLEAR }
    )

    SectionHeader("Tentative price", "Worked out from this car's details")
    PriceBasisCard(report, rc, make, model, variant, newPrice, yearInput.toIntOrNull() ?: autoYear)
    var editing by remember { mutableStateOf(false) }
    Text(
        if (editing) "Hide details" else "Wrong model, variant or year? Change it →",
        style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent),
        modifier = Modifier.clickable { editing = !editing }.padding(vertical = 8.dp)
    )
    if (editing) {
        PriceCard(
            report = report, make = make, onMake = onMake, model = model, onModel = onModel,
            variant = variant, onVariant = onVariant,
            yearInput = yearInput, onYear = onYear, autoYear = autoYear, autoYearSource = "RTO record",
            newPriceUsed = newPrice
        )
    }

    // Before paying for a car with red flags, a buyer wants a second pair of eyes.
    if (report.redFlags.isNotEmpty() || report.worthAsking.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        TalkToMechanicCard("Unsure about this car? Go through the results with a mechanic before you pay.", onTalkToMechanic)
    }
    if (report.redFlags.isNotEmpty()) {
        SectionHeader("Red flags", "Signs something may have been hidden from you")
        report.redFlags.forEach { SignalCard(it, GetAutoSureColors.Attention) }
    }
    if (report.worthAsking.isNotEmpty()) {
        SectionHeader("Worth asking about", "Not tampering — but raise these with the seller")
        report.worthAsking.forEach { SignalCard(it, GetAutoSureColors.Watch) }
    }
    if (insight.checks.isNotEmpty()) {
        SectionHeader("What we checked")
        ChecksCard(report.checks)
    }

    SectionHeader("Negotiation")
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                if (insight.negotiationPoints.isEmpty()) "Nothing to raise beyond the points above" else "${insight.negotiationPoints.size} thing${if (insight.negotiationPoints.size == 1) "" else "s"} worth raising with the seller",
                style = GetAutoSureType.Body
            )
            insight.negotiationPoints.forEach { point ->
                Spacer(Modifier.height(8.dp))
                Text("• ${point.title}", style = GetAutoSureType.Body)
                Text(point.recommendedAction, style = GetAutoSureType.Caption, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }

    SectionHeader("Pre-purchase report", "A PDF of everything above — red flags, odometer, RTO record, repair costs and price")
    PrePurchaseReportActions(r, report, ratings, onViewPdf)

    Spacer(Modifier.height(24.dp))
    Button(onClick = { viewModel.clearPrePurchaseCheck(); onDone() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Done")
    }
}

/**
 * The tentative price with its working shown line by line — the car (from VAHAN), its new price,
 * age and km, the depreciation, and the repairs taken off — so the buyer can see why it's that number.
 */
@Composable
private fun PriceBasisCard(report: PrePurchaseReport, rc: RcLookupResult?, make: String?, model: String?, variant: Variant, newPrice: Double?, year: Int?) {
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val value = report.value
            if (value == null) {
                Text("No price yet", style = GetAutoSureType.Body)
                Text(report.valueMissingReason ?: "Pick the model below to get a price.", style = GetAutoSureType.Caption)
            } else {
                val shownAfterRepairs = report.repairMaxInr > 0
                Text(
                    "₹${formatRupees(if (shownAfterRepairs) value.afterRepairsLow else value.fair.lowEstimate)} – ₹${formatRupees(if (shownAfterRepairs) value.afterRepairsHigh else value.fair.highEstimate)}",
                    style = GetAutoSureType.ScreenTitle.copy(color = GetAutoSureColors.Accent)
                )
                Text("A rough guide, not a valuation. Bargain from here.", style = GetAutoSureType.Caption)
            }
            Spacer(Modifier.height(12.dp))
            Text("HOW WE GOT THIS", style = GetAutoSureType.Caption.copy(letterSpacing = 1.2.sp))
            Spacer(Modifier.height(4.dp))
            val car = com.enginesense.app.dealerreport.CarNameFormatter.carName(rc?.make, rc?.model, rc?.fuelType) ?: listOfNotNull(make, model).joinToString(" ").ifBlank { null }
            val km = report.odometer.dashboardKm ?: report.odometer.ecuKm
            val lines = listOfNotNull(
                "Car" to (car?.let { "$it${if (rc != null) " (VAHAN)" else ""}" } ?: "Not known"),
                "Year" to (year?.toString() ?: "Not known"),
                rc?.fuelType?.let { "Fuel" to it.lowercase().replaceFirstChar { c -> c.uppercase() } },
                rc?.ownerCount?.let { "Owners" to com.enginesense.app.dealerreport.DealerSaleSummaryBuilder.ordinalOwner(it) },
                "Km driven" to (km?.let { "%,d km".format(it.toLong()) + " (average is ~15,000 km a year)" } ?: "Not entered"),
                "New price (ex-showroom)" to (newPrice?.let { "₹${formatRupees(it)}, ${variant.label.lowercase()} variant — tentative" } ?: "Not in our price list"),
                report.value?.fair?.let { "Age and km" to "−${it.depreciationPercent}% after ${it.ageYears} year${if (it.ageYears == 1) "" else "s"}" },
                (if (report.repairMaxInr > 0) "Repairs taken off" to "₹${formatRupees(report.repairMinInr.toDouble())} – ₹${formatRupees(report.repairMaxInr.toDouble())}" else null)
            )
            lines.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(k, style = GetAutoSureType.Caption, modifier = Modifier.width(140.dp))
                    Text(v, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun VerdictHeader(label: String, emoji: String, color: Color, headline: String, redFlags: Int, toAsk: Int, clear: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(color.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(emoji, style = GetAutoSureType.SectionTitle) }
            Spacer(Modifier.width(14.dp))
            Text(label, style = GetAutoSureType.ScreenTitle.copy(color = color))
        }
        Spacer(Modifier.height(10.dp))
        Text(headline, style = GetAutoSureType.Body)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (redFlags > 0) SummaryChip("$redFlags red flag${if (redFlags == 1) "" else "s"}", GetAutoSureColors.Attention)
            if (toAsk > 0) SummaryChip("$toAsk to ask about", GetAutoSureColors.Watch)
            if (clear > 0) SummaryChip("$clear clear", GetAutoSureColors.Good)
        }
    }
}

@Composable
private fun SummaryChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color, modifier = Modifier.size(7.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = GetAutoSureType.Caption.copy(color = color, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String? = null) {
    Spacer(Modifier.height(28.dp))
    Text(title, style = GetAutoSureType.SectionTitle)
    if (subtitle != null) Text(subtitle, style = GetAutoSureType.Caption)
    Spacer(Modifier.height(12.dp))
}

/** Title, then what the scan actually saw, then what to say to the seller — the order a buyer reads it in at the car. */
@Composable
private fun SignalCard(signal: BuyerSignal, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(GetAutoSureColors.Surface)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Column(modifier = Modifier.padding(16.dp)) {
            Text(signal.title, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold, color = color))
            Spacer(Modifier.height(6.dp))
            Text(signal.evidence, style = GetAutoSureType.Body)
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GetAutoSureColors.SurfaceRaised)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("💬 Ask the seller", style = GetAutoSureType.Caption.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(2.dp))
                Text(signal.advice, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextPrimary))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ChecksCard(checks: List<PurchaseCheck>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GetAutoSureColors.Surface)
            .padding(vertical = 6.dp)
    ) {
        checks.forEach { check ->
            val (mark, label, color) = when (check.outcome) {
                CheckOutcome.CLEAR -> Triple("✓", "Clear", GetAutoSureColors.Good)
                CheckOutcome.FLAGGED -> Triple("!", "Red flag", GetAutoSureColors.Attention)
                CheckOutcome.WORTH_ASKING -> Triple("?", "Ask", GetAutoSureColors.Watch)
                CheckOutcome.NOT_AVAILABLE -> Triple("–", "Not reported", GetAutoSureColors.Unavailable)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(24.dp).background(color.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(mark, style = GetAutoSureType.Caption.copy(color = color, fontWeight = FontWeight.Bold)) }
                Spacer(Modifier.width(12.dp))
                Text(check.label, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
                Text(label, style = GetAutoSureType.Caption.copy(color = color))
            }
        }
    }
}

/** Official RTO record for the car being bought (via the vehicle-lookup function), shown in full. */
@Composable
internal fun RcCheckResult(rc: RcLookupResult, note: String? = "Compare these with what the seller told you and the RC they hand over.") {
    Spacer(Modifier.height(12.dp))
    Text("RTO record", style = GetAutoSureType.Body)
    Spacer(Modifier.height(4.dp))
    // Everything the record has, the same rows the PDF prints. Chassis and engine numbers are left out.
    com.enginesense.app.vehiclelookup.RtoRecord.vehicleRows(rc).forEach { RcRow(it.label, it.value, if (it.concern) GetAutoSureColors.Attention else null) }
    com.enginesense.app.vehiclelookup.RtoRecord.dealerRows(rc)
        .filterNot { it.label.startsWith("Chassis") || it.label.startsWith("Engine no") }
        .forEach { RcRow(it.label, it.value, if (it.concern) GetAutoSureColors.Attention else null) }
    Spacer(Modifier.height(8.dp))
    Text("Valid until", style = GetAutoSureType.Caption.copy(fontWeight = FontWeight.SemiBold))
    com.enginesense.app.vehiclelookup.RtoRecord.validity(rc, java.time.LocalDate.now()).forEach { line ->
        val color = when (line.status) {
            com.enginesense.app.vehiclelookup.ValidityStatus.VALID, com.enginesense.app.vehiclelookup.ValidityStatus.LIFETIME -> GetAutoSureColors.Good
            com.enginesense.app.vehiclelookup.ValidityStatus.EXPIRES_SOON -> GetAutoSureColors.Watch
            com.enginesense.app.vehiclelookup.ValidityStatus.EXPIRED -> GetAutoSureColors.Attention
            com.enginesense.app.vehiclelookup.ValidityStatus.UNKNOWN -> null
        }
        RcRow(line.label, line.display, color)
    }
    Spacer(Modifier.height(6.dp))
    note?.let { Text(it, style = GetAutoSureType.Caption) }
    if (rc.partial) {
        Spacer(Modifier.height(4.dp))
        Text("Some details aren't available from the RTO for this car.", style = GetAutoSureType.Caption)
    }
}

@Composable
private fun RcRow(label: String, value: String, valueColor: Color?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = GetAutoSureType.Caption, modifier = Modifier.weight(0.42f))
        Text(value, style = GetAutoSureType.Caption.copy(color = valueColor ?: GetAutoSureColors.TextPrimary), modifier = Modifier.weight(0.58f))
    }
}

/**
 * Evidence, never a verdict: OBD can show the ECU's km and whether it matches the dashboard,
 * but a cluster-only rollback or a replaced ECU can't be told apart from here — so this never
 * says "not tampered". Hidden when it's unknown whether the car reports PID 0xA6 (old saved scans).
 */
@Composable
internal fun OdometerCheckCard(ecuKm: Double?, supported: Boolean?, dashboardInput: String, onDashboardChange: (String) -> Unit) {
    if (supported == null) return

    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Odometer check", style = GetAutoSureType.Body)
            Spacer(Modifier.height(4.dp))
            if (ecuKm == null) {
                Text(
                    if (supported) "This car says it reports its odometer, but didn't send a usable reading this scan. Try rescanning with the engine running."
                    else "This car doesn't report its odometer over OBD — normal for most cars before about 2020. Compare the dashboard km with the service records and the car's age instead.",
                    style = GetAutoSureType.Caption
                )
            } else {
                Text("The car's computer says ${"%,d".format(ecuKm.toLong())} km.", style = GetAutoSureType.Body)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dashboardInput,
                    onValueChange = { onDashboardChange(it.filter(Char::isDigit)) },
                    label = { Text("Dashboard reading (km)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val dashboardKm = dashboardInput.toDoubleOrNull()
                if (dashboardKm != null) {
                    Spacer(Modifier.height(8.dp))
                    when (PrePurchaseAnalyzer.compareOdometer(ecuKm, dashboardKm)) {
                        OdometerMatch.MATCHES -> Text(
                            "🟢 The dashboard matches the car's computer. No odometer red flags from OBD.",
                            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Good)
                        )
                        OdometerMatch.MISMATCH -> Text(
                            "🔴 The dashboard and the car's computer differ by ${"%,d".format(kotlin.math.abs(ecuKm - dashboardKm).toLong())} km. " +
                                "Ask the seller why — it can mean the odometer was changed, or that the instrument cluster or engine computer was replaced. Ask for the service records.",
                            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention)
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

/**
 * Makes the pre-purchase PDF, then offers View and Share. Regenerated whenever [report] changes
 * (the buyer typed a dashboard km or price, or the RTO check came back), so a shared PDF always
 * matches the screen. A saved dealer profile brands it, same as the advanced report.
 */
@Composable
private fun PrePurchaseReportActions(result: com.enginesense.app.ScanResult, report: PrePurchaseReport, ratings: Map<String, Int>, onViewPdf: (java.io.File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember(report, ratings) { mutableStateOf<java.io.File?>(null) }
    var generating by remember { mutableStateOf(false) }

    if (report.rc == null && !result.isDemo) {
        Text("Tip: go back and get the RTO record so the report includes it.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(8.dp))
    }
    val current = file
    if (current == null) {
        Button(
            onClick = {
                generating = true
                scope.launch {
                    try {
                        file = withContext(Dispatchers.IO) {
                            val dealerStore = com.enginesense.app.storage.DealerProfileStore(context)
                            val dealerProfile = dealerStore.load()
                            val dealerLogo = if (dealerProfile != null && dealerStore.hasLogo()) {
                                android.graphics.BitmapFactory.decodeFile(dealerStore.logoFile.absolutePath)
                            } else null
                            PrePurchaseReportPdfGenerator.generate(context, result, report, dealerProfile, dealerLogo, buyerRatings = ratings)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Couldn't make the report: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        generating = false
                    }
                }
            },
            enabled = !generating,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (generating) "Making report…" else "Make pre-purchase report (PDF)") }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onViewPdf(current) }, modifier = Modifier.weight(1f)) { Text("View") }
            Button(onClick = { context.startActivity(sharePdfIntent(context, current)) }, modifier = Modifier.weight(1f)) { Text("Share") }
        }
    }
}