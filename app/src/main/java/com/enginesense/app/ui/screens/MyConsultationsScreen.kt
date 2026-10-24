package com.enginesense.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enginesense.app.consultation.BookingStatus
import com.enginesense.app.consultation.ConsultationBooking
import com.enginesense.app.consultation.ConsultationViewModel
import com.enginesense.app.consultation.LoadState
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyConsultationsScreen(viewModel: ConsultationViewModel, onSelectBooking: (ConsultationBooking) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadMyBookings(context) }
    val state by viewModel.myBookings.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("My Consultations", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            is LoadState.Loading -> LoadingRow()
            is LoadState.Error -> ErrorRow(s.message, onRetry = { viewModel.loadMyBookings(context) })
            is LoadState.Success -> {
                val bookings = s.data
                if (bookings.isEmpty()) {
                    Text("No consultations booked yet.", style = GetAutoSureType.Caption)
                } else {
                    val upcoming = bookings.filter { it.status == BookingStatus.CONFIRMED && it.slotEndEpochMillis > System.currentTimeMillis() }
                    val completed = bookings.filter { it.status == BookingStatus.COMPLETED || (it.status == BookingStatus.CONFIRMED && it.slotEndEpochMillis <= System.currentTimeMillis()) }
                    val cancelled = bookings.filter { it.status == BookingStatus.CANCELLED }
                    val pending = bookings.filter { it.status == BookingStatus.PENDING_PAYMENT }

                    ConsultationSection("Upcoming", upcoming, onSelectBooking)
                    ConsultationSection("Pending payment", pending, onSelectBooking)
                    ConsultationSection("Completed", completed, onSelectBooking)
                    ConsultationSection("Cancelled", cancelled, onSelectBooking)
                }
            }
        }
    }
}

@Composable
private fun ConsultationSection(title: String, bookings: List<ConsultationBooking>, onSelect: (ConsultationBooking) -> Unit) {
    if (bookings.isEmpty()) return
    Text(title, style = GetAutoSureType.SectionTitle)
    Spacer(Modifier.height(8.dp))
    bookings.forEach { booking -> BookingRow(booking, onClick = { onSelect(booking) }) }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun BookingRow(booking: ConsultationBooking, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(booking.expertName, style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold))
            Text(
                SimpleDateFormat("EEE, d MMM — h:mm a", Locale.getDefault()).format(Date(booking.slotStartEpochMillis)),
                style = GetAutoSureType.Caption
            )
            Text("${booking.bookingReference} · ₹${booking.amountInr}", style = GetAutoSureType.Caption)
        }
    }
}
