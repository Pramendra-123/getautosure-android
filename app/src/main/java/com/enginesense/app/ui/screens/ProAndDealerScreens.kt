package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

private enum class Feasibility(val label: String, val color: Color) {
    AVAILABLE("Already works today", GetAutoSureColors.Good),
    BUILDABLE("Buildable locally, not built yet", GetAutoSureColors.Watch),
    NEEDS_BACKEND("Needs a real backend/server", GetAutoSureColors.Attention)
}

private data class FeatureStatus(val name: String, val feasibility: Feasibility, val note: String)
private data class FeaturedPitch(val name: String, val pitch: String, val feasibility: Feasibility)

/**
 * The three features actually worth a subscription, not just "more of the same for a fee":
 * the hotline because a real person beats a number on a screen, forecast+diagnosis because
 * it's the only pitch here that can save real money (catch it before it's a bigger repair),
 * and context-aware AI because it's the highest-ceiling one — assuming it actually reasons
 * over history rather than reading like the same free chat with a longer memory.
 */
private val FEATURED = listOf(
    FeaturedPitch(
        "Ask a real expert",
        "Talk to a mechanic on call, over WhatsApp — not a bot. For when you need an actual answer, not another reading.",
        Feasibility.AVAILABLE
    ),
    FeaturedPitch(
        "Issue forecast + detailed diagnosis",
        "Catch a problem while it's still small: trend-based prediction from your scan history, plus the cross-signal read on what it actually means — not just a raw number.",
        Feasibility.AVAILABLE
    ),
    FeaturedPitch(
        "Context-aware AI Mechanic",
        "Reasons over your scan history and trends, not just this one scan — ask it something and it remembers what your car's been doing.",
        Feasibility.NEEDS_BACKEND
    )
)

/** Pure visual teaser — no billing wired up; "Explore Pro" leads here instead of a real paywall. */
@Composable
fun ProScreen() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("GetAutoSure Pro", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Not a real subscription yet — no payment is taken here. This previews what Pro " +
                "would unlock once billing is wired up.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(20.dp))
        Text("Worth the subscription", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.AccentGold))
        Spacer(Modifier.height(4.dp))
        Text("The three reasons to actually pay for this, not just more of the same for a fee.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(12.dp))

        FEATURED.forEach { pitch -> FeaturedCard(pitch) }

        Spacer(Modifier.height(24.dp))
        Text("Also included", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))

        val items = listOf(
            FeatureStatus("Emissions trend history", Feasibility.AVAILABLE, "This scan's pass/fail status is free (VIN & ECU Emission Readiness) — this adds per-monitor history across scans."),
            FeatureStatus("Repair cost reference", Feasibility.AVAILABLE, "A free link to servicecost.in's per-model pricing already lives on Home — this adds a curated in-app reference by vehicle segment."),
            FeatureStatus("Unlimited vehicle history", Feasibility.AVAILABLE, "Actually free for everyone — every real scan is saved and shown, no cap."),
            FeatureStatus("Advanced reports", Feasibility.AVAILABLE, "Also free for everyone now — view, save and share your full PDF report. Dealer Sathi adds dealer branding on top of this same feature."),
            FeatureStatus("Advanced live data", Feasibility.BUILDABLE, "Logging, export and multi-session graphing — viewing live data stays free, not built yet."),
            FeatureStatus("Multi-car garage", Feasibility.BUILDABLE, "Track more than one vehicle — needs a per-vehicle data model on top of the new local history store, not built yet.")
        )
        items.forEach { item ->
            FeatureRow(item)
            HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeaturedCard(pitch: FeaturedPitch, accent: Color = GetAutoSureColors.AccentGold) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.SurfaceRaised),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(pitch.name, style = GetAutoSureType.Body.copy(color = accent))
            Spacer(Modifier.height(6.dp))
            Text(pitch.pitch, style = GetAutoSureType.Caption)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .background(pitch.feasibility.color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(pitch.feasibility.label, style = GetAutoSureType.Caption.copy(color = pitch.feasibility.color))
            }
        }
    }
}

/**
 * The core dealer loop — scan every car, generate a branded report, share it — plus the
 * real-AI ideas that actually fit a dealer's business rather than being consumer features
 * relabeled. The disclosure-note and shared-report-link items are the two worth building
 * first once the shared backend exists: they strengthen "buy with confidence" directly,
 * rather than just saving the dealer time.
 */
private val DEALER_CORE_LOOP = listOf(
    FeaturedPitch(
        "Multi-vehicle inventory",
        "Track every car you're selling, not just one — the foundation everything else here builds on.",
        Feasibility.NEEDS_BACKEND
    ),
    FeaturedPitch(
        "Branded report, shared like a real inspection",
        "Scan a car, generate a PDF with your name and logo, and share the same kind of verified-report link your WhatsApp inspection reports already use — not a separate, lesser product.",
        Feasibility.NEEDS_BACKEND
    )
)

private val DEALER_AI = listOf(
    FeatureStatus("AI-drafted listing description", Feasibility.NEEDS_BACKEND, "Scan data turned into ready-to-post copy for OLX/Cars24/Facebook — grounded in real numbers, not generic sales language."),
    FeatureStatus("AI-drafted disclosure note", Feasibility.NEEDS_BACKEND, "Findings turned into a plain-language \"known issues\" note you can show proactively — the actual mechanism behind \"buy with confidence,\" not just a convenience."),
    FeatureStatus("Fleet-wide AI insight", Feasibility.NEEDS_BACKEND, "\"3 of your 12 cars need the battery checked before they go on the lot\" — Issue Forecast across your whole inventory, not one car."),
    FeatureStatus("Buyer-facing \"Ask AI about this car\"", Feasibility.NEEDS_BACKEND, "Lives on the shared report page itself (the website, not this app) — a buyer asks the AI Mechanic about this specific scan without calling you.")
)

/** Pure visual teaser for the dealer-facing plan, with an honest feasibility note per feature — answers "which is possible". */
@Composable
fun DealerScreen(onSetUpDealerProfile: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("GetAutoSure Dealer", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Not a real account/billing system yet — this previews what a dealer plan would " +
                "include, with an honest note on what's realistic to build without a server.",
            style = GetAutoSureType.Caption
        )
        Spacer(Modifier.height(16.dp))

        androidx.compose.material3.Button(onClick = onSetUpDealerProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Set up your dealer profile")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Real and working today — your logo, name and contact info get stamped on every PDF report you generate.",
            style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Good)
        )
        Spacer(Modifier.height(20.dp))

        Text("Check every car, share with your customer", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Good))
        Spacer(Modifier.height(4.dp))
        Text("The core loop this plan is actually for.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(12.dp))
        DEALER_CORE_LOOP.forEach { pitch -> FeaturedCard(pitch, accent = GetAutoSureColors.Good) }

        Spacer(Modifier.height(24.dp))
        Text("Real AI for your business", style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Good))
        Spacer(Modifier.height(4.dp))
        Text("Not consumer features relabeled — things that specifically save a dealer time or build buyer trust.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(8.dp))
        DEALER_AI.forEach { item -> FeatureRow(item); HorizontalDivider(color = GetAutoSureColors.SurfaceRaised) }

        Spacer(Modifier.height(24.dp))
        Text("Already built", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        val items = listOf(
            FeatureStatus("Vehicle health score", Feasibility.AVAILABLE, "Already built."),
            FeatureStatus("34-PID diagnostic report", Feasibility.AVAILABLE, "Already built (All Sensors)."),
            FeatureStatus("Scan history", Feasibility.AVAILABLE, "Already built."),
            FeatureStatus("AI vehicle analysis", Feasibility.AVAILABLE, "Already built (local rules engine + AI report/chat)."),
            FeatureStatus(
                "Advanced reports",
                Feasibility.AVAILABLE,
                "Already built and free for every user, not just dealers — scan a car, then view, save or share the full PDF report. With a dealer profile set up (see Dealer branding below), the same report carries your branding instead."
            ),
            FeatureStatus("Dealer branding", Feasibility.AVAILABLE, "Already built — set up your profile above. A local dealer name/logo profile stamped on the PDF report, single-dealer only without a backend."),
            FeatureStatus("Fast-scan intake mode", Feasibility.BUILDABLE, "A streamlined add-vehicle flow for scanning your 20th car this week, not your first car ever — not built yet."),
            FeatureStatus("QR-verifiable reports", Feasibility.NEEDS_BACKEND, "A QR code can hold data, but real tamper-proof \"verification\" needs a server to check against.")
        )
        items.forEach { item ->
            FeatureRow(item)
            HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(item: FeatureStatus) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(item.name, style = GetAutoSureType.Body)
        Spacer(Modifier.height(4.dp))
        Row {
            Box(
                modifier = Modifier
                    .background(item.feasibility.color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(item.feasibility.label, style = GetAutoSureType.Caption.copy(color = item.feasibility.color))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(item.note, style = GetAutoSureType.Caption)
    }
}
