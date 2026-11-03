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

/** What a customer can count on today. Build notes stay out of the app: anything not ready is just "Coming soon". */
private enum class Availability(val label: String, val color: Color) {
    INCLUDED("Included", GetAutoSureColors.Good),
    COMING_SOON("Coming soon", GetAutoSureColors.Unavailable)
}

private data class FeatureStatus(val name: String, val availability: Availability, val note: String)
private data class FeaturedPitch(val name: String, val pitch: String, val availability: Availability)

/**
 * The three features actually worth a subscription, not just "more of the same for a fee":
 * the hotline because a real person beats a number on a screen, forecast+diagnosis because
 * it's the only pitch here that can save real money (catch it before it's a bigger repair),
 * and context-aware AI because it's the highest-ceiling one.
 */
private val FEATURED = listOf(
    FeaturedPitch(
        "Ask a real expert",
        "A 15-minute video call with a real mechanic, not a bot, for when you need an actual answer, not another reading.",
        Availability.INCLUDED
    ),
    FeaturedPitch(
        "Issue forecast + detailed diagnosis",
        "Catch a problem while it's still small: trends from your scan history, and what the readings mean together.",
        Availability.INCLUDED
    ),
    FeaturedPitch(
        "AI Mechanic that knows your car's history",
        "Answers that take your past scans and trends into account, not just today's scan.",
        Availability.COMING_SOON
    )
)

/** A teaser, not a paywall: no billing is wired up, so nothing here asks for payment. */
@Composable
fun ProScreen() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("GetAutoSure Pro", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text("More help when something's wrong with your car. Pro plans are coming soon.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(20.dp))

        FEATURED.forEach { pitch -> FeaturedCard(pitch) }

        Spacer(Modifier.height(24.dp))
        Text("Also included", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))

        val items = listOf(
            FeatureStatus("Emissions history", Availability.INCLUDED, "Your car's emissions self-tests across every scan, not just the last one."),
            FeatureStatus("Repair cost guide", Availability.INCLUDED, "Typical price ranges for common repairs, so you know what to expect before you pay."),
            FeatureStatus("Live data logging and export", Availability.COMING_SOON, "Record a drive and save the readings. Viewing live data stays free."),
            FeatureStatus("More than one car", Availability.COMING_SOON, "Keep every car in your family in one app.")
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
            AvailabilityChip(pitch.availability)
        }
    }
}

/** What a dealer can expect next. Only shown as "Coming soon"; the reasoning behind each lives in the project notes, not the app. */
private val DEALER_COMING_SOON = listOf(
    FeatureStatus("Your whole inventory", Availability.COMING_SOON, "Track every car you're selling in one place."),
    FeatureStatus("Listing descriptions", Availability.COMING_SOON, "Scan results turned into ready-to-post copy for OLX, Cars24 or Facebook."),
    FeatureStatus("Known-issues note", Availability.COMING_SOON, "A plain-language note of what the scan found, to show buyers up front."),
    FeatureStatus("Ask about this car", Availability.COMING_SOON, "Buyers ask the AI Mechanic about the car from your shared report, without calling you.")
)

@Composable
fun DealerScreen(
    onSetUpDealerProfile: () -> Unit = {},
    onOpenPricingCalculator: () -> Unit = {},
    onPublishWebReport: () -> Unit = {},
    onDealerReport: () -> Unit = {},
    onDealerPhotoReport: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Dealer Sathi", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(8.dp))
        Text("For used-car dealers: scan every car and share a report with your name on it, so buyers can trust it.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(16.dp))

        androidx.compose.material3.Button(onClick = onSetUpDealerProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Set up your dealer profile")
        }
        Spacer(Modifier.height(4.dp))
        Text("Your logo, name and contact details go on every report you make.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(20.dp))

        Text("Dealer reports", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(4.dp))
        Text("A branded PDF for the car you just scanned, with your own asking price.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Button(onClick = onDealerReport, modifier = Modifier.fillMaxWidth()) {
            Text("Dealer report")
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Button(onClick = onDealerPhotoReport, modifier = Modifier.fillMaxWidth()) {
            Text("Dealer report with photos")
        }
        Spacer(Modifier.height(4.dp))
        Text("Adds the car's photos, with the backgrounds cleaned up, and your condition notes.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(16.dp))

        androidx.compose.material3.OutlinedButton(onClick = onPublishWebReport, modifier = Modifier.fillMaxWidth()) {
            Text("Publish web report")
        }
        Spacer(Modifier.height(4.dp))
        Text("A web page for the car you can share as a link, with or without a scan.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(10.dp))

        androidx.compose.material3.OutlinedButton(onClick = onOpenPricingCalculator, modifier = Modifier.fillMaxWidth()) {
            Text("Max buy price calculator")
        }
        Spacer(Modifier.height(4.dp))
        Text("Work out the most to pay for a car, from selling prices you enter.", style = GetAutoSureType.Caption)

        Spacer(Modifier.height(24.dp))
        Text("Coming soon", style = GetAutoSureType.SectionTitle)
        Spacer(Modifier.height(8.dp))
        DEALER_COMING_SOON.forEach { item -> FeatureRow(item); HorizontalDivider(color = GetAutoSureColors.SurfaceRaised) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(item: FeatureStatus) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(item.name, style = GetAutoSureType.Body)
        Spacer(Modifier.height(4.dp))
        AvailabilityChip(item.availability)
        Spacer(Modifier.height(4.dp))
        Text(item.note, style = GetAutoSureType.Caption)
    }
}

@Composable
private fun AvailabilityChip(availability: Availability) {
    Box(
        modifier = Modifier
            .background(availability.color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(availability.label, style = GetAutoSureType.Caption.copy(color = availability.color))
    }
}
