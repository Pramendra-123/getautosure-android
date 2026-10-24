package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType

private enum class LightSeverity { CRITICAL, WARNING, INFO }

private data class WarningLight(val name: String, val severity: LightSeverity, val meaning: String, val action: String)

/**
 * Built natively rather than linking to the website — getautosure.com/dash-lights isn't
 * deployed yet (verified live: it falls through to the report-slug route and hangs on
 * "Loading report...", which is worse than a 404). Content is the same 30-symbol set
 * originally written for Warning_Lights_Guide.pdf / the video-script series, not reinvented.
 * This guide never depends on the website being live.
 */
private val LIGHTS = listOf(
    WarningLight("Check Engine Light", LightSeverity.WARNING,
        "Engine or emission system fault — could be minor (loose fuel cap) or serious (misfire).",
        "Scan with GetAutoSure to get the exact code. If it's blinking, stop and get it looked at — that's an active misfire."),
    WarningLight("Battery / Charging Warning", LightSeverity.CRITICAL,
        "The alternator or battery isn't charging properly — the car may stop running soon.",
        "Pull over safely. Check the alternator belt and battery connections."),
    WarningLight("Oil Pressure Warning", LightSeverity.CRITICAL,
        "Engine oil pressure is dangerously low — the engine is running without proper lubrication.",
        "Stop the engine immediately. Continuing can seize the engine within seconds."),
    WarningLight("Coolant Temperature Warning", LightSeverity.CRITICAL,
        "The engine is overheating.",
        "Stop safely, turn off the engine, let it cool. Never open a hot radiator cap."),
    WarningLight("Brake System Warning", LightSeverity.CRITICAL,
        "Brake fluid is low, the handbrake is engaged, or there's a brake system fault.",
        "Check the handbrake first. If it's off and the light stays on, get it checked immediately."),
    WarningLight("ABS Warning", LightSeverity.WARNING,
        "A fault in the anti-lock braking system — normal brakes still work.",
        "Continue driving normally, but get the ABS sensor checked soon."),
    WarningLight("Airbag (SRS) Warning", LightSeverity.CRITICAL,
        "A fault in the airbag system — it may not deploy in an accident.",
        "No visible symptom, but this is safety-critical. Get it diagnosed promptly."),
    WarningLight("Tyre Pressure Warning (TPMS)", LightSeverity.WARNING,
        "One or more tyres are low on pressure, or a sensor fault.",
        "Check pressure on all 4 tyres plus spare."),
    WarningLight("Power Steering Warning", LightSeverity.WARNING,
        "A fault in the power steering system — steering may feel heavy.",
        "Drive carefully. Check fluid if hydraulic; get an electric system scanned."),
    WarningLight("Seatbelt Reminder", LightSeverity.INFO,
        "Driver or passenger hasn't buckled their seatbelt.",
        "Buckle up — not a mechanical issue."),
    WarningLight("Fuel Level Low", LightSeverity.INFO,
        "Roughly 10-15% fuel or less remaining.",
        "Refuel soon — running very low repeatedly isn't good for the fuel pump."),
    WarningLight("Handbrake Engaged", LightSeverity.INFO,
        "Parking brake is on.",
        "Release before driving."),
    WarningLight("Door Open Indicator", LightSeverity.INFO,
        "One or more doors aren't fully closed.",
        "Check all doors, including the boot."),
    WarningLight("Traction Control / ESP Warning", LightSeverity.WARNING,
        "Blinking is normal (wheel slip on a slippery road). Staying on continuously indicates a fault.",
        "If it's always on, get the system scanned."),
    WarningLight("Glow Plug Indicator (Diesel)", LightSeverity.INFO,
        "Glow plugs are pre-heating for cold start.",
        "Wait for the light to go off, then start."),
    WarningLight("DPF Warning (Diesel Particulate Filter)", LightSeverity.WARNING,
        "The particulate filter has soot buildup.",
        "A 15-20 minute highway drive (40+ km/h) often clears it via regeneration."),
    WarningLight("EPC Warning", LightSeverity.WARNING,
        "An electronic throttle/engine-management fault (common on VW/Audi group cars).",
        "Scan to identify the exact component."),
    WarningLight("Washer Fluid Low", LightSeverity.INFO,
        "Windshield washer fluid is low.",
        "Top up — no mechanical issue."),
    WarningLight("Immobilizer / Security Light", LightSeverity.WARNING,
        "Blinking while parked/locked is normal. Blinking and refusing to start is a real fault.",
        "If it won't start, the key/immobilizer chip needs dealer attention."),
    WarningLight("Cruise Control Indicator", LightSeverity.INFO,
        "Cruise control is active.",
        "No action needed — status only."),
    WarningLight("High Beam Indicator", LightSeverity.INFO,
        "High beam headlights are on.",
        "Switch to low beam for oncoming traffic."),
    WarningLight("Fog Light Indicator", LightSeverity.INFO,
        "Fog lights are on.",
        "Turn off in clear weather — it can blind other drivers."),
    WarningLight("Turn Signal Indicator", LightSeverity.INFO,
        "A turn indicator is active.",
        "Should turn off after the turn — if it stays on continuously, check the bulb."),
    WarningLight("Master Warning Light", LightSeverity.WARNING,
        "A general alert paired with another specific dashboard message.",
        "Check the dashboard display for the detail."),
    WarningLight("Low Key Fob Battery", LightSeverity.INFO,
        "The remote key's battery is low.",
        "Replace the key fob battery (a common, cheap CR-series cell)."),
    WarningLight("Transmission Temperature Warning", LightSeverity.CRITICAL,
        "Transmission fluid is overheating.",
        "Pull over and let it cool. Check ATF level/condition if this recurs."),
    WarningLight("Parking Sensor / Camera Fault", LightSeverity.WARNING,
        "A fault in the parking sensors or reverse camera — often just dirt/mud on a sensor.",
        "Clean the sensors first; if it persists, check the wiring."),
    WarningLight("Engine Overheating (gauge variant)", LightSeverity.CRITICAL,
        "Same as Coolant Temperature Warning — shown as the needle reaching the red zone instead of a dedicated light.",
        "Same response: stop, cool down, don't open the radiator cap hot."),
    WarningLight("Service / Maintenance Required", LightSeverity.INFO,
        "Scheduled maintenance is due — not an active fault.",
        "Book a regular service: oil, filters, inspection."),
    WarningLight("AC/Compressor Snowflake Indicator", LightSeverity.INFO,
        "The AC compressor is active in cooling mode.",
        "Normal status indicator — no action needed.")
)

@Composable
fun WarningLightGuideScreen(onRunScan: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column {
                Text("Dashboard Warning Light Guide", style = GetAutoSureType.ScreenTitle)
                Spacer(Modifier.height(4.dp))
                Text(
                    "🔴 Critical — stop safely  ·  🟡 Warning — get it checked soon  ·  ⚪ Info — just a status",
                    style = GetAutoSureType.Caption
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRunScan, modifier = Modifier.fillMaxWidth()) { Text("Scan My Car") }
                Spacer(Modifier.height(16.dp))
            }
        }

        items(LIGHTS) { light -> WarningLightRow(light) }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun WarningLightRow(light: WarningLight) {
    var expanded by remember { mutableStateOf(false) }
    val color = when (light.severity) {
        LightSeverity.CRITICAL -> GetAutoSureColors.Attention
        LightSeverity.WARNING -> GetAutoSureColors.Watch
        LightSeverity.INFO -> GetAutoSureColors.TextSecondary
    }
    val dot = when (light.severity) {
        LightSeverity.CRITICAL -> "🔴"
        LightSeverity.WARNING -> "🟡"
        LightSeverity.INFO -> "⚪"
    }

    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(dot, modifier = Modifier.padding(end = 8.dp))
                Text(light.name, style = GetAutoSureType.Body.copy(color = color), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text(light.meaning, style = GetAutoSureType.Caption)
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)).padding(10.dp)
                ) {
                    Text(light.action, style = GetAutoSureType.Caption.copy(color = color))
                }
            }
        }
    }
}
