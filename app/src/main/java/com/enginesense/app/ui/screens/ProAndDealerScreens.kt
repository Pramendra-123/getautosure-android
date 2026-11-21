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
