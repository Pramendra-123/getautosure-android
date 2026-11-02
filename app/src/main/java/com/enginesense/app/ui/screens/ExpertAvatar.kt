package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enginesense.app.consultation.Expert
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A mechanic's photo, drawn over their initials: if there's no photo, or it fails to load, the
 * initials show through instead of an empty grey circle.
 */
@Composable
internal fun ExpertAvatar(expert: Expert, size: Dp) {
    val initials = expert.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(GetAutoSureColors.Accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, style = GetAutoSureType.SectionTitle.copy(color = GetAutoSureColors.Accent, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.6f).sp))
        expert.photoUrl?.let {
            AsyncImage(model = it, contentDescription = "${expert.name}'s photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** A mechanic's specialities as chips that move to the next line whole, never breaking a word mid-chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpecialityChips(specialities: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        specialities.forEach { spec ->
            Box(modifier = Modifier.background(GetAutoSureColors.SurfaceRaised, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(spec, style = GetAutoSureType.Caption, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** "Today, 5:00 PM", "Tomorrow, 10:20 AM" or "Wed 12 Nov, 6:00 PM". */
internal fun nextSlotLabel(epochMillis: Long): String {
    val slot = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    fun Calendar.sameDay(other: Calendar) = get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
    return when {
        slot.sameDay(today) -> "Today, $time"
        slot.sameDay(tomorrow) -> "Tomorrow, $time"
        else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(epochMillis)) + ", $time"
    }
}
