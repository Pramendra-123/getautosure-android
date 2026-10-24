package com.enginesense.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ScanResult
import com.enginesense.app.ui.components.StatusDot
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.CategoryState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything that used to crowd Home's "My Dashboard" list now lives here, grouped by what it's for. */
private val LATEST_SCAN_ITEMS = listOf(
    DiscoverMoreItem("aiReport", "Full AI Report", "The complete narrative write-up of this scan", Icons.Filled.Summarize)
)

private val DIAGNOSTIC_ITEMS = listOf(
    DiscoverMoreItem("issues", "Fault Codes", "Read & clear diagnostic trouble codes", Icons.Filled.Build),
    DiscoverMoreItem("allSensors", "All Sensor Readings", "34 known PIDs", Icons.Filled.ListAlt),
    DiscoverMoreItem("vehicleDetails", "VIN & ECU Information", "VIN details and readiness status", Icons.Filled.Description),
    DiscoverMoreItem("milStatus", "Emissions Readiness & MIL Status", "Check fault indicator status", Icons.Filled.ReportProblem)
)

private val PERFORMANCE_ITEMS = listOf(
    DiscoverMoreItem("connect", "Live Data", "Real-time sensor readings", Icons.Filled.Timeline),
    DiscoverMoreItem("connect", "Driving Performance", "Acceleration & braking distance", Icons.Filled.Speed),
    DiscoverMoreItem("connect", "Trip Mileage", "Estimated km/L for a drive, petrol & diesel only", Icons.Filled.LocalGasStation)
)

private val GUIDES_ITEMS = listOf(
    DiscoverMoreItem("prePurchaseCheck", "Pre-Purchase Check", "Considering buying a car? Scan it first", Icons.Filled.FactCheck),
    DiscoverMoreItem(
        route = "", title = "Dashboard Warning Light Guide", subtitle = "What that icon on your dash actually means",
        icon = Icons.Filled.WarningAmber, externalUrl = "https://getautosure.com/dash-lights"
    ),
    DiscoverMoreItem(
        route = "", title = "Car Care Learning Hub", subtitle = "Videos & guides on common problems, maintenance & more",
        icon = Icons.Filled.OndemandVideo, externalUrl = "https://getautosure.com/learn"
    ),
    // Opens servicecost.in directly rather than us hosting the data ourselves — their terms
    // prohibit scraping/redistributing their pricing database.
    DiscoverMoreItem(
        route = "", title = "Service Cost Estimate", subtitle = "Typical service costs by car & city",
        icon = Icons.Filled.CurrencyRupee, externalUrl = "https://servicecost.in/"
    )
)

private val PRO_ITEMS = listOf(
    DiscoverMoreItem("issueForecast", "Issue Forecast", "Trend-based prediction from scan history", Icons.Filled.TrendingUp, isPremium = true),
    DiscoverMoreItem("detailedDiagnosis", "Detailed Repair Diagnosis", "Cross-signal analysis, not one reading", Icons.Filled.Biotech, isPremium = true),
    DiscoverMoreItem("emissionsTrend", "Emissions Trend History", "Beyond this scan's free pass/fail status", Icons.Filled.ShowChart, isPremium = true),
    DiscoverMoreItem("repairCostReference", "Repair Cost Reference", "Beyond the free Service Cost Estimate above", Icons.Filled.RequestQuote, isPremium = true),
    DiscoverMoreItem("pro", "Advanced Live Data", "Logging, export & multi-session graphing", Icons.Filled.Insights, isPremium = true),
    DiscoverMoreItem("pro", "Multi-Car Garage", "Track more than one vehicle", Icons.Filled.Garage, isPremium = true),
    DiscoverMoreItem("pro", "Advanced Reports", "PDF export, shareable & branded reports", Icons.Filled.Assessment, isPremium = true)
)

/**
 * Reports — everything about your car beyond the Home summary: this scan's detail screens,
 * reference guides, Pro features, and the scan-over-time timeline at the bottom. Moved here
 * from Home's old 17-row "My Dashboard" list so Home could stay to one hero number + one
 * action; nothing was removed, just relocated and grouped by what it's for.
 */
@Composable
fun ReportsScreen(viewModel: GetAutoSureViewModel, onNavigate: (String) -> Unit) {
    val history by viewModel.history.collectAsState()
    val dateFormat = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    val context = LocalContext.current

    fun open(item: DiscoverMoreItem) {
        val url = item.externalUrl
        if (url != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        else onNavigate(item.route)
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Reports", style = GetAutoSureType.ScreenTitle)
            Spacer(Modifier.height(4.dp))
            Text("Everything about your car, beyond the summary", style = GetAutoSureType.Caption)
        }

        item { ReportsSection("Latest Scan", startExpanded = true) { LATEST_SCAN_ITEMS.forEach { DiscoverMoreRow(it) { open(it) } } } }
        item { ReportsSection("Diagnostic Details", startExpanded = true) { DIAGNOSTIC_ITEMS.forEach { DiscoverMoreRow(it) { open(it) } } } }
        item { ReportsSection("Performance & Live Data") { PERFORMANCE_ITEMS.forEach { DiscoverMoreRow(it) { open(it) } } } }
        item { ReportsSection("Guides & Tools") { GUIDES_ITEMS.forEach { DiscoverMoreRow(it) { open(it) } } } }
        item { ReportsSection("Pro") { PRO_ITEMS.forEach { DiscoverMoreRow(it) { open(it) } } } }

        item {
            Column {
                Text("Scan History", style = GetAutoSureType.SectionTitle)
                Spacer(Modifier.height(4.dp))
                Text("${history.size} scans", style = GetAutoSureType.Caption)
            }
        }

        if (history.isEmpty()) {
            item { Text("No scans yet.", style = GetAutoSureType.Body) }
        } else {
            items(history) { entry -> HistoryCard(entry, dateFormat) }
        }
    }
}

/** A true expandable group — collapsed groups don't render their rows at all, so Reports doesn't open onto a long intimidating list. */
@Composable
private fun ReportsSection(title: String, startExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(startExpanded) }
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = GetAutoSureType.SectionTitle)
                Text(if (expanded) "▾" else "▸", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.TextSecondary))
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: ScanResult, dateFormat: SimpleDateFormat) {
    var expanded by remember { mutableStateOf(false) }
    val ringColor = when {
        entry.health.score == null -> GetAutoSureColors.Unavailable
        entry.health.categories.any { it.state == CategoryState.ATTENTION } -> GetAutoSureColors.Attention
        entry.health.categories.any { it.state == CategoryState.WATCH } -> GetAutoSureColors.Watch
        else -> GetAutoSureColors.Good
    }

    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(ringColor)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(dateFormat.format(Date(entry.timestampMillis)), style = GetAutoSureType.Body)
                    Text(
                        if (entry.isDemo) "Demo · ${entry.profileLabel}" else entry.profileLabel,
                        style = GetAutoSureType.Caption
                    )
                }
                Text(entry.health.score?.toString() ?: "—", style = GetAutoSureType.SectionTitle.copy(color = ringColor))
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(entry.health.label, style = GetAutoSureType.Body)
                Spacer(Modifier.height(8.dp))
                if (entry.findings.isEmpty()) {
                    Text("No issues found on this scan.", style = GetAutoSureType.Caption)
                } else {
                    entry.findings.forEach { finding ->
                        Text("• ${finding.title}", style = GetAutoSureType.Caption)
                    }
                }
            }
        }
    }
}
