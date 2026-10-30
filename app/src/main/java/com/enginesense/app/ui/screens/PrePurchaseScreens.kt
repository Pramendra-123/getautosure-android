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
import androidx.compose.runtime.Composable
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
import com.enginesense.app.report.viewPdfIntent
import com.enginesense.app.vehicle.PrePurchaseReport
import com.enginesense.app.vehicle.PrePurchaseReportBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Entry point — explains the feature, then starts a scan same as any other, just flagged as a pre-purchase check (see GetAutoSureViewModel.startPrePurchaseCheck). */
@Composable
fun PrePurchaseCheckScreen(viewModel: GetAutoSureViewModel, onStartScan: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Considering buying a car?", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan it before you commit — the same health check this app runs on your own car, read as a buy/caution/avoid call instead of an ownership status.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(24.dp))
        listOf(
            "Plug in at the seller's location, same as any scan",
            "Free verdict: good buy, buy with caution, or avoid",
            "Dealer Pro: itemized talking points for the negotiation"
        ).forEach { line ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text("•  ", style = GetAutoSureType.Body)
                Text(line, style = GetAutoSureType.Body)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { viewModel.startPrePurchaseCheck(); onStartScan() },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Scan this car") }
    }
}

/**
 * Depreciation-based fair price estimate — see [FairPriceEstimator]. Built on IRDAI's
 * published standard motor depreciation schedule, not any company's internal pricing
 * model. The user supplies the ex-showroom price; this app has no price catalog and
 * never guesses one.
 */
@Composable
private fun FairPriceEstimateCard(
    priceInput: String,
    onPriceChange: (String) -> Unit,
    yearInput: String,
    onYearChange: (String) -> Unit,
    /** From the RTO lookup, when done — wins over [yearInput], which is only asked for without it. */
    rcYear: Int?,
    /** Dashboard km if entered, else the car's computer — never asked for twice. */
    km: Int?
) {
    val year = rcYear ?: yearInput.toIntOrNull()

    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Fair Price Estimate", style = GetAutoSureType.Body)
            Spacer(Modifier.height(2.dp))
            Text(
                "Based on the IRDAI standard depreciation schedule — a rough estimate, not a verified valuation.",
                style = GetAutoSureType.Caption
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = priceInput,
                onValueChange = { onPriceChange(it.filter(Char::isDigit)) },
                label = { Text("Ex-showroom price (₹)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (rcYear == null) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = yearInput,
                    onValueChange = { onYearChange(it.filter(Char::isDigit).take(4)) },
                    label = { Text("Year of manufacture") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text("Manufactured $rcYear, from RTO records", style = GetAutoSureType.Caption)
            }

            val price = priceInput.toDoubleOrNull()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val ageYears = year?.let { (currentYear - it).toDouble().coerceAtLeast(0.5) }

            Spacer(Modifier.height(12.dp))
            when {
                price == null || price <= 0 -> Text(
                    "Enter the ex-showroom price to see an estimate — we don't have a pricing catalog, so this can't be guessed.",
                    style = GetAutoSureType.Caption
                )
                ageYears == null -> Text(
                    "Add the year of manufacture, or check RTO records above, to calculate an estimate.",
                    style = GetAutoSureType.Caption
                )
                else -> {
                    val est = FairPriceEstimator.estimate(price, ageYears, km)
                    Text(
                        "₹${formatRupees(est.lowEstimate)} – ₹${formatRupees(est.highEstimate)}",
                        style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Accent)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${est.depreciationPercent}% depreciation over ${est.ageYears} year${if (est.ageYears == 1) "" else "s"}" +
                            if (km != null) ", adjusted for ${"%,d".format(km)} km" else "",
                        style = GetAutoSureType.Caption
                    )
                    if (est.beyondPublishedSchedule) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "IRDAI only publishes rates up to 5 years — beyond that this extrapolates, it isn't an official figure.",
                            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch)
                        )
                    }
                }
            }
        }
    }
}

private fun formatRupees(value: Double): String =
    if (value >= 100000) "%.2f L".format(value / 100000) else "%,.0f".format(value)

@Composable
fun PrePurchaseResultScreen(viewModel: GetAutoSureViewModel, onOpenDealer: () -> Unit, onDone: () -> Unit) {
    val result by viewModel.latestResult.collectAsState()
    val context = LocalContext.current
    val r = result
    // What the buyer adds at the car — kept here, not in each card, because the PDF needs all of it.
    val rcViewModel: PurchaseRcCheckViewModel = viewModel()
    val rcState by rcViewModel.state.collectAsState()
    var dashboardInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var yearInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        if (r == null) {
            Text("No scan result to show.", style = GetAutoSureType.Body)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { viewModel.clearPrePurchaseCheck(); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            return@Column
        }

        val insight = PrePurchaseAnalyzer.analyze(r.findings, r.health.score, r.readiness, r.snapshot)
        val rc = (rcState as? PurchaseRcState.Found)?.result
        val report = PrePurchaseReportBuilder.build(
            findings = r.findings, healthScore = r.health.score, readiness = r.readiness, snapshot = r.snapshot,
            vin = r.vehicleId.vin, dashboardKm = dashboardInput.toDoubleOrNull(), rc = rc, registration = rc?.registrationNumber,
            exShowroomPriceInr = priceInput.toDoubleOrNull(), buyerEnteredYear = yearInput.toIntOrNull(),
            currentYear = Calendar.getInstance().get(Calendar.YEAR)
        )
        // The report's verdict also counts the buyer's own checks (odometer, chassis), so the screen uses it too.
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

        Text("USED CAR CHECK", style = GetAutoSureType.Caption.copy(letterSpacing = 1.5.sp))
        Spacer(Modifier.height(12.dp))
        VerdictHeader(
            label = verdictLabel,
            emoji = emoji,
            color = color,
            headline = report.headline,
            redFlags = report.redFlags.size,
            toAsk = report.worthAsking.size,
            clear = report.checks.count { it.outcome == CheckOutcome.CLEAR }
        )

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

        SectionHeader("Verify the car", "Compare the car with its dashboard and papers")
        OdometerCheckCard(insight.ecuOdometerKm, insight.odometerSupported, dashboardInput) { dashboardInput = it }
        VehicleIdentityCard(r.vehicleId.vin, isDemo = r.isDemo)

        SectionHeader("Price & negotiation")

        // Dealer Pro: the itemized list is the paid part — teased by count, not revealed free.
        Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDealer)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (insight.negotiationPoints.isEmpty()) "No specific talking points from this scan" else "${insight.negotiationPoints.size} thing${if (insight.negotiationPoints.size == 1) "" else "s"} worth raising with the seller",
                        style = GetAutoSureType.Body
                    )
                    Text("Itemized negotiation points — Dealer Pro", style = GetAutoSureType.Caption)
                }
                if (insight.negotiationPoints.isNotEmpty()) {
                    Box(
                        modifier = Modifier.background(GetAutoSureColors.Good.copy(alpha = 0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("PRO", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Good, fontSize = 10.sp)) }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = GetAutoSureColors.Unavailable, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        FairPriceEstimateCard(
            priceInput = priceInput, onPriceChange = { priceInput = it },
            yearInput = yearInput, onYearChange = { yearInput = it },
            rcYear = rc?.manufacturingMonth?.take(4)?.toIntOrNull(),
            km = (dashboardInput.toDoubleOrNull() ?: insight.ecuOdometerKm)?.toInt()
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "Want a second opinion? These tools factor in local market listings, which this estimate doesn't:",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.cardekho.com/used-car-valuation.htm"))) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("CarDekho SmartPrice →") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.cars24.com/used-car-valuation/"))) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Cars24 Valuation →") }

        SectionHeader("Pre-purchase report", "A PDF of everything above — red flags, odometer, RTO record, repair costs and value")
        PrePurchaseReportActions(r, report)

        Spacer(Modifier.height(24.dp))
        Button(onClick = { viewModel.clearPrePurchaseCheck(); onDone() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Done")
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
private fun SectionHeader(title: String, subtitle: String? = null) {
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

/**
 * Official RTO record for the car being bought (via the vehicle-lookup function), plus whether
 * the VIN its computer reports matches the RC's chassis number. The match is decided server-side
 * and comes back as yes/no/can't-tell — customer mode never sees the chassis number itself.
 */
@Composable
private fun VehicleIdentityCard(vin: String?, isDemo: Boolean) {
    if (isDemo) return
    val rcViewModel: PurchaseRcCheckViewModel = viewModel()
    val state by rcViewModel.state.collectAsState()
    val context = LocalContext.current
    var plateInput by remember { mutableStateOf("") }

    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Check against RTO records", style = GetAutoSureType.Body)
            Spacer(Modifier.height(4.dp))
            Text(
                "See the official manufacturing year and whether this car's computer matches the chassis number on its RC.",
                style = GetAutoSureType.Caption
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = plateInput,
                onValueChange = { plateInput = it.take(13) },
                label = { Text("Registration number, e.g. MH12AB1234") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { rcViewModel.check(context, plateInput, vin) },
                enabled = state != PurchaseRcState.Looking && plateInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state == PurchaseRcState.Looking) "Checking RTO records…" else "Check RTO records") }

            when (val s = state) {
                is PurchaseRcState.Found -> RcCheckResult(s.result, vinReported = vin != null)
                is PurchaseRcState.Failed -> {
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
                }
                PurchaseRcState.Idle, PurchaseRcState.Looking -> Unit
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun RcCheckResult(rc: RcLookupResult, vinReported: Boolean) {
    Spacer(Modifier.height(12.dp))
    val car = listOfNotNull(rc.make, rc.model).joinToString(" ")
    if (car.isNotBlank()) Text("RTO record: $car", style = GetAutoSureType.Body)
    listOfNotNull(
        PurchaseRcCheckViewModel.formatMonth(rc.manufacturingMonth)?.let { "Manufactured: $it" },
        rc.registrationDate?.let { "Registered: $it" },
        rc.insuranceUpto?.let { "Insurance valid until: $it" },
        rc.puccUpto?.let { "PUC valid until: $it" }
    ).forEach { Text(it, style = GetAutoSureType.Caption) }
    Text("Compare these with what the seller told you.", style = GetAutoSureType.Caption)

    Spacer(Modifier.height(8.dp))
    val (text, color) = when {
        !vinReported -> "This car's computer didn't report a VIN, so the chassis couldn't be checked automatically. Check that the chassis number stamped on the body matches the RC." to GetAutoSureColors.Watch
        rc.chassisMatch == true -> "🟢 This car's computer matches the chassis number on the RC." to GetAutoSureColors.Good
        rc.chassisMatch == false -> ("🔴 This car's computer doesn't match the chassis number on the RC. Ask the seller — the engine computer may have been replaced, " +
            "or the car may not match its papers. Check the chassis number stamped on the body.") to GetAutoSureColors.Attention
        else -> "The RTO record doesn't show enough of the chassis number to compare. Check that the number stamped on the body matches the RC." to GetAutoSureColors.Watch
    }
    Text(text, style = GetAutoSureType.Caption.copy(color = color))
    if (rc.partial) {
        Spacer(Modifier.height(4.dp))
        Text("Some details aren't available from the RTO for this car.", style = GetAutoSureType.Caption)
    }
}

/**
 * Evidence, never a verdict: OBD can show the ECU's km and whether it matches the dashboard,
 * but a cluster-only rollback or a replaced ECU can't be told apart from here — so this never
 * says "not tampered". Hidden when it's unknown whether the car reports PID 0xA6 (old saved scans).
 */
@Composable
private fun OdometerCheckCard(ecuKm: Double?, supported: Boolean?, dashboardInput: String, onDashboardChange: (String) -> Unit) {
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
private fun PrePurchaseReportActions(result: com.enginesense.app.ScanResult, report: PrePurchaseReport) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember(report) { mutableStateOf<java.io.File?>(null) }
    var generating by remember { mutableStateOf(false) }

    if (report.rc == null && !result.isDemo) {
        Text("Tip: check RTO records above first so the report includes them.", style = GetAutoSureType.Caption)
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
                            PrePurchaseReportPdfGenerator.generate(context, result, report, dealerProfile, dealerLogo)
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
            OutlinedButton(onClick = { context.startActivity(viewPdfIntent(context, current)) }, modifier = Modifier.weight(1f)) { Text("View") }
            Button(onClick = { context.startActivity(sharePdfIntent(context, current)) }, modifier = Modifier.weight(1f)) { Text("Share") }
        }
    }
}