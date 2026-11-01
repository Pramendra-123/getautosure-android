package com.enginesense.app.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enginesense.app.dealerreport.DealerConditionInput
import com.enginesense.app.dealerreport.DealerConditionItem
import com.enginesense.app.vehicle.DealerPrice
import androidx.test.platform.app.InstrumentationRegistry
import com.enginesense.app.ScanResult
import com.enginesense.app.obd.VehicleIdInfo
import com.enginesense.app.storage.DealerProfile
import com.enginesense.app.vehicle.DiagnosticRulesEngine
import com.enginesense.app.vehicle.DtcReading
import com.enginesense.app.vehicle.DtcSource
import com.enginesense.app.vehicle.HealthScoreCalculator
import com.enginesense.app.vehicle.IgnitionType
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidReading
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehicle.PrePurchaseReportBuilder
import com.enginesense.app.vehicle.ReadinessMonitor
import com.enginesense.app.vehicle.ReadinessStatus
import com.enginesense.app.vehicle.VehicleSnapshot
import com.enginesense.app.vehicle.ai.AiVehicleContext
import com.enginesense.app.vehicle.ai.TemplateAiCarHealthReportService
import com.enginesense.app.vehiclelookup.RcLookupResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Renders the sample PDFs kept in brand/reports with the real generators — PdfDocument needs a
 * device, so this is an instrumented test. Run it, then pull the files:
 *   adb exec-out run-as com.enginesense.app cat cache/sample-reports/<name>.pdf > brand/reports/<name>.pdf
 * Every registration, VIN and RTO value here is made up; the clean car's sensor values come
 * from a real Maruti Suzuki scan.
 */
@RunWith(AndroidJUnit4::class)
class SampleReportsTest {

    private val today = LocalDate.of(2026, 11, 1)
    private val scannedAt = 1_793_453_394_432L // 31 Oct 2026, 6:59 PM IST
    private val outDir: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "sample-reports").apply { mkdirs() }
    }

    private fun readings(vararg values: Pair<PidId, Double?>): Map<PidId, PidReading> = values.associate { (pid, v) ->
        pid to PidReading(pid, if (v == null) PidStatus.UNSUPPORTED else PidStatus.VERIFIED, v, null)
    }

    private fun scan(
        readings: Map<PidId, PidReading>, dtcs: List<DtcReading>, readiness: ReadinessStatus, vin: String?, profile: String
    ): ScanResult {
        val snapshot = VehicleSnapshot(scannedAt, "Maruti Suzuki", readings, dtcs)
        val findings = DiagnosticRulesEngine.evaluate(snapshot, emptyMap())
        val health = HealthScoreCalculator.calculate(snapshot, findings)
        val vehicleId = VehicleIdInfo(vin, null, null)
        val aiContext = AiVehicleContext.from(snapshot, findings, vehicleId, readiness)
        val aiReport = runBlocking { TemplateAiCarHealthReportService().generateReport(aiContext) }
        return ScanResult(scannedAt, profile, false, snapshot, findings, health, aiReport, aiContext, vehicleId, readiness, health.fuelCombustionInsight)
    }

    private fun readiness(vararg notReady: String) = ReadinessStatus(
        milOn = false, dtcCount = 0, ignitionType = IgnitionType.SPARK,
        monitors = listOf("Misfire", "Fuel System", "Components", "Catalyst", "Oxygen Sensor", "Oxygen Sensor Heater", "EGR/VVT System")
            .map { ReadinessMonitor(it, supported = true, ready = it !in notReady) } +
            listOf(ReadinessMonitor("Evaporative System", supported = false, ready = true))
    )

    private fun rc(
        insuranceUpto: String = "14-Mar-2027", puccUpto: String = "10-Jan-2027", mode: String = "customer",
        financed: Boolean? = null, ownerCount: Int? = null, chassisMatch: Boolean? = true
    ) = RcLookupResult(
        registrationNumber = "MH12AB1234", make = "MARUTI SUZUKI", model = "SWIFT VXI", fuelType = "PETROL",
        color = "PEARL ARCTIC WHITE", category = "Motor Car(LMV)", manufacturingMonth = "2021-03", registrationDate = "02-Apr-2021",
        registeredAt = "PUNE RTO", cubicCapacity = "1197.00", insuranceCompany = "ICICI Lombard", insuranceUpto = insuranceUpto,
        puccUpto = puccUpto, fitnessUpto = "01-Apr-2036", taxUpto = "LTT", partial = false,
        source = "RTO (VAHAN) records via Surepass RC V2", chassisMatch = chassisMatch, bodyType = "HATCHBACK",
        fetchedAt = "2026-10-31T13:20:00Z", mode = mode, ownerCount = ownerCount, financed = financed,
        financer = if (financed == true) "HDFC BANK LTD" else null, rcStatus = if (mode == "dealer") "ACTIVE" else null,
        blacklistStatus = if (mode == "dealer") "NA" else null, chassisNumber = if (mode == "dealer") "XX9SAMPLEK0123456" else null,
        engineNumber = if (mode == "dealer") "K12MN7654321" else null, emissionNorms = "BHARAT STAGE VI", cylinders = 4,
        seatCapacity = 5, rtoCode = "MH12"
    )

    /** The real Maruti scan from 31 Oct 2026 — engine idling 3½ minutes after a cold start. */
    private val realMaruti = readings(
        PidId.ENGINE_LOAD to 22.75, PidId.COOLANT_TEMP_C to 50.0, PidId.SHORT_TERM_FUEL_TRIM_1 to 8.59,
        PidId.LONG_TERM_FUEL_TRIM_1 to -3.91, PidId.INTAKE_MANIFOLD_PRESSURE to 29.0, PidId.O2_SENSOR_B1S1_VOLTAGE to 0.125,
        PidId.INTAKE_AIR_TEMP_C to 26.0, PidId.RPM to 838.0, PidId.SPEED_KMH to 0.0, PidId.TIMING_ADVANCE to 5.0,
        PidId.THROTTLE_POSITION to 14.51, PidId.RUN_TIME_SINCE_START to 215.0, PidId.DISTANCE_WITH_MIL_ON to 0.0,
        PidId.COMMANDED_EVAP_PURGE to 0.0, PidId.DISTANCE_SINCE_CODES_CLEARED to 10_921.0, PidId.BAROMETRIC_PRESSURE to 91.0,
        PidId.CONTROL_MODULE_VOLTAGE to 14.11, PidId.ABSOLUTE_LOAD_VALUE to 18.04, PidId.RELATIVE_THROTTLE_POSITION to 5.1,
        PidId.ACCELERATOR_PEDAL_POSITION_D to 14.51, PidId.COMMANDED_THROTTLE_ACTUATOR to 6.27,
        PidId.FUEL_PRESSURE to null, PidId.MAF_AIR_FLOW_RATE to null, PidId.FUEL_LEVEL to null, PidId.CATALYST_TEMP_B1S1 to null,
        PidId.AMBIENT_AIR_TEMP_C to null, PidId.ENGINE_OIL_TEMP_C to null, PidId.ODOMETER to null
    )

    @Test
    fun cleanPrePurchaseReport() {
        val result = scan(realMaruti, emptyList(), readiness(), vin = null, profile = "Real dongle")
        val report = PrePurchaseReportBuilder.build(
            result.findings, result.health.score, result.readiness, result.snapshot, vin = null, dashboardKm = 48_500.0,
            rc = rc(chassisMatch = null), registration = null, exShowroomPriceInr = 750_000.0, buyerEnteredYear = null,
            currentYear = 2026, today = today
        )
        write("pre-purchase-clean-sample", PrePurchaseReportPdfGenerator.generateTo(outDir, result, report, null, null, today))
    }

    @Test
    fun redFlagPrePurchaseReport() {
        val tampered = readings(
            PidId.ODOMETER to 84_210.0, PidId.DISTANCE_SINCE_CODES_CLEARED to 18.0, PidId.WARM_UPS_SINCE_CODES_CLEARED to 3.0,
            PidId.TIME_SINCE_CODES_CLEARED to 40.0, PidId.COOLANT_TEMP_C to 85.0, PidId.RUN_TIME_SINCE_START to 60.0,
            PidId.CONTROL_MODULE_VOLTAGE to 14.2, PidId.SHORT_TERM_FUEL_TRIM_1 to 6.3, PidId.LONG_TERM_FUEL_TRIM_1 to 11.7,
            PidId.RPM to 790.0, PidId.ENGINE_LOAD to 24.0, PidId.INTAKE_MANIFOLD_PRESSURE to 33.0, PidId.DISTANCE_WITH_MIL_ON to 0.0
        )
        val dtcs = listOf(DtcReading("P0420", DtcSource.PERMANENT), DtcReading("P0171", DtcSource.PENDING))
        val result = scan(tampered, dtcs, readiness("Catalyst"), vin = "XX9SAMPLEK0123456", profile = "Sample — used car, codes cleared by seller")
        val report = PrePurchaseReportBuilder.build(
            result.findings, result.health.score, result.readiness, result.snapshot, vin = "XX9SAMPLEK0123456", dashboardKm = 62_000.0,
            rc = rc(insuranceUpto = "30-Sep-2026", mode = "dealer", financed = true, ownerCount = 2), registration = null,
            exShowroomPriceInr = 650_000.0, buyerEnteredYear = null, currentYear = 2026, today = today
        )
        write("pre-purchase-sample", PrePurchaseReportPdfGenerator.generateTo(outDir, result, report, null, null, today))
    }

    /** What a dealer hands a buyer: their own branding on the pre-purchase report, with the full (dealer-mode) RTO record. */
    @Test
    fun dealerReport() {
        val result = scan(realMaruti, emptyList(), readiness(), vin = "XX9SAMPLEK0123456", profile = "Real dongle")
        val report = PrePurchaseReportBuilder.build(
            result.findings, result.health.score, result.readiness, result.snapshot, vin = "XX9SAMPLEK0123456", dashboardKm = 48_500.0,
            rc = rc(mode = "dealer", financed = false, ownerCount = 1), registration = null, exShowroomPriceInr = 750_000.0,
            buyerEnteredYear = null, currentYear = 2026, today = today
        )
        val dealer = DealerProfile(
            name = "AutoTrust Motors", tagline = "Certified pre-owned cars", phone = "+91 98765 43210", location = "Baner, Pune"
        )
        write("dealer-report-sample", PrePurchaseReportPdfGenerator.generateTo(outDir, result, report, dealer, null, today))
    }

    /** The second Dealer Sathi option: photos (stand-in drawings here — the app uses the dealer's Gemini-cleaned photos), condition notes and the dealer's own price. */
    @Test
    fun dealerPhotoReport() {
        val result = scan(realMaruti, emptyList(), readiness(), vin = "XX9SAMPLEK0123456", profile = "Real dongle")
        val dealer = DealerProfile(name = "AutoTrust Motors", tagline = "Certified pre-owned cars", phone = "+91 98765 43210", location = "Baner, Pune")
        val report = PrePurchaseReportBuilder.build(
            result.findings, result.health.score, result.readiness, result.snapshot, vin = "XX9SAMPLEK0123456", dashboardKm = 48_500.0,
            rc = rc(mode = "dealer", financed = false, ownerCount = 1), registration = null, exShowroomPriceInr = 750_000.0,
            buyerEnteredYear = null, currentYear = 2026, today = today
        ).copy(dealerPrice = DealerPrice(395_000.0, 425_000.0, dealer.name))
        val extras = DealerReportExtras(
            photos = listOf("Front", "Rear", "Left Side", "Right Side").map { ReportPhoto(it, standInPhoto(it, 800, 520), backgroundCleaned = true) } +
                listOf(ReportPhoto("Photo 1", standInPhoto("Interior", 600, 450), false), ReportPhoto("Photo 2", standInPhoto("Dashboard", 600, 450), false)),
            condition = DealerConditionInput(
                exterior = DealerConditionItem("Good", listOf("Minor scratches", "No visible damage")),
                interior = DealerConditionItem("Good", listOf("Clean, no visible wear", "AC working well")),
                tyres = DealerConditionItem("Fair", listOf("Moderate wear (~40-70%)", "Spare tyre available"))
            )
        )
        write("dealer-photo-report-sample", PrePurchaseReportPdfGenerator.generateTo(outDir, result, report, dealer, null, today, extras))
    }

    /** A plain labelled drawing standing in for a car photo, so no real photo lives in the repo. */
    private fun standInPhoto(label: String, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#EEF1F5"))
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C9D1DC") }
        c.drawRoundRect(RectF(w * 0.12f, h * 0.42f, w * 0.88f, h * 0.70f), 40f, 40f, body)
        c.drawRoundRect(RectF(w * 0.28f, h * 0.26f, w * 0.70f, h * 0.48f), 50f, 50f, body)
        val wheel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4B5563") }
        c.drawCircle(w * 0.28f, h * 0.70f, h * 0.09f, wheel)
        c.drawCircle(w * 0.72f, h * 0.70f, h * 0.09f, wheel)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7280"); textSize = h * 0.07f; textAlign = Paint.Align.CENTER }
        c.drawText("$label (sample photo)", w / 2f, h * 0.92f, text)
        return bmp
    }

    @Test
    fun healthReport() {
        val result = scan(realMaruti, emptyList(), readiness(), vin = null, profile = "Real dongle")
        val vehicle = VehicleReportInfo(
            brand = "Maruti Suzuki", nickname = "My Swift", model = "Swift", year = 2021, fuelType = "Petrol",
            odometerKm = 48_500, plate = "MH12AB1234", rc = rc(chassisMatch = null)
        )
        write("health-report-sample", VehicleReportPdfGenerator.generateTo(outDir, result, vehicle, null, null, today))
    }

    private fun write(name: String, generated: File) {
        val target = File(outDir, "$name.pdf")
        generated.copyTo(target, overwrite = true)
        generated.delete()
        assertTrue(target.length() > 10_000)
    }
}
