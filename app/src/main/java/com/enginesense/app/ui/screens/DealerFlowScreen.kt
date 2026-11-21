package com.enginesense.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ScanResult
import com.enginesense.app.dealerreport.BadgeGroup
import com.enginesense.app.dealerreport.CarNameFormatter
import com.enginesense.app.dealerreport.StatusBadgeOption
import com.enginesense.app.dealerreport.StatusBadges
import com.enginesense.app.dealerreport.PhotoUpload
import com.enginesense.app.dealerreport.SaleReportPublishRequest
import com.enginesense.app.dealerreport.saleDealerConditionJson
import com.enginesense.app.dealerreport.saleHealthSummaryJson
import com.enginesense.app.dealerreport.DealerInspection
import com.enginesense.app.dealerreport.DealerSaleSummaryBuilder
import com.enginesense.app.dealerreport.INSPECTION_EXTRAS
import com.enginesense.app.dealerreport.INSPECTION_ITEMS
import com.enginesense.app.dealerreport.PublishReportViewModel
import com.enginesense.app.dealerreport.DealerReportHttpException
import com.enginesense.app.report.DealerSaleReportInput
import com.enginesense.app.report.DealerSaleReportPdfGenerator
import com.enginesense.app.report.DealerStatusImage
import com.enginesense.app.report.ReportPhoto
import com.enginesense.app.report.sharePdfIntent
import com.enginesense.app.storage.DealerProfileStore
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.PrePurchaseReportBuilder
import com.enginesense.app.vehiclelookup.PurchaseRcCheckViewModel
import com.enginesense.app.vehiclelookup.PurchaseRcState
import com.enginesense.app.vehiclelookup.RcLookupResult
import com.enginesense.app.vehiclelookup.RtoRecord
import com.enginesense.app.vehiclelookup.ValidityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate


/** Photo slots in step 4; the first ("Front") is required — it's the PDF cover and the Status picture. */
private val DEALER_PHOTO_SLOTS = listOf("Front", "Rear", "Left side", "Right side", "Interior", "Dashboard")

/**
 * Everything the dealer has entered so far. Scoped to the "dealerFlow" screen, so it survives the
 * trip to the scanner and back (plain remember{} would be lost while the scan runs).
 */
class DealerFlowState : ViewModel() {
    var step by mutableIntStateOf(0)
    var plate by mutableStateOf("")
    var dashboardKm by mutableStateOf("")
    val ratings = mutableStateMapOf<String, Int>()
    val extras = mutableStateListOf<String>()
    var note by mutableStateOf("")
    internal val photos = DEALER_PHOTO_SLOTS.map { ExteriorPhotoSlot(it) }
    var showPrice by mutableStateOf(false)
    /** Background Gemini puts behind the car: always the grey studio wall and turntable floor, so every dealer's photos look the same. */
    val photoStyle = "studio"
    /** Gemini blanks the number plate so the full registration isn't public on Status. */
    var hidePlate by mutableStateOf(true)
    /** The badge ids the dealer picked for the Status picture; empty until they touch the picker (then the defaults apply). */
    val statusBadges = mutableStateListOf<String>()
    var price by mutableStateOf("")
    var generating by mutableStateOf(false)
    var pdf by mutableStateOf<File?>(null)
    var status by mutableStateOf<File?>(null)
    /** The report's web page (getautosure.com/<slug>), once published; goes in the WhatsApp Status text. */
    var webUrl by mutableStateOf<String?>(null)
    var webError by mutableStateOf<String?>(null)
    /** The server refused the report: no credits left (only once payments are open). */
    var outOfCredits by mutableStateOf(false)

    val inspection: DealerInspection get() = DealerInspection(ratings.toMap(), extras.toList(), note)

    /** Anything the dealer changed after making the report makes it stale. */
    fun invalidate() {
        pdf = null
        status = null
        webUrl = null
        webError = null
        outOfCredits = false
    }
}

/**
 * Dealer Sathi v2 (docs/dealer-sathi-v2.md): scan → car number and RTO record → the dealer's own
 * inspection → photos, then the PDF and the WhatsApp Status picture. Made after refurbishment, for
 * the dealer's customers.
 */
@Composable
fun DealerFlowScreen(
    viewModel: GetAutoSureViewModel,
    onStartScan: () -> Unit,
    onSetUpDealerProfile: () -> Unit,
    onMyReports: () -> Unit = {},
    onBuyPack: () -> Unit = {},
    onViewPdf: (File) -> Unit
) {
    val state: DealerFlowState = viewModel()
    val rcViewModel: PurchaseRcCheckViewModel = viewModel()
    val photoViewModel: PublishReportViewModel = viewModel()
    val result by viewModel.dealerResult.collectAsState()
    val rcState by rcViewModel.state.collectAsState()

    // Back from the scanner with a result: stop routing scans here, and move on to the car number.
    LaunchedEffect(result) {
        if (result != null) {
            viewModel.clearDealerScan()
            if (state.step == 0) state.step = 1
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        StepHeader(state.step)
        Spacer(Modifier.height(16.dp))
        if (state.step == 0) {
            DealerProfileCard(onEdit = onSetUpDealerProfile)
            Spacer(Modifier.height(12.dp))
            MyReportsCard(onOpen = onMyReports)
            Spacer(Modifier.height(16.dp))
        }
        when (state.step) {
            0 -> ScanStep(result, onScan = { viewModel.startDealerScan(); state.invalidate(); onStartScan() }, onNext = { state.step = 1 })
            1 -> CarNumberStep(state, rcViewModel, rcState, result, onNext = { state.step = 2 })
            2 -> InspectionStep(state, onNext = { state.step = 3 })
            else -> PhotosStep(state, photoViewModel, result, rcState, onSetUpDealerProfile, onBuyPack, onViewPdf)
        }
        if (state.step > 0) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { state.step -= 1 }) { Text("← Back") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Who the report is from — logo, name, phone, area — with Edit. The profile is saved on the phone
 * once and reused, which looked like the app "just knew" the dealer; this makes it visible and
 * changeable. Re-read each time the screen is shown, so an edit shows straight away.
 */
@Composable
private fun DealerProfileCard(onEdit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { DealerProfileStore(context) }
    val dealer = remember { store.load() }
    val logo = remember { if (store.hasLogo()) android.graphics.BitmapFactory.decodeFile(store.logoFile.absolutePath) else null }
    Card(
        colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (logo != null) {
                androidx.compose.foundation.Image(
                    logo.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(Modifier.size(44.dp).background(GetAutoSureColors.SurfaceRaised, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text(dealer?.name?.split(" ")?.take(2)?.mapNotNull { it.firstOrNull()?.uppercase() }?.joinToString("") ?: "?", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.Bold))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (dealer == null) {
                    Text("Set up your dealer profile", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                    Text("Logo, name, numbers and showroom — needed before your first report.", style = GetAutoSureType.Caption)
                } else {
                    Text(dealer.name, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                    Text(listOfNotNull(dealer.phones.firstOrNull(), dealer.location.trim().ifBlank { null }).joinToString(" · "), style = GetAutoSureType.Caption, maxLines = 1)
                    Text("Shown on every report and Status", style = GetAutoSureType.Caption)
                }
            }
            Text(if (dealer == null) "Set up" else "Edit", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** The 4-bar progress header shared by Dealer Saathi and Used Car Check. */
@Composable
internal fun StepHeader(step: Int, titles: List<String> = DEALER_STEP_TITLES) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(titles.size) { i ->
            Box(
                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i <= step) GetAutoSureColors.Accent else GetAutoSureColors.SurfaceRaised)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("Step ${step + 1} of ${titles.size}", style = GetAutoSureType.Caption)
    Text(titles[step], style = GetAutoSureType.ScreenTitle)
}

private val DEALER_STEP_TITLES = listOf("Scan the car", "Car number", "Your inspection", "Photos and report")

// ---------- 1. scan ----------

@Composable
private fun ScanStep(result: ScanResult?, onScan: () -> Unit, onNext: () -> Unit) {
    if (result == null) {
        Text("Plug the scanner into the car you've refurbished and start the engine.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RoundScanButton(onScan, title = null, subtitle = "Takes about a minute")
        }
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Scanned", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Good, fontWeight = FontWeight.SemiBold))
            Text(
                listOfNotNull(result.health.score?.let { "Health $it/100" }, "${result.findings.size} finding${if (result.findings.size == 1) "" else "s"}").joinToString(" · "),
                style = GetAutoSureType.Body
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Next: car number") }
    TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
}

// ---------- 2. car number ----------

@Composable
private fun CarNumberStep(
    state: DealerFlowState,
    rcViewModel: PurchaseRcCheckViewModel,
    rcState: PurchaseRcState,
    result: ScanResult?,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun check() {
        if (state.plate.isBlank()) return
        state.invalidate()
        rcViewModel.check(context, state.plate, null)
    }
    // Record found: the field is done — no blinking cursor or keyboard left behind.
    LaunchedEffect(rcState) {
        if (rcState is PurchaseRcState.Found) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }

    Text("The RTO record goes on the report: owner, insurance, PUC, loan and more.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.plate,
        onValueChange = { state.plate = it.uppercase().take(13) },
        label = { Text("Registration number, e.g. KA02MH3313") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { check() }),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { check() },
        enabled = rcState != PurchaseRcState.Looking && state.plate.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (rcState == PurchaseRcState.Looking) "Checking RTO records…" else "Get RTO record") }

    when (rcState) {
        PurchaseRcState.Looking -> RtoSearching()
        is PurchaseRcState.Found -> RtoSummary(rcState.result)
        is PurchaseRcState.Failed -> {
            Spacer(Modifier.height(8.dp))
            Text(rcState.message, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
        }
        else -> Unit
    }

    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.dashboardKm,
        onValueChange = { state.dashboardKm = it.filter(Char::isDigit).take(7); state.invalidate() },
        label = { Text("Dashboard km") },
        supportingText = { Text("Read it off the dashboard. We compare it with the km stored in the car's computer to check the odometer hasn't been rolled back.") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    // The RTO record is required: it's the first section of the report and fills the car's name.
    Button(onClick = onNext, enabled = rcState is PurchaseRcState.Found, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(if (rcState is PurchaseRcState.Found) "Next: your inspection" else "Get the RTO record to continue")
    }
}

/** Shown while the RTO lookup runs (a few seconds), so it's clear something is being fetched. */
@Composable
internal fun RtoSearching() {
    val steps = listOf("Contacting the RTO database…", "Finding the registration…", "Fetching owner and insurance…", "Checking loan and blacklist…")
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1400)
            index = (index + 1) % steps.size
        }
    }
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = GetAutoSureColors.Accent)
                Spacer(Modifier.width(12.dp))
                Text(steps[index], style = GetAutoSureType.Body)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GetAutoSureColors.Accent, trackColor = GetAutoSureColors.SurfaceRaised)
        }
    }
}

@Composable
private fun RtoSummary(rc: RcLookupResult) {
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("✓ RTO record found", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Good, fontWeight = FontWeight.SemiBold))
            Text(
                listOfNotNull(CarNameFormatter.carName(rc.make, rc.model, rc.fuelType), rc.fuelType, rc.ownerCount?.let { DealerSaleSummaryBuilder.ordinalOwner(it) })
                    .joinToString(" · "),
                style = GetAutoSureType.Body
            )
            // The whole record, same rows as Used Car Check and the PDF — the dealer checks it before it goes on the report.
            RcCheckResult(rc, note = "All of this goes on the PDF and the web report. The number is masked on the web page.")
        }
    }
}

// ---------- 3. inspection ----------

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InspectionStep(state: DealerFlowState, onNext: () -> Unit) {
    Text("Tap stars for each. Skip any you didn't check. The report says these are your ratings.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(12.dp))
    INSPECTION_ITEMS.forEach { item ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                .background(GetAutoSureColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(item, style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
            val rating = state.ratings[item] ?: 0
            (1..5).forEach { n ->
                Text(
                    if (n <= rating) "★" else "☆",
                    style = GetAutoSureType.Body.copy(fontSize = 24.sp, color = if (n <= rating) GetAutoSureColors.Watch else GetAutoSureColors.Unavailable),
                    // Tapping the current rating again clears it — back to "not rated".
                    modifier = Modifier.clickable {
                        if (rating == n) state.ratings.remove(item) else state.ratings[item] = n
                        state.invalidate()
                    }.padding(horizontal = 2.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        INSPECTION_EXTRAS.forEach { extra ->
            val on = extra in state.extras
            FilterChip(
                selected = on,
                onClick = { if (on) state.extras.remove(extra) else state.extras.add(extra); state.invalidate() },
                label = { Text(extra) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.note,
        onValueChange = { state.note = it.take(140); state.invalidate() },
        label = { Text("Note (optional)") },
        placeholder = { Text("e.g. Well kept family car") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Next: photos") }
}

// ---------- 4. photos, price, report ----------

/** "On your Status picture": health score fixed first, the dealer picks 3 more; only what's true for this car is enabled. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StatusBadgePicker(options: List<StatusBadgeOption>, state: DealerFlowState) {
    val chosen = state.statusBadges.ifEmpty { StatusBadges.defaults(options) }
        .filter { id -> options.any { it.id == id && it.available } }
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("On your Status picture", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
            Text("Pick ${StatusBadges.MAX}. Health score is always first. ${chosen.size} of ${StatusBadges.MAX} chosen.", style = GetAutoSureType.Caption)
            listOf(
                BadgeGroup.SCAN to "FROM THE SCAN", BadgeGroup.RTO to "FROM THE RTO RECORD", BadgeGroup.DEALER to "YOUR TICKS"
            ).forEach { (group, title) ->
                val inGroup = options.filter { it.group == group }
                if (inGroup.isEmpty()) return@forEach
                Spacer(Modifier.height(8.dp))
                Text(title, style = GetAutoSureType.Caption.copy(fontWeight = FontWeight.Bold))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    inGroup.forEach { option ->
                        val selected = option.id in chosen
                        FilterChip(
                            selected = selected,
                            enabled = option.available && option.id != StatusBadges.HEALTH,
                            onClick = {
                                val next = chosen.toMutableList()
                                if (selected) next.remove(option.id) else if (next.size < StatusBadges.MAX) next.add(option.id)
                                state.statusBadges.clear()
                                state.statusBadges.addAll(next)
                                state.invalidate()
                            },
                            label = { Text(option.badge.title) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Greyed out: not true for this car, or a tick you didn't give.", style = GetAutoSureType.Caption)
        }
    }
}

@Composable
private fun PhotosStep(
    state: DealerFlowState,
    photoViewModel: PublishReportViewModel,
    result: ScanResult?,
    rcState: PurchaseRcState,
    onSetUpDealerProfile: () -> Unit,
    onBuyPack: () -> Unit,
    onViewPdf: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DealerProfileStore(context) }
    val dealer = remember { store.load() }

    Text("The front photo is required — it's the cover of the report and the Status picture. Tap \"Remove background\" to put it on a grey studio floor.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Hide number plate", style = GetAutoSureType.Body)
            Text("Blanks the plate in cleaned photos, so the full number isn't public on Status.", style = GetAutoSureType.Caption)
        }
        Switch(
            checked = state.hidePlate,
            onCheckedChange = {
                state.hidePlate = it
                state.photos.forEach { slot -> slot.processedPhoto = null; slot.error = null }
            }
        )
    }
    Spacer(Modifier.height(8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            state.photos.forEachIndexed { i, slot ->
                ExteriorPhotoSlotRow(slot, photoViewModel, state.photoStyle, state.hidePlate)
                if (i == 0 && slot.originalUri == null) Text("Required", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent))
                if (i != state.photos.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = GetAutoSureColors.SurfaceRaised)
            }
        }
    }
    // Any photo change makes an already-made report stale.
    LaunchedEffect(state.photos.map { it.originalUri to it.processedPhoto }) { state.invalidate() }

    Spacer(Modifier.height(12.dp))
    if (!state.showPrice) {
        Text(
            "+ Add your price (optional)",
            style = GetAutoSureType.Body.copy(color = GetAutoSureColors.TextSecondary),
            modifier = Modifier.clickable { state.showPrice = true }.padding(vertical = 6.dp)
        )
    } else {
        OutlinedTextField(
            value = state.price,
            onValueChange = { state.price = it.filter(Char::isDigit).take(9); state.invalidate() },
            label = { Text("Your price (₹)") },
            supportingText = { Text("Leave empty to keep the price off the report.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(Modifier.height(16.dp))
    if (dealer == null) {
        Text("Set up your dealer profile first — the report carries your name, logo and numbers.", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSetUpDealerProfile, modifier = Modifier.fillMaxWidth()) { Text("Set up dealer profile") }
        return
    }
    if (result == null) {
        Text("Scan the car first (step 1).", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
        return
    }
    val hasFront = state.photos.first().originalUri != null

    // What can go on the Status picture, worked out from this scan, the RTO record and the dealer's ticks.
    val badgeOptions = remember(result, rcState, state.dashboardKm, state.inspection) {
        val rc = (rcState as? PurchaseRcState.Found)?.result
        val report = PrePurchaseReportBuilder.build(
            findings = result.findings, healthScore = result.health.score, readiness = result.readiness,
            snapshot = result.snapshot, vin = result.vehicleId.vin, dashboardKm = state.dashboardKm.toDoubleOrNull(),
            rc = rc, registration = rc?.registrationNumber ?: state.plate.ifBlank { null },
            exShowroomPriceInr = null, buyerEnteredYear = null, currentYear = LocalDate.now().year
        )
        StatusBadges.options(DealerSaleSummaryBuilder.build(result, report, LocalDate.now()), report, state.inspection)
    }
    StatusBadgePicker(badgeOptions, state)
    Spacer(Modifier.height(16.dp))

    Text("Free for now — packs start when payments open.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(6.dp))
    Button(
        onClick = {
            state.generating = true
            scope.launch {
                try {
                    val (input, summary, publishRequest) = withContext(Dispatchers.IO) {
                        val logo = if (store.hasLogo()) android.graphics.BitmapFactory.decodeFile(store.logoFile.absolutePath) else null
                        val rc = (rcState as? PurchaseRcState.Found)?.result
                        val report = PrePurchaseReportBuilder.build(
                            findings = result.findings, healthScore = result.health.score, readiness = result.readiness,
                            snapshot = result.snapshot, vin = result.vehicleId.vin, dashboardKm = state.dashboardKm.toDoubleOrNull(),
                            rc = rc, registration = rc?.registrationNumber ?: state.plate.ifBlank { null },
                            exShowroomPriceInr = null, buyerEnteredYear = null, currentYear = LocalDate.now().year
                        )
                        val photos = state.photos.mapNotNull { slot ->
                            slot.processedPhoto?.let { p -> decodeBase64Bitmap(p.base64)?.let { ReportPhoto(slot.label, it, backgroundCleaned = true) } }
                                ?: slot.originalUri?.let { uri -> encodeUriAsPhoto(context, uri)?.let { decodeBase64Bitmap(it.base64) }?.let { ReportPhoto(slot.label, it, backgroundCleaned = false) } }
                        }
                        val input = DealerSaleReportInput(result, report, dealer, logo, state.inspection, photos, state.price.toDoubleOrNull())
                        val summary = DealerSaleSummaryBuilder.build(result, report, LocalDate.now())
                        // The same report for its web page: cleaned photos where there are some, originals otherwise.
                        // Gemini returns big PNGs; a 1280 px JPEG keeps six photos well inside the request limit.
                        val uploads = photos.map { photo ->
                            val scale = minOf(1f, 1280f / maxOf(photo.bitmap.width, photo.bitmap.height))
                            val small = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(photo.bitmap, (photo.bitmap.width * scale).toInt(), (photo.bitmap.height * scale).toInt(), true) else photo.bitmap
                            val bytes = java.io.ByteArrayOutputStream().apply { small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, this) }.toByteArray()
                            PhotoUpload("${photo.label.lowercase().replace(' ', '-')}.jpg", "image/jpeg", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                        }
                        val logoUpload = if (store.hasLogo()) PhotoUpload("logo.png", "image/png", android.util.Base64.encodeToString(store.logoFile.readBytes(), android.util.Base64.NO_WRAP)) else null
                        val request = SaleReportPublishRequest(
                            dealerName = dealer.name, dealerTagline = dealer.tagline, dealerPhone = dealer.phones.joinToString(" / "),
                            dealerLocation = dealer.location, dealerLogo = logoUpload,
                            make = CarNameFormatter.brand(rc?.make, rc?.model), model = CarNameFormatter.model(rc?.make, rc?.model, rc?.fuelType),
                            variant = summary.variant, year = summary.year, fuelType = summary.fuel, transmission = summary.transmission,
                            odometerKm = summary.km?.toInt(), ownership = summary.ownerLabel, registrationNo = summary.registration,
                            healthSummary = saleHealthSummaryJson(result, report, summary, rc, LocalDate.now()),
                            dealerCondition = saleDealerConditionJson(state.inspection, state.price.toDoubleOrNull()),
                            photos = uploads,
                            scannedAtIso = java.time.Instant.ofEpochMilli(result.timestampMillis).toString()
                        )
                        Triple(input, summary, request)
                    }
                    // Publish the web page first: the PDF carries its QR code and link, and the Status
                    // text its link (sharing before it came back used to fall back to the Maps link).
                    // The PDF and Status still work if this fails (no network, not signed in).
                    val publishResult = photoViewModel.publishSaleReport(context, publishRequest)
                    // Out of credits: the server made nothing, so neither does the app — no free PDF.
                    if ((publishResult.exceptionOrNull() as? DealerReportHttpException)?.code == "no_credits") {
                        state.outOfCredits = true
                        return@launch
                    }
                    val published = publishResult.fold(
                        onSuccess = { state.webUrl = it.url; it },
                        onFailure = {
                            android.util.Log.w("DealerPublish", "failed", it)
                            state.webError = "The web page couldn't be made (${it.message}). The PDF and Status still work."
                            null
                        }
                    )
                    val (pdfFile, statusFile) = withContext(Dispatchers.IO) {
                        val pdf = DealerSaleReportPdfGenerator.generate(context, input.copy(webUrl = published?.url, webSlug = published?.slug))
                        val options = StatusBadges.options(summary, input.report, state.inspection)
                        val badges = StatusBadges.chosen(options, state.statusBadges.ifEmpty { StatusBadges.defaults(options) })
                        val image = DealerStatusImage.render(summary, dealer, input.dealerLogo, input.photos.firstOrNull(), result.timestampMillis, input.priceInr, badges)
                        pdf to DealerStatusImage.save(context, image, "status-" + (published?.slug ?: DealerSaleReportPdfGenerator.reportId(result.timestampMillis)))
                    }
                    state.pdf = pdfFile
                    state.status = statusFile
                    // Saved to the dealer's account for "My reports", so it can be sent again from any phone.
                    published?.let { report ->
                        val bytes = withContext(Dispatchers.IO) { pdfFile.readBytes() to statusFile.readBytes() }
                        photoViewModel.saveOutputs(context, report.slug, bytes.first, bytes.second)
                            .onFailure { android.util.Log.w("DealerPublish", "saving PDF/Status to the account failed", it) }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Couldn't make the report: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    state.generating = false
                }
            }
        },
        enabled = hasFront && !state.generating,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(if (state.generating) "Making report and web page…" else if (!hasFront) "Add the front photo first" else "Make report") }

    if (state.outOfCredits) {
        Spacer(Modifier.height(12.dp))
        Text("You've used all your report credits. Buy a pack to make this report — your scan, photos and ratings stay here.", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Watch))
        Spacer(Modifier.height(8.dp))
        Button(onClick = onBuyPack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Buy a pack") }
    }

    val pdf = state.pdf
    val status = state.status
    if (pdf != null && status != null) {
        Spacer(Modifier.height(16.dp))
        Text("Ready to share", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        state.webUrl?.let { url ->
            Text("Web page: $url", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent),
                modifier = Modifier.clickable { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) })
            Spacer(Modifier.height(8.dp))
        }
        state.webError?.let {
            Text(it, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch))
            Spacer(Modifier.height(8.dp))
        }
        // Order: check the PDF, send it, then post the Status last.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onViewPdf(pdf) }, modifier = Modifier.weight(1f)) { Text("View PDF") }
            Button(onClick = { context.startActivity(sharePdfIntent(context, pdf)) }, modifier = Modifier.weight(1f)) { Text("Send PDF") }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { DealerStatusImage.shareToWhatsApp(context, status, statusCaption(state.webUrl, dealer.mapsUrl, result, rcState)) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Share to WhatsApp Status") }
        Text("Opens WhatsApp — tap \"My status\". Send the PDF to anyone who replies.", style = GetAutoSureType.Caption)
    }
}

/**
 * The text under the Status picture, e.g. "Kia Seltos · Health 100/100" and the report link. No phone
 * number: it's already in the picture, and the Status comes from the dealer's own number.
 */
private fun statusCaption(webUrl: String?, mapsUrl: String?, result: ScanResult, rcState: PurchaseRcState): String {
    val rc = (rcState as? PurchaseRcState.Found)?.result
    val line = listOfNotNull(
        CarNameFormatter.carName(rc?.make, rc?.model, rc?.fuelType),
        result.health.score?.let { "Health $it/100" }
    ).joinToString(" · ")
    // A link in a Status caption can be tapped: the full web report when it was published (it has
    // the map and a WhatsApp button too), else the showroom on Google Maps.
    return when {
        webUrl != null -> "$line\nFull report: $webUrl"
        mapsUrl != null -> "$line\n📍 $mapsUrl"
        else -> line
    }
}
