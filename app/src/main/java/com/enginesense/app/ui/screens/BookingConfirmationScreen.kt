package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enginesense.app.consultation.BookingStatus
import com.enginesense.app.consultation.ConsultationBooking
import com.enginesense.app.consultation.ConsultationPricing
import com.enginesense.app.consultation.ConsultationViewModel
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** How long before a slot's start time the Join button becomes available — matches typical video-consultation UX (early enough to not feel late, not so early it's pointless). */
private val JOIN_WINDOW_OPENS_BEFORE_MILLIS = TimeUnit.MINUTES.toMillis(5)

@Composable
fun BookingConfirmationScreen(viewModel: ConsultationViewModel, booking: ConsultationBooking, onJoin: () -> Unit, onViewMyConsultations: () -> Unit) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    val canJoin = now in (booking.slotStartEpochMillis - JOIN_WINDOW_OPENS_BEFORE_MILLIS)..booking.slotEndEpochMillis
    // Mirrors the Edge Function's own enforcement (cancel_booking action) — this is a UX
    // convenience so the button visibly disables itself, not the real guarantee; the backend
    // checks this independently and is the actual authority, same "never trust the client"
    // principle as everywhere else in this app that touches money.
    val pastCancelCutoff = now > booking.slotStartEpochMillis - TimeUnit.MINUTES.toMillis(ConsultationPricing.RESCHEDULE_CUTOFF_MINUTES.toLong())
    val canCancel = booking.status != BookingStatus.CANCELLED && booking.status != BookingStatus.COMPLETED && !pastCancelCutoff

    var cancelling by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GetAutoSureColors.Good, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (booking.status == BookingStatus.CANCELLED) "Booking Cancelled" else "Booking Confirmed", style = GetAutoSureType.ScreenTitle)
        }
        Spacer(Modifier.height(20.dp))

        Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reference: ${booking.bookingReference}", style = GetAutoSureType.Caption)
                Spacer(Modifier.height(10.dp))
                Text(booking.expertName, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    SimpleDateFormat("EEEE, d MMMM yyyy — h:mm a", Locale.getDefault()).format(Date(booking.slotStartEpochMillis)),
                    style = GetAutoSureType.Caption
                )
                Text("${ConsultationPricing.DURATION_MINUTES}-minute video consultation", style = GetAutoSureType.Caption)
            }
        }

        if (booking.status != BookingStatus.CANCELLED) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onJoin,
                enabled = canJoin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Join Consultation")
            }
            if (!canJoin) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "The Join button becomes active 5 minutes before your consultation starts.",
                    style = GetAutoSureType.Caption
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Need to change this? You can reschedule or cancel free of charge up to ${ConsultationPricing.RESCHEDULE_CUTOFF_MINUTES / 60} hours before your consultation. After that, the booking is locked in.",
                style = GetAutoSureType.Caption
            )
            Spacer(Modifier.height(10.dp))

            error?.let {
                Text(it, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
                Spacer(Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = {
                    cancelling = true
                    error = null
                    viewModel.cancelBooking(booking.id, context) { result ->
                        cancelling = false
                        result.onFailure { e -> error = e.message ?: "Couldn't cancel this booking" }
                    }
                },
                enabled = canCancel && !cancelling,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GetAutoSureColors.Attention),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (cancelling) "Cancelling…" else "Cancel Booking")
            }
            if (pastCancelCutoff && booking.status != BookingStatus.COMPLETED) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "This booking is within ${ConsultationPricing.RESCHEDULE_CUTOFF_MINUTES / 60} hours of starting and can no longer be cancelled.",
                    style = GetAutoSureType.Caption
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onViewMyConsultations, modifier = Modifier.fillMaxWidth()) {
            Text("View My Consultations")
        }
    }
}
