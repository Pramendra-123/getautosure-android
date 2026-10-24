package com.enginesense.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * No licensed brand logos or model photos in this build — see the "Brand/model images"
 * decision this was built against. These are deliberately generic: a consistent color per
 * brand name and a plain car glyph for every model, not a real logo or photo. Swap in real
 * assets (keyed by the same brand/model strings already used in SetupScreens.kt) whenever
 * licensed ones are available — nothing else about the picker screens needs to change.
 */
private val BRAND_PALETTE = listOf(
    Color(0xFFFF6B2C), Color(0xFF4FD1C5), Color(0xFFA78BFA), Color(0xFFD4AF37),
    Color(0xFF34D399), Color(0xFFF87171), Color(0xFF60A5FA), Color(0xFFF472B6),
    Color(0xFFFBBF24), Color(0xFF818CF8)
)

private fun colorForKey(key: String): Color = BRAND_PALETTE[abs(key.hashCode()) % BRAND_PALETTE.size]

private fun initialsFor(name: String): String =
    name.split(" ", "-").filter { it.isNotBlank() }.mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

@Composable
fun BrandAvatar(brand: String, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val color = colorForKey(brand)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(initialsFor(brand), color = color, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.6f).sp)
    }
}

/** Every model gets the same generic car glyph (tinted by brand, so a brand's model list still reads as one family) — not a per-model photo. */
@Composable
fun ModelAvatar(brand: String, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val color = colorForKey(brand)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}
