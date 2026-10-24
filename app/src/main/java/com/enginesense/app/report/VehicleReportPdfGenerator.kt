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
import com.enginesense.app.vehicle.scannedSystemGroupFor
import com.enginesense.app.vehicle.ScannedSystemGroup
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
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
    val plate: String?
)

/**
 * Builds the shareable PDF report using Android's built-in [PdfDocument] — deliberately no new
 * Gradle dependency, same reasoning as the rest of this app's "smallest change" architecture.
 * Every figure here traces back to real fields on [ScanResult]; nothing is invented, and a
 * value this app never collects (VIN when unread, odometer when never entered) is shown as
 * "Not available" rather than guessed. See docs on [ScanResult] for the field-by-field source.
 */
object VehicleReportPdfGenerator {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_RIGHT = PAGE_W - MARGIN
    private const val CONTENT_BOTTOM = PAGE_H - MARGIN - 20f

    private val ORANGE = Color.parseColor("#FF6B2C")
    private val INK = Color.parseColor("#12151A")
    private val GRAY = Color.parseColor("#6B7280")
    private val GRAY_LIGHT = Color.parseColor("#F8F9FB")
    private val BORDER = Color.parseColor("#E6E8EB")
    private val GOOD = Color.parseColor("#1AA168")
    private val WATCH = Color.parseColor("#B45309")
    private val ATTENTION = Color.parseColor("#B91C1C")
    private val AMBER_BG = Color.parseColor("#FFFBEB")
    private val AMBER_BORDER = Color.parseColor("#F0DBA8")
    private val WHITE = Color.WHITE
    private val DEALER_BADGE = Color.parseColor("#1E3A8A")

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
        val doc = PdfDocument()
        val dateFmt = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        val reportId = "GetAutoSure-" + dateFmt.format(Date(result.timestampMillis))
        val scannedOn = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(result.timestampMillis))
        val b = ReportBuilder(doc, dealerProfile, dealerLogo)

        drawCoverPage(b, result, vehicle, reportId, scannedOn, dealerProfile, dealerLogo)
        drawHealthAndFindingsPage(b, result)
        drawSensorsPage(b, result)
        drawNextStepsPage(b, result)
        b.finish()

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
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
            drawLogo(b.canvas, MARGIN, b.y, 34f)
            val wordX = MARGIN + 42f
            b.canvas.drawText("GetAuto", wordX, b.y + 23f, textPaint(INK, 15f, bold = true))
            val getAutoW = textPaint(INK, 15f, bold = true).measureText("GetAuto")
            b.canvas.drawText("Sure", wordX + getAutoW, b.y + 23f, textPaint(ORANGE, 15f, bold = true))

            val metaPaint = textPaint(GRAY, 8f)
            metaPaint.textAlign = Paint.Align.RIGHT
            b.canvas.drawText("Report ID: $reportId", CONTENT_RIGHT, b.y + 7f, metaPaint)
            b.canvas.drawText("Scanned: $scannedOn", CONTENT_RIGHT, b.y + 17f, metaPaint)
            b.canvas.drawText("Device: GetAutoSure OBD2 Scanner", CONTENT_RIGHT, b.y + 27f, metaPaint)
            b.y += 44f
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
        val plate = vehicle.plate?.takeIf { it.isNotBlank() } ?: "Not entered"
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
        val majorCount = result.findings.count { it.severity == Severity.CRITICAL }
        val watchCount = result.findings.count { it.severity == Severity.MODERATE || it.severity == Severity.MINOR }
        val boxH = 58f
        val boxRect = RectF(MARGIN, b.y, CONTENT_RIGHT, b.y + boxH)
        b.canvas.drawRoundRect(boxRect, 8f, 8f, fillPaint(GRAY_LIGHT))
        b.canvas.drawRoundRect(boxRect, 8f, 8f, strokePaint(BORDER, 0.8f))

        val scoreText = health.score?.toString() ?: "—"
        b.canvas.drawText(scoreText, MARGIN + 18f, b.y + 40f, textPaint(stateColor, 30f, bold = true))
        val scoreW = textPaint(stateColor, 30f, bold = true).measureText(scoreText)
        if (health.score != null) b.canvas.drawText("/100", MARGIN + 18f + scoreW + 2f, b.y + 40f, textPaint(GRAY, 11f))

        val labelX = MARGIN + 95f
        b.canvas.drawText(health.label, labelX, b.y + 22f, textPaint(stateColor, 12f, bold = true))
        val countsText = if (result.findings.isEmpty()) "Nothing flagged this scan" else "$majorCount major · $watchCount to watch"
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

        drawWrapped(
            b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN,
            "This report reflects sensor data read directly from the vehicle's OBD2 port via a GetAutoSure scan. " +
                "Nothing in it is estimated or guessed — a reading not available on this scan is shown as such, not invented.",
            textPaint(GRAY, 8.5f)
        )
    }

    // ---------- page 2 ----------

    private fun drawHealthAndFindingsPage(b: ReportBuilder, result: ScanResult) {
        b.newPage("Health & findings")
        b.canvas.drawText("Health breakdown", MARGIN, b.y + 14f, textPaint(INK, 16f, bold = true))
        b.y += 20f
        b.y += drawWrapped(
            b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN,
            "Engine, Cooling and Fuel & Combustion each receive a 0-100 score, fitted to that system's own readings. " +
                "Battery, Diagnostics and Emissions are shown by status only, not a score — averaging three independent " +
                "pass/watch/attention states into one number would overstate a precision this data doesn't have.",
            textPaint(GRAY, 8.5f)
        )
        b.y += 14f

        val whatItCovers = mapOf(
            HealthCategory.ENGINE to "Load, RPM, timing, misfire counters",
            HealthCategory.COOLING to "Coolant temp, fan operation, thermostat behaviour",
            HealthCategory.FUEL_COMBUSTION to "Fuel trim (STFT/LTFT), O2 sensor response, MAF",
            HealthCategory.BATTERY to "Voltage, charging behaviour",
            HealthCategory.DIAGNOSTICS to "Stored & pending trouble codes",
            HealthCategory.EMISSIONS to "MIL status, readiness monitors"
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
        b.y += 10f

        if (result.findings.isNotEmpty()) {
            b.canvas.drawText("DTC SUMMARY", MARGIN, b.y, textPaint(ORANGE, 8.5f, bold = true))
            b.y += 12f
            val dtcColX = floatArrayOf(MARGIN, MARGIN + 70f, CONTENT_RIGHT - 70f)
            b.y += drawTableHeader(b.canvas, b.y, dtcColX, listOf("DTC", "Description", "Status"))
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

    // ---------- page 4 ----------

    private fun drawSensorsPage(b: ReportBuilder, result: ScanResult) {
        b.newPage("Sensor readings")
        b.canvas.drawText("Sensor readings", MARGIN, b.y + 14f, textPaint(INK, 16f, bold = true))
        b.y += 20f

        // PIDs this vehicle doesn't support are left out entirely rather than listed as noise —
        // noted once, in one line, instead of a long "Not supported" row per PID.
        val allReadings = result.snapshot.readings.values
        val shown = allReadings.filter { it.status.name != "UNSUPPORTED" }.sortedBy { it.pid.displayName }
        val unsupportedCount = allReadings.size - shown.size
        val summaryText = "${shown.size} sensors this vehicle supports" +
            (if (unsupportedCount > 0) " ($unsupportedCount other monitored PIDs aren't supported by this vehicle and are left out)." else ".") +
            " \"Valid range\" is each sensor's full decodable range, not a \"healthy\" band — a reading outside it is flagged as implausible, not just unusual."
        b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, summaryText, textPaint(GRAY, 8.5f))
        b.y += 10f

        val colX = floatArrayOf(MARGIN, MARGIN + 190f, MARGIN + 280f, MARGIN + 380f)
        b.y += drawTableHeader(b.canvas, b.y, colX, listOf("Sensor", "Value", "Valid range", "Status"))
        shown.forEach { reading ->
            b.ensure(18f, "Sensor readings")
            val valueText = when {
                reading.status.name == "VERIFIED" && reading.value != null -> "%.1f %s".format(reading.value, reading.pid.unit)
                reading.status.name == "SUPPORTED_NO_DATA" -> "No data this scan"
                else -> "—"
            }
            val range = reading.pid.plausibleRange
            val rangeText = "${trimNum(range.start)}–${trimNum(range.endInclusive)} ${reading.pid.unit}"
            val statusText = reading.status.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " ")
            b.canvas.drawText(reading.pid.displayName, colX[0], b.y + 12f, textPaint(INK, 8.5f))
            b.canvas.drawText(valueText, colX[1], b.y + 12f, textPaint(INK, 8.5f))
            b.canvas.drawText(rangeText, colX[2], b.y + 12f, textPaint(GRAY, 8f))
            b.canvas.drawText(statusText, colX[3], b.y + 12f, textPaint(GRAY, 8.5f))
            b.canvas.drawLine(MARGIN, b.y + 16f, CONTENT_RIGHT, b.y + 16f, strokePaint(BORDER, 0.5f))
            b.y += 18f
        }
        b.y += 12f

        val readiness = result.readiness
        b.ensure(50f, "Sensor readings")
        b.canvas.drawText("Emissions readiness", MARGIN, b.y + 11f, textPaint(INK, 11f, bold = true))
        b.y += 16f
        if (readiness == null) {
            b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, "Not read on this scan.", textPaint(GRAY, 9f))
        } else {
            val readyCount = readiness.monitors.count { it.supported && it.ready }
            val supportedCount = readiness.monitors.count { it.supported }
            val milText = if (readiness.milOn) "On" else "Off"
            val notReady = readiness.monitors.filter { it.supported && !it.ready }.joinToString(", ") { it.name }
            val text = "MIL (check engine light): $milText. $readyCount of $supportedCount readiness monitors complete." +
                if (notReady.isNotBlank()) " Incomplete: $notReady — needs further drive cycles to finish self-testing." else ""
            b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, text, textPaint(INK, 9f))
        }
    }

    // ---------- page 5 ----------

    private fun drawNextStepsPage(b: ReportBuilder, result: ScanResult) {
        b.newPage("Next steps")
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
            "Not sure what this means for you? Talk to a real mechanic on WhatsApp, with this report already in hand.",
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
        Severity.CRITICAL -> "ATTENTION" to ATTENTION
        Severity.MODERATE -> "WATCH" to WATCH
        Severity.MINOR -> "WATCH" to WATCH
        Severity.INFO -> "INFO" to GRAY
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        isFakeBoldText = bold
    }

    private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.FILL
    }

    private fun drawField(canvas: Canvas, x: Float, y: Float, label: String, value: String) {
        canvas.drawText(label, x, y + 8f, textPaint(GRAY, 7.5f))
        canvas.drawText(value, x, y + 20f, textPaint(INK, 9.5f, bold = true))
    }

    private fun drawTableHeader(canvas: Canvas, y: Float, colX: FloatArray, headers: List<String>): Float {
        val h = 20f
        canvas.drawRect(MARGIN, y, CONTENT_RIGHT, y + h, fillPaint(INK))
        headers.forEachIndexed { i, text -> canvas.drawText(text, colX[i] + 6f, y + 14f, textPaint(WHITE, 8.5f, bold = true)) }
        return h + 4f
    }

    /** Draws wrapped body text and returns the height consumed, so callers can advance their y cursor. */
    private fun drawWrapped(canvas: Canvas, x: Float, y: Float, width: Float, text: String, paint: TextPaint): Float {
        if (text.isBlank()) return 0f
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1f, 1.15f)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /** Returns the height consumed. */
    private fun drawBanner(canvas: Canvas, y: Float, text: String, bg: Int, border: Int, textColor: Int): Float {
        val padding = 10f
        val width = CONTENT_RIGHT - MARGIN - 2 * padding
        val paint = textPaint(textColor, 8.5f, bold = true)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setLineSpacing(1f, 1.15f).build()
        val boxH = layout.height + 2 * padding
        val rect = RectF(MARGIN, y, CONTENT_RIGHT, y + boxH)
        canvas.drawRoundRect(rect, 6f, 6f, fillPaint(bg))
        canvas.drawRoundRect(rect, 6f, 6f, strokePaint(border, 0.8f))
        canvas.save()
        canvas.translate(MARGIN + padding, y + padding)
        layout.draw(canvas)
        canvas.restore()
        return boxH
    }

    private fun drawStatusBadge(canvas: Canvas, cx: Float, cy: Float, r: Float, count: Int) {
        val color = if (count > 0) ATTENTION else GOOD
        canvas.drawCircle(cx, cy, r, fillPaint(color))
        val label = if (count > 0) count.toString() else "✓"
        val p = textPaint(WHITE, 8f, bold = true)
        p.textAlign = Paint.Align.CENTER
        canvas.drawText(label, cx, cy + 3f, p)
    }

    /** Same geometry as brand/getautosure-icon.svg — orange badge, white arc + checkmark, black wheels. */
    private fun drawLogo(canvas: Canvas, x: Float, y: Float, size: Float) {
        val s = size / 64f
        val badge = RectF(x + 4 * s, y, x + 60 * s, y + 56 * s)
        canvas.drawRoundRect(badge, 16 * s, 16 * s, fillPaint(ORANGE))

        val arc = Path()
        val cx = x + 32 * s
        val cy = y + 34 * s
        val r = 18 * s
        for (i in 0..32) {
            val ang = Math.PI * i / 32.0
            val px = (cx - r * cos(ang)).toFloat()
            val py = (cy - r * sin(ang)).toFloat()
            if (i == 0) arc.moveTo(px, py) else arc.lineTo(px, py)
        }
        canvas.drawPath(arc, strokePaint(WHITE, 5 * s).apply { strokeCap = Paint.Cap.ROUND })

        val check = Path().apply {
            moveTo(x + 23 * s, y + 30 * s)
            lineTo(x + 29 * s, y + 37 * s)
            lineTo(x + 43 * s, y + 19 * s)
        }
        canvas.drawPath(check, strokePaint(WHITE, 5 * s).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })

        canvas.drawCircle(x + 14 * s, y + 48 * s, 6.5f * s, fillPaint(INK))
        canvas.drawCircle(x + 50 * s, y + 48 * s, 6.5f * s, fillPaint(INK))
    }

    /** Up to the first two words' initials, uppercased — "AutoTrust Motors" -> "AM". Falls back to "?" for a blank name, which [DealerProfileScreen] already prevents via its save button, but this stays defensive regardless. */
    private fun dealerInitials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("").ifBlank { "?" }

    /** Draws [dealerLogo] scaled into a rounded square if present, else an initials badge in [DEALER_BADGE] — same visual slot either way. */
    private fun drawDealerBadge(canvas: Canvas, x: Float, y: Float, size: Float, logo: Bitmap?, dealerName: String) {
        val rect = RectF(x, y, x + size, y + size)
        if (logo != null) {
            canvas.save()
            val clip = Path().apply { addRoundRect(rect, size * 0.22f, size * 0.22f, Path.Direction.CW) }
            canvas.clipPath(clip)
            val srcRect = android.graphics.Rect(0, 0, logo.width, logo.height)
            val dstRect = android.graphics.Rect(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
            canvas.drawBitmap(logo, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            canvas.drawRoundRect(rect, size * 0.22f, size * 0.22f, fillPaint(DEALER_BADGE))
            val p = textPaint(WHITE, size * 0.36f, bold = true)
            p.textAlign = Paint.Align.CENTER
            val initials = dealerInitials(dealerName)
            canvas.drawText(initials, x + size / 2f, y + size / 2f + p.textSize * 0.35f, p)
        }
    }

    /**
     * The cover page's prominent dealer block — logo/initials, name, tagline, phone/location —
     * plus the smaller "Powered by GetAutoSure" trust badge beneath it, matching the reviewed
     * mockup. Report metadata (ID/scanned/device) stays in its original top-right position.
     */
    private fun drawDealerCoverHeader(
        canvas: Canvas, y: Float, dealer: DealerProfile, logo: Bitmap?, reportId: String, scannedOn: String
    ) {
        val badgeSize = 44f
        drawDealerBadge(canvas, MARGIN, y, badgeSize, logo, dealer.name)

        val textX = MARGIN + badgeSize + 12f
        canvas.drawText(dealer.name, textX, y + 15f, textPaint(INK, 15f, bold = true))
        var lineY = y + 15f
        if (dealer.tagline.isNotBlank()) {
            lineY += 13f
            canvas.drawText(dealer.tagline, textX, lineY, textPaint(GRAY, 8.5f))
        }
        val contactLine = listOfNotNull(
            dealer.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            dealer.location.takeIf { it.isNotBlank() }
        ).joinToString("   ·   ")
        if (contactLine.isNotBlank()) {
            lineY += 13f
            canvas.drawText(contactLine, textX, lineY, textPaint(GRAY, 8f))
        }

        val metaPaint = textPaint(GRAY, 8f)
        metaPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Report ID: $reportId", CONTENT_RIGHT, y + 7f, metaPaint)
        canvas.drawText("Scanned: $scannedOn", CONTENT_RIGHT, y + 17f, metaPaint)
        canvas.drawText("Device: GetAutoSure OBD2 Scanner", CONTENT_RIGHT, y + 27f, metaPaint)

        // "Powered by GetAutoSure" badge — secondary to the dealer's own identity above.
        val poweredY = y + badgeSize + 12f
        val checkSize = 14f
        canvas.drawRoundRect(RectF(MARGIN, poweredY, MARGIN + checkSize, poweredY + checkSize), 3f, 3f, fillPaint(ORANGE))
        val checkPaint = textPaint(WHITE, 8f, bold = true)
        checkPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("✓", MARGIN + checkSize / 2f, poweredY + checkSize / 2f + 3f, checkPaint)

        val poweredByX = MARGIN + checkSize + 6f
        val poweredPrefix = "Powered by "
        val poweredSuffix = " — independently verified diagnostic data"
        val prefixPaint = textPaint(GRAY, 8f)
        canvas.drawText(poweredPrefix, poweredByX, poweredY + 10f, prefixPaint)
        val prefixW = prefixPaint.measureText(poweredPrefix)
        canvas.drawText("GetAuto", poweredByX + prefixW, poweredY + 10f, textPaint(INK, 8f, bold = true))
        val getAutoW = textPaint(INK, 8f, bold = true).measureText("GetAuto")
        canvas.drawText("Sure", poweredByX + prefixW + getAutoW, poweredY + 10f, textPaint(ORANGE, 8f, bold = true))
        val sureW = textPaint(ORANGE, 8f, bold = true).measureText("Sure")
        canvas.drawText(poweredSuffix, poweredByX + prefixW + getAutoW + sureW, poweredY + 10f, textPaint(GRAY, 8f))
    }

    /** The compact recurring-page version — small badge, name, small "Powered by GetAutoSure", page title on the right. Used by [ReportBuilder.newPage] in place of the plain GetAutoSure-only header when a dealer profile is set. */
    private fun drawDealerCompactHeader(canvas: Canvas, y: Float, dealer: DealerProfile, logo: Bitmap?, title: String) {
        val badgeSize = 20f
        drawDealerBadge(canvas, MARGIN, y, badgeSize, logo, dealer.name)
        var x = MARGIN + badgeSize + 8f
        canvas.drawText(dealer.name, x, y + 14f, textPaint(INK, 10f, bold = true))
        x += textPaint(INK, 10f, bold = true).measureText(dealer.name) + 8f
        canvas.drawText("·", x, y + 14f, textPaint(Color.parseColor("#9BA1AC"), 9f))
        x += 10f
        canvas.drawText("Powered by ", x, y + 14f, textPaint(GRAY, 8f))
        x += textPaint(GRAY, 8f).measureText("Powered by ")
        canvas.drawText("GetAuto", x, y + 14f, textPaint(INK, 8f, bold = true))
        x += textPaint(INK, 8f, bold = true).measureText("GetAuto")
        canvas.drawText("Sure", x, y + 14f, textPaint(ORANGE, 8f, bold = true))

        val titlePaint = textPaint(GRAY, 8.5f)
        titlePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(title, CONTENT_RIGHT, y + 14f, titlePaint)
    }

    /** Tracks the current page/canvas/y-cursor across a variable-length report, starting a new page (re-drawing the header) whenever content would overflow. */
    private class ReportBuilder(
        private val doc: PdfDocument,
        private val dealerProfile: DealerProfile?,
        private val dealerLogo: Bitmap?
    ) {
        private var pageNum = 0
        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = 0f
        private var currentTitle = ""

        fun newPage(title: String, drawBrandHeader: Boolean = true) {
            if (::page.isInitialized) {
                drawFooter()
                doc.finishPage(page)
            }
            pageNum++
            currentTitle = title
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            y = MARGIN
            if (drawBrandHeader) {
                if (dealerProfile != null) {
                    drawDealerCompactHeader(canvas, y, dealerProfile, dealerLogo, title)
                } else {
                    drawLogo(canvas, MARGIN, y, 20f)
                    canvas.drawText("GetAuto", MARGIN + 26f, y + 14f, textPaint(INK, 10f, bold = true))
                    val w = textPaint(INK, 10f, bold = true).measureText("GetAuto")
                    canvas.drawText("Sure", MARGIN + 26f + w, y + 14f, textPaint(ORANGE, 10f, bold = true))
                    val titlePaint = textPaint(GRAY, 8.5f)
                    titlePaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(title, CONTENT_RIGHT, y + 14f, titlePaint)
                }
                y += 30f
            }
        }

        fun ensure(space: Float, sectionTitleForNewPage: String) {
            if (y + space > CONTENT_BOTTOM) newPage(sectionTitleForNewPage)
        }

        fun finish() {
            drawFooter()
            if (::page.isInitialized) doc.finishPage(page)
        }

        private fun drawFooter() {
            val p = textPaint(GRAY, 7.5f)
            canvas.drawText("Page $pageNum", CONTENT_RIGHT - 30f, PAGE_H - 24f, p)
            if (dealerProfile != null) {
                canvas.drawText(
                    "Shared by ${dealerProfile.name} · Report data verified by GetAutoSure",
                    MARGIN, PAGE_H - 24f, textPaint(Color.parseColor("#9BA1AC"), 7.5f)
                )
            }
        }
    }
}
