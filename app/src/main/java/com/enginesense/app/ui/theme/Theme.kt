package com.enginesense.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Option 2" (picked 2026-11-20, design canvas "GetAutoSure – Vehicle Intelligence Screens"):
 * warm white background, white cards, black hero cards ([Ink]) and orange highlights. Status
 * colors are used sparingly and only where they mean something (a category's health state),
 * never as decoration. Every shade here passes 4.5:1 for text on [Background] and [Surface].
 */
object GetAutoSureColors {
    val Background = Color(0xFFF5F4F2)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceRaised = Color(0xFFEDEBE7)
    val Border = Color(0xFFE3E8EE)
    val TextPrimary = Color(0xFF111111)
    val TextSecondary = Color(0xFF5B6675)
    val Accent = Color(0xFFC2410C) // orange that reads as text on white — links, highlights, CTA fills
    val AccentBright = Color(0xFFFF8A1F) // the brand orange — fills and on black cards, never as text on white
    val AccentSoft = Color(0xFFFFF1E6) // orange tint behind icons and tips
    val AccentPurple = Color(0xFF6D4AD9) // the AI Mechanic's own accent — distinct from Accent so "ask AI" reads differently from "do a thing"
    val AccentGold = Color(0xFF8A6A00) // GetAutoSure Pro's accent — premium tier, not a status color

    /** Black hero cards and primary buttons, with their own text colors. */
    val Ink = Color(0xFF111111)
    val InkRaised = Color(0xFF2B2B2B)
    val OnInk = Color(0xFFFFFFFF)
    val OnInkMuted = Color(0xFFBDBDBD)

    val Good = Color(0xFF0B7A63)
    val Watch = Color(0xFFA15C07)
    val Attention = Color(0xFFB42318)
    val Unavailable = Color(0xFF7A828E)

    fun forCategoryState(state: com.enginesense.app.vehicle.CategoryState): Color = when (state) {
        com.enginesense.app.vehicle.CategoryState.GOOD -> Good
        com.enginesense.app.vehicle.CategoryState.WATCH -> Watch
        com.enginesense.app.vehicle.CategoryState.ATTENTION -> Attention
        com.enginesense.app.vehicle.CategoryState.UNAVAILABLE -> Unavailable
    }
}

private val LightScheme = lightColorScheme(
    background = GetAutoSureColors.Background,
    surface = GetAutoSureColors.Surface,
    surfaceVariant = GetAutoSureColors.SurfaceRaised,
    surfaceContainer = GetAutoSureColors.Surface,
    surfaceContainerLow = GetAutoSureColors.Surface,
    surfaceContainerHigh = GetAutoSureColors.Surface,
    surfaceContainerHighest = GetAutoSureColors.SurfaceRaised,
    // Buttons are black with white text; orange is kept for highlights (see Accent).
    primary = GetAutoSureColors.Ink,
    onPrimary = GetAutoSureColors.OnInk,
    primaryContainer = GetAutoSureColors.AccentSoft,
    onPrimaryContainer = GetAutoSureColors.TextPrimary,
    secondary = GetAutoSureColors.TextSecondary,
    secondaryContainer = GetAutoSureColors.AccentSoft,
    onSecondaryContainer = GetAutoSureColors.TextPrimary,
    onBackground = GetAutoSureColors.TextPrimary,
    onSurface = GetAutoSureColors.TextPrimary,
    onSurfaceVariant = GetAutoSureColors.TextSecondary,
    outline = Color(0xFFD5DBE2),
    outlineVariant = GetAutoSureColors.Border,
    error = GetAutoSureColors.Attention
)

object GetAutoSureType {
    val ScoreNumber = TextStyle(fontSize = 76.sp, fontWeight = FontWeight.Bold, color = GetAutoSureColors.TextPrimary)
    val ScreenTitle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GetAutoSureColors.TextPrimary)
    val SectionTitle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = GetAutoSureColors.TextPrimary)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = GetAutoSureColors.TextPrimary)
    val Caption = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = GetAutoSureColors.TextSecondary)
}

@Composable
fun GetAutoSureTheme(content: @Composable () -> Unit) {
    // Always light — the product has one look and doesn't follow the system dark mode.
    MaterialTheme(
        colorScheme = LightScheme,
        typography = Typography(),
        content = content
    )
}
