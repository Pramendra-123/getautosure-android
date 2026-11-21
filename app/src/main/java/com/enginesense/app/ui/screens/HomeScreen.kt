package com.enginesense.app.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
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
import com.enginesense.app.ui.components.CarImages
import com.enginesense.app.ui.components.DemoDataBanner
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import com.enginesense.app.ui.components.HealthScoreRing
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import com.enginesense.app.vehicle.CategoryState
import com.enginesense.app.vehicle.DiagnosticFinding
import com.enginesense.app.vehicle.HealthCategory
import com.enginesense.app.vehicle.PidId

/**
 * Home leads with the hero score, next action, and scan button, then a fixed 8-tile "Explore
 * your car" grid: Advanced Reports, Guides & Tools, Live Data, Car Diagnostics, Repair Guidance,
 * Used Car Check, Ask a Real Expert, and Dealer Sathi. AI Mechanic has no tile, button or
 * floating button on Home by request (it was on screen four times); the bottom tab is its one
 * way in from here. Everything else
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
    val car = rememberCarSummary(viewModel)
    val listState = rememberLazyListState()
    // The car card is the list's first item; once it has scrolled off, the top bar names the car instead.
    val carScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeHeader(onNavigate, car.takeIf { carScrolledAway })

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Room at the bottom so the floating AI Mechanic button never covers the last tiles.
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { VehicleStrip(car, onNavigate) }
            if (result == null) {
                item { EmptyHomeState(onRunScan, onNavigate) }
            } else {
                filledHomeItems(result!!, onRunScan, onOpenIssues, onNavigate)
            }
        }
    }
}

/** What the car card and, once it has scrolled away, the top bar show about the user's car. */
private data class CarSummary(val title: String, val details: String, val picture: Int?)

@Composable
private fun rememberCarSummary(viewModel: GetAutoSureViewModel): CarSummary {
    val nickname by viewModel.vehicleNickname.collectAsState()
    val brand by viewModel.vehicleBrand.collectAsState()
    val model by viewModel.vehicleModel.collectAsState()
    val year by viewModel.vehicleYear.collectAsState()
    val fuelType by viewModel.vehicleFuelType.collectAsState()
    val plate by viewModel.vehiclePlate.collectAsState()
    val rc by viewModel.vehicleRc.collectAsState()

    val title = nickname.ifBlank { listOf(brand, model.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" ").ifBlank { "My Car" } }
    val colour = rc?.color?.lowercase()?.replaceFirstChar { it.uppercase() }
    val details = listOfNotNull(year?.toString(), fuelType, plate, colour).joinToString(" • ")
    return CarSummary(title, details, CarImages.forCar(brand, model, fuelType))
}

/** The app name at the top of Home, or the car's name once its card has scrolled out of view. */
@Composable
private fun HomeHeader(onNavigate: (String) -> Unit, scrolledCar: CarSummary?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(targetState = scrolledCar, modifier = Modifier.weight(1f), label = "homeHeader") { car ->
            if (car == null) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("GetAuto", style = GetAutoSureType.SectionTitle)
                        Text("Sure", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Accent))
                    }
                    Text("Your AI Car Health Expert", style = GetAutoSureType.Caption)
                }
            } else {
                Column(modifier = Modifier.clickable { onNavigate("selectBrand") }) {
                    Text(car.title, style = GetAutoSureType.SectionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (car.details.isNotEmpty()) Text(car.details, style = GetAutoSureType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
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

/**
 * The user's car: its picture on a light panel when we have one for this make and model (see
 * [CarImages]), else a slim strip with the car icon. The picture shows the maker's colour, not the
 * user's; the RTO colour, when known, is shown as text so a mismatch doesn't look like a mistake.
 */
@Composable
private fun VehicleStrip(car: CarSummary, onNavigate: (String) -> Unit) {
    val (title, details, picture) = car

    if (picture != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GetAutoSureColors.Surface)
                .clickable { onNavigate("selectBrand") }
        ) {
            // Transparent picture, straight on the card: the car sits on the dark screen with its own soft shadow.
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 12.dp, end = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(picture),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            }
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = GetAutoSureType.SectionTitle)
                    if (details.isNotEmpty()) Text(details, style = GetAutoSureType.Caption)
                }
                Icon(Icons.Filled.Edit, contentDescription = "Edit vehicle", tint = GetAutoSureColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
}

@Composable
private fun EmptyHomeState(onRunScan: () -> Unit, onNavigate: (String) -> Unit) {
    // Everything except the health score works before a first scan (used-car check, expert
    // calls, Dealer Sathi), so it's all on show rather than an empty screen.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.height(16.dp))
        Text("No scan yet", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Plug the GetAutoSure scanner into the port under your steering wheel, then tap below for your first health check.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(16.dp))
        RoundScanButton(onRunScan)
        Spacer(Modifier.height(32.dp))
        Text("Explore your car", style = GetAutoSureType.SectionTitle, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        ExploreGrid(onNavigate)
        Spacer(Modifier.height(24.dp))
    }
}

private fun LazyListScope.filledHomeItems(
    result: ScanResult,
    onRunScan: () -> Unit,
    onOpenIssues: () -> Unit,
    onNavigate: (String) -> Unit
) {
    if (result.isDemo) {
        item { DemoDataBanner() }
    }

    item { HeroHealthCard(result, onOpenBreakdown = { onNavigate("healthBreakdown") }, onOpenIssues = onOpenIssues) }

    item { NextBestActionCard(result) { onNavigate("aiMechanic") } }

    item {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RoundScanButton(onRunScan, buttonSize = 88.dp, title = null, subtitle = lastScannedText(result.timestampMillis))
        }
    }

    item { CategoryPillsRow(result, onNavigate) }

    item { Text("Explore your car", style = GetAutoSureType.SectionTitle) }
    item { ExploreGrid(onNavigate) }

    item { Spacer(Modifier.height(8.dp)) }
}

/**
 * The scan button: a round "engine start" button with a slow pulse ring. Full size with "Ready to
 * scan" on the empty home screen; smaller with when the car was last scanned after that.
 */
@Composable
internal fun RoundScanButton(
    onRunScan: () -> Unit,
    buttonSize: Dp = 112.dp,
    title: String? = "Ready to scan",
    subtitle: String = "Engine on, scanner plugged in"
) {
    val accent = GetAutoSureColors.Accent
    val pulse = rememberInfiniteTransition(label = "scanPulse")
    val progress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "scanPulseProgress"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(buttonSize * 1.5f), contentAlignment = Alignment.Center) {
            // Pulse ring grows out from the button and fades as it goes.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val base = (buttonSize / 2).toPx()
                drawCircle(
                    color = accent.copy(alpha = 0.45f * (1f - progress)),
                    radius = base + (size.minDimension / 2 - base) * progress
                )
                drawCircle(
                    color = GetAutoSureColors.SurfaceRaised,
                    radius = base + (buttonSize / 11).toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            Column(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(onClickLabel = "Scan my car", onClick = onRunScan),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = GetAutoSureColors.Background, modifier = Modifier.size(buttonSize * 0.27f))
                Spacer(Modifier.height(2.dp))
                Text("Scan", style = GetAutoSureType.Body.copy(color = GetAutoSureColors.Background, fontWeight = FontWeight.SemiBold))
            }
        }
        if (title != null) {
            Spacer(Modifier.height(8.dp))
            Text(title, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(subtitle, style = GetAutoSureType.Caption)
    }
}

/** "Last scanned 23 hours ago" / "Last scanned yesterday" (DateUtils capitalises "Yesterday"). */
private fun lastScannedText(lastScanMillis: Long): String {
    val ago = DateUtils.getRelativeTimeSpanString(lastScanMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    return "Last scanned " + ago.toString().replaceFirstChar { it.lowercase() }
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
                    else "$majorCount critical · $watchCount to watch",
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
            "Monitoring 6 systems · ${PidId.entries.size} sensors",
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

/**
 * The one free AI-generated insight from the latest scan, condensed to title + one line. No
 * button: the AI Mechanic tab is already on screen, and a second solid orange button competed
 * with Scan. When there's a finding, a quiet "Ask about this" link takes it to the AI Mechanic.
 */
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
                Spacer(Modifier.height(10.dp))
                Text(
                    "Ask about this →",
                    style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Accent),
                    modifier = Modifier.clickable(onClick = onOpenAiMechanic)
                )
            }
        }
    }
}

/** Highest-severity finding, or null if nothing was flagged — never invents one. */
private fun topFinding(findings: List<DiagnosticFinding>): DiagnosticFinding? {
    val rank = { s: Severity -> when (s) { Severity.CRITICAL -> 0; Severity.MODERATE -> 1; Severity.MINOR -> 2; Severity.INFO -> 3 } }
    return findings.minByOrNull { rank(it.severity) }
}

/**
 * Engine / Cooling / Fuel & Combustion get a pill each. Battery, Diagnostics and Emissions share
 * one "Electrical & Diagnostics" pill showing the lowest of their scores (and the worst state),
 * not an average, so one problem can't be hidden by two healthy systems.
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

    val electricalScore = electrical.mapNotNull { it.score }.minOrNull()

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        scored.forEach { cat ->
            CategoryPill(
                cat.category.displayName,
                cat.score?.let { "$it" } ?: stateLabel(cat.state),
                GetAutoSureColors.forCategoryState(cat.state),
                onClick = { onNavigate("categoryDetail/${cat.category.name}") },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        CategoryPill(
            "Electrical & Diagnostics",
            electricalScore?.let { "$it" } ?: stateLabel(electricalState),
            GetAutoSureColors.forCategoryState(electricalState),
            onClick = { onNavigate("healthBreakdown") },
            modifier = Modifier.weight(1f).fillMaxHeight()
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
    ExploreItem("aiReport", "Full report", "View, save & share your PDF report", Icons.Filled.Assessment),
    ExploreItem("guidesAndTools", "Guides & tools", "Maintenance tips, checklists & tools", Icons.Filled.MenuBook),
    ExploreItem("connect", "Live data", "Readings, 0-60 & trip mileage", Icons.Filled.Insights),
    ExploreItem("reports", "Car diagnostics", "Fault codes, sensors, emissions", Icons.Filled.Biotech),
    ExploreItem("understandRepair", "Repair guidance", "Understand a repair & its cost", Icons.Filled.Handyman),
    ExploreItem("prePurchaseCheck", "Used car check", "Buying a car? Catch hidden faults first", Icons.Filled.FactCheck),
    ExploreItem("expertList", "Ask a real expert", "Book a 15-min video consultation", Icons.Filled.SupportAgent, isPremium = true)
)

@Composable
private fun ExploreGrid(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EXPLORE_ITEMS.chunked(2).forEach { pair ->
            // Both tiles in a row take the taller one's height, so the grid lines up.
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { item ->
                    ExploreCard(
                        item,
                        onClick = { item.customAction?.invoke(context) ?: onNavigate(item.route) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
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
