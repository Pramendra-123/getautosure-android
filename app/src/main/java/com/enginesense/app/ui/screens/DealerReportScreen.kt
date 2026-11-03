package com.enginesense.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.dealerreport.DealerConditionInput
import com.enginesense.app.dealerreport.DealerConditionItem
import com.enginesense.app.dealerreport.DealerConditionOptions
import com.enginesense.app.dealerreport.PublishReportViewModel
import com.enginesense.app.report.DealerReportExtras
import com.enginesense.app.report.PrePurchaseReportPdfGenerator
import com.enginesense.app.report.ReportPhoto
import com.enginesense.app.report.sharePdfIntent
import com.enginesense.app.storage.DealerProfileStore
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.DealerPrice
import com.enginesense.app.vehicle.PrePurchaseAnalyzer
import com.enginesense.app.vehicle.PrePurchaseReportBuilder
import com.enginesense.app.vehiclelookup.PurchaseRcCheckViewModel
import com.enginesense.app.vehiclelookup.PurchaseRcState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** How much each "Drop"/"Add" tap moves the dealer's price range. */
private const val PRICE_STEP_INR = 10_000.0

/**
 * Dealer Sathi's dealer report: the buyer's pre-purchase PDF for a car on the dealer's lot,
 * branded with their profile, with the dealer's own asking price beside GetAutoSure's estimate.
 * [withPhotos] is the second option — it adds the car's photos (backgrounds cleaned with Gemini,
 * the same as Publish Web Report) and the dealer's condition notes as a page after the summary.
 */
@Composable
fun DealerReportScreen(viewModel: GetAutoSureViewModel, withPhotos: Boolean, onSetUpDealerProfile: () -> Unit, onViewPdf: (java.io.File) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by viewModel.latestResult.collectAsState()
    val dealerStore = remember { DealerProfileStore(context) }
    val dealerProfile = remember { dealerStore.load() }

    val rcViewModel: PurchaseRcCheckViewModel = viewModel()
    val rcState by rcViewModel.state.collectAsState()
    var dashboardInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var yearInput by remember { mutableStateOf("") }
    var dealerLow by remember { mutableStateOf("") }
    var dealerHigh by remember { mutableStateOf("") }

    // Photo version only.
    val publishViewModel: PublishReportViewModel = viewModel()
    val exterior = remember { ConditionFieldState() }
    val interior = remember { ConditionFieldState() }
    val tyres = remember { ConditionFieldState() }
    val exteriorSlots = remember { EXTERIOR_SLOT_LABELS.map { ExteriorPhotoSlot(it) } }
    val additionalPhotoUris = remember { mutableStateListOf<Uri>() }
    val pickAdditionalPhotos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        additionalPhotoUris.addAll(uris.take((MAX_ADDITIONAL_PHOTOS - additionalPhotoUris.size).coerceAtLeast(0)))
    }

    var file by remember { mutableStateOf<java.io.File?>(null) }
    var generating by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(if (withPhotos) "Dealer report with photos" else "Dealer report", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            if (withPhotos) "Your branded inspection PDF for this car, with its photos (backgrounds cleaned by Gemini) and your condition notes."
            else "Your branded inspection PDF for this car — scan results, RTO record and your price.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(16.dp))

        if (dealerProfile == null) {
            Box(modifier = Modifier.fillMaxWidth().background(GetAutoSureColors.Attention.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(14.dp)) {
                Text(
                    "Set up your dealer profile first — the report carries your name, logo and contact details.",
                    style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention)
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onSetUpDealerProfile, modifier = Modifier.fillMaxWidth()) { Text("Set up dealer profile") }
            return@Column
        }

        val r = result
        if (r == null) {
            Text("Scan this car first — the report is built from its scan.", style = GetAutoSureType.Body)
            return@Column
        }

        val insight = remember(r) { PrePurchaseAnalyzer.analyze(r.findings, r.health.score, r.readiness, r.snapshot) }
        val rc = (rcState as? PurchaseRcState.Found)?.result
        val baseReport = PrePurchaseReportBuilder.build(
            findings = r.findings, healthScore = r.health.score, readiness = r.readiness, snapshot = r.snapshot,
            vin = r.vehicleId.vin, dashboardKm = dashboardInput.toDoubleOrNull(), rc = rc, registration = rc?.registrationNumber,
            exShowroomPriceInr = priceInput.toDoubleOrNull(), buyerEnteredYear = yearInput.toIntOrNull(),
            currentYear = Calendar.getInstance().get(Calendar.YEAR)
        )
        val dealerPrice = DealerPrice.from(dealerLow.toDoubleOrNull(), dealerHigh.toDoubleOrNull(), dealerProfile.name)
        val report = baseReport.copy(dealerPrice = dealerPrice)

        SectionHeader("The car", "Check its RTO record and dashboard km — both go in the report")
        VehicleIdentityCard(r.vehicleId.vin, isDemo = r.isDemo)
        OdometerCheckCard(insight.ecuOdometerKm, insight.odometerSupported, dashboardInput) { dashboardInput = it }

        SectionHeader("Price", "GetAutoSure's estimate, and your own price — drop it or add to it")
        Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                NumberField(priceInput, "Ex-showroom price when new (₹)") { priceInput = it }
                if (rc?.manufacturingMonth == null) {
                    Spacer(Modifier.height(8.dp))
                    NumberField(yearInput, "Year of manufacture") { yearInput = it.take(4) }
                }
                Spacer(Modifier.height(12.dp))
                val estimate = baseReport.value?.fair
                Text(
                    estimate?.let { "GetAutoSure estimate: ${inr(it.lowEstimate)} – ${inr(it.highEstimate)}" }
                        ?: (baseReport.valueMissingReason ?: "No estimate yet."),
                    style = GetAutoSureType.Body
                )
                Spacer(Modifier.height(12.dp))
                Text("Your price", style = GetAutoSureType.Body)
                Text("Shown on the report as your price, next to the estimate. Leave it empty to show only the estimate.", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { NumberField(dealerLow, "From (₹)") { dealerLow = it } }
                    Box(Modifier.weight(1f)) { NumberField(dealerHigh, "To (₹)") { dealerHigh = it } }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (estimate != null) {
                        OutlinedButton(
                            onClick = {
                                dealerLow = roundToThousand(estimate.lowEstimate)
                                dealerHigh = roundToThousand(estimate.highEstimate)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Use estimate") }
                    }
                    OutlinedButton(
                        onClick = {
                            dealerLow.toDoubleOrNull()?.let { dealerLow = roundToThousand((it - PRICE_STEP_INR).coerceAtLeast(PRICE_STEP_INR)) }
                            dealerHigh.toDoubleOrNull()?.let { dealerHigh = roundToThousand((it - PRICE_STEP_INR).coerceAtLeast(PRICE_STEP_INR)) }
                        },
                        enabled = dealerPrice != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("− ₹10k") }
                    OutlinedButton(
                        onClick = {
                            dealerLow.toDoubleOrNull()?.let { dealerLow = roundToThousand(it + PRICE_STEP_INR) }
                            dealerHigh.toDoubleOrNull()?.let { dealerHigh = roundToThousand(it + PRICE_STEP_INR) }
                        },
                        enabled = dealerPrice != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("+ ₹10k") }
                }
                dealerPrice?.comparedTo(estimate)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextPrimary))
                }
            }
        }

        if (withPhotos) {
            SectionHeader("Photos", "Front, rear and both sides — tap \"Remove background\" to clean each with Gemini")
            Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    exteriorSlots.forEachIndexed { index, slot ->
                        ExteriorPhotoSlotRow(slot, publishViewModel)
                        if (index != exteriorSlots.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = GetAutoSureColors.SurfaceRaised)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("More photos (interior, dashboard)", style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
                Text("${additionalPhotoUris.size}/$MAX_ADDITIONAL_PHOTOS", style = GetAutoSureType.Caption)
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(additionalPhotoUris) { uri -> PhotoThumbnail(uri) { additionalPhotoUris.remove(uri) } }
                item {
                    OutlinedButton(
                        onClick = { pickAdditionalPhotos.launch("image/*") },
                        enabled = additionalPhotoUris.size < MAX_ADDITIONAL_PHOTOS,
                        modifier = Modifier.size(84.dp)
                    ) { Text("+ Add") }
                }
            }

            SectionHeader("Condition", "Your own notes — the report labels them as reported by you")
            ConditionRow("Exterior", exterior, DealerConditionOptions.exterior)
            Spacer(Modifier.height(10.dp))
            ConditionRow("Interior", interior, DealerConditionOptions.interior)
            Spacer(Modifier.height(10.dp))
            ConditionRow("Tyres", tyres, DealerConditionOptions.tyres)
        }

        SectionHeader("Report")
        if (report.rc == null && !r.isDemo) {
            Text("Tip: check the RTO record above first so the report includes it.", style = GetAutoSureType.Caption)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                generating = true
                scope.launch {
                    try {
                        file = withContext(Dispatchers.IO) {
                            val logo = if (dealerStore.hasLogo()) android.graphics.BitmapFactory.decodeFile(dealerStore.logoFile.absolutePath) else null
                            val extras = if (!withPhotos) null else DealerReportExtras(
                                photos = exteriorSlots.mapNotNull { slot ->
                                    slot.processedPhoto?.let { p -> decodeBase64Bitmap(p.base64)?.let { ReportPhoto(slot.label, it, backgroundCleaned = true) } }
                                        ?: slot.originalUri?.let { uri ->
                                            encodeUriAsPhoto(context, uri)?.let { p -> decodeBase64Bitmap(p.base64) }?.let { ReportPhoto(slot.label, it, backgroundCleaned = false) }
                                        }
                                } + additionalPhotoUris.mapIndexedNotNull { i, uri ->
                                    encodeUriAsPhoto(context, uri)?.let { p -> decodeBase64Bitmap(p.base64) }?.let { ReportPhoto("Photo ${i + 1}", it, backgroundCleaned = false) }
                                },
                                condition = DealerConditionInput(
                                    exterior = DealerConditionItem(exterior.rating, exterior.options),
                                    interior = DealerConditionItem(interior.rating, interior.options),
                                    tyres = DealerConditionItem(tyres.rating, tyres.options)
                                )
                            )
                            PrePurchaseReportPdfGenerator.generate(context, r, report, dealerProfile, logo, extras)
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
        ) { Text(if (generating) "Making report…" else if (file == null) "Make dealer report (PDF)" else "Make it again with changes") }

        file?.let { current ->
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onViewPdf(current) }, modifier = Modifier.weight(1f)) { Text("View") }
                Button(onClick = { context.startActivity(sharePdfIntent(context, current)) }, modifier = Modifier.weight(1f)) { Text("Share") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumberField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(9)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun roundToThousand(value: Double): String = (Math.round(value / 1000.0) * 1000).toString()

private fun inr(value: Double): String = "₹" + "%,.0f".format(value)
