package com.enginesense.app.consultation

import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Every screen in this feature talks to this interface only — never to mock data or a backend
 * directly. [MockExpertRepository] was the only implementation through Phase 2;
 * [SupabaseExpertRepository] is the real one Phase 3 adds. Swapping them touches no UI code,
 * same pattern as [com.enginesense.app.vehicle.ai.AiMechanicChatService]'s rule-based/remote
 * split, gated by [ConsultationBackendConfig] the same way [com.enginesense.app.vehicle.ai.AiBackendConfig] gates AI Mechanic's.
 */
interface ExpertRepository {
    suspend fun listActiveExperts(): List<Expert>
    suspend fun availableSlots(expertId: String): List<AvailabilitySlot>
    suspend fun myBookings(): List<ConsultationBooking>

    /**
     * Reserves a slot and returns a PENDING_PAYMENT booking — the real implementation does this
     * via the atomic reserve_consultation_slot Postgres function; this mock just marks it
     * reserved in memory. [vehicleContext] is
     * [com.enginesense.app.vehicle.ai.AiVehicleContext.toJson]'s output, attached only with the
     * customer's consent — null is always valid, per the explicit "do not require an OBD scan
     * just to book an expert" requirement. The UI toggle to actually offer attaching it is a
     * follow-up; this parameter exists so the data path is already complete.
     */
    suspend fun reserveSlot(slotId: String, vehicleContext: JSONObject? = null): Result<ConsultationBooking>

    /** Real implementation only ever returns CONFIRMED after the backend verifies payment — this mock simulates that step, never a real transaction. */
    suspend fun confirmBookingAfterPayment(bookingId: String): Result<ConsultationBooking>

    suspend fun cancelBooking(bookingId: String): Result<Unit>
}

/**
 * Phase 2 scaffolding ONLY — every expert here is explicitly illustrative, not a real person,
 * per the product owner's explicit instruction not to imply a real verified professional.
 * Names are generic labels, bios say so outright, and [AiMechanicScreen]-style callers should
 * surface a visible "preview data" notice wherever this repository's data is shown.
 */
class MockExpertRepository : ExpertRepository {

    private val now = System.currentTimeMillis()
    private fun hoursFromNow(h: Long) = now + TimeUnit.HOURS.toMillis(h)

    private val experts = listOf(
        Expert(
            id = "preview-expert-1",
            name = "Expert Profile A",
            title = "Automotive Diagnostics Specialist",
            bio = "Illustrative placeholder for layout and testing only — not a real, verified professional. Replace with an actual expert's real details before this ships to users.",
            yearsExperience = 0,
            specializations = listOf("Engine diagnostics", "OBD fault codes"),
            photoUrl = null
        ),
        Expert(
            id = "preview-expert-2",
            name = "Expert Profile B",
            title = "Electrical Systems Specialist",
            bio = "Illustrative placeholder for layout and testing only — not a real, verified professional. Replace with an actual expert's real details before this ships to users.",
            yearsExperience = 0,
            specializations = listOf("Electrical issues", "Battery & charging"),
            photoUrl = null
        ),
        Expert(
            id = "preview-expert-3",
            name = "Expert Profile C",
            title = "Repair Cost & Estimation Specialist",
            bio = "Illustrative placeholder for layout and testing only — not a real, verified professional. Replace with an actual expert's real details before this ships to users.",
            yearsExperience = 0,
            specializations = listOf("Repair-cost advice", "Pre-purchase inspection"),
            photoUrl = null
        )
    )

    private val slotsByExpert: Map<String, List<AvailabilitySlot>> = experts.associate { expert ->
        expert.id to listOf(
            AvailabilitySlot("${expert.id}-slot-1", expert.id, hoursFromNow(4), hoursFromNow(4) + TimeUnit.MINUTES.toMillis(15), SlotStatus.OPEN),
            AvailabilitySlot("${expert.id}-slot-2", expert.id, hoursFromNow(6), hoursFromNow(6) + TimeUnit.MINUTES.toMillis(15), SlotStatus.OPEN),
            AvailabilitySlot("${expert.id}-slot-3", expert.id, hoursFromNow(26), hoursFromNow(26) + TimeUnit.MINUTES.toMillis(15), SlotStatus.OPEN)
        )
    }

    private val bookings = mutableMapOf<String, ConsultationBooking>()
    private var bookingCounter = 0

    override suspend fun listActiveExperts(): List<Expert> = experts.filter { it.active }

    override suspend fun availableSlots(expertId: String): List<AvailabilitySlot> =
        slotsByExpert[expertId]?.filter { it.status == SlotStatus.OPEN } ?: emptyList()

    override suspend fun myBookings(): List<ConsultationBooking> = bookings.values.sortedBy { it.slotStartEpochMillis }

    override suspend fun reserveSlot(slotId: String, vehicleContext: JSONObject?): Result<ConsultationBooking> {
        val slot = slotsByExpert.values.flatten().firstOrNull { it.id == slotId }
            ?: return Result.failure(IllegalArgumentException("Slot not found"))
        if (slot.status != SlotStatus.OPEN) return Result.failure(IllegalStateException("Slot is no longer available"))
        val expert = experts.first { it.id == slot.expertId }
        bookingCounter++
        val booking = ConsultationBooking(
            id = "preview-booking-$bookingCounter",
            bookingReference = "GAS-${1000 + bookingCounter}",
            expertId = expert.id,
            expertName = expert.name,
            slotStartEpochMillis = slot.startEpochMillis,
            slotEndEpochMillis = slot.endEpochMillis,
            status = BookingStatus.PENDING_PAYMENT,
            amountInr = ConsultationPricing.PRICE_INR
        )
        bookings[booking.id] = booking
        return Result.success(booking)
    }

    override suspend fun confirmBookingAfterPayment(bookingId: String): Result<ConsultationBooking> {
        val existing = bookings[bookingId] ?: return Result.failure(IllegalArgumentException("Booking not found"))
        val confirmed = existing.copy(status = BookingStatus.CONFIRMED)
        bookings[bookingId] = confirmed
        return Result.success(confirmed)
    }

    override suspend fun cancelBooking(bookingId: String): Result<Unit> {
        val existing = bookings[bookingId] ?: return Result.failure(IllegalArgumentException("Booking not found"))
        bookings[bookingId] = existing.copy(status = BookingStatus.CANCELLED)
        return Result.success(Unit)
    }
}
