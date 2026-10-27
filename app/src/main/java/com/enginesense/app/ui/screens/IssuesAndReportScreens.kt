package com.enginesense.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.obd.Severity
import com.enginesense.app.report.VehicleReportInfo
import com.enginesense.app.report.VehicleReportPdfGenerator
import com.enginesense.app.ui.components.DemoDataBanner
import com.enginesense.app.ui.components.SeverityBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.CategoryState
import com.enginesense.app.vehicle.DiagnosticFinding
import com.enginesense.app.vehicle.ScannedSystemGroup
import com.enginesense.app.vehicle.scannedSystemGroupFor
import com.enginesense.app.ui.components.HealthScoreRing

/** Companion checklist to the findings list above — which standard DTC groups this scan found something in, not a second score. */
@Composable
private fun ScannedSystemsCard(findings: List<DiagnosticFinding>) {
    val codes = findings.flatMap { it.relatedDtcCodes }

    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Scanned systems", style = GetAutoSureType.Body)
            Spacer(Modifier.height(2.dp))
            Text(
                "Standard diagnostic groups by code range — not a second health score, just which groups this scan found something in.",
                style = GetAutoSureType.Caption
            )
            Spacer(Modifier.height(10.dp))
            ScannedSystemGroup.entries.forEachIndexed { index, group ->
                val hitCount = codes.count { scannedSystemGroupFor(it) == group }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.label, style = GetAutoSureType.Body.copy(fontSize = 13.sp))
                        Text(group.codeRange, style = GetAutoSureType.Caption.copy(fontSize = 10.sp))
                    }
                    if (hitCount > 0) {
                        Text(
                            "$hitCount code${if (hitCount == 1) "" else "s"}",
                            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch, fontSize = 12.sp)
                        )
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = "No codes in this group", tint = GetAutoSureColors.Good, modifier = Modifier.size(18.dp))
                    }
                }
                if (index != ScannedSystemGroup.entries.lastIndex) {
                    HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
                }
            }
        }
    }
}

private fun severityColor(severity: Severity) = when (severity) {
    Severity.CRITICAL -> GetAutoSureColors.Attention
    Severity.MODERATE -> GetAutoSureColors.Watch
    Severity.MINOR -> GetAutoSureColors.Watch
    Severity.INFO -> GetAutoSureColors.Unavailable
}

private fun urgencyText(severity: Severity) = when (severity) {
    Severity.CRITICAL -> "High urgency"
    Severity.MODERATE -> "Medium urgency"
    Severity.MINOR -> "Low urgency"
    Severity.INFO -> "No urgency"
}

@Composable
fun IssuesScreen(viewModel: GetAutoSureViewModel, onOpenIssue: (String) -> Unit) {
    val result by viewModel.latestResult.collectAsState()
    val findings = result?.findings.orEmpty()
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Issues detected", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text("${findings.size} found in your last scan", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(16.dp))

        if (result?.isDemo == true) {
            DemoDataBanner()
            Spacer(Modifier.height(12.dp))
        }

        if (findings.isEmpty()) {
            Text("Nothing to show — your last scan found no issues.", style = GetAutoSureType.Body)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f, fill = false)) {
                items(findings) { finding -> IssueCard(finding, onClick = { onOpenIssue(finding.id) }) }
            }
        }

        // Only offered after a real/test-mode scan with actual stored codes — a demo history
        // entry has no live connection to clear anything on, and there's nothing to clear if
        // the last scan found no DTCs.
        if (result?.isDemo == false && (result?.snapshot?.dtcs?.isNotEmpty() == true)) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showClearConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GetAutoSureColors.Attention),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear codes on vehicle") }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear trouble codes?") },
            text = {
                Text(
                    "This sends a real command to your car's computer to erase stored fault " +
                        "codes and turn off the check-engine light. It does not fix the " +
                        "underlying problem — if it's still happening, the code will likely " +
                        "come back. Some cars only accept this with the ignition on and the " +
                        "engine off. The final decision to actually reset is made by your " +
                        "car's control unit, not this app."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearDtcsAndRescan()
                }) { Text("Clear codes", color = GetAutoSureColors.Attention) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun IssueCard(finding: DiagnosticFinding, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(finding.title, style = GetAutoSureType.SectionTitle, modifier = Modifier.weight(1f))
                SeverityBadge(finding.severity.name, severityColor(finding.severity))
            }
            Spacer(Modifier.height(6.dp))
            Text(finding.plainLanguageSummary, style = GetAutoSureType.Body.copy(color = GetAutoSureColors.TextSecondary))
        }
    }
}

@Composable
fun IssueDetailScreen(viewModel: GetAutoSureViewModel, issueId: String) {
    val result by viewModel.latestResult.collectAsState()
    val finding = result?.findings?.firstOrNull { it.id == issueId }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        if (finding == null) {
            Text("This issue is no longer available — try running a new scan.", style = GetAutoSureType.Body)
            return@Column
        }

        Text(finding.title, style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(12.dp))
        SeverityBadge(finding.severity.name, severityColor(finding.severity))
        Spacer(Modifier.height(20.dp))

        DetailSection("What it means", finding.plainLanguageSummary)
        DetailSection("Why it matters", finding.severity.label)
        DetailSection("Recommended action", finding.recommendedAction)
        DetailSection("Urgency", urgencyText(finding.severity))

        if (finding.relatedDtcCodes.isNotEmpty() || finding.relatedPids.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("View technical details", style = GetAutoSureType.Caption)
            Spacer(Modifier.height(4.dp))
            if (finding.relatedDtcCodes.isNotEmpty()) {
                Text("DTC: ${finding.relatedDtcCodes.joinToString(", ")}", style = GetAutoSureType.Caption)
            }
            if (finding.relatedPids.isNotEmpty()) {
                Text("PIDs: ${finding.relatedPids.joinToString(", ") { it.displayName }}", style = GetAutoSureType.Caption)
            }
        }
    }
}

@Composable
private fun DetailSection(heading: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(heading, style = GetAutoSureType.Caption)
        Spacer(Modifier.height(2.dp))
        Text(body, style = GetAutoSureType.Body)
    }
}

@Composable
fun AiReportScreen(viewModel: GetAutoSureViewModel, onPublishReport: () -> Unit = {}) {
    val latestResult by viewModel.latestResult.collectAsState()
    val result = latestResult
    val report = result?.aiReport
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var generatingPdf by remember { mutableStateOf(false) }
    var reportFile by remember(result?.timestampMillis) { mutableStateOf<java.io.File?>(null) }

    val vehicleBrand by viewModel.vehicleBrand.collectAsState()
    val vehicleNickname by viewModel.vehicleNickname.collectAsState()
    val vehicleModel by viewModel.vehicleModel.collectAsState()
    val vehicleYear by viewModel.vehicleYear.collectAsState()
    val vehicleFuelType by viewModel.vehicleFuelType.collectAsState()
    val vehicleOdometerKm by viewModel.vehicleOdometerKm.collectAsState()
    val vehiclePlate by viewModel.vehiclePlate.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Advanced Report", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(4.dp))
        Text("A quick summary of this scan, then your full shareable PDF.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(16.dp))

        if (result == null || report == null) {
            Text("Run a scan first to get your report.", style = GetAutoSureType.Body)
            return@Column
        }

        if (result.isDemo) {
            DemoDataBanner()
            Spacer(Modifier.height(12.dp))
        }

        ReportSummaryCard(result)
        Spacer(Modifier.height(16.dp))

        Text(report.headline, style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(6.dp))
        Text(report.summary, style = GetAutoSureType.Body.copy(color = GetAutoSureColors.TextSecondary))
        Spacer(Modifier.height(16.dp))

        report.sections.forEach { section ->
            Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(section.heading, style = GetAutoSureType.SectionTitle)
                    Spacer(Modifier.height(6.dp))
                    Text(section.body, style = GetAutoSureType.Body)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Text(report.disclaimer, style = GetAutoSureType.Caption)
        Spacer(Modifier.height(20.dp))

        PdfReportActions(
            result = result, context = context, scope = scope,
            generatingPdf = generatingPdf, onGeneratingChange = { generatingPdf = it },
            reportFile = reportFile, onReportFileChange = { reportFile = it },
            vehicleInfo = VehicleReportInfo(
                brand = vehicleBrand, nickname = vehicleNickname, model = vehicleModel,
                year = vehicleYear, fuelType = vehicleFuelType,
                odometerKm = vehicleOdometerKm, plate = vehiclePlate
            )
        )

        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onPublishReport, modifier = Modifier.fillMaxWidth()) {
            Text("Publish Web Report (Dealer Sathi)")
        }

        RawLogSection(viewModel)
    }
}

/** The score, state and finding counts for this scan — the context you'd want before asking for a PDF of it. */
@Composable
private fun ReportSummaryCard(result: com.enginesense.app.ScanResult) {
    val worstState = when {
        result.health.categories.any { it.state == CategoryState.ATTENTION } -> CategoryState.ATTENTION
        result.health.categories.any { it.state == CategoryState.WATCH } -> CategoryState.WATCH
        result.health.score == null -> CategoryState.UNAVAILABLE
        else -> CategoryState.GOOD
    }
    val stateColor = GetAutoSureColors.forCategoryState(worstState)
    val stateLabel = when (worstState) {
        CategoryState.GOOD -> "Good"
        CategoryState.WATCH -> "Watch"
        CategoryState.ATTENTION -> "Needs attention"
        CategoryState.UNAVAILABLE -> "No data"
    }
    val majorCount = result.findings.count { it.severity == Severity.CRITICAL }
    val watchCount = result.findings.count { it.severity == Severity.MODERATE || it.severity == Severity.MINOR }

    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (result.health.score != null) {
                HealthScoreRing(result.health.score, stateColor, diameter = 64.dp)
            } else {
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center
                ) { Text("No\ndata", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Unavailable), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stateLabel, style = GetAutoSureType.SectionTitle.copy(color = stateColor))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (result.findings.isEmpty()) "Nothing to report" else "$majorCount major · $watchCount to watch",
                    style = GetAutoSureType.Caption
                )
                Spacer(Modifier.height(4.dp))
                Text(result.profileLabel, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextSecondary))
            }
        }
    }
}

/**
 * Generates the report once and keeps it in [reportFile] so View/Save/Share all act on the
 * same file rather than regenerating per tap. API 26-28 needs a runtime storage permission
 * before [saveToDownloads]; API 29+ (scoped storage) needs none.
 */
@Composable
private fun PdfReportActions(
    result: com.enginesense.app.ScanResult?,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    generatingPdf: Boolean,
    onGeneratingChange: (Boolean) -> Unit,
    reportFile: java.io.File?,
    onReportFileChange: (java.io.File?) -> Unit,
    vehicleInfo: VehicleReportInfo
) {
    var pendingLegacySave by remember { mutableStateOf(false) }
    val legacyPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val file = reportFile
        if (granted && pendingLegacySave && file != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { com.enginesense.app.report.saveToDownloads(context, file) }
                Toast.makeText(context, if (saved) "Saved to Downloads" else "Couldn't save the report", Toast.LENGTH_LONG).show()
            }
        } else if (!granted) {
            Toast.makeText(context, "Storage permission needed to save to Downloads", Toast.LENGTH_LONG).show()
        }
        pendingLegacySave = false
    }

    fun generate(onDone: (java.io.File) -> Unit) {
        val scanResult = result ?: return
        onGeneratingChange(true)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    // Null for every normal user — only set once a dealer has actually saved a
                    // profile (DealerProfileScreen). VehicleReportPdfGenerator's own doc explains
                    // why that keeps the free/unbranded path byte-for-byte unchanged.
                    val dealerStore = com.enginesense.app.storage.DealerProfileStore(context)
                    val dealerProfile = dealerStore.load()
                    val dealerLogo = if (dealerProfile != null && dealerStore.hasLogo()) {
                        android.graphics.BitmapFactory.decodeFile(dealerStore.logoFile.absolutePath)
                    } else null
                    VehicleReportPdfGenerator.generate(context, scanResult, vehicleInfo, dealerProfile, dealerLogo)
                }
                onReportFileChange(file)
                onDone(file)
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't generate the report: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                onGeneratingChange(false)
            }
        }
    }

    fun saveCurrentFile(file: java.io.File) {
        if (com.enginesense.app.report.needsLegacyStoragePermission()) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { com.enginesense.app.report.saveToDownloads(context, file) }
                    Toast.makeText(context, if (saved) "Saved to Downloads" else "Couldn't save the report", Toast.LENGTH_LONG).show()
                }
            } else {
                pendingLegacySave = true
                legacyPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { com.enginesense.app.report.saveToDownloads(context, file) }
                Toast.makeText(context, if (saved) "Saved to Downloads" else "Couldn't save the report", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (reportFile == null) {
        Button(
            // Deliberately does NOT launch viewPdfIntent on completion — with no default PDF
            // viewer set, Android's "Open with" app picker for a PDF looks near-identical to a
            // share sheet (WhatsApp/Gmail/Drive all show up in both), which read as "Prepare
            // immediately goes to share." Generating now just reveals the View/Save/Share row
            // below, so any external app handoff only happens from a deliberate tap.
            onClick = { generate {} },
            enabled = !generatingPdf,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (generatingPdf) "Preparing report…" else "Prepare PDF report") }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { context.startActivity(com.enginesense.app.report.viewPdfIntent(context, reportFile)) },
                modifier = Modifier.weight(1f)
            ) { Text("View") }
            OutlinedButton(onClick = { saveCurrentFile(reportFile) }, modifier = Modifier.weight(1f)) { Text("Save") }
            Button(
                onClick = { context.startActivity(com.enginesense.app.report.sharePdfIntent(context, reportFile)) },
                modifier = Modifier.weight(1f)
            ) { Text("Share") }
        }
    }
}

/**
 * Diagnostic view of the exact AT command / raw adapter response pairs from the last
 * real-dongle scan — only present after a real (non-simulated) scan, since the simulator's
 * responses are already known exactly. Added after real-hardware testing surfaced PIDs the
 * dongle actually supports being reported as unsupported by the app — this is what lets a
 * future test pin that down precisely instead of guessing at the parser from screenshots.
 */
@Composable
private fun RawLogSection(viewModel: GetAutoSureViewModel) {
    val rawLog by viewModel.lastRawLog.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    if (rawLog.isEmpty()) return

    Spacer(Modifier.height(16.dp))
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "Hide technical details" else "View technical details (${rawLog.size} commands)")
    }
    if (expanded) {
        Column(
            modifier = Modifier
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            rawLog.forEach { (command, response) ->
                Text(
                    "$command  →  $response",
                    style = GetAutoSureType.Caption,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
