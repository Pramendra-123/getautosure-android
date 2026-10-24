package com.enginesense.app.consultation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockExpertRepositoryTest {

    @Test
    fun `lists only active experts`() = runBlocking {
        val experts = MockExpertRepository().listActiveExperts()
        assertTrue(experts.isNotEmpty())
        assertTrue(experts.all { it.active })
    }

    @Test
    fun `no expert's bio is missing the illustrative-placeholder disclosure`() = runBlocking {
        // A real-data regression here would be serious — the product owner was explicit that
        // mock experts must never read as real, verified professionals.
        val experts = MockExpertRepository().listActiveExperts()
        experts.forEach { expert ->
            assertTrue(
                "Expert '${expert.name}' bio must disclose it's a placeholder",
                expert.bio.contains("placeholder", ignoreCase = true) || expert.bio.contains("not a real", ignoreCase = true)
            )
        }
    }

    @Test
    fun `available slots are only ones in OPEN status`() = runBlocking {
        val repo = MockExpertRepository()
        val expert = repo.listActiveExperts().first()
        val slots = repo.availableSlots(expert.id)
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.all { it.status == SlotStatus.OPEN })
    }

    @Test
    fun `reserving a slot creates a PENDING_PAYMENT booking`() = runBlocking {
        val repo = MockExpertRepository()
        val expert = repo.listActiveExperts().first()
        val slot = repo.availableSlots(expert.id).first()

        val result = repo.reserveSlot(slot.id)

        assertTrue(result.isSuccess)
        val booking = result.getOrThrow()
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.status)
        assertEquals(expert.id, booking.expertId)
        assertEquals(ConsultationPricing.PRICE_INR, booking.amountInr)
    }

    @Test
    fun `reserving an unknown slot id fails instead of crashing`() = runBlocking {
        val result = MockExpertRepository().reserveSlot("does-not-exist")
        assertTrue(result.isFailure)
    }

    @Test
    fun `confirming after payment transitions the booking to CONFIRMED`() = runBlocking {
        val repo = MockExpertRepository()
        val expert = repo.listActiveExperts().first()
        val slot = repo.availableSlots(expert.id).first()
        val booking = repo.reserveSlot(slot.id).getOrThrow()

        val confirmed = repo.confirmBookingAfterPayment(booking.id).getOrThrow()

        assertEquals(BookingStatus.CONFIRMED, confirmed.status)
    }

    @Test
    fun `cancelling a booking transitions it to CANCELLED and it still appears in myBookings`() = runBlocking {
        val repo = MockExpertRepository()
        val expert = repo.listActiveExperts().first()
        val slot = repo.availableSlots(expert.id).first()
        val booking = repo.reserveSlot(slot.id).getOrThrow()

        val cancelResult = repo.cancelBooking(booking.id)
        val bookings = repo.myBookings()

        assertTrue(cancelResult.isSuccess)
        assertEquals(BookingStatus.CANCELLED, bookings.first { it.id == booking.id }.status)
    }

    @Test
    fun `cancelling an unknown booking id fails instead of crashing`() = runBlocking {
        val result = MockExpertRepository().cancelBooking("does-not-exist")
        assertTrue(result.isFailure)
    }
}
