package com.enginesense.app.ui.components

import com.enginesense.app.R
import com.enginesense.app.ui.screens.MODELS_BY_MAKE
import com.enginesense.app.vehiclelookup.RcPrefillMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarImagesTest {

    @Test
    fun `picker names find their picture`() {
        assertEquals(R.drawable.model_maruti_swift, CarImages.forCar("Maruti Suzuki", "Swift", "Petrol"))
        assertEquals(R.drawable.model_maruti_wagon_r, CarImages.forCar("Maruti Suzuki", "WagonR", "CNG"))
        assertEquals(R.drawable.model_mahindra_scorpio_n, CarImages.forCar("Mahindra", "Scorpio-N", "Diesel"))
        assertEquals(R.drawable.model_mercedes_c_class, CarImages.forCar("Mercedes-Benz", "C-Class", "Petrol"))
        assertEquals(R.drawable.model_mg_hector, CarImages.forCar("MG Motor", "Hector", "Petrol"))
    }

    @Test
    fun `an electric car gets its EV picture, a petrol one the regular picture`() {
        assertEquals(R.drawable.model_tata_nexon_ev, CarImages.forCar("Tata", "Nexon", "Electric"))
        assertEquals(R.drawable.model_tata_nexon, CarImages.forCar("Tata", "Nexon", "Petrol"))
        assertEquals(R.drawable.model_tata_punch_ev, CarImages.forCar("Tata", "Punch", "Electric"))
        assertEquals(R.drawable.model_hyundai_creta_electric, CarImages.forCar("Hyundai", "Creta", "Electric"))
        // No EV picture for this model: the regular one is still better than the icon.
        assertEquals(R.drawable.model_maruti_swift, CarImages.forCar("Maruti Suzuki", "Swift", "Electric"))
    }

    @Test
    fun `RTO style names and old model names still match`() {
        assertEquals(R.drawable.model_tata_nexon, CarImages.forCar("TATA MOTORS LTD", "NEXON XZ PLUS", "Diesel"))
        assertEquals(R.drawable.model_maruti_alto800, CarImages.forCar("MARUTI SUZUKI INDIA LTD", "Alto", "Petrol"))
        assertEquals(R.drawable.model_mahindra_xuv_3xo, CarImages.forCar("Mahindra", "XUV300", "Petrol"))
    }

    @Test
    fun `a car saved from the Surepass RTO record gets its picture`() {
        fun pictureFor(rtoMaker: String, rtoModel: String, fuel: String): Int? {
            val make = RcPrefillMapper.matchMake(rtoMaker, rtoModel)
            val model = make?.let { RcPrefillMapper.matchModel(it, rtoModel) } ?: rtoModel
            return CarImages.forCar(make ?: rtoMaker, model, fuel)
        }
        assertEquals(R.drawable.model_tata_nexon_ev, pictureFor("TATA PASSENGER ELECTRIC MOBILITY LTD", "NEXON EV XZ PLUS LUX", "Electric"))
        assertEquals(R.drawable.model_skoda_kylaq, pictureFor("SKODA AUTO VOLKSWAGEN INDIA PVT LTD", "KYLAQ SIGNATURE 1.0 TSI", "Petrol"))
        assertEquals(R.drawable.model_mahindra_thar_roxx, pictureFor("MAHINDRA & MAHINDRA LIMITED", "THAR ROXX AX7L 4WD", "Diesel"))
        assertEquals(R.drawable.model_maruti_swift, pictureFor("MARUTI SUZUKI INDIA LTD", "SWIFT VXI", "Petrol"))
        assertEquals(R.drawable.model_tesla_model_y, pictureFor("TESLA INDIA MOTORS AND ENERGY PVT LTD", "MODEL Y RWD", "Electric"))
    }

    @Test
    fun `unknown cars fall back to the icon`() {
        assertNull(CarImages.forCar("Ford", "EcoSport", "Petrol"))
        assertNull(CarImages.forCar("Tata", "Nano", "Petrol"))
        assertNull(CarImages.forCar(null, "Swift", "Petrol"))
        assertNull(CarImages.forCar("Hyundai", "", "Petrol"))
    }

    @Test
    fun `every picture can be picked from the model list`() {
        val pickable = MODELS_BY_MAKE.flatMap { (make, models) -> models.map { make to it } }
        val reachable = pickable.mapNotNull { (make, model) -> CarImages.forCar(make, model, "Petrol") }.toSet() +
            pickable.mapNotNull { (make, model) -> CarImages.forCar(make, model, "Electric") }
        assertEquals(emptySet<Int>(), CarImages.all() - reachable)
    }
}
