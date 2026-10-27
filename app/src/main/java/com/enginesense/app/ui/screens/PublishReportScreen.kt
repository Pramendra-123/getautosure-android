package com.enginesense.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enginesense.app.ScanResult
import com.enginesense.app.dealerreport.*
import com.enginesense.app.report.VehicleReportInfo
import com.enginesense.app.storage.DealerProfileStore
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MAX_PHOTO_DIMENSION_PX = 1024
private val EXTERIOR_SLOT_LABELS = listOf("Front", "Rear", "Left Side", "Right Side")
private const val MAX_ADDITIONAL_PHOTOS = 4 // Exterior (4, fixed slots) + additional (this) = 8 total, same overall budget the backend already validates against.

private data class ConditionFieldState(var rating: String = "Good", var options: List<String> = emptyList())

/** One fixed exterior-view slot. [originalUri] is the untouched pick; [processedPhoto] only exists once the dealer has run — and kept — background removal, and publishing always falls back to the original when it's null (never processed, declined, or failed). */
private class ExteriorPhotoSlot(val label: String) {
    var originalUri by mutableStateOf<Uri?>(null)
    var processedPhoto by mutableStateOf<PhotoUpload?>(null)
    var processing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/**
 * Lets the dealer publish the current scan as a shareable web report (Phase A — see
 * supabase/functions/reports/index.ts). Reuses exactly the data [PdfReportActions] already
 * assembles for the PDF ([ScanResult] + [VehicleReportInfo] + the saved [DealerProfileStore]
 * profile) rather than collecting any of it twice.
 */
@Composable
fun PublishReportScreen(scanResult: ScanResult?, vehicleInfo: VehicleReportInfo) {
    val context = LocalContext.current
    val viewModel: PublishReportViewModel = viewModel()
    val publishState by viewModel.publishState.collectAsState()

    val dealerProfile = remember { DealerProfileStore(context).load() }
    val dealerStore = remember { DealerProfileStore(context) }

    val exterior = remember { ConditionFieldState() }
    val interior = remember { ConditionFieldState() }
    val tyres = remember { ConditionFieldState() }

    val exteriorSlots = remember { EXTERIOR_SLOT_LABELS.map { ExteriorPhotoSlot(it) } }
    val additionalPhotoUris = remember { mutableStateListOf<Uri>() }
    val pickAdditionalPhotos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val room = (MAX_ADDITIONAL_PHOTOS - additionalPhotoUris.size).coerceAtLeast(0)
        additionalPhotoUris.addAll(uris.take(room))
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Publish Web Report", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            "Creates a shareable link and QR code a buyer can open without installing anything — the same scan data already in your PDF report.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(20.dp))

        when (val state = publishState) {
            is PublishState.Success -> PublishSuccessCard(
                report = state.report,
                unpublished = state.unpublished,
                onUnpublish = { viewModel.unpublish(context, state.report) },
                onPublishAnother = { viewModel.reset() }
            )
            else -> {
                if (dealerProfile == null) {
                    Box(modifier = Modifier.background(GetAutoSureColors.Attention.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(14.dp)) {
                        Text(
                            "Set up your dealer profile first — a published report always carries your name and contact details.",
                            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }

                Text("Dealer-reported condition", style = GetAutoSureType.SectionTitle)
                Spacer(Modifier.height(4.dp))
                Text("Self-reported by you — always labeled as such on the report, never shown as an independent inspection.", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(10.dp))
                ConditionRow("Exterior", exterior, com.enginesense.app.dealerreport.DealerConditionOptions.exterior)
                Spacer(Modifier.height(10.dp))
                ConditionRow("Interior", interior, com.enginesense.app.dealerreport.DealerConditionOptions.interior)
                Spacer(Modifier.height(10.dp))
                ConditionRow("Tyres", tyres, com.enginesense.app.dealerreport.DealerConditionOptions.tyres)

                Spacer(Modifier.height(20.dp))
                Text("Exterior Photos", style = GetAutoSureType.SectionTitle)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Front, rear and both sides — background removed automatically so every listing looks consistent. You can keep the original instead if you prefer it.",
                    style = GetAutoSureType.Caption
                )
                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        exteriorSlots.forEachIndexed { index, slot ->
                            ExteriorPhotoSlotRow(slot, viewModel)
                            if (index != exteriorSlots.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = GetAutoSureColors.SurfaceRaised)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Additional Photos", style = GetAutoSureType.SectionTitle, modifier = Modifier.weight(1f))
                    Text("${additionalPhotoUris.size}/$MAX_ADDITIONAL_PHOTOS", style = GetAutoSureType.Caption)
                }
                Spacer(Modifier.height(4.dp))
                Text("Interior, dashboard, odometer — uploaded as-is, no processing.", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(additionalPhotoUris) { uri ->
                        PhotoThumbnail(uri) { additionalPhotoUris.remove(uri) }
                    }
                    item {
                        OutlinedButton(
                            onClick = { if (additionalPhotoUris.size < MAX_ADDITIONAL_PHOTOS) pickAdditionalPhotos.launch("image/*") },
                            enabled = additionalPhotoUris.size < MAX_ADDITIONAL_PHOTOS,
                            modifier = Modifier.size(84.dp)
                        ) { Text("+ Add") }
                    }
                }

                Spacer(Modifier.height(20.dp))
                if (state is PublishState.Error) {
                    Text(state.message, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = {
                        viewModel.publish(
                            uiContext = context,
                            dealerName = dealerProfile?.name ?: "",
                            dealerTagline = dealerProfile?.tagline ?: "",
                            dealerPhone = dealerProfile?.phone ?: "",
                            dealerLocation = dealerProfile?.location ?: "",
                            dealerLogo = if (dealerStore.hasLogo()) encodeFileAsPhoto(dealerStore.logoFile.absolutePath, "logo.png", "image/png") else null,
                            make = vehicleInfo.brand,
                            model = vehicleInfo.model,
                            variant = null,
                            year = vehicleInfo.year,
                            fuelType = vehicleInfo.fuelType,
                            transmission = null,
                            odometerKm = vehicleInfo.odometerKm,
                            ownership = null,
                            registrationNo = vehicleInfo.plate,
                            healthSummary = scanResult?.let { healthSummaryJson(it) } ?: org.json.JSONObject(),
                            dealerCondition = DealerConditionInput(
                                exterior = DealerConditionItem(exterior.rating, exterior.options),
                                interior = DealerConditionItem(interior.rating, interior.options),
                                tyres = DealerConditionItem(tyres.rating, tyres.options)
                            ),
                            photos = exteriorSlots.mapNotNull { slot ->
                                slot.processedPhoto ?: slot.originalUri?.let { encodeUriAsPhoto(context, it) }
                            } + additionalPhotoUris.mapNotNull { encodeUriAsPhoto(context, it) },
                            scannedAtIso = scanResult?.let { isoTimestamp(it.timestampMillis) }
                        )
                    },
                    enabled = state !is PublishState.Publishing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state is PublishState.Publishing) "Publishing…" else "Publish Report") }
            }
        }
    }
}

@Composable
private fun ConditionRow(label: String, field: ConditionFieldState, options: List<String>) {
    var rating by remember { mutableStateOf(field.rating) }
    val selectedOptions = remember { mutableStateListOf<String>().apply { addAll(field.options) } }

    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                listOf("Good", "Fair", "Poor").forEach { option ->
                    val selected = option == rating
                    Text(
                        option,
                        style = GetAutoSureType.Caption.copy(
                            color = if (selected) GetAutoSureColors.Good else GetAutoSureColors.TextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        ),
                        modifier = Modifier.padding(start = 10.dp).clickable { rating = option; field.rating = option }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick any that apply — not typed, so every report uses the same wording.",
                style = GetAutoSureType.Caption
            )
            Spacer(Modifier.height(4.dp))
            options.forEach { option ->
                val checked = option in selectedOptions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (checked) selectedOptions.remove(option) else selectedOptions.add(option)
                        field.options = selectedOptions.toList()
                    }
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Text(option, style = GetAutoSureType.Body)
                }
            }
        }
    }
}

@Composable
private fun ExteriorPhotoSlotRow(slot: ExteriorPhotoSlot, viewModel: PublishReportViewModel) {
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            slot.originalUri = uri
            slot.processedPhoto = null
            slot.error = null
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(84.dp).clickable { pickPhoto.launch("image/*") }) {
            val bitmap = remember(slot.originalUri, slot.processedPhoto) {
                slot.processedPhoto?.let { decodeBase64Bitmap(it.base64) }
                    ?: slot.originalUri?.let { uri ->
                        runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
                    }
            }
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = slot.label,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(GetAutoSureColors.Surface, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("+", style = GetAutoSureType.SectionTitle) }
            }
            if (slot.originalUri != null) {
                Text(
                    "✕",
                    style = GetAutoSureType.Caption.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(GetAutoSureColors.Attention, RoundedCornerShape(bottomStart = 8.dp, topEnd = 8.dp))
                        .clickable {
                            slot.originalUri = null
                            slot.processedPhoto = null
                            slot.error = null
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(slot.label, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
            when {
                slot.originalUri == null -> Text("Tap to add a photo", style = GetAutoSureType.Caption)
                slot.processing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Removing background…", style = GetAutoSureType.Caption)
                }
                slot.processedPhoto != null -> {
                    Text("Background removed", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Good))
                    Text(
                        "Use original instead",
                        style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextSecondary),
                        modifier = Modifier.clickable { slot.processedPhoto = null }
                    )
                }
                else -> {
                    slot.error?.let { Text(it, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention)) }
                    Text(
                        "Remove background",
                        style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Good, fontWeight = FontWeight.Bold),
                        modifier = Modifier.clickable {
                            val uri = slot.originalUri ?: return@clickable
                            val photo = encodeUriAsPhoto(context, uri)
                            if (photo == null) {
                                slot.error = "Couldn't read this photo"
                                return@clickable
                            }
                            slot.processing = true
                            slot.error = null
                            viewModel.removeBackground(context, photo) { result ->
                                slot.processing = false
                                result.fold(
                                    onSuccess = { slot.processedPhoto = it },
                                    onFailure = { slot.error = "Background removal failed — kept the original photo" }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun decodeBase64Bitmap(base64: String): Bitmap? = runCatching {
    val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

@Composable
private fun PhotoThumbnail(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    Box(modifier = Modifier.size(84.dp)) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(GetAutoSureColors.Surface, RoundedCornerShape(8.dp)))
        }
        Text(
            "✕",
            style = GetAutoSureType.Caption.copy(color = Color.White, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(GetAutoSureColors.Attention, RoundedCornerShape(bottomStart = 8.dp, topEnd = 8.dp))
                .clickable(onClick = onRemove)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PublishSuccessCard(
    report: PublishedReport,
    unpublished: Boolean,
    onUnpublish: () -> Unit,
    onPublishAnother: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val qrBitmap = remember(report.url) { generateQrBitmap(report.url, 480) }
    var copied by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (unpublished) GetAutoSureColors.Attention.copy(alpha = 0.12f) else GetAutoSureColors.Good.copy(alpha = 0.12f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (unpublished) "Report unpublished" else "Report published",
                    style = GetAutoSureType.SectionTitle.copy(color = if (unpublished) GetAutoSureColors.Attention else GetAutoSureColors.Good)
                )
                if (!unpublished) {
                    Spacer(Modifier.height(12.dp))
                    if (qrBitmap != null) {
                        Image(qrBitmap.asImageBitmap(), contentDescription = "QR code for ${report.url}", modifier = Modifier.size(200.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(report.url, style = GetAutoSureType.Body)
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text("No longer visible to buyers at ${report.url}.", style = GetAutoSureType.Caption)
                }
            }
        }

        if (!unpublished) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(report.url))
                        copied = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (copied) "Copied" else "Copy link") }

                Button(
                    onClick = {
                        val text = "Check out this vehicle report: ${report.url}"
                        val whatsappIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=" + Uri.encode(text)))
                        try {
                            context.startActivity(whatsappIntent)
                        } catch (e: android.content.ActivityNotFoundException) {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share report link"))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("WhatsApp") }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, report.url)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share report link"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share link (other apps)") }

            Spacer(Modifier.height(10.dp))
            Text(
                "Unpublish",
                style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention),
                modifier = Modifier.clickable(onClick = onUnpublish)
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(16.dp))
        }

        OutlinedButton(onClick = onPublishAnother, modifier = Modifier.fillMaxWidth()) { Text("Publish another") }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bitmap
}.getOrNull()

/** Downscales, JPEG-compresses and base64-encodes a picked photo — same budget-conscious approach as DealerProfileScreen's logo handling, just for a larger "listing photo" size instead of a small logo. */
private fun encodeUriAsPhoto(context: android.content.Context, uri: Uri): PhotoUpload? = runCatching {
    val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    val scaled = downscaleBitmap(source, MAX_PHOTO_DIMENSION_PX)
    val bytes = ByteArrayOutputStream().apply { scaled.compress(Bitmap.CompressFormat.JPEG, 80, this) }.toByteArray()
    PhotoUpload(filename = "photo.jpg", contentType = "image/jpeg", base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
}.getOrNull()

private fun encodeFileAsPhoto(path: String, filename: String, contentType: String): PhotoUpload? = runCatching {
    val bytes = java.io.File(path).readBytes()
    PhotoUpload(filename = filename, contentType = contentType, base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
}.getOrNull()

private fun downscaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
    val longerEdge = maxOf(source.width, source.height)
    if (longerEdge <= maxDimension) return source
    val scale = maxDimension.toFloat() / longerEdge
    val newW = (source.width * scale).toInt().coerceAtLeast(1)
    val newH = (source.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, newW, newH, true)
}

private fun isoTimestamp(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMillis))
