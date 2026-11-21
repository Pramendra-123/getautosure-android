package com.enginesense.app.dealerreport

import org.junit.Assert.assertEquals
import org.junit.Test

class CarNameFormatterTest {

    @Test
    fun `company names become the brand buyers know`() {
        assertEquals("Kia Seltos", CarNameFormatter.carName("KIA INDIA PRIVATE LIMITED", "SELTOS G1.4 7DCT GTX PLUS"))
        assertEquals("Maruti Suzuki Swift", CarNameFormatter.carName("MARUTI SUZUKI INDIA LTD", "SWIFT VXI"))
        assertEquals("Hyundai i20", CarNameFormatter.carName("HYUNDAI MOTOR INDIA LTD", "I20 ASTA 1.2"))
        assertEquals("Tata Nexon EV", CarNameFormatter.carName("TATA MOTORS PASSENGER VEHICLES LTD", "NEXON EV MAX", "Electric"))
        assertEquals("Toyota Innova Crysta", CarNameFormatter.carName("TOYOTA KIRLOSKAR MOTOR PVT LTD", "INNOVA CRYSTA 2.4 VX"))
        assertEquals("Mahindra XUV700", CarNameFormatter.carName("MAHINDRA & MAHINDRA LTD", "XUV700 AX7"))
    }

    @Test
    fun `two-word model names keep both words but drop the variant`() {
        assertEquals("Maruti Suzuki Wagon R", CarNameFormatter.carName("MARUTI SUZUKI INDIA LTD", "WAGON R VXI"))
        assertEquals("Maruti Suzuki Alto K10", CarNameFormatter.carName("MARUTI SUZUKI INDIA LTD", "ALTO K10 VXI"))
        assertEquals("Maruti Suzuki Alto", CarNameFormatter.carName("MARUTI SUZUKI INDIA LTD", "ALTO LXI"))
        assertEquals("Hyundai Grand i10", CarNameFormatter.carName("HYUNDAI MOTOR INDIA LTD", "GRAND I10 SPORTZ"))
    }

    @Test
    fun `Skoda and Volkswagen share a company, the model decides`() {
        assertEquals("Volkswagen Virtus", CarNameFormatter.carName("SKODA AUTO VOLKSWAGEN INDIA PVT LTD", "VIRTUS GT"))
        assertEquals("Skoda Kushaq", CarNameFormatter.carName("SKODA AUTO VOLKSWAGEN INDIA PVT LTD", "KUSHAQ STYLE"))
    }

    @Test
    fun `an unknown maker is cleaned of company words`() {
        assertEquals("Vinfast", CarNameFormatter.brand("VINFAST AUTO INDIA PVT LTD", null))
        assertEquals(null, CarNameFormatter.carName(null, null))
    }

    @Test
    fun `fuel reads naturally`() {
        assertEquals("Petrol + CNG", CarNameFormatter.fuel("PETROL/CNG"))
        assertEquals("Diesel", CarNameFormatter.fuel("DIESEL"))
        assertEquals(null, CarNameFormatter.fuel(" "))
    }
}
