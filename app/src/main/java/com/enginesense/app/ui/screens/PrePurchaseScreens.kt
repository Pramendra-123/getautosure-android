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
import com.enginesense.app.vehicle.pricing.AiPriceQuery
import com.enginesense.app.vehicle.LookAround
import com.enginesense.app.vehicle.PanelIssue
import com.enginesense.app.vehicle.QuickAnswer
import com.enginesense.app.vehicle.aiSummaryLines
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
private enum class DriveView { NONE, INTRO, SUMMARY }

private val BUYER_STEP_TITLES = listOf("Scan the car", "Seller check", "Odometer and car number", "Look around the car", "Price and report")

/** Step 1 — starts a scan same as any other, just flagged as a pre-purchase check (see GetAutoSureViewModel.startPrePurchaseCheck). */
@Composable
fun PrePurchaseCheckScreen(viewModel: GetAutoSureViewModel, onStartScan: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        StepHeader(0, BUYER_STEP_TITLES)
        Spacer(Modifier.height(8.dp))
        Text(
            "Find out what the seller might be hiding: fault codes cleared before the sale, a rolled-back odometer, an engine warmed up before you arrived. Then a clear answer — good buy, buy with caution, or avoid — and a tentative price.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(16.dp))
        listOf(
            "Plug the scanner into the seller's car, engine on",
            "See what the seller might be hiding",
            "Check the RTO (VAHAN) record and the odometer",
            "Mark any damage on a picture of the car"
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
    var kmByRange by remember { mutableStateOf(false) }
    var kmRangeChoice by remember { mutableStateOf<com.enginesense.app.vehicle.KmRange?>(null) }
    var yearInput by remember { mutableStateOf("") }
    var make by remember { mutableStateOf<String?>(null) }
    var model by remember { mutableStateOf<String?>(null) }
    var variant by remember { mutableStateOf(Variant.MID) }
    val panelMarks = remember { mutableStateMapOf<String, Set<PanelIssue>>() }
    val quickAnswers = remember { mutableStateMapOf<String, QuickAnswer>() }
    val accidentAnswers = remember { mutableStateMapOf<Int, Boolean>() }
    var askingInput by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }

    // The test drive is an add-on after the price: its own screens, then back to step 5 with the report updated.
    val driveState by viewModel.testDrive.collectAsState()
    val driveResult by viewModel.driveResult.collectAsState()
    var driveView by remember { mutableStateOf(DriveView.NONE) }
    LaunchedEffect(driveResult) { if (driveResult != null && driveView == DriveView.INTRO) driveView = DriveView.SUMMARY }
    if (driveState != GetAutoSureViewModel.TestDriveState.Idle) {
        TestDriveLive(viewModel, driveState)
        return
    }
    when (driveView) {
        DriveView.INTRO -> {
            TestDriveIntro(viewModel, (rcState as? PurchaseRcState.Found)?.result?.model, onBack = { driveView = DriveView.NONE })
            return
        }
        DriveView.SUMMARY -> driveResult?.let {
            DriveSummary(it, onBack = { driveView = DriveView.NONE })
            return
        }
        DriveView.NONE -> Unit
    }

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

        // Ask for the AI market price as soon as the car is known, so it's usually ready by the price step.
        val context = LocalContext.current
        val priceYear = yearInput.toIntOrNull() ?: autoYear
        // The km is required: the exact dashboard reading, or the seller's range when the dashboard is dead.
        // Demo mode fills one in, so the demo still reaches a price.
        LaunchedEffect(r.isDemo) {
            if (r.isDemo && dashboardInput.isEmpty()) dashboardInput = (insight.ecuOdometerKm?.toLong() ?: 42_000L).toString()
        }
        val exactKm = if (kmByRange) null else com.enginesense.app.vehicle.OdometerReading.exactKm(dashboardInput)
        val kmRange = if (kmByRange) kmRangeChoice else null
        val priceKm = com.enginesense.app.vehicle.OdometerReading.kmForPrice(exactKm, kmRange)
        val priceQuery = if (make != null && model != null && priceYear != null && priceKm != null) AiPriceQuery(
            make = make!!, model = model!!, variant = variant, year = priceYear,
            fuel = rc?.fuelType, km = priceKm,
            city = rc?.registeredAt, owners = rc?.ownerCount, rcModel = rc?.model
        ) else null
        LaunchedEffect(priceQuery?.banded()) {
            // Waits for the buyer to stop typing the km, so one check doesn't fire a request per keystroke.
            kotlinx.coroutines.delay(1_500)
            if (priceQuery != null) viewModel.requestAiPrice(priceQuery, context)
        }
        val aiState by viewModel.aiPrice.collectAsState()
        val bandedQuery = priceQuery?.banded()
        val aiReady = (aiState as? GetAutoSureViewModel.AiPriceState.Ready)?.takeIf { it.query == bandedQuery }?.estimate
        val aiChecking = (aiState as? GetAutoSureViewModel.AiPriceState.Loading)?.query == bandedQuery && bandedQuery != null
        val aiFailed = (aiState as? GetAutoSureViewModel.AiPriceState.Failed)?.query == bandedQuery && bandedQuery != null

        val report = PrePurchaseReportBuilder.build(
            findings = r.findings, healthScore = r.health.score, readiness = r.readiness, snapshot = r.snapshot,
            vin = r.vehicleId.vin, dashboardKm = exactKm, kmRange = kmRange, rc = rc, registration = rc?.registrationNumber,
            exShowroomPriceInr = newPrice, buyerEnteredYear = yearInput.toIntOrNull() ?: autoYear,
            currentYear = currentYear,
            exteriorCounts = LookAround.counts(panelMarks, quickAnswers),
            make = make, fuelType = rc?.fuelType,
            aiPrice = aiReady,
            accidentSigns = LookAround.accidentFindings(accidentAnswers),
            drive = driveResult
        )
        val negotiation = com.enginesense.app.vehicle.NegotiationPlanner.plan(report, askingInput.toDoubleOrNull())
        val ratings = LookAround.ratings(quickAnswers)
        val carName = com.enginesense.app.dealerreport.CarNameFormatter.carName(rc?.make, rc?.model, rc?.fuelType)
            ?: listOfNotNull(make, model).joinToString(" ").ifBlank { null }

        // The AI Mechanic answers from the report the buyer is looking at, and the home screen remembers the car.
        LaunchedEffect(report, carName) {
            viewModel.setUsedCarReport(report.aiSummaryLines(carName))
            if (step >= 4) viewModel.rememberCheckedCar(report, carName, r)
        }

        StepHeader(step, BUYER_STEP_TITLES)
        Spacer(Modifier.height(12.dp))
        when (step) {
            1 -> SellerCheckStep(insight, onNext = { step = 2 })
            2 -> BuyerCarNumberStep(
                plate = plate, onPlate = { plate = it },
                rcViewModel = rcViewModel, rcState = rcState, isDemo = r.isDemo,
                ecuKm = insight.ecuOdometerKm, dashboardInput = dashboardInput, onDashboard = { dashboardInput = it },
                kmByRange = kmByRange, onKmByRange = { kmByRange = it },
                kmRange = kmRangeChoice, onKmRange = { kmRangeChoice = it },
                ageYears = com.enginesense.app.vehicle.OdometerReading.ageYears(
                    com.enginesense.app.vehiclelookup.RtoRecord.parseDate(rc?.registrationDate), report.manufactureYear, java.time.LocalDate.now()
                ),
                kmReady = priceKm != null,
                onNext = { step = 3 }
            )
            3 -> LookAroundStep(panelMarks, quickAnswers, accidentAnswers, onNext = { step = 4 })
            else -> BuyerPriceAndReportStep(
                viewModel = viewModel, r = r, insight = insight, report = report, rc = rc, ratings = ratings,
                make = make, onMake = { make = it; model = null }, model = model, onModel = { model = it },
                variant = variant, onVariant = { variant = it },
                yearInput = yearInput, onYear = { yearInput = it }, autoYear = autoYear, newPrice = newPrice,
                aiChecking = aiChecking, aiFailed = aiFailed,
                onRetryAi = { priceQuery?.let { viewModel.requestAiPrice(it, context, force = true) } },
                onTalkToMechanic = onTalkToMechanic, onDone = onDone, onViewPdf = onViewPdf,
                negotiation = negotiation, carName = carName, askingInput = askingInput, onAsking = { askingInput = it },
                drive = driveResult,
                onStartDrive = { driveView = DriveView.INTRO }, onViewDrive = { driveView = DriveView.SUMMARY }
            )
        }
        if (step > 1) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { step -= 1 }) { Text("← Back") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Step 2: the km (required — exact, or a range when the dashboard is dead) and the RTO (VAHAN) record. Both feed the price. */
@Composable
private fun BuyerCarNumberStep(
    plate: String, onPlate: (String) -> Unit,
    rcViewModel: PurchaseRcCheckViewModel, rcState: PurchaseRcState, isDemo: Boolean,
    ecuKm: Double?, dashboardInput: String, onDashboard: (String) -> Unit,
    kmByRange: Boolean, onKmByRange: (Boolean) -> Unit,
    kmRange: com.enginesense.app.vehicle.KmRange?, onKmRange: (com.enginesense.app.vehicle.KmRange) -> Unit,
    ageYears: Double?, kmReady: Boolean,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun check() { if (plate.isNotBlank()) rcViewModel.check(context, plate, null) }
    LaunchedEffect(rcState) {
        if (rcState is PurchaseRcState.Found) { focus.clearFocus(); keyboard?.hide() }
    }

    Text("Both decide the price. We check the km against the car's computer and its age.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(12.dp))
    OdometerEntry(
        input = dashboardInput, onInput = onDashboard,
        byRange = kmByRange, onByRange = onKmByRange,
        range = kmRange, onRange = onKmRange,
        ecuKm = ecuKm, ageYears = ageYears
    )

    Spacer(Modifier.height(12.dp))
    Text("Registration number", style = GetAutoSureType.SectionTitle)
    Text("We fetch the car's official record from VAHAN: make and model, year, owners, insurance, PUC, loan and more.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(8.dp))
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
    val found = rcState is PurchaseRcState.Found || isDemo
    Button(onClick = onNext, enabled = found && kmReady, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(
            when {
                !kmReady -> "Enter the km to continue"
                found -> "Next: look around the car"
                else -> "Get the RTO record to continue"
            }
        )
    }
    // The RTO service can be down; the check mustn't stop there. Make, model and year can be picked on the price step.
    if (!found && rcState is PurchaseRcState.Failed) {
        TextButton(onClick = onNext, enabled = kmReady, modifier = Modifier.fillMaxWidth()) {
            Text("Skip the RTO record and carry on")
        }
    }
}

/** Step 4: verdict, what to raise with the seller, the price with how it was worked out, and the report. */
@Composable
private fun BuyerPriceAndReportStep(
    viewModel: GetAutoSureViewModel, r: com.enginesense.app.ScanResult, insight: com.enginesense.app.vehicle.PrePurchaseInsight,
    report: PrePurchaseReport, rc: RcLookupResult?, ratings: Map<String, Int>,
    make: String?, onMake: (String) -> Unit, model: String?, onModel: (String) -> Unit,
    variant: Variant, onVariant: (Variant) -> Unit,
    yearInput: String, onYear: (String) -> Unit, autoYear: Int?, newPrice: Double?,
    aiChecking: Boolean, aiFailed: Boolean, onRetryAi: () -> Unit,
    onTalkToMechanic: () -> Unit, onDone: () -> Unit, onViewPdf: (java.io.File) -> Unit,
    negotiation: com.enginesense.app.vehicle.NegotiationPlan?, carName: String?,
    askingInput: String, onAsking: (String) -> Unit,
    drive: com.enginesense.app.vehicle.DriveResult?, onStartDrive: () -> Unit, onViewDrive: () -> Unit
) {
    val context = LocalContext.current
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
    PriceBasisCard(report, rc, make, model, variant, newPrice, yearInput.toIntOrNull() ?: autoYear, aiChecking, aiFailed, onRetryAi)
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

    NegotiationCard(
        viewModel, report, negotiation, carName, askingInput, onAsking,
        onSharePdf = {
            withContext(Dispatchers.IO) {
                com.enginesense.app.report.PrePurchaseReportPdfGenerator.generate(context, r, report, null, null)
            }
        }
    )

    // Optional add-ons: they never block the report, and both feed back into it.
    StrongerCheckCards(drive, onStartDrive, onViewDrive, onExpert = onTalkToMechanic)

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
private fun PriceBasisCard(
    report: PrePurchaseReport, rc: RcLookupResult?, make: String?, model: String?, variant: Variant, newPrice: Double?, year: Int?,
    aiChecking: Boolean = false, aiFailed: Boolean = false, onRetryAi: () -> Unit = {}
) {
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val value = report.value
            val ai = value?.ai?.takeIf { value.basis == com.enginesense.app.vehicle.pricing.PriceBasis.AI_MARKET }
            if (value != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ai != null) {
                        PriceBadge("✦ AI market check", GetAutoSureColors.AccentPurple, Color(0xFFEFEAFD))
                        Spacer(Modifier.width(6.dp))
                        val (label, fg, bg) = when (ai.confidence) {
                            "high" -> Triple("High confidence", GetAutoSureColors.Good, Color(0xFFE6F4F0))
                            "medium" -> Triple("Medium confidence", GetAutoSureColors.Watch, Color(0xFFFDF3E3))
                            else -> Triple("Low confidence", GetAutoSureColors.TextSecondary, GetAutoSureColors.SurfaceRaised)
                        }
                        PriceBadge(label, fg, bg)
                    } else {
                        PriceBadge("Our estimate", GetAutoSureColors.TextSecondary, GetAutoSureColors.SurfaceRaised)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (value == null) {
                Text("No price yet", style = GetAutoSureType.Body)
                Text(report.valueMissingReason ?: "Pick the model below to get a price.", style = GetAutoSureType.Caption)
            } else {
                val shownAfterRepairs = report.repairMaxInr > 0
                Text(
                    "₹${formatRupees(if (shownAfterRepairs) value.afterRepairsLow else value.fair.lowEstimate)} – ₹${formatRupees(if (shownAfterRepairs) value.afterRepairsHigh else value.fair.highEstimate)}",
                    style = GetAutoSureType.ScreenTitle.copy(color = GetAutoSureColors.Accent)
                )
                Text(
                    (if (shownAfterRepairs) "As it stands, after the repairs found. " else "") + "A rough guide, not a valuation. Bargain from here.",
                    style = GetAutoSureType.Caption
                )
                if (ai != null && shownAfterRepairs) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PriceSplit("In good order", "₹${formatRupees(value.fair.lowEstimate)} – ${formatRupees(value.fair.highEstimate)}", Modifier.weight(1f))
                        PriceSplit("Repairs taken off", "₹${formatRupees(report.repairMinInr.toDouble())} – ${formatRupees(report.repairMaxInr.toDouble())}", Modifier.weight(1f))
                    }
                }
            }
            if (aiChecking && ai == null) {
                Spacer(Modifier.height(10.dp))
                PriceNote("Checking current listings for this car… usually 15–30 seconds.", Color(0xFF3F2A8C), Color(0xFFF3EFFE))
            }
            value?.note?.let {
                Spacer(Modifier.height(10.dp))
                PriceNote(it, Color(0xFF6B3E06), Color(0xFFFDF3E3))
            }

            if (ai != null) {
                AiPriceExplanation(ai, value?.formula)
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            Text("HOW WE GOT THIS", style = GetAutoSureType.Caption.copy(letterSpacing = 1.2.sp))
            Spacer(Modifier.height(4.dp))
            val car = com.enginesense.app.dealerreport.CarNameFormatter.carName(rc?.make, rc?.model, rc?.fuelType) ?: listOfNotNull(make, model).joinToString(" ").ifBlank { null }
            val km = report.odometer.priceKm
            val kmText = report.odometer.enteredText ?: km?.let { "%,d km (car's computer)".format(it.toLong()) }
            val lines = listOfNotNull(
                "Car" to (car?.let { "$it${if (rc != null) " (VAHAN)" else ""}" } ?: "Not known"),
                "Year" to (year?.toString() ?: "Not known"),
                rc?.fuelType?.let { "Fuel" to it.lowercase().replaceFirstChar { c -> c.uppercase() } },
                rc?.ownerCount?.let { "Owners" to com.enginesense.app.dealerreport.DealerSaleSummaryBuilder.ordinalOwner(it) },
                "Km driven" to (kmText?.let { it + (report.odometer.perYear?.let { p -> ", about ${"%,d".format(p.kmPerYear)} a year" } ?: "") } ?: "Not entered"),
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
            if (aiFailed) {
                Text(
                    "Try the market check again",
                    style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.clickable { onRetryAi() }.padding(top = 10.dp)
                )
            }
        }
    }
}

/** The AI's working: its steps, the listings it compared, the sites it searched, and what our formula said. */
@Composable
private fun AiPriceExplanation(ai: com.enginesense.app.vehicle.pricing.AiPriceEstimate, formula: com.enginesense.app.vehicle.FairPriceEstimate?) {
    val context = LocalContext.current
    val label = GetAutoSureType.Caption.copy(letterSpacing = 1.2.sp)
    Spacer(Modifier.height(14.dp))
    Text("HOW THE AI GOT THIS", style = label)
    ai.steps.forEachIndexed { i, step ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("${i + 1}", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.AccentPurple, fontWeight = FontWeight.Bold), modifier = Modifier.width(18.dp))
            Text(step, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
        }
    }
    if (ai.comparables.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("LISTINGS IT COMPARED", style = label)
        ai.comparables.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.description, style = GetAutoSureType.Body)
                    if (c.site.isNotBlank()) Text(c.site, style = GetAutoSureType.Caption)
                }
                Text("₹${formatRupees(c.priceInr)}", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
    if (ai.sources.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("SEARCHED", style = label)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ai.sources.take(4).forEach { s ->
                Text(
                    s.title.ifBlank { "Source" },
                    style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent),
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                        .clip(RoundedCornerShape(50)).background(GetAutoSureColors.AccentSoft)
                        .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url))) } }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        listOfNotNull(
            "Prices found online, checked for this car" + (if (ai.pricedAt != null) " in the last week." else "."),
            formula?.let { "Our own formula said ₹${formatRupees(it.lowEstimate)} – ${formatRupees(it.highEstimate)}." }
        ).joinToString(" "),
        style = GetAutoSureType.Caption
    )
}

@Composable
private fun PriceBadge(text: String, fg: Color, bg: Color) {
    Text(
        text,
        style = GetAutoSureType.Caption.copy(color = fg, fontWeight = FontWeight.Bold),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
private fun PriceSplit(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(GetAutoSureColors.SurfaceRaised).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, style = GetAutoSureType.Caption)
        Text(value, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun PriceNote(text: String, fg: Color, bg: Color) {
    Text(
        text,
        style = GetAutoSureType.Caption.copy(color = fg),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(10.dp)
    )
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

/**
 * Step 2, straight after the scan: what the car's own computer says the seller might be hiding,
 * before any form. Each check is a red flag, something to ask, clear, or not reported by this car.
 */
@Composable
private fun SellerCheckStep(insight: com.enginesense.app.vehicle.PrePurchaseInsight, onNext: () -> Unit) {
    val flags = insight.fraudRiskFlags
    val asks = insight.cautionNotes
    val clear = insight.checks.count { it.outcome == CheckOutcome.CLEAR }
    val notReported = insight.checks.count { it.outcome == CheckOutcome.NOT_AVAILABLE }

    Text("What the seller might be hiding", style = GetAutoSureType.ScreenTitle)
    Text("Read from the car's own computer. Washing or polishing the car can't hide these.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(12.dp))

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GetAutoSureColors.Ink).padding(16.dp)
    ) {
        val total = flags.size + asks.size
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$total", color = GetAutoSureColors.AccentBright, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    total == 0 -> "nothing to ask about"
                    flags.isEmpty() -> if (total == 1) "thing to ask about" else "things to ask about"
                    else -> "${flags.size} red flag${if (flags.size == 1) "" else "s"}" + if (asks.isNotEmpty()) ", ${asks.size} to ask about" else ""
                },
                color = GetAutoSureColors.OnInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Text(
            listOfNotNull(
                "$clear clear",
                if (notReported > 0) "$notReported this car doesn't report" else null
            ).joinToString(" · "),
            color = GetAutoSureColors.OnInkMuted, fontSize = 12.sp
        )
    }
    Spacer(Modifier.height(12.dp))

    if (insight.checks.isEmpty()) {
        Text("The scan didn't read enough from this car to run these checks. Try scanning again with the engine running.", style = GetAutoSureType.Body)
    }
    flags.forEach { SignalCard(it.copy(title = "Red flag · ${it.title}"), GetAutoSureColors.Attention) }
    asks.forEach { SignalCard(it.copy(title = "Ask the seller · ${it.title}"), GetAutoSureColors.Watch) }
    if (insight.checks.isNotEmpty()) {
        Text("Everything we checked", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        ChecksCard(insight.checks)
        Spacer(Modifier.height(8.dp))
        Text(
            if (insight.ecuOdometerKm != null) "Odometer: the car's computer says ${"%,d".format(insight.ecuOdometerKm.toLong())} km. You'll compare it with the dashboard in the next step."
            else "Odometer: this car doesn't share its total km, so we can't check the dashboard against it. Ask for service records instead.",
            style = GetAutoSureType.Caption
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Next: car number") }
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
                        OdometerMatch.SMALL_GAP -> Text(
                            "🟠 A small gap of ${"%,d".format(kotlin.math.abs(ecuKm - dashboardKm).toLong())} km from the car's computer. Often a typo or a replaced cluster: read the dashboard again, and ask the seller if it's real.",
                            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch)
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
 * matches the screen. In dealer mode the saved dealer profile brands it; otherwise it's unbranded.
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
                            // Dealer branding only in dealer mode, same as the owner report: a dealer
                            // profile saved on this phone must never brand a buyer's own check.
                            val dealerStore = com.enginesense.app.storage.DealerProfileStore(context)
                            val dealerMode = com.enginesense.app.storage.AppModePrefs.get(context) == com.enginesense.app.storage.AppMode.DEALER
                            val dealerProfile = if (dealerMode) dealerStore.load() else null
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