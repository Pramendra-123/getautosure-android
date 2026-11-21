package com.enginesense.app.dealerreport

import com.enginesense.app.vehicle.PidId
import org.junit.Assert.assertEquals
import org.junit.Test

class DealerFindingsTest {

    @Test
    fun `reading values are written the way the report shows them`() {
        assertEquals("115°C", DealerFindings.valueText(PidId.COOLANT_TEMP_C, 115.0))
        assertEquals("11.8 V", DealerFindings.valueText(PidId.CONTROL_MODULE_VOLTAGE, 11.8))
        assertEquals("+12.5%", DealerFindings.valueText(PidId.LONG_TERM_FUEL_TRIM_1, 12.5))
    }
}
