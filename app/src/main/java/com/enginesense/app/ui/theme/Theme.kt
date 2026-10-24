package com.enginesense.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Premium, dark, WHOOP/Oura-inspired palette — not the generic light-Material look of a
 * traditional OBD scanner app. Status colors are used sparingly and only where they mean
 * something (a category's health state), never as decoration.
 */
object GetAutoSureColors {
    val Background = Color(0xFF0A0B0D)
    val Surface = Color(0xFF16181C)
    val SurfaceRaised = Color(0xFF1E2126)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF9BA1AC)
    val Accent = Color(0xFFFF6B2C) // orange — primary CTA / brand accent, not a status color
    val AccentPurple = Color(0xFFA78BFA) // the AI Mechanic's own accent — distinct from Accent so "ask AI" reads differently from "do a thing"
    val AccentGold = Color(0xFFD4AF37) // GetAutoSure Pro's accent — premium tier, not a status color

    val Good = Color(0xFF34D399)
    val Watch = Color(0xFFFBBF24)
    val Attention = Color(0xFFF87171)
    val Unavailable = Color(0xFF5B6270)

    fun forCategoryState(state: com.enginesense.app.vehicle.CategoryState): Color = when (state) {
        com.enginesense.app.vehicle.CategoryState.GOOD -> Good
        com.enginesense.app.vehicle.CategoryState.WATCH -> Watch
        com.enginesense.app.vehicle.CategoryState.ATTENTION -> Attention
        com.enginesense.app.vehicle.CategoryState.UNAVAILABLE -> Unavailable
    }
}

private val DarkScheme = darkColorScheme(
    background = GetAutoSureColors.Background,
    surface = GetAutoSureColors.Surface,
    surfaceVariant = GetAutoSureColors.SurfaceRaised,
    primary = GetAutoSureColors.Accent,
    onPrimary = Color.White,
    onBackground = GetAutoSureColors.TextPrimary,
    onSurface = GetAutoSureColors.TextPrimary,
    secondary = GetAutoSureColors.TextSecondary,
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
    // Always dark — this product deliberately doesn't follow system light/dark, same as WHOOP/Oura.
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = Typography(),
        content = content
    )
}
