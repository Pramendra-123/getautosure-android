package com.enginesense.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.enginesense.app.consultation.AvailabilitySlot
import com.enginesense.app.consultation.ConsultationPricing
import com.enginesense.app.consultation.ConsultationViewModel
import com.enginesense.app.consultation.Expert
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Booking summary + "payment". Phase 2 has no real payment provider wired in yet — the button
 * here is explicitly labeled as a preview action, not a disguised fake-success path. Real
 * Razorpay checkout (Phase 4) replaces this button's action, not its position in the flow.
 */
@Composable
fun BookingScreen(viewModel: ConsultationViewModel, expert: Expert, slot: AvailabilitySlot, onBooked: (bookingId: String) -> Unit) {
    var reserving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Asked here, not at app launch — this is the first point a notification (the
    // reservation-expiry warning) actually becomes relevant. Declining just means that one
    // courtesy alert never fires; booking itself doesn't depend on it.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Booking Summary", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(20.dp))

        Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SummaryRow("Expert", expert.name)
                SummaryRow("Date", SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(slot.startEpochMillis)))
                SummaryRow("Time", SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(slot.startEpochMillis)))
                SummaryRow("Duration", "${ConsultationPricing.DURATION_MINUTES} minutes")
                HorizontalDivider(color = GetAutoSureColors.SurfaceRaised, modifier = Modifier.padding(vertical = 10.dp))
                SummaryRow("Total", "₹${ConsultationPricing.PRICE_INR}", emphasize = true)
            }
        }

        Spacer(Modifier.height(20.dp))
        Box(modifier = Modifier.background(GetAutoSureColors.AccentGold.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(14.dp)) {
            Text(
                "Preview only — no real payment is taken here yet. This demonstrates the booking flow; real payment (Razorpay) connects in a later phase.",
                style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.AccentGold)
            )
        }
        Spacer(Modifier.height(20.dp))

        error?.let {
            Text(it, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
            Spacer(Modifier.height(10.dp))
        }

        Button(
            onClick = {
                reserving = true
                error = null
                viewModel.reserveSlot(slot.id, context) { reserveResult ->
                    reserveResult.fold(
                        onSuccess = { booking ->
                            viewModel.confirmBookingAfterPayment(booking.id) { confirmResult ->
                                reserving = false
                                confirmResult.fold(
                                    onSuccess = { confirmed -> onBooked(confirmed.id) },
                                    onFailure = { e -> error = e.message ?: "Couldn't confirm the booking" }
                                )
                            }
                        },
                        onFailure = { e ->
                            reserving = false
                            error = e.message ?: "That slot is no longer available"
                        }
                    )
                }
            },
            enabled = !reserving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (reserving) "Processing…" else "Simulate Payment (Preview)")
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, emphasize: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = GetAutoSureType.Caption)
        Text(
            value,
            style = if (emphasize) GetAutoSureType.Body.copy(fontWeight = FontWeight.Bold, color = GetAutoSureColors.Good) else GetAutoSureType.Body
        )
    }
}
