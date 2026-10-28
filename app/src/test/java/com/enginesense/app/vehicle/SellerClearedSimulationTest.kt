package com.enginesense.app.vehicle

import com.enginesense.app.obd.Elm327Client
import com.enginesense.app.obd.SimulatedObdTransport
import com.enginesense.app.obd.SimulatedProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The test-mode "codes cleared by seller" car must exercise every Used Car Check signal through the real read path. */
class SellerClearedSimulationTest {

    private fun connected() = SimulatedObdTransport(SimulatedProfile.SELLER_CLEARED_CODES).apply { connect() }

    @Test
    fun `new PIDs read through the supported-PID discovery`() {
        val readings = PidReader(connected()).readAll()
        assertEquals(84210.5, readings.getValue(PidId.ODOMETER).value!!, 0.01)
        assertEquals(18.0, readings.getValue(PidId.DISTANCE_SINCE_CODES_CLEARED).value!!, 0.01)
        assertEquals(3.0, readings.getValue(PidId.WARM_UPS_SINCE_CODES_CLEARED).value!!, 0.01)
        assertEquals(40.0, readings.getValue(PidId.TIME_SINCE_CODES_CLEARED).value!!, 0.01)
        assertEquals(90.0, readings.getValue(PidId.RUN_TIME_SINCE_START).value!!, 0.01)
        assertEquals(85.0, readings.getValue(PidId.COOLANT_TEMP_C).value!!, 0.01)
    }

    @Test
    fun `stored list is empty while pending and permanent codes survive`() {
        val t = connected()
        assertTrue(Elm327Client.parseDtcResponse(t.sendCommand("03")).isEmpty())
        assertEquals(listOf("P0171"), Elm327Client.parseDtcResponse(t.sendCommand("07"), modeEcho = "47"))
        assertEquals(listOf("P0420"), Elm327Client.parseDtcResponse(t.sendCommand("0A"), modeEcho = "4A"))
    }

    @Test
    fun `the full pre-purchase read flags all of it`() {
        val t = connected()
        val snapshot = VehicleSnapshot(
            timestampMillis = 0L,
            vehicleBrand = null,
            readings = PidReader(t).readAll(),
            dtcs = listOf(DtcReading("P0171", DtcSource.PENDING), DtcReading("P0420", DtcSource.PERMANENT))
        )
        val insight = PrePurchaseAnalyzer.analyze(emptyList(), healthScore = 95, readiness = ReadinessReader.read(t), snapshot = snapshot)

        assertEquals(PurchaseVerdict.BUY_WITH_CAUTION, insight.verdict)
        assertEquals(2, insight.fraudRiskFlags.size) // recent reset + permanent code
        assertEquals(2, insight.cautionNotes.size) // pending code + pre-warmed engine
    }
}
