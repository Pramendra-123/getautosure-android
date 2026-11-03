package com.enginesense.app.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.enginesense.app.ScanResult
import com.enginesense.app.obd.Severity
import com.enginesense.app.storage.DealerProfile
import com.enginesense.app.vehicle.CategoryState
import com.enginesense.app.vehicle.HealthCategory
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.PidStatus
import com.enginesense.app.vehicle.SensorRating
import com.enginesense.app.vehicle.benchmark
import com.enginesense.app.vehicle.rate
import com.enginesense.app.vehicle.scannedSystemGroupFor
import com.enginesense.app.vehicle.ScannedSystemGroup
import com.enginesense.app.vehiclelookup.RcLookupResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** What's known about the vehicle outside the scan itself — whatever the user actually entered, nothing guessed. */
data class VehicleReportInfo(
    val brand: String?,
    val nickname: String,
    val model: String,
    val year: Int?,
    val fuelType: String?,
    val odometerKm: Int?,
    val plate: String?,
    /** The RTO record confirmed when the car was added, if it was added by registration number. */
    val rc: RcLookupResult? = null
)

/**
 * Builds the shareable PDF report using Android's built-in [PdfDocument] — deliberately no new
 * Gradle dependency, same reasoning as the rest of this app's "smallest change" architecture.
 * Every figure here traces back to real fields on [ScanResult]; nothing is invented, and a
 * value this app never collects (VIN when unread, odometer when never entered) is shown as
 * "Not available" rather than guessed. See docs on [ScanResult] for the field-by-field source.
 */
object VehicleReportPdfGenerator {

    /**
     * [dealerProfile]/[dealerLogo] are null for every normal (free, non-dealer) report — that
     * path is byte-for-byte what it always was. When set (see [com.enginesense.app.storage.DealerProfileStore]),
     * the cover page's header and every recurring page header carry the dealer's identity instead
     * of/alongside the plain GetAutoSure header, per the reviewed mockup.
     */
    fun generate(
        context: Context,
        result: ScanResult,
        vehicle: VehicleReportInfo,
        dealerProfile: DealerProfile? = null,
        dealerLogo: Bitmap? = null
    ): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        return generateTo(dir, result, vehicle, dealerProfile, dealerLogo, LocalDate.now())
    }

    /** [generate] into [dir] — also what the sample-report test calls, with a fixed [today]. */
    internal fun generateTo(
        dir: File,
        result: ScanResult,
        vehicle: VehicleReportInfo,
        dealerProfile: DealerProfile?,
        dealerLogo: Bitmap?,
        today: LocalDate
    ): File {
        val doc = PdfDocument()
        val dateFmt = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        val reportId = "GetAutoSure-" + dateFmt.format(Date(result.timestampMillis))
        val scannedOn = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(result.timestampMillis))
        val b = ReportBuilder(doc, dealerProfile, dealerLogo)

        drawCoverPage(b, result, vehicle, reportId, scannedOn, dealerProfile, dealerLogo)
        drawHealthAndFindingsPage(b, result)
        drawPapersSection(b, vehicle, today)
        drawSensorsPage(b, result)
        drawNextStepsPage(b, result)
        b.finish()

        val file = File(dir, "$reportId.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    // ---------- page 1 ----------

    private fun drawCoverPage(
        b: ReportBuilder, result: ScanResult, vehicle: VehicleReportInfo, reportId: String, scannedOn: String,
        dealerProfile: DealerProfile?, dealerLogo: Bitmap?
    ) {
        b.newPage("Vehicle health report", drawBrandHeader = false)

        if (dealerProfile != null) {
            drawDealerCoverHeader(b.canvas, b.y, dealerProfile, dealerLogo, reportId, scannedOn)
            b.y += 80f
        } else {
            b.y += drawBrandCoverHeader(b.canvas, b.y, reportId, scannedOn)
        }

        b.canvas.drawLine(MARGIN, b.y, CONTENT_RIGHT, b.y, strokePaint(ORANGE, 1.2f))
        b.y += 20f

        if (result.isDemo) {
            b.y += drawBanner(b.canvas, b.y, "Demo data — this report does not reflect a real vehicle scan.", AMBER_BG, AMBER_BORDER, Color.parseColor("#412402"))
            b.y += 10f
        }

        b.canvas.drawText("Vehicle health report", MARGIN, b.y + 18f, textPaint(INK, 19f, bold = true))
        b.y += 24f
        val subtitle = listOfNotNull(
            vehicle.year?.toString(),
            listOfNotNull(vehicle.brand, vehicle.model.ifBlank { null }).joinToString(" ").ifBlank { null },
            vehicle.fuelType
        ).joinToString("  ·  ").ifBlank { "Vehicle details not entered" }
        b.canvas.drawText(subtitle, MARGIN, b.y + 11f, textPaint(GRAY, 10f))
        b.y += 26f

        // Vehicle information / Device & report, two columns
        val col2X = MARGIN + 260f
        b.canvas.drawText("VEHICLE INFORMATION", MARGIN, b.y, textPaint(ORANGE, 8.5f, bold = true))
        b.canvas.drawText("REPORT DETAILS", col2X, b.y, textPaint(ORANGE, 8.5f, bold = true))
        b.y += 14f

        val vin = result.vehicleId.vin?.takeIf { it.isNotBlank() } ?: "Not available"
        val odometer = vehicle.odometerKm?.let { "%,d km".format(it) } ?: "Not entered"
        val plate = vehicle.plate?.takeIf { it.isNotBlank() } ?: vehicle.rc?.registrationNumber ?: "Not entered"
        val makeModel = listOfNotNull(vehicle.brand, vehicle.model.ifBlank { null }).joinToString(" ").ifBlank { "Not entered" }

        val leftFields = listOf("Make / model" to makeModel, "Registration" to plate, "VIN" to vin, "Odometer" to odometer)
        val rightFields = listOf("Scanner" to "GetAutoSure OBD2", "Report ID" to reportId, "Scanned on" to scannedOn, "Fuel type" to (vehicle.fuelType ?: "Not entered"))
        for (i in leftFields.indices) {
            drawField(b.canvas, MARGIN, b.y, leftFields[i].first, leftFields[i].second)
            drawField(b.canvas, col2X, b.y, rightFields[i].first, rightFields[i].second)
            b.y += 30f
        }
        b.y += 6f

        // Health score box
        val health = result.health
        val stateColor = when {
            health.score == null -> GRAY
            health.categories.any { it.state == CategoryState.ATTENTION } -> ATTENTION
            health.categories.any { it.state == CategoryState.WATCH } -> WATCH
            else -> GOOD
        }
        val criticalCount = result.findings.count { it.severity == Severity.CRITICAL }
        val watchCount = result.findings.count { it.severity == Severity.MODERATE || it.severity == Severity.MINOR }
        val boxH = 58f
        val boxRect = RectF(MARGIN, b.y, CONTENT_RIGHT, b.y + boxH)
        b.canvas.drawRoundRect(boxRect, 8f, 8f, fillPaint(GRAY_LIGHT))
        b.canvas.drawRoundRect(boxRect, 8f, 8f, strokePaint(BORDER, 0.8f))

        val scoreText = health.score?.toString() ?: "—"
        b.canvas.drawText(scoreText, MARGIN + 18f, b.y + 40f, textPaint(stateColor, 30f, bold = true))
        val scoreW = textPaint(stateColor, 30f, bold = true).measureText(scoreText)
        if (health.score != null) b.canvas.drawText("/100", MARGIN + 18f + scoreW + 2f, b.y + 40f, textPaint(GRAY, 11f))

        // Clear of "100/100" whatever its width, so the label never runs into the score.
        val labelX = maxOf(MARGIN + 95f, MARGIN + 18f + scoreW + 2f + textPaint(GRAY, 11f).measureText("/100") + 16f)
        b.canvas.drawText(health.label, labelX, b.y + 22f, textPaint(stateColor, 12f, bold = true))
        val countsText = if (result.findings.isEmpty()) "Nothing flagged this scan" else "$criticalCount critical · $watchCount to watch"
        b.canvas.drawText(countsText, labelX, b.y + 36f, textPaint(GRAY, 9f))
        b.canvas.drawText("Monitoring 6 systems · ${result.snapshot.readings.size} sensors read this scan", labelX, b.y + 49f, textPaint(GRAY, 8.5f))
        b.y += boxH + 14f

        b.y += drawBanner(
            b.canvas, b.y,
            "Scope of this scan: covers the Powertrain system (engine, fuel, emissions) via the vehicle's standard " +
                "OBD2 port. ABS, airbags, body control and other modules need manufacturer-specific tools and are not included.",
            AMBER_BG, AMBER_BORDER, Color.parseColor("#412402")
        )
        b.y += 12f

        b.y += drawWrapped(
            b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN,
            "This report reflects sensor data read directly from the vehicle's OBD2 port via a GetAutoSure scan. " +
                "Nothing in it is estimated or guessed — a reading not available on this scan is shown as such, not invented.",
            textPaint(GRAY, 8.5f)
        )
        b.y += 18f

        drawKeyReadings(b, result.snapshot, "Key readings")
    }

    // ---------- vehicle papers (RTO) ----------

    private fun drawPapersSection(b: ReportBuilder, vehicle: VehicleReportInfo, today: LocalDate) {
        val rc = vehicle.rc
        if (rc != null) {
            drawRtoSection(b, rc, today, "Vehicle papers")
            return
        }
        drawSectionTitle(b, "RTO record", "Insurance, PUC, fitness and road tax dates from the official registration record.", "Vehicle papers")
        b.y += drawBanner(
            b.canvas, b.y,
            "Not included — this car was added without looking up its registration number. Add the car by registration number to include its RTO record and expiry dates.",
            GRAY_LIGHT, BORDER, GRAY
        )
        b.y += 16f
    }

    // ---------- page 2 ----------

    private fun drawHealthAndFindingsPage(b: ReportBuilder, result: ScanResult) {
        b.newPage("Health & findings")
        b.canvas.drawText("Health breakdown", MARGIN, b.y + 14f, textPaint(INK, 16f, bold = true))
        b.y += 20f
        b.y += drawWrapped(
            b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN,
            "Each system gets a 0-100 score from what this scan found in it. \"No data\" means the car didn't report " +
                "what that system needs, so it isn't scored rather than guessed.",
            textPaint(GRAY, 8.5f)
        )
        b.y += 14f

        val whatItCovers = mapOf(
            HealthCategory.ENGINE to "Engine load, engine speed, timing, misfires",
            HealthCategory.COOLING to "Coolant temperature, fan, thermostat",
            HealthCategory.FUEL_COMBUSTION to "How the engine balances air and fuel",
            HealthCategory.BATTERY to "Voltage, charging",
            HealthCategory.DIAGNOSTICS to "Fault codes the car has stored or noticed",
            HealthCategory.EMISSIONS to "Check-engine light, emissions self-tests"
        )
        val headers = listOf("System", "Score / status", "State", "What it covers")
        val colX = floatArrayOf(MARGIN, MARGIN + 115f, MARGIN + 195f, MARGIN + 300f)
        b.y += drawTableHeader(b.canvas, b.y, colX, headers)

        result.health.categories.forEach { cat ->
            val stateLabel = when (cat.state) {
                CategoryState.GOOD -> "Good"; CategoryState.WATCH -> "Watch"
                CategoryState.ATTENTION -> "Attention"; CategoryState.UNAVAILABLE -> "No data"
            }
            val stateColor = when (cat.state) {
                CategoryState.GOOD -> GOOD; CategoryState.WATCH -> WATCH
                CategoryState.ATTENTION -> ATTENTION; CategoryState.UNAVAILABLE -> GRAY
            }
            val scoreText = cat.score?.let { "$it / 100" } ?: stateLabel
            b.ensure(22f, "Health & findings")
            b.canvas.drawText(cat.category.displayName, colX[0], b.y + 14f, textPaint(INK, 9f, bold = true))
            b.canvas.drawText(scoreText, colX[1], b.y + 14f, textPaint(INK, 9f))
            b.canvas.drawText(stateLabel, colX[2], b.y + 14f, textPaint(stateColor, 9f, bold = true))
            drawWrapped(b.canvas, colX[3], b.y + 1f, CONTENT_RIGHT - colX[3], whatItCovers[cat.category] ?: "", textPaint(GRAY, 8f))
            b.canvas.drawLine(MARGIN, b.y + 20f, CONTENT_RIGHT, b.y + 20f, strokePaint(BORDER, 0.5f))
            b.y += 22f
        }
        b.y += 12f

        val fc = result.fuelCombustionInsight
        b.ensure(50f, "Health & findings")
        b.canvas.drawText("Fuel & Combustion detail", MARGIN, b.y + 11f, textPaint(INK, 11f, bold = true))
        b.y += 16f
        b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, fc.interpretation, textPaint(INK, 9f))
        b.y += 18f

        b.ensure(30f, "Health & findings")
        b.canvas.drawText("Findings & fault codes", MARGIN, b.y + 14f, textPaint(INK, 16f, bold = true))
        b.y += 20f
        val summaryText = if (result.findings.isEmpty()) "No faults found in this scan."
            else "${result.findings.size} finding${if (result.findings.size == 1) "" else "s"} from this scan. Severity reflects how soon it's worth acting on, not how expensive it is to fix."
        b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, summaryText, textPaint(GRAY, 8.5f))
        b.y += 16f

        if (result.findings.isNotEmpty()) {
            b.canvas.drawText("SUMMARY", MARGIN, b.y, textPaint(ORANGE, 8.5f, bold = true))
            b.y += 12f
            val dtcColX = floatArrayOf(MARGIN, MARGIN + 70f, CONTENT_RIGHT - 70f)
            b.y += drawTableHeader(b.canvas, b.y, dtcColX, listOf("Code", "What it is", "Severity"))
            result.findings.forEach { f ->
                b.ensure(20f, "Health & findings")
                val code = f.relatedDtcCodes.firstOrNull() ?: "—"
                val (sevLabel, sevColor) = severityLabelColor(f.severity)
                b.canvas.drawText(code, dtcColX[0], b.y + 13f, textPaint(INK, 9f, bold = true))
                drawWrapped(b.canvas, dtcColX[1], b.y + 1f, dtcColX[2] - dtcColX[1] - 8f, f.title, textPaint(INK, 9f))
                b.canvas.drawText(sevLabel, dtcColX[2], b.y + 13f, textPaint(sevColor, 9f, bold = true))
                b.canvas.drawLine(MARGIN, b.y + 18f, CONTENT_RIGHT, b.y + 18f, strokePaint(BORDER, 0.5f))
                b.y += 20f
            }
            b.y += 10f

            result.findings.forEach { f ->
                val (sevLabel, sevColor) = severityLabelColor(f.severity)
                b.ensure(70f, "Health & findings")
                val startY = b.y
                val cardLeft = MARGIN
                val textLeft = MARGIN + 60f
                var innerY = startY + 14f
                b.canvas.drawText(sevLabel, cardLeft + 10f, innerY, textPaint(sevColor, 9f, bold = true))
                val title = if (f.relatedDtcCodes.isNotEmpty()) "${f.relatedDtcCodes.first()} — ${f.title}" else f.title
                b.canvas.drawText(title, textLeft, innerY, textPaint(INK, 9.5f, bold = true))
                innerY += 14f
                innerY += drawWrapped(b.canvas, textLeft, innerY, CONTENT_RIGHT - textLeft - 10f, f.plainLanguageSummary, textPaint(GRAY, 8.5f))
                innerY += 6f
                innerY += drawWrapped(b.canvas, textLeft, innerY, CONTENT_RIGHT - textLeft - 10f, "Recommended action: ${f.recommendedAction}", textPaint(INK, 8.5f, bold = false))
                innerY += 10f
                val cardRect = RectF(cardLeft, startY, CONTENT_RIGHT, innerY)
                b.canvas.drawRoundRect(cardRect, 6f, 6f, fillPaint(AMBER_BG))
                b.canvas.drawRoundRect(cardRect, 6f, 6f, strokePaint(AMBER_BORDER, 0.8f))
                // redraw text on top since fill was drawn after measuring (draw order fix below)
                redrawFindingCard(b.canvas, cardLeft, textLeft, startY, sevLabel, sevColor, title, f.plainLanguageSummary, f.recommendedAction, CONTENT_RIGHT)
                b.y = innerY + 12f
            }
        }

        b.ensure(140f, "Health & findings")
        b.canvas.drawText("SCANNED SYSTEMS", MARGIN, b.y, textPaint(ORANGE, 8.5f, bold = true))
        b.y += 12f
        b.y += drawWrapped(
            b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN,
            "Standard SAE diagnostic groups, by code range — this shows which groups this scan found something in, " +
                "not a second health score. All within the Powertrain scope noted on page 1.",
            textPaint(GRAY, 8f)
        )
        b.y += 8f

        val codes = result.findings.flatMap { it.relatedDtcCodes }
        val scannedColX = floatArrayOf(MARGIN, MARGIN + 190f, MARGIN + 290f)
        b.y += drawTableHeader(b.canvas, b.y, scannedColX, listOf("System", "Code range", "Result"))
        ScannedSystemGroup.entries.forEach { group ->
            val hitCount = codes.count { scannedSystemGroupFor(it) == group }
            b.ensure(22f, "Health & findings")
            b.canvas.drawText(group.label, scannedColX[0], b.y + 14f, textPaint(INK, 9f))
            b.canvas.drawText(group.codeRange, scannedColX[1], b.y + 14f, textPaint(GRAY, 8.5f))
            drawStatusBadge(b.canvas, scannedColX[2] + 7f, b.y + 10f, 7f, hitCount)
            b.canvas.drawLine(MARGIN, b.y + 20f, CONTENT_RIGHT, b.y + 20f, strokePaint(BORDER, 0.5f))
            b.y += 22f
        }
        b.y += 18f
    }

    private fun redrawFindingCard(
        canvas: Canvas, cardLeft: Float, textLeft: Float, startY: Float,
        sevLabel: String, sevColor: Int, title: String, summary: String, action: String, right: Float
    ) {
        var innerY = startY + 14f
        canvas.drawText(sevLabel, cardLeft + 10f, innerY, textPaint(sevColor, 9f, bold = true))
        canvas.drawText(title, textLeft, innerY, textPaint(INK, 9.5f, bold = true))
        innerY += 14f
        innerY += drawWrapped(canvas, textLeft, innerY, right - textLeft - 10f, summary, textPaint(GRAY, 8.5f))
        innerY += 6f
        drawWrapped(canvas, textLeft, innerY, right - textLeft - 10f, "Recommended action: $action", textPaint(INK, 8.5f))
    }

    // ---------- readiness & sensors ----------

    private fun drawSensorsPage(b: ReportBuilder, result: ScanResult) {
        drawReadiness(b, result.readiness, "Sensor readings")

        // PIDs this vehicle doesn't support are left out entirely rather than listed as noise —
        // noted once, in one line, instead of a long "Not supported" row per PID.
        val allReadings = result.snapshot.readings.values
        val shown = allReadings.filter { it.status != PidStatus.UNSUPPORTED }.sortedBy { it.pid.displayName }
        val unsupportedCount = allReadings.size - shown.size
        val summaryText = "${shown.size} sensors this vehicle supports" +
            (if (unsupportedCount > 0) " ($unsupportedCount other monitored sensors aren't supported by this vehicle and are left out)." else ".") +
            " \"Normal\" is the range for a warmed-up, healthy engine where one exists; readings that change with how the car is driven are shown without a verdict."
        drawSectionTitle(b, "All sensor readings", summaryText, "Sensor readings")

        val colX = floatArrayOf(MARGIN, MARGIN + 200f, MARGIN + 290f)
        b.ensure(44f, "Sensor readings")
        b.y += drawTableHeader(b.canvas, b.y, colX, listOf("Sensor", "Value", "Normal"))
        shown.forEach { reading ->
            b.ensure(20f, "Sensor readings")
            val value = reading.value
            val valueText = when {
                reading.status == PidStatus.VERIFIED && value != null -> "${trimNum(value)} ${reading.pid.unit}".trim()
                reading.status == PidStatus.SUPPORTED_NO_DATA -> "No data this scan"
                reading.status == PidStatus.IMPLAUSIBLE -> "Unreliable reading"
                else -> "—"
            }
            val benchmark = reading.pid.benchmark()
            val rangeText = benchmark?.typicalRange?.let { "${trimNum(it.start)}–${trimNum(it.endInclusive)} ${reading.pid.unit}".trim() } ?: "—"
            val (chip, chipColor) = sensorChip(reading.pid, reading.status, value)
            b.canvas.drawText(ellipsize(reading.pid.displayName, colX[1] - colX[0] - 10f, textPaint(INK, 8.5f)), colX[0] + 6f, b.y + 13f, textPaint(INK, 8.5f))
            b.canvas.drawText(valueText, colX[1] + 6f, b.y + 13f, textPaint(INK, 8.5f, bold = true))
            b.canvas.drawText(rangeText, colX[2] + 6f, b.y + 13f, textPaint(GRAY, 8f))
            if (chip != null) drawChip(b.canvas, CONTENT_RIGHT, b.y + 3f, chip, chipColor)
            b.canvas.drawLine(MARGIN, b.y + 19f, CONTENT_RIGHT, b.y + 19f, strokePaint(BORDER, 0.5f))
            b.y += 20f
        }
        b.y += 16f
    }

    /** The verdict chip for one sensor row — null when there's nothing honest to say (no benchmark, no data). */
    private fun sensorChip(pid: PidId, status: PidStatus, value: Double?): Pair<String?, Int> {
        if (status == PidStatus.IMPLAUSIBLE) return "! Unreliable" to WATCH
        if (status != PidStatus.VERIFIED || value == null) return null to GRAY
        val benchmark = pid.benchmark() ?: return null to GRAY
        if ((pid == PidId.COOLANT_TEMP_C || pid == PidId.ENGINE_OIL_TEMP_C) && value < benchmark.typicalRange.start) return "Warming up" to GRAY
        if (benchmark.contextDependent) return (if (value in benchmark.typicalRange) "✓ Typical" else "Varies with driving") to (if (value in benchmark.typicalRange) GOOD else GRAY)
        return when (pid.rate(value)) {
            SensorRating.GOOD -> "✓ Normal" to GOOD
            SensorRating.WATCH -> "! Watch" to WATCH
            SensorRating.ATTENTION -> "! Check" to ATTENTION
            SensorRating.NOT_RATED -> null to GRAY
        }
    }

    // ---------- next steps ----------

    private fun drawNextStepsPage(b: ReportBuilder, result: ScanResult) {
        b.ensure(180f, "Next steps")
        b.canvas.drawText("Next steps", MARGIN, b.y + 14f, textPaint(INK, 16f, bold = true))
        b.y += 24f

        val report = result.aiReport
        b.canvas.drawText(report.headline, MARGIN, b.y + 12f, textPaint(INK, 11f, bold = true))
        b.y += 16f
        b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, report.summary, textPaint(INK, 9f))
        b.y += 14f

        report.sections.forEach { section ->
            b.ensure(40f, "Next steps")
            b.canvas.drawText(section.heading, MARGIN, b.y + 11f, textPaint(INK, 10f, bold = true))
            b.y += 15f
            b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, section.body, textPaint(GRAY, 8.5f))
            b.y += 10f
        }

        b.ensure(50f, "Next steps")
        b.y += drawBanner(
            b.canvas, b.y,
            "Not sure what this means for you? Book a 15-minute video call with a real mechanic in the GetAutoSure app, with this report in hand.",
            Color.parseColor("#FFF8EC"), AMBER_BORDER, Color.parseColor("#412402")
        )
        b.y += 16f

        b.ensure(60f, "Next steps")
        b.canvas.drawText("What this report doesn't cover", MARGIN, b.y + 9f, textPaint(GRAY, 8f, bold = true))
        b.y += 13f
        b.y += drawWrapped(
            b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN,
            "This report is built entirely from the vehicle's OBD2 electronic data, scoped to the Powertrain system " +
                "(see page 1). It does not include tyre tread, brake pad thickness, suspension condition, bodywork, rust, " +
                "interior condition, or ABS/airbag/body-module codes — these need a physical inspection or manufacturer-" +
                "specific tools an OBD2 scan structurally cannot provide. ${report.disclaimer}",
            textPaint(GRAY, 8f)
        )
        b.y += 10f
        b.y += drawWrapped(
            b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN,
            "Generated by GetAutoSure · getautosure.com",
            textPaint(GRAY, 7.5f)
        )
    }

    // ---------- shared drawing helpers ----------

    /** Drops a trailing ".0" so plausible-range bounds like 0.0–655.35 read as "0–655.35", not "0.0–655.35". */
    private fun trimNum(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

    private fun severityLabelColor(severity: Severity): Pair<String, Int> = when (severity) {
        // Same words the app shows on each finding.
        Severity.CRITICAL -> "CRITICAL" to ATTENTION
        Severity.MODERATE -> "MODERATE" to WATCH
        Severity.MINOR -> "MINOR" to WATCH
        Severity.INFO -> "INFO" to GRAY
    }


    private fun drawStatusBadge(canvas: Canvas, cx: Float, cy: Float, r: Float, count: Int) {
        val color = if (count > 0) ATTENTION else GOOD
        canvas.drawCircle(cx, cy, r, fillPaint(color))
        val label = if (count > 0) count.toString() else "✓"
        val p = textPaint(WHITE, 8f, bold = true)
        p.textAlign = Paint.Align.CENTER
        canvas.drawText(label, cx, cy + 3f, p)
    }

}
