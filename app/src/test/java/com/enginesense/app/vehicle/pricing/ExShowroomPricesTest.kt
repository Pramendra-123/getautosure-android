package com.enginesense.app.vehicle.pricing

import com.enginesense.app.ui.screens.MODELS_BY_MAKE
import com.enginesense.app.vehiclelookup.RcPrefillMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExShowroomPricesTest {

    @Test
    fun `every model the app lists has a price, so no car goes unpriced`() {
        MODELS_BY_MAKE.forEach { (make, models) ->
            models.forEach { model -> assertNotNull("$make $model", ExShowroomPrices.lookup(make, model)) }
        }
    }

    @Test
    fun `every price range runs base to top`() {
        MODELS_BY_MAKE.forEach { (make, models) ->
            models.forEach { model ->
                val r = ExShowroomPrices.lookup(make, model)!!
                assertTrue("$make $model", r.baseInr > 0 && r.topInr >= r.baseInr)
            }
        }
    }

    @Test
    fun `an RTO name for an old Maruti Omni finds the Omni and its price`() {
        val make = RcPrefillMapper.matchMake("MARUTI SUZUKI INDIA LTD", "MARUTI OMNI E MPI STD BSIV")
        assertEquals("Maruti Suzuki", make)
        val model = RcPrefillMapper.matchModel(make!!, "MARUTI OMNI E MPI STD BSIV")
        assertEquals("Omni", model)
        assertNotNull(ExShowroomPrices.lookup(make, model))
    }
}
