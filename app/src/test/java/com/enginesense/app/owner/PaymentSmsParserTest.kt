package com.enginesense.app.owner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PaymentSmsParserTest {

    private val today = LocalDate.of(2026, 11, 21)

    @Test
    fun fastagTollDebit() {
        val p = PaymentSmsParser.parse("Rs.120.00 debited from your FASTag wallet for KA03NG8216 at NELAMANGALA TOLL PLAZA on 20-11-2026. Avl Bal Rs.1,432.50", today)!!
        assertEquals(120, p.amountInr)
        assertEquals(PaymentKind.TOLL, p.kind)
        assertEquals(LocalDate.of(2026, 11, 20), p.date)
    }

    @Test
    fun fuelPumpUpiPayment() {
        val p = PaymentSmsParser.parse("INR 2,000.00 debited from A/c XX1234 on 18-Nov-26 to VPA hpcl.retail@okaxis (UPI Ref 6123). Not you? Call 1800", today)!!
        assertEquals(2000, p.amountInr)
        assertEquals(PaymentKind.FUEL, p.kind)
        assertEquals(LocalDate.of(2026, 11, 18), p.date)
    }

    @Test
    fun cardSpendAtParking() {
        val p = PaymentSmsParser.parse("You have spent ₹60 on your HDFC Bank Card at PHOENIX MALL PARKING on 2026-11-19", today)!!
        assertEquals(60, p.amountInr)
        assertEquals(PaymentKind.PARKING, p.kind)
    }

    @Test
    fun unknownShopIsOther() {
        assertEquals(PaymentKind.OTHER, PaymentSmsParser.parse("Rs 450 paid to SHARMA STORES via UPI", today)!!.kind)
    }

    @Test
    fun creditsAndRechargesAreNotExpenses() {
        assertNull(PaymentSmsParser.parse("Rs.500 credited to your account as refund", today))
        assertNull(PaymentSmsParser.parse("Your FASTag has been recharged with Rs.1000", today))
        assertNull(PaymentSmsParser.parse("Hello, your car is ready", today))
    }
}
