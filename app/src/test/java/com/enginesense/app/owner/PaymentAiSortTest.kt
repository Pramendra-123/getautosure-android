package com.enginesense.app.owner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaymentAiSortTest {

    private val alert = "Rs.4,200.00 debited from A/c XX4821 on 23-11-26 to SRI SAI MOTORS. UPI Ref 532198764410. Avl Bal Rs 58,240.15. Call 18002586161 if not you. -HDFC Bank"

    @Test
    fun `account, ref, balance and phone numbers never leave the phone`() {
        val m = PaymentAiSort.mask(alert)
        for (secret in listOf("4821", "532198764410", "58,240", "18002586161")) assertFalse("$secret in: $m", secret in m)
    }

    @Test
    fun `amount and merchant are kept, they're needed to sort`() {
        val m = PaymentAiSort.mask(alert)
        assertTrue(m, "4,200" in m)
        assertTrue(m, "SRI SAI MOTORS" in m)
    }

    @Test
    fun `UPI ids and emails are hidden`() {
        assertFalse("ramesh" in PaymentAiSort.mask("Paid Rs 500 to ramesh.k@okaxis via UPI"))
    }

    @Test
    fun `the same merchant written differently is one merchant`() {
        assertEquals(PaymentAiSort.merchantKey("SRI SAI MOTORS"), PaymentAiSort.merchantKey("Sri Sai Motors."))
    }

    @Test
    fun `a merchant the owner already sorted is known`() {
        val data = OwnerCarData(merchantCategories = mapOf("srisaimotors" to "REPAIR", "bigbasket" to PaymentAiSort.NOT_CAR))
        assertEquals("REPAIR", PaymentAiSort.known(data, "Sri Sai Motors"))
        assertEquals(PaymentAiSort.NOT_CAR, PaymentAiSort.known(data, "BigBasket"))
    }

    @Test
    fun `only unsure car guesses wait for the owner`() {
        val p = PendingPayment("a", "2026-11-23", 4200, "SRI SAI MOTORS", "")
        assertFalse(p.needsAnswer)
        assertTrue(p.copy(suggestion = "REPAIR", confidence = "medium").needsAnswer)
        assertFalse(p.copy(suggestion = "REPAIR", confidence = "high").needsAnswer)
        assertFalse(p.copy(suggestion = PaymentAiSort.NOT_CAR, confidence = "low").needsAnswer)
    }

    @Test
    fun `a debit to a workshop is read even though the rules can't sort it`() {
        val parsed = PaymentSmsParser.parseDebitAlert(alert)!!
        assertEquals(4200, parsed.amountInr)
        assertEquals(null, PaymentSmsParser.parseAlert(alert))
    }

    @Test
    fun `the server's answer is read, bad rows skipped`() {
        val sorted = RemotePaymentSorter.parse("""{"results":[{"id":"a","category":"REPAIR","confidence":"high"},{"category":"WASH"}]}""")
        assertEquals(listOf(SortedPayment("a", "REPAIR", "high")), sorted)
    }

    @Test
    fun `a sorted fuel payment becomes a fill-up, anything else an expense`() {
        val fuel = PaymentAlertListener.addSorted(OwnerCarData(), "FUEL", 1, "2026-11-23", 2000, "HP Pump", EntrySource.AI)
        assertEquals(1, fuel.fuel.size)
        val repair = PaymentAlertListener.addSorted(OwnerCarData(), "REPAIR", 1, "2026-11-23", 4200, "Sri Sai Motors", EntrySource.AI)
        assertEquals(ExpenseCategory.REPAIR, repair.expenses.single().category)
        assertEquals(EntrySource.AI, repair.expenses.single().source)
    }
}
