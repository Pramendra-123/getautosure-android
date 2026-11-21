package com.enginesense.app.ui.components

import com.enginesense.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrandLogosTest {

    @Test
    fun `picker brand names find their logo`() {
        assertEquals(R.drawable.logo_mg, BrandLogos.forBrand("MG Motor"))
        assertEquals(R.drawable.logo_mercedes, BrandLogos.forBrand("Mercedes-Benz"))
        assertEquals(R.drawable.logo_vinfast, BrandLogos.forBrand("VinFast"))
        assertEquals(R.drawable.logo_hyundai, BrandLogos.forBrand("Hyundai"))
        assertEquals(R.drawable.logo_maruti, BrandLogos.forBrand("Maruti Suzuki"))
    }

    @Test
    fun `every make in the picker that has a logo in the Drive set shows it`() {
        val withLogos = listOf("Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Kia", "Honda", "Toyota", "Volkswagen", "Skoda",
            "Renault", "Nissan", "MG Motor", "Jeep", "Citroen", "BYD", "Mercedes-Benz", "BMW", "Audi", "Volvo", "Tesla", "VinFast")
        withLogos.forEach { assertEquals(it, true, BrandLogos.forBrand(it) != null) }
    }

    @Test
    fun `brands without a logo yet keep the initials avatar`() {
        assertNull(BrandLogos.forBrand("Ford"))
        assertNull(BrandLogos.forBrand("Other / not sure"))
    }
}
