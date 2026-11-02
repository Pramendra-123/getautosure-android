package com.enginesense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.enginesense.app.consultation.AvailabilitySlot
import com.enginesense.app.consultation.ConsultationPricing
import com.enginesense.app.consultation.ConsultationViewModel
import com.enginesense.app.consultation.Expert
import com.enginesense.app.consultation.LoadState
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Profile + available slots, grouped by date, in the device's local timezone — slot times are stored/compared as UTC epoch millis everywhere else in this feature; this is the one place they become a local-time string, purely for display. */
@Composable
fun ExpertProfileScreen(viewModel: ConsultationViewModel, expert: Expert, onSlotSelected: (AvailabilitySlot) -> Unit) {
    LaunchedEffect(expert.id) { viewModel.loadSlots(expert.id) }
    val slotsState by viewModel.slots.collectAsState()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExpertAvatar(expert, 72.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(expert.name, style = GetAutoSureType.ScreenTitle.copy(fontSize = 22.sp))
                Text(expert.title, style = GetAutoSureType.Caption)
                if (expert.yearsExperience > 0) {
                    Text("${expert.yearsExperience} years experience", style = GetAutoSureType.Caption)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text(expert.bio, style = GetAutoSureType.Body)

        if (expert.specializations.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Expertise", style = GetAutoSureType.SectionTitle.copy(fontSize = 16.sp))
            Spacer(Modifier.height(6.dp))
            SpecialityChips(expert.specializations)
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = GetAutoSureColors.SurfaceRaised)
        Spacer(Modifier.height(16.dp))

        Text("Choose a time", style = GetAutoSureType.SectionTitle.copy(fontSize = 18.sp))
        Spacer(Modifier.height(4.dp))
        Text("${ConsultationPricing.DURATION_MINUTES}-minute video consultation — ₹${ConsultationPricing.PRICE_INR}", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(14.dp))

        when (val state = slotsState) {
            is LoadState.Loading -> LoadingRow()
            is LoadState.Error -> ErrorRow(state.message, onRetry = { viewModel.loadSlots(expert.id) })
            is LoadState.Success -> {
                if (state.data.isEmpty()) {
                    Text("No free slots this week", style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(4.dp))
                    Text("This mechanic hasn't opened any times yet. Another mechanic may be free sooner.", style = GetAutoSureType.Caption)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { backDispatcher?.onBackPressed() }, modifier = Modifier.fillMaxWidth()) {
                        Text("See other mechanics")
                    }
                } else {
                    val grouped = state.data.groupBy { dateLabel(it.startEpochMillis) }
                    grouped.forEach { (date, slotsForDate) ->
                        Text(date, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            slotsForDate.forEach { slot ->
                                Box(
                                    modifier = Modifier
                                        .background(GetAutoSureColors.Surface, RoundedCornerShape(10.dp))
                                        .clickable { onSlotSelected(slot) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(timeLabel(slot.startEpochMillis), style = GetAutoSureType.Body)
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}

private fun dateLabel(epochMillis: Long): String =
    SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(epochMillis))

private fun timeLabel(epochMillis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
