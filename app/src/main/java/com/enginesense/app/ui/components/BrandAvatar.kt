package com.enginesense.app.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enginesense.app.R
import kotlin.math.abs

/**
 * Brand logos for the onboarding brand picker (source: the LOGOS folder of "car images final" on
 * Drive, kept in brand/car-images-final). Each is pre-cut as a round white badge. A brand without
 * a logo yet keeps the generic avatar: a consistent colour per brand name with its initials.
 * Models still get the plain car glyph; model pictures live in [CarImages] for the Home card.
 */
object BrandLogos {
    private val logos = mapOf(
        "audi" to R.drawable.logo_audi, "bmw" to R.drawable.logo_bmw, "byd" to R.drawable.logo_byd,
        "citroen" to R.drawable.logo_citroen, "honda" to R.drawable.logo_honda, "hyundai" to R.drawable.logo_hyundai,
        "jeep" to R.drawable.logo_jeep, "kia" to R.drawable.logo_kia, "mahindra" to R.drawable.logo_mahindra,
        "maruti" to R.drawable.logo_maruti, "mercedes" to R.drawable.logo_mercedes, "mg" to R.drawable.logo_mg,
        "nissan" to R.drawable.logo_nissan, "renault" to R.drawable.logo_renault, "skoda" to R.drawable.logo_skoda,
        "tata" to R.drawable.logo_tata, "tesla" to R.drawable.logo_tesla, "toyota" to R.drawable.logo_toyota,
        "vinfast" to R.drawable.logo_vinfast, "volkswagen" to R.drawable.logo_volkswagen, "volvo" to R.drawable.logo_volvo
    )

    /** The logo for a brand name like "MG Motor" or "Mercedes-Benz", or null when there isn't one yet. */
    fun forBrand(brand: String): Int? {
        val b = brand.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        return logos.entries.firstOrNull { b.startsWith(it.key) }?.value
    }
}

private val BRAND_PALETTE = listOf(
    Color(0xFFFF6B2C), Color(0xFF4FD1C5), Color(0xFFA78BFA), Color(0xFFD4AF37),
    Color(0xFF34D399), Color(0xFFF87171), Color(0xFF60A5FA), Color(0xFFF472B6),
    Color(0xFFFBBF24), Color(0xFF818CF8)
)

private fun colorForKey(key: String): Color = BRAND_PALETTE[abs(key.hashCode()) % BRAND_PALETTE.size]

private fun initialsFor(name: String): String {
    val words = name.split(" ", "-").filter { it.isNotBlank() }
    // A brand that's already initials (MG, BMW) keeps them, rather than "MM" for MG Motor.
    words.firstOrNull()?.takeIf { it.length in 2..3 && it.all(Char::isUpperCase) }?.let { return it }
    return words.mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
}

@Composable
fun BrandAvatar(brand: String, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    BrandLogos.forBrand(brand)?.let { logo ->
        Image(painterResource(logo), contentDescription = brand, modifier = modifier.size(size).clip(CircleShape))
        return
    }
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
