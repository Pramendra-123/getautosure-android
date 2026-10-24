package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.GetAutoSureViewModel
import com.enginesense.app.ScanResult
import com.enginesense.app.obd.Severity
import com.enginesense.app.ui.components.DemoDataBanner
import com.enginesense.app.ui.components.HealthScoreRing
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.CategoryState
import com.enginesense.app.vehicle.DiagnosticFinding
import com.enginesense.app.vehicle.HealthCategory

/**
 * Home leads with the hero score, next action, and scan button, then a fixed 8-tile "Explore
 * your car" grid: Advanced Reports, AI Mechanic, Live Data, Car Diagnostics, Repair Guidance,
 * Guides & Tools, Ask a Real Expert, and Dealer Sathi — the full, current set of primary
 * destinations, not a subset. "Used Car Check" (prePurchaseCheck) was removed from this grid by
 * request — the screen and its route still exist and are still reachable from the Reports tab's
 * "Discover More" section (see ReportsScreen.kt), just no longer surfaced here. Everything else
 * not on this list (all sensors, VIN/ECU detail, scan history, remaining Pro teasers) still
 * lives one tap deeper on the Reports tab; nothing else was deleted from the app.
 */
@Composable
fun HomeScreen(
    viewModel: GetAutoSureViewModel,
    onRunScan: () -> Unit,
    onOpenIssues: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val result by viewModel.latestResult.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        HomeHeader(onNavigate)
        VehicleStrip(viewModel, onNavigate)

        if (result == null) {
            EmptyHomeState(onRunScan)
        } else {
            FilledHomeState(result!!, onRunScan, onOpenIssues, onNavigate)
        }
    }
}

@Composable
private fun HomeHeader(onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("GetAuto", style = GetAutoSureType.SectionTitle)
                Text("Sure", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Accent))
            }
            Text("Your AI Car Health Expert", style = GetAutoSureType.Caption)
        }
        val gold = GetAutoSureColors.AccentGold
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(gold.copy(alpha = 0.15f))
                .clickable { onNavigate("pro") }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("👑 Pro", style = GetAutoSureType.Caption.copy(color = gold))
        }
    }
}

/** Slim vehicle identity strip — no background art, just the facts. */
@Composable
private fun VehicleStrip(viewModel: GetAutoSureViewModel, onNavigate: (String) -> Unit) {
    val nickname by viewModel.vehicleNickname.collectAsState()
    val brand by viewModel.vehicleBrand.collectAsState()
    val model by viewModel.vehicleModel.collectAsState()
    val year by viewModel.vehicleYear.collectAsState()
    val fuelType by viewModel.vehicleFuelType.collectAsState()
    val plate by viewModel.vehiclePlate.collectAsState()

    val title = nickname.ifBlank { listOf(brand, model.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" ").ifBlank { "My Car" } }
    val details = listOfNotNull(year?.toString(), fuelType, plate).joinToString(" • ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GetAutoSureColors.Surface)
            .clickable { onNavigate("selectBrand") }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(GetAutoSureColors.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = GetAutoSureType.Body)
            if (details.isNotEmpty()) Text(details, style = GetAutoSureType.Caption)
        }
        Icon(Icons.Filled.Edit, contentDescription = "Edit vehicle", tint = GetAutoSureColors.TextSecondary, modifier = Modifier.size(16.dp))
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun EmptyHomeState(onRunScan: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(40.dp))
        Text("No scan yet", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Plug your OBD2 adapter into the port under your dashboard, then tap below to run your first scan.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRunScan, modifier = Modifier.fillMaxWidth()) { Text("Scan My Car") }
    }
}

@Composable
private fun FilledHomeState(
    result: ScanResult,
    onRunScan: () -> Unit,
    onOpenIssues: () -> Unit,
    onNavigate: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (result.isDemo) {
            item { DemoDataBanner() }
        }

        item { HeroHealthCard(result, onOpenBreakdown = { onNavigate("healthBreakdown") }, onOpenIssues = onOpenIssues) }

        item { NextBestActionCard(result) { onNavigate("aiMechanic") } }

        item { Button(onClick = onRunScan, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Scan My Car") } }

        item { CategoryPillsRow(result, onNavigate) }

        item { Text("Explore your car", style = GetAutoSureType.SectionTitle) }
        item { ExploreGrid(onNavigate) }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** One number, one state word, one line about issues — tap for the 6-category breakdown, or tap the issues line directly. */
@Composable
private fun HeroHealthCard(result: ScanResult, onOpenBreakdown: () -> Unit, onOpenIssues: () -> Unit) {
    val allUnavailable = result.health.score == null
    val ringColor = when {
        allUnavailable -> GetAutoSureColors.Unavailable
        result.health.categories.any { it.state == CategoryState.ATTENTION } -> GetAutoSureColors.Attention
        result.health.categories.any { it.state == CategoryState.WATCH } -> GetAutoSureColors.Watch
        else -> GetAutoSureColors.Good
    }
    val stateEmoji = when {
        allUnavailable -> "⚪"
        result.health.categories.any { it.state == CategoryState.ATTENTION } -> "🔴"
        result.health.categories.any { it.state == CategoryState.WATCH } -> "🟡"
        else -> "🟢"
    }
    val majorCount = result.findings.count { it.severity == Severity.CRITICAL }
    val watchCount = result.findings.count { it.severity == Severity.MODERATE || it.severity == Severity.MINOR }

    Card(
        colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenBreakdown)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (result.health.score != null) {
                HealthScoreRing(result.health.score, ringColor, diameter = 84.dp)
            } else {
                Box(
                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(42.dp)).background(GetAutoSureColors.Unavailable.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Text("No\ndata", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Unavailable), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Your Car Health", style = GetAutoSureType.Caption)
                Text("$stateEmoji ${stateLabel(worstOverallState(result))}", style = GetAutoSureType.SectionTitle.copy(color = ringColor))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (result.findings.isEmpty()) "Nothing to report"
                    else "$majorCount major · $watchCount to watch",
                    style = GetAutoSureType.Caption,
                    modifier = if (result.findings.isNotEmpty()) Modifier.clickable(onClick = onOpenIssues) else Modifier
                )
                Spacer(Modifier.height(4.dp))
                Text("See full breakdown →", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent))
            }
        }
        // One line signaling scan depth, not a dashboard full of rows — the actual detail
        // still lives in the breakdown/Reports screens, this just answers "is that all it checks?".
        Text(
            "Monitoring 6 systems · 34 sensors",
            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.TextSecondary, fontSize = 11.sp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
        )
    }
}

private fun stateLabel(state: CategoryState): String = when (state) {
    CategoryState.GOOD -> "Good"
    CategoryState.WATCH -> "Watch"
    CategoryState.ATTENTION -> "Needs attention"
    CategoryState.UNAVAILABLE -> "No data"
}

private fun worstOverallState(result: ScanResult): CategoryState = when {
    result.health.categories.any { it.state == CategoryState.ATTENTION } -> CategoryState.ATTENTION
    result.health.categories.any { it.state == CategoryState.WATCH } -> CategoryState.WATCH
    result.health.score == null -> CategoryState.UNAVAILABLE
    else -> CategoryState.GOOD
}

/** The one free AI-generated insight from the latest scan, condensed to title + one line + one button. */
@Composable
private fun NextBestActionCard(result: ScanResult, onOpenAiMechanic: () -> Unit) {
    val finding = topFinding(result.findings)
    val accent = when (finding?.severity) {
        Severity.CRITICAL -> GetAutoSureColors.Attention
        Severity.MODERATE, Severity.MINOR -> GetAutoSureColors.Watch
        else -> GetAutoSureColors.Accent
    }

    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.SurfaceRaised), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Next Best Action", style = GetAutoSureType.Caption)
            Spacer(Modifier.height(4.dp))
            if (finding == null) {
                Text("Nothing worth monitoring right now", style = GetAutoSureType.Body)
            } else {
                Text(finding.title, style = GetAutoSureType.Body.copy(color = accent))
                Spacer(Modifier.height(2.dp))
                Text(finding.plainLanguageSummary, style = GetAutoSureType.Caption)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenAiMechanic, modifier = Modifier.fillMaxWidth()) { Text("Ask AI Mechanic") }
        }
    }
}

/** Highest-severity finding, or null if nothing was flagged — never invents one. */
private fun topFinding(findings: List<DiagnosticFinding>): DiagnosticFinding? {
    val rank = { s: Severity -> when (s) { Severity.CRITICAL -> 0; Severity.MODERATE -> 1; Severity.MINOR -> 2; Severity.INFO -> 3 } }
    return findings.minByOrNull { rank(it.severity) }
}

/**
 * Engine / Cooling / Fuel & Combustion keep their real 0-100 scores. Battery, Diagnostics and
 * Emissions are combined into one "Electrical & Diagnostics" state — there's no honest single
 * score across three independently-scored categories, so this shows their worst state, not an
 * invented average.
 */
@Composable
private fun CategoryPillsRow(result: ScanResult, onNavigate: (String) -> Unit) {
    val scored = listOf(HealthCategory.ENGINE, HealthCategory.COOLING, HealthCategory.FUEL_COMBUSTION)
        .mapNotNull { cat -> result.health.categories.firstOrNull { it.category == cat } }
    val electrical = listOf(HealthCategory.BATTERY, HealthCategory.DIAGNOSTICS, HealthCategory.EMISSIONS)
        .mapNotNull { cat -> result.health.categories.firstOrNull { it.category == cat } }
    val electricalState = electrical.map { it.state }.let { states ->
        when {
            states.any { it == CategoryState.ATTENTION } -> CategoryState.ATTENTION
            states.any { it == CategoryState.WATCH } -> CategoryState.WATCH
            states.all { it == CategoryState.UNAVAILABLE } -> CategoryState.UNAVAILABLE
            else -> CategoryState.GOOD
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        scored.forEach { cat ->
            CategoryPill(
                cat.category.displayName,
                cat.score?.let { "$it" } ?: stateLabel(cat.state),
                GetAutoSureColors.forCategoryState(cat.state),
                onClick = { onNavigate("categoryDetail/${cat.category.name}") },
                modifier = Modifier.weight(1f)
            )
        }
        CategoryPill(
            "Electrical & Diagnostics",
            stateLabel(electricalState),
            GetAutoSureColors.forCategoryState(electricalState),
            onClick = { onNavigate("healthBreakdown") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CategoryPill(label: String, value: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GetAutoSureColors.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = GetAutoSureType.Body.copy(color = color, fontSize = 15.sp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = GetAutoSureType.Caption.copy(fontSize = 10.sp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
    }
}

/**
 * One card in the "Explore your car" grid. [route] is used unless [customAction] is set (for
 * the one tile — Ask a real expert — whose real action is opening WhatsApp directly, not
 * navigating anywhere in-app). [isPremium] shows a PRO badge; it's a positioning signal only,
 * nothing here is actually paywalled yet — see docs/free-pro-readiness-and-architecture.md.
 */
private data class ExploreItem(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isPremium: Boolean = false,
    val customAction: ((android.content.Context) -> Unit)? = null
)

/**
 * Everything beyond the hero number. Advanced Reports is a real, free, working feature now (the
 * PDF view/save/share built this session) — not a Pro teaser, so it isn't badged. "Ask a real
 * expert" now opens the scheduled-consultation flow (expertList) instead of the old direct
 * WhatsApp handoff — see ConsultationViewModel's doc: Phase 2 only, mock experts/payment/video,
 * not production-complete yet. Dealer Sathi reuses the existing (still preview-only) DealerScreen.
 */
private val EXPLORE_ITEMS = listOf(
    ExploreItem("aiReport", "Advanced reports", "View, save & share your full PDF report", Icons.Filled.Assessment),
    ExploreItem("aiMechanic", "AI mechanic", "Ask any car question", Icons.Filled.Chat),
    ExploreItem("connect", "Live data", "Readings, 0-60 & trip mileage", Icons.Filled.Insights),
    ExploreItem("reports", "Car diagnostics", "Fault codes, sensors, emissions", Icons.Filled.Biotech),
    ExploreItem("understandRepair", "Repair guidance", "Understand a repair & its cost", Icons.Filled.Handyman),
    ExploreItem("guidesAndTools", "Guides & tools", "Decode your dashboard, learn your car", Icons.Filled.MenuBook),
    ExploreItem("expertList", "Ask a real expert", "Book a 15-min video consultation", Icons.Filled.SupportAgent, isPremium = true),
    ExploreItem("dealer", "Dealer Sathi", "Branded reports to build buyer trust", Icons.Filled.Storefront, isPremium = true)
)

@Composable
private fun ExploreGrid(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EXPLORE_ITEMS.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { item ->
                    ExploreCard(
                        item,
                        onClick = { item.customAction?.invoke(context) ?: onNavigate(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExploreCard(item: ExploreItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GetAutoSureColors.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Icon(item.icon, contentDescription = null, tint = GetAutoSureColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.weight(1f))
            if (item.isPremium) {
                Icon(Icons.Filled.Lock, contentDescription = "Pro", tint = GetAutoSureColors.AccentGold, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = GetAutoSureType.Body.copy(fontSize = 13.sp))
        Text(item.subtitle, style = GetAutoSureType.Caption.copy(fontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
