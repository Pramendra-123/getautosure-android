package com.enginesense.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.enginesense.app.dealerreport.MyReport
import com.enginesense.app.dealerreport.MyReports
import com.enginesense.app.dealerreport.PublishReportViewModel
import com.enginesense.app.dealerreport.priceLabel
import com.enginesense.app.report.DealerStatusImage
import com.enginesense.app.report.sharePdfIntent
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// "My reports" (brand/dealer-saathi/my-reports-mockup.png): every car a dealer has made a report
// for, saved to their account on the server (list_reports), so they can send the link, PDF or
// Status again any time, and see how many credits they have left.

private val Raised = GetAutoSureColors.SurfaceRaised
private val StudioGrey = Color(0xFFD9DBDE)
private val WhatsAppGreen = Color(0xFF128C4A) // darker WhatsApp green so it reads on the light pill
private val StatusBlue = Color(0xFF1D5FB8)

private sealed interface MyReportsState {
    data object Loading : MyReportsState
    data class Loaded(val data: MyReports) : MyReportsState
    data class Failed(val message: String) : MyReportsState
}

@Composable
private fun rememberMyReports(): Pair<MyReportsState, () -> Unit> {
    val context = LocalContext.current
    val vm: PublishReportViewModel = viewModel()
    var state by remember { mutableStateOf<MyReportsState>(MyReportsState.Loading) }
    var reloads by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloads) {
        state = vm.listMyReports(context).fold(
            onSuccess = { MyReportsState.Loaded(it) },
            onFailure = { MyReportsState.Failed("Couldn't load your reports. Check the internet and sign-in, then try again.") }
        )
    }
    return state to { reloads++ }
}

/** "8 reports made · 22 credits left": on Dealer Saathi and at the start of a new report. */
@Composable
fun MyReportsCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val (state, _) = rememberMyReports()
    val data = (state as? MyReportsState.Loaded)?.data
    Column(
        modifier.fillMaxWidth()
            .background(GetAutoSureColors.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, GetAutoSureColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Text("My reports", color = GetAutoSureColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text("Every car you've made a report for", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CountTile(data?.made?.toString() ?: "…", "reports made", GetAutoSureColors.TextPrimary, Modifier.weight(1f))
            CreditsTile(data, Modifier.weight(1f))
            Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open →", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CountTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(Raised, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, style = GetAutoSureType.Caption, maxLines = 1)
    }
}

/** While payments aren't open, a dealer with no credits makes reports free — say so, not "0 left". */
@Composable
private fun CreditsTile(data: MyReports?, modifier: Modifier = Modifier) = when {
    data == null -> CountTile("…", "credits left", GetAutoSureColors.Good, modifier)
    data.credits <= 0 && !data.paymentsLive -> CountTile("Free", "for now", GetAutoSureColors.Good, modifier)
    else -> CountTile(data.credits.toString(), "credits left", if (data.credits > 0) GetAutoSureColors.Good else GetAutoSureColors.Attention, modifier)
}

@Composable
fun MyReportsScreen(onBuy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: PublishReportViewModel = viewModel()
    val (state, reload) = rememberMyReports()
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var soldDialog by remember { mutableStateOf<MyReport?>(null) }

    Column(Modifier.fillMaxSize().background(GetAutoSureColors.Background).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("My reports", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(12.dp))
        when (state) {
            MyReportsState.Loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GetAutoSureColors.Accent)
            }
            is MyReportsState.Failed -> {
                Text(state.message, style = GetAutoSureType.Body)
                TextButton(onClick = reload) { Text("Try again") }
            }
            is MyReportsState.Loaded -> {
                val data = state.data
                Row(
                    Modifier.fillMaxWidth().background(GetAutoSureColors.Surface, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Stat(data.made.toString(), "reports made", GetAutoSureColors.TextPrimary, Modifier.weight(1f))
                    Stat(data.thisMonth.toString(), "this month", GetAutoSureColors.TextPrimary, Modifier.weight(1f))
                    if (data.credits <= 0 && !data.paymentsLive) Stat("Free", "for now", GetAutoSureColors.Good, Modifier.weight(1f))
                    else Stat(data.credits.toString(), "credits left", if (data.credits > 0) GetAutoSureColors.Good else GetAutoSureColors.Attention, Modifier.weight(1f))
                    Button(onClick = onBuy) { Text("Buy", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search by car or number") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Raised),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                val shown = data.reports.filter { matches(it, query) }
                if (data.reports.isEmpty()) {
                    Text("No reports yet. Make your first one from Dealer Saathi → Create a report.", style = GetAutoSureType.Body, modifier = Modifier.padding(top = 16.dp))
                } else if (shown.isEmpty()) {
                    Text("No report matches \"$query\".", style = GetAutoSureType.Body, modifier = Modifier.padding(top = 16.dp))
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                    items(shown, key = { it.slug }) { report ->
                        ReportCard(
                            report = report,
                            busy = busy,
                            onOpen = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(report.url))) },
                            onLink = { shareLink(context, report) },
                            onPdf = {
                                val url = report.pdfUrl
                                if (url == null) notSaved(context, "PDF") else scope.launch {
                                    busy = report.slug + "pdf"
                                    download(context, url, "${report.slug}.pdf")?.let { context.startActivity(sharePdfIntent(context, it)) } ?: failedDownload(context)
                                    busy = null
                                }
                            },
                            onStatus = {
                                val url = report.statusUrl
                                if (url == null) notSaved(context, "Status picture") else scope.launch {
                                    busy = report.slug + "status"
                                    download(context, url, "status-${report.slug}.png")?.let { DealerStatusImage.shareToWhatsApp(context, it, "${report.title}\n${report.url}") } ?: failedDownload(context)
                                    busy = null
                                }
                            },
                            onToggleSold = { soldDialog = report }
                        )
                    }
                    item {
                        Text(
                            "Tap a card for the full report. Sending a report again is always free. Saved to your account, not just this phone.",
                            style = GetAutoSureType.Caption, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    soldDialog?.let { report ->
        AlertDialog(
            onDismissRequest = { soldDialog = null },
            title = { Text(if (report.sold) "Mark as not sold?" else "Mark as sold?") },
            text = { Text(if (report.sold) "${report.title} goes back to Live." else "${report.title} shows as Sold here. The report link keeps working for anyone who has it.") },
            confirmButton = {
                TextButton(onClick = {
                    soldDialog = null
                    scope.launch {
                        vm.setSold(context, report.slug, !report.sold).onFailure { Toast.makeText(context, "Couldn't update it. Try again.", Toast.LENGTH_LONG).show() }
                        reload()
                    }
                }) { Text(if (report.sold) "Not sold" else "Sold") }
            },
            dismissButton = { TextButton(onClick = { soldDialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, style = GetAutoSureType.Caption, maxLines = 1)
    }
}

@Composable
private fun ReportCard(
    report: MyReport,
    busy: String?,
    onOpen: () -> Unit,
    onLink: () -> Unit,
    onPdf: () -> Unit,
    onStatus: () -> Unit,
    onToggleSold: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(GetAutoSureColors.Surface, RoundedCornerShape(16.dp)).clickable(onClick = onOpen).padding(12.dp)) {
        Row {
            // The whole car, never cropped: fit inside the tile on the studio grey.
            AsyncImage(
                model = report.photoUrl, contentDescription = report.title, contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 96.dp, height = 72.dp).clip(RoundedCornerShape(10.dp)).background(StudioGrey)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(report.title, color = GetAutoSureColors.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(report.registrationNo?.let { plateShort(it) }, report.priceInr?.let { priceLabel(it) }).joinToString(" · "),
                    style = GetAutoSureType.Caption
                )
                Text(dateLabel(report.createdAtIso), style = GetAutoSureType.Caption)
            }
            Text(
                if (report.sold) "Sold" else "Live",
                color = if (report.sold) GetAutoSureColors.TextSecondary else GetAutoSureColors.Good,
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(if (report.sold) Raised else GetAutoSureColors.Good.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleSold)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill("Link", WhatsAppGreen, busy = false, onClick = onLink, modifier = Modifier.weight(1f))
            ActionPill("PDF", GetAutoSureColors.Accent, busy = busy == report.slug + "pdf", onClick = onPdf, modifier = Modifier.weight(1f))
            ActionPill("Status", StatusBlue, busy = busy == report.slug + "status", onClick = onStatus, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionPill(label: String, color: Color, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick, enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = Raised, contentColor = color, disabledContainerColor = Raised),
        contentPadding = PaddingValues(vertical = 6.dp),
        modifier = modifier.height(34.dp)
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = color)
        else Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

internal fun matches(report: MyReport, query: String): Boolean {
    val q = query.trim().replace(" ", "").lowercase()
    if (q.isEmpty()) return true
    return report.title.replace(" ", "").lowercase().contains(q) ||
        (report.registrationNo?.replace(" ", "")?.lowercase()?.contains(q) ?: false)
}

/** "KA03 •• 8216": enough for the dealer to tell their cars apart at a glance. */
internal fun plateShort(plate: String): String {
    val p = plate.replace(" ", "").uppercase()
    return if (p.length >= 8) "${p.take(4)} •• ${p.takeLast(4)}" else p
}

private val DATE = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

internal fun dateLabel(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DATE) }.getOrDefault("")

private fun shareLink(context: Context, report: MyReport) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${report.title} — vehicle health report: ${report.url}")
    }
    context.startActivity(Intent.createChooser(send, "Send report link"))
}

private fun notSaved(context: Context, what: String) =
    Toast.makeText(context, "This report's $what wasn't saved — it was made before saving started. Send the link instead.", Toast.LENGTH_LONG).show()

private fun failedDownload(context: Context) =
    Toast.makeText(context, "Couldn't download it. Check the internet and try again.", Toast.LENGTH_LONG).show()

/** Fetches a saved PDF or Status picture into the reports cache (where the FileProvider can share it). */
private suspend fun download(context: Context, url: String, name: String): File? = withContext(Dispatchers.IO) {
    runCatching {
        val file = File(File(context.cacheDir, "reports").apply { mkdirs() }, name)
        URL(url).openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        file
    }.getOrNull()
}
