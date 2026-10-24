package com.enginesense.app.consultation

/**
 * Domain models for "Ask a Real Expert" — the paid, scheduled video-consultation feature.
 * These shapes mirror the planned Supabase schema (experts / expert_availability_slots /
 * consultations) deliberately, so Phase 3's real repository implementation is a straight
 * mapping, not a redesign.
 */
data class Expert(
    val id: String,
    val name: String,
    val title: String,
    val bio: String,
    val yearsExperience: Int,
    val specializations: List<String>,
    val photoUrl: String?,
    val active: Boolean = true
)

enum class SlotStatus { OPEN, RESERVED, BOOKED }

data class AvailabilitySlot(
    val id: String,
    val expertId: String,
    /** UTC epoch millis — converted to the device's local timezone only at display time, never stored or compared in local time. */
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val status: SlotStatus
)

enum class BookingStatus { PENDING_PAYMENT, CONFIRMED, CANCELLED, COMPLETED }

data class ConsultationBooking(
    val id: String,
    val bookingReference: String,
    val expertId: String,
    val expertName: String,
    val slotStartEpochMillis: Long,
    val slotEndEpochMillis: Long,
    val status: BookingStatus,
    val amountInr: Int
)

/**
 * Centralizes the price/duration so Phase 3+ only has to change ONE place to pull these from
 * a backend config instead — per the explicit requirement not to scatter hardcoded values
 * across the UI. Every screen in this feature reads from here, never a literal "₹499".
 */
object ConsultationPricing {
    const val PRICE_INR = 499
    const val DURATION_MINUTES = 15

    /** Reschedule/cancel only allowed up to this long before the slot starts — confirmed with the product owner. */
    const val RESCHEDULE_CUTOFF_MINUTES = 120
}
