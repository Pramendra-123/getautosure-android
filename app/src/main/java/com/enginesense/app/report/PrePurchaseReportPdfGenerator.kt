package com.enginesense.app.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.enginesense.app.ScanResult
import com.enginesense.app.obd.Severity
import com.enginesense.app.storage.DealerProfile
import com.enginesense.app.vehicle.BuyerSignal
import com.enginesense.app.vehicle.CheckOutcome
import com.enginesense.app.vehicle.FaultKind
import com.enginesense.app.vehicle.OdometerMatch
import com.enginesense.app.vehicle.PrePurchaseReport
import com.enginesense.app.vehicle.PurchaseVerdict
import com.enginesense.app.vehiclelookup.PurchaseRcCheckViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The buyer's PDF: "should I buy this car, and for how much?" — verdict, fraud/tamper checks,
 * odometer evidence, RTO record, faults with tentative repair costs, and fair value, in the order
 * a buyer reads it. Everything comes from a [PrePurchaseReport]; this only draws it, with the same
 * page kit ([ReportBuilder], [PdfKit]) as the advanced report so a dealer profile brands it the
 * same way. Like the advanced report, a value the scan didn't get is shown as such, never guessed.
 */
object PrePurchaseReportPdfGenerator {

    private const val PAGE_FRAUD = "Fraud & tamper checks"
    private const val PAGE_COST = "Faults, repair cost & value"
    private const val PAGE_BEFORE_YOU_PAY = "Before you pay"

    fun generate(
        context: Context,
        result: ScanResult,
        report: PrePurchaseReport,
        dealerProfile: DealerProfile? = null,
        dealerLogo: Bitmap? = null
    ): File {
        val doc = PdfDocument()
        val reportId = "GetAutoSure-PPI-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(result.timestampMillis))
        val scannedOn = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(result.timestampMillis))
        val b = ReportBuilder(doc, dealerProfile, dealerLogo)

        drawSummaryPage(b, result, report, reportId, scannedOn, dealerProfile, dealerLogo)
        drawFraudPage(b, report)
        drawCostPage(b, report)
        drawBeforeYouPayPage(b, result)
        b.finish()

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "$reportId.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    // ---------- page 1: summary ----------

    private fun drawSummaryPage(
        b: ReportBuilder, result: ScanResult, report: PrePurchaseReport, reportId: String, scannedOn: String,
        dealerProfile: DealerProfile?, dealerLogo: Bitmap?
    ) {
        b.newPage("Pre-purchase report", drawBrandHeader = false)
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

        b.canvas.drawText("PRE-PURCHASE INSPECTION REPORT", MARGIN, b.y + 8f, textPaint(ORANGE, 8.5f, bold = true))
        b.y += 14f
        val rc = report.rc
        val carName = listOfNotNull(rc?.make, rc?.model).joinToString(" ").ifBlank { null }
        b.canvas.drawText(carName ?: "Used car check", MARGIN, b.y + 18f, textPaint(INK, 19f, bold = true))
        b.y += 24f
        val subtitle = listOfNotNull(
            report.registration,
            report.manufactureYear?.let { "Manufactured $it" },
            rc?.fuelType
        ).joinToString("  ·  ").ifBlank { "Registration not checked" }
        b.canvas.drawText(subtitle, MARGIN, b.y + 11f, textPaint(GRAY, 10f))
        b.y += 24f

        b.y += drawVerdictBox(b.canvas, b.y, report, result.health.score)
        b.y += 12f
        b.y += drawStatTiles(b.canvas, b.y, report)
        b.y += 18f

        b.canvas.drawText("VEHICLE", MARGIN, b.y, textPaint(ORANGE, 8.5f, bold = true))
        b.canvas.drawText("ODOMETER", MARGIN + 260f, b.y, textPaint(ORANGE, 8.5f, bold = true))
        b.y += 12f
        val odo = report.odometer
        val left = listOf(
            "Registration" to (report.registration ?: "Not entered"),
            "VIN (from the car's computer)" to (result.vehicleId.vin?.takeIf { it.isNotBlank() } ?: "Not reported by this car"),
            "Manufactured" to (rc?.manufacturingMonth?.let { PurchaseRcCheckViewModel.formatMonth(it) } ?: report.manufactureYear?.toString() ?: "Not known")
        )
        val right = listOf(
            "Engine computer" to ecuKmText(report),
            "Dashboard (entered at the car)" to (odo.dashboardKm?.let { km(it) } ?: "Not entered"),
            "Scanned on" to scannedOn
        )
        for (i in left.indices) {
            drawField(b.canvas, MARGIN, b.y, left[i].first, left[i].second)
            drawField(b.canvas, MARGIN + 260f, b.y, right[i].first, right[i].second)
            b.y += 30f
        }
        b.y += 8f

        b.canvas.drawText("WHAT WE CHECKED", MARGIN, b.y, textPaint(ORANGE, 8.5f, bold = true))
        b.y += 8f
        report.checks.forEach { check ->
            b.ensure(20f, PAGE_FRAUD)
            b.canvas.drawText(check.label, MARGIN + 4f, b.y + 13f, textPaint(INK, 9f))
            val (label, color) = outcomeLabel(check.outcome)
            drawChip(b.canvas, CONTENT_RIGHT, b.y + 3f, label, color)
            b.canvas.drawLine(MARGIN, b.y + 19f, CONTENT_RIGHT, b.y + 19f, strokePaint(BORDER, 0.5f))
            b.y += 20f
        }
    }

    private fun drawVerdictBox(canvas: Canvas, y: Float, report: PrePurchaseReport, healthScore: Int?): Float {
        val (label, color) = when (report.verdict) {
            PurchaseVerdict.GOOD_BUY -> "Good buy" to GOOD
            PurchaseVerdict.BUY_WITH_CAUTION -> "Buy with caution" to WATCH
            PurchaseVerdict.AVOID -> "Avoid" to ATTENTION
        }
        val textLeft = MARGIN + 18f
        val textWidth = CONTENT_RIGHT - textLeft - 110f
        val headlinePaint = textPaint(INK, 9.5f)
        val h = 44f + measureWrapped(textWidth, report.headline, headlinePaint) + 12f
        val rect = RectF(MARGIN, y, CONTENT_RIGHT, y + h)
        canvas.drawRoundRect(rect, 8f, 8f, fillPaint(tint(color, 22)))
        canvas.drawRect(MARGIN, y, MARGIN + 5f, y + h, fillPaint(color))
        canvas.drawText("VERDICT", textLeft, y + 16f, textPaint(GRAY, 7.5f, bold = true))
        canvas.drawText(label, textLeft, y + 36f, textPaint(color, 18f, bold = true))
        drawWrapped(canvas, textLeft, y + 44f, textWidth, report.headline, headlinePaint)

        val scoreRight = CONTENT_RIGHT - 16f
        val small = textPaint(GRAY, 7.5f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("ENGINE HEALTH", scoreRight, y + 16f, small)
        val scorePaint = textPaint(INK, 22f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(healthScore?.let { "$it" } ?: "—", scoreRight - (if (healthScore != null) 24f else 0f), y + 40f, scorePaint)
        if (healthScore != null) canvas.drawText("/100", scoreRight, y + 40f, textPaint(GRAY, 9f).apply { textAlign = Paint.Align.RIGHT })
        return h
    }

    private fun drawStatTiles(canvas: Canvas, y: Float, report: PrePurchaseReport): Float {
        val faultCount = report.faults.count { it.kind != FaultKind.PENDING }
        val pending = report.faults.count { it.kind == FaultKind.PENDING }
        val tiles = listOf(
            Triple("RED FLAGS", report.redFlags.size.toString(), if (report.redFlags.isEmpty()) "None found" else "Need an answer") to
                (if (report.redFlags.isEmpty()) GOOD else ATTENTION),
            Triple("FAULTS FOUND", faultCount.toString(), if (pending > 0) "+ $pending developing" else if (faultCount == 0) "Nothing stored" else "Incl. hidden codes") to
                (if (faultCount == 0) GOOD else WATCH),
            Triple("EST. REPAIR COST", if (report.repairMaxInr == 0) "₹0" else "${rupees(report.repairMinInr.toDouble())}–${rupees(report.repairMaxInr.toDouble(), symbol = false)}", "Tentative range") to
                (if (report.repairMaxInr == 0) GOOD else WATCH),
            Triple("FAIR VALUE", report.value?.let { "${rupees(it.fair.lowEstimate)}–${rupees(it.fair.highEstimate, symbol = false)}" } ?: "Not set", if (report.value != null) "Before repairs" else "Add ex-showroom price") to
                (if (report.value != null) INK else GRAY)
        )
        val gap = 8f
        val w = (CONTENT_RIGHT - MARGIN - gap * 3) / 4
        val h = 58f
        tiles.forEachIndexed { i, (t, color) ->
            val x = MARGIN + i * (w + gap)
            val rect = RectF(x, y, x + w, y + h)
            canvas.drawRoundRect(rect, 6f, 6f, fillPaint(GRAY_LIGHT))
            canvas.drawRoundRect(rect, 6f, 6f, strokePaint(BORDER, 0.8f))
            canvas.drawText(t.first, x + 9f, y + 15f, textPaint(GRAY, 7f, bold = true))
            canvas.drawText(t.second, x + 9f, y + 35f, textPaint(color, fitSize(t.second, w - 18f, 14f), bold = true))
            canvas.drawText(t.third, x + 9f, y + 49f, textPaint(GRAY, 7.5f))
        }
        return h
    }

    // ---------- page 2: fraud, odometer, RTO ----------

    private fun drawFraudPage(b: ReportBuilder, report: PrePurchaseReport) {
        b.newPage(PAGE_FRAUD)
        sectionTitle(b, "Red flags", "Signs something may have been hidden from you. Each needs an answer from the seller before you pay.")
        if (report.redFlags.isEmpty()) {
            b.y += drawBanner(b.canvas, b.y, "No red flags from this scan.", tint(GOOD, 22), tint(GOOD, 70), GOOD)
            b.y += 16f
        } else {
            report.redFlags.forEach { drawSignalCard(b, it, ATTENTION, PAGE_FRAUD) }
            b.y += 6f
        }

        b.ensure(170f, PAGE_FRAUD)
        sectionTitle(b, "Odometer history checks", "What the car's own computer says about its distance and when its fault memory was last cleared.")
        val odo = report.odometer
        val rows = listOf(
            "Engine computer odometer" to ecuKmText(report),
            "Dashboard odometer (entered at the car)" to (odo.dashboardKm?.let { km(it) } ?: "Not entered"),
            "Dashboard vs computer" to when (odo.match) {
                OdometerMatch.MATCHES -> "Match"
                OdometerMatch.MISMATCH -> "Mismatch — ${km(kotlin.math.abs((odo.ecuKm ?: 0.0) - (odo.dashboardKm ?: 0.0)))} apart"
                null -> "Couldn't compare"
            },
            "Distance since fault codes were cleared" to (odo.kmSinceCodesCleared?.let { km(it) } ?: "Not reported"),
            "Engine warm-ups since codes were cleared" to (odo.warmUpsSinceCodesCleared?.toInt()?.toString() ?: "Not reported"),
            "Engine running time since codes were cleared" to (odo.minutesSinceCodesCleared?.let { "${it.toInt()} min" } ?: "Not reported"),
            "Distance driven with the warning light on" to (odo.kmWithWarningLightOn?.let { km(it) } ?: "Not reported")
        )
        drawKeyValueRows(b, rows, PAGE_FRAUD) { key, value ->
            when {
                key == "Dashboard vs computer" && value.startsWith("Mismatch") -> ATTENTION
                key == "Dashboard vs computer" && value == "Match" -> GOOD
                value.startsWith("Not") || value.startsWith("Couldn't") -> GRAY
                else -> INK
            }
        }
        b.y += 6f
        b.ensure(40f, PAGE_FRAUD)
        b.y += drawWrapped(
            b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN,
            "This is an odometer red-flag check, not a guarantee. A match means OBD shows no red flags; it can't rule out a " +
                "rollback done on both the dashboard and the engine computer. Most cars made before about 2020 don't report " +
                "their odometer over OBD at all — then service records are the best check.",
            textPaint(GRAY, 8f)
        )
        b.y += 18f

        b.ensure(150f, PAGE_FRAUD)
        sectionTitle(b, "RTO verification", "The official registration record for this car, from the RTO.")
        val rc = report.rc
        if (rc == null) {
            b.y += drawBanner(
                b.canvas, b.y,
                "RTO records weren't checked for this report. Enter the registration number under \"Check against RTO records\" on the Used Car Check screen, then make the report again.",
                AMBER_BG, AMBER_BORDER, Color.parseColor("#412402")
            )
            b.y += 12f
            return
        }
        val chassis = when (rc.chassisMatch) {
            true -> "Matches the car's computer"
            false -> "Does NOT match the car's computer"
            null -> "Couldn't compare — check the number stamped on the body"
        }
        val rcRows = listOfNotNull(
            "Registration" to rc.registrationNumber,
            "Make / model" to (listOfNotNull(rc.make, rc.model).joinToString(" ").ifBlank { "Not on record" }),
            rc.fuelType?.let { "Fuel" to it },
            rc.color?.let { "Colour" to it },
            "Manufactured" to (PurchaseRcCheckViewModel.formatMonth(rc.manufacturingMonth) ?: "Not on record"),
            "Registered on" to (rc.registrationDate ?: "Not on record"),
            rc.registeredAt?.let { "Registering RTO" to it },
            "Insurance" to (listOfNotNull(rc.insuranceCompany, rc.insuranceUpto?.let { "valid until $it" }).joinToString(", ").ifBlank { "Not on record" }),
            "PUC valid until" to (rc.puccUpto ?: "Not on record"),
            rc.fitnessUpto?.let { "Fitness valid until" to it },
            rc.taxUpto?.let { "Road tax valid until" to it },
            "Chassis number" to chassis
        )
        drawKeyValueRows(b, rcRows, PAGE_FRAUD) { key, value ->
            when {
                key == "Chassis number" && rc.chassisMatch == true -> GOOD
                key == "Chassis number" && rc.chassisMatch == false -> ATTENTION
                value.startsWith("Not on record") || value.startsWith("Couldn't") -> GRAY
                else -> INK
            }
        }
        if (rc.partial) {
            b.y += 4f
            b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, "Some details aren't available from the RTO for this car.", textPaint(GRAY, 8f))
        }
        b.y += 6f
        b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, "Check these against the seller's RC and what they told you about the car.", textPaint(GRAY, 8f))
    }

    // ---------- page 3: faults, cost, value ----------

    private fun drawCostPage(b: ReportBuilder, report: PrePurchaseReport) {
        b.newPage(PAGE_COST)
        sectionTitle(
            b, "Major faults & estimated repair cost",
            "Tentative Indian workshop ranges, not quotes — real prices vary by city, workshop and car. Get a quote before you negotiate on them."
        )
        if (report.faults.isEmpty()) {
            b.y += drawBanner(b.canvas, b.y, "No fault codes found — stored, hidden or developing.", tint(GOOD, 22), tint(GOOD, 70), GOOD)
            b.y += 16f
        } else {
            val colX = floatArrayOf(MARGIN, MARGIN + 52f, MARGIN + 290f, MARGIN + 390f)
            b.y += drawTableHeader(b.canvas, b.y, colX, listOf("Code", "Fault", "Type", "Est. cost"))
            report.faults.forEach { line ->
                val titlePaint = textPaint(INK, 9f)
                val rowH = maxOf(20f, measureWrapped(colX[2] - colX[1] - 12f, line.title, titlePaint) + 8f)
                b.ensure(rowH, PAGE_COST)
                b.canvas.drawText(line.code ?: "—", colX[0] + 6f, b.y + 13f, textPaint(INK, 9f, bold = true))
                drawWrapped(b.canvas, colX[1] + 6f, b.y + 3f, colX[2] - colX[1] - 12f, line.title, titlePaint)
                val (type, typeColor) = faultType(line.kind, line.severity)
                b.canvas.drawText(type, colX[2] + 6f, b.y + 13f, textPaint(typeColor, 8.5f, bold = true))
                val cost = line.cost?.let { "${rupees(it.minInr.toDouble())}–${rupees(it.maxInr.toDouble(), symbol = false)}" } ?: "Workshop quote"
                b.canvas.drawText(cost, colX[3] + 6f, b.y + 13f, textPaint(if (line.cost == null) GRAY else INK, 9f))
                b.canvas.drawLine(MARGIN, b.y + rowH - 1f, CONTENT_RIGHT, b.y + rowH - 1f, strokePaint(BORDER, 0.5f))
                b.y += rowH
            }
            b.ensure(26f, PAGE_COST)
            b.canvas.drawRect(MARGIN, b.y + 2f, CONTENT_RIGHT, b.y + 24f, fillPaint(GRAY_LIGHT))
            b.canvas.drawText("Estimated total", colX[1] + 6f, b.y + 17f, textPaint(INK, 9.5f, bold = true))
            val total = if (report.repairMaxInr == 0) "No priced items" else "${rupees(report.repairMinInr.toDouble())}–${rupees(report.repairMaxInr.toDouble(), symbol = false)}"
            b.canvas.drawText(total, colX[3] + 6f, b.y + 17f, textPaint(INK, 9.5f, bold = true))
            b.y += 30f
            val notes = buildList {
                if (report.faults.any { it.kind == FaultKind.PENDING }) add("Developing faults aren't in the total — the car hasn't confirmed them yet.")
                if (report.faults.any { it.cost == null }) add("\"Workshop quote\" means no reliable range fits that fault; it isn't free to fix.")
                add("Several codes can share one cause, so the total can overstate the real bill.")
            }
            b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, notes.joinToString(" "), textPaint(GRAY, 8f))
            b.y += 18f
        }

        b.ensure(130f, PAGE_COST)
        sectionTitle(b, "Fair estimated value", "Based on the IRDAI standard depreciation schedule — a rough estimate, not a verified valuation.")
        val value = report.value
        if (value == null) {
            b.y += drawBanner(b.canvas, b.y, report.valueMissingReason ?: "Fair value not available.", GRAY_LIGHT, BORDER, GRAY)
            b.y += 16f
        } else {
            val boxH = 74f
            val half = (CONTENT_RIGHT - MARGIN - 10f) / 2
            listOf(
                Triple("FAIR VALUE (BEFORE REPAIRS)", "${rupees(value.fair.lowEstimate)} – ${rupees(value.fair.highEstimate, symbol = false)}", "What a car like this is worth in good order"),
                Triple("AS IT STANDS (AFTER REPAIRS)", "${rupees(value.afterRepairsLow)} – ${rupees(value.afterRepairsHigh, symbol = false)}", "Fair value less the repair estimate — a sensible offer range")
            ).forEachIndexed { i, (label, amount, note) ->
                val x = MARGIN + i * (half + 10f)
                val rect = RectF(x, b.y, x + half, b.y + boxH)
                b.canvas.drawRoundRect(rect, 8f, 8f, fillPaint(if (i == 1) tint(ORANGE, 22) else GRAY_LIGHT))
                b.canvas.drawRoundRect(rect, 8f, 8f, strokePaint(if (i == 1) tint(ORANGE, 90) else BORDER, 0.8f))
                b.canvas.drawText(label, x + 12f, b.y + 18f, textPaint(GRAY, 7.5f, bold = true))
                b.canvas.drawText(amount, x + 12f, b.y + 42f, textPaint(if (i == 1) ORANGE else INK, 17f, bold = true))
                drawWrapped(b.canvas, x + 12f, b.y + 50f, half - 24f, note, textPaint(GRAY, 7.5f))
            }
            b.y += boxH + 10f
            val basis = "${value.fair.depreciationPercent}% depreciation over ${value.fair.ageYears} year${if (value.fair.ageYears == 1) "" else "s"}" +
                (report.odometer.dashboardKm ?: report.odometer.ecuKm)?.let { ", adjusted for ${km(it)}" }.orEmpty() + "." +
                (if (value.fair.beyondPublishedSchedule) " IRDAI only publishes rates up to 5 years — beyond that this extrapolates, it isn't an official figure." else "") +
                " Compare listings for the same model, year and km on CarDekho, Cars24 or OLX — they reflect your local market, this doesn't."
            b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, basis, textPaint(GRAY, 8f))
            b.y += 18f
        }

        if (report.worthAsking.isNotEmpty()) {
            b.ensure(80f, PAGE_COST)
            sectionTitle(b, "Worth asking the seller", "Not signs of tampering — but good questions before you agree a price.")
            report.worthAsking.forEach { drawSignalCard(b, it, WATCH, PAGE_COST) }
        }
    }

    // ---------- page 4: before you pay ----------

    private fun drawBeforeYouPayPage(b: ReportBuilder, result: ScanResult) {
        b.newPage(PAGE_BEFORE_YOU_PAY)
        sectionTitle(b, "Before you pay", "What an OBD scan can't see. Tick these off at the car.")
        listOf(
            "Chassis and engine numbers stamped on the car match the RC",
            "Service book and invoices support the odometer reading",
            "No loan on the car — the RC shows no hypothecation, or the bank's NOC and Form 35 are handed over",
            "No pending traffic challans (check on the Parivahan e-challan site)",
            "Insurance claim history — ask the insurer; a low No Claim Bonus can mean past claims",
            "Body: even panel gaps, no repaint or rust; no damp carpet or silt under the seats (flood damage)",
            "Cold start: starts easily, no smoke, steady idle",
            "Test drive: brakes, steering, clutch and gearbox, suspension noises, AC",
            "Tyres: even wear, enough tread, same make on each axle",
            "Transfer papers: Form 29 and 30 signed, original RC, insurance and PUC in hand"
        ).forEach { item ->
            val paint = textPaint(INK, 9f)
            val h = maxOf(20f, measureWrapped(CONTENT_RIGHT - MARGIN - 26f, item, paint) + 8f)
            b.ensure(h, PAGE_BEFORE_YOU_PAY)
            b.canvas.drawRoundRect(RectF(MARGIN + 2f, b.y + 3f, MARGIN + 13f, b.y + 14f), 2f, 2f, strokePaint(GRAY, 0.9f))
            drawWrapped(b.canvas, MARGIN + 24f, b.y + 2f, CONTENT_RIGHT - MARGIN - 26f, item, paint)
            b.y += h
        }
        b.y += 14f

        b.ensure(50f, PAGE_BEFORE_YOU_PAY)
        b.y += drawBanner(
            b.canvas, b.y,
            "Not sure about something in this report? Talk to a real mechanic on WhatsApp from the GetAutoSure app, with this report in hand.",
            Color.parseColor("#FFF8EC"), AMBER_BORDER, Color.parseColor("#412402")
        )
        b.y += 16f

        b.ensure(80f, PAGE_BEFORE_YOU_PAY)
        b.canvas.drawText("What this report doesn't cover", MARGIN, b.y + 9f, textPaint(GRAY, 8f, bold = true))
        b.y += 13f
        b.y += drawWrapped(
            b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN,
            "This report is built from the car's OBD2 data (engine, fuel and emissions) and, where checked, its RTO record. " +
                "It doesn't cover accident repairs, bodywork, tyres, brakes, suspension, interior, or ABS/airbag/body-module codes, " +
                "and it isn't a guarantee of the car's condition or history. Repair costs and fair value are tentative estimates. " +
                "Scan profile: ${result.profileLabel}.",
            textPaint(GRAY, 8f)
        )
        b.y += 10f
        drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, "Generated by GetAutoSure · getautosure.com", textPaint(GRAY, 7.5f))
    }

    // ---------- helpers ----------

    private fun sectionTitle(b: ReportBuilder, title: String, subtitle: String) {
        b.canvas.drawText(title, MARGIN, b.y + 14f, textPaint(INK, 15f, bold = true))
        b.y += 20f
        b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, subtitle, textPaint(GRAY, 8.5f))
        b.y += 10f
    }

    /** Title, what the scan saw, then what to ask — measured first so the card's background goes under the text. */
    private fun drawSignalCard(b: ReportBuilder, signal: BuyerSignal, color: Int, pageTitle: String) {
        val textX = MARGIN + 16f
        val width = CONTENT_RIGHT - textX - 12f
        val evidencePaint = textPaint(INK, 8.5f)
        val askPaint = textPaint(GRAY, 8.5f)
        // The advice already starts with "Ask…", so it gets its own small label rather than an "Ask the seller:" prefix.
        val askLabelH = 11f
        val h = 12f + 12f + 4f + measureWrapped(width, signal.evidence, evidencePaint) + 6f + askLabelH + measureWrapped(width, signal.advice, askPaint) + 10f
        b.ensure(h + 8f, pageTitle)
        val y = b.y
        b.canvas.drawRoundRect(RectF(MARGIN, y, CONTENT_RIGHT, y + h), 6f, 6f, fillPaint(GRAY_LIGHT))
        b.canvas.drawRect(MARGIN, y, MARGIN + 4f, y + h, fillPaint(color))
        b.canvas.drawText(signal.title, textX, y + 20f, textPaint(color, 10f, bold = true))
        var innerY = y + 28f
        innerY += drawWrapped(b.canvas, textX, innerY, width, signal.evidence, evidencePaint) + 6f
        b.canvas.drawText("ASK THE SELLER", textX, innerY + 7f, textPaint(GRAY, 7f, bold = true))
        innerY += askLabelH
        drawWrapped(b.canvas, textX, innerY, width, signal.advice, askPaint)
        b.y += h + 8f
    }

    private fun drawKeyValueRows(b: ReportBuilder, rows: List<Pair<String, String>>, pageTitle: String, colorFor: (String, String) -> Int) {
        val valueX = MARGIN + 230f
        rows.forEach { (key, value) ->
            val paint = textPaint(colorFor(key, value), 9f, bold = true)
            val h = maxOf(20f, measureWrapped(CONTENT_RIGHT - valueX, value, paint) + 8f)
            b.ensure(h, pageTitle)
            b.canvas.drawText(key, MARGIN + 4f, b.y + 13f, textPaint(GRAY, 9f))
            drawWrapped(b.canvas, valueX, b.y + 3f, CONTENT_RIGHT - valueX, value, paint)
            b.canvas.drawLine(MARGIN, b.y + h - 1f, CONTENT_RIGHT, b.y + h - 1f, strokePaint(BORDER, 0.5f))
            b.y += h
        }
    }

    /** A small rounded label right-aligned at [right]. */
    private fun drawChip(canvas: Canvas, right: Float, top: Float, text: String, color: Int) {
        val paint = textPaint(color, 8f, bold = true)
        val w = paint.measureText(text) + 16f
        canvas.drawRoundRect(RectF(right - w, top, right, top + 14f), 7f, 7f, fillPaint(tint(color, 30)))
        canvas.drawText(text, right - w + 8f, top + 10f, paint)
    }

    private fun outcomeLabel(outcome: CheckOutcome): Pair<String, Int> = when (outcome) {
        CheckOutcome.CLEAR -> "✓ Clear" to GOOD
        CheckOutcome.FLAGGED -> "! Red flag" to ATTENTION
        CheckOutcome.WORTH_ASKING -> "? Ask the seller" to WATCH
        CheckOutcome.NOT_AVAILABLE -> "– Not reported" to GRAY
    }

    private fun faultType(kind: FaultKind, severity: Severity?): Pair<String, Int> = when (kind) {
        FaultKind.STORED -> when (severity) {
            Severity.CRITICAL -> "Serious" to ATTENTION
            else -> "Stored" to WATCH
        }
        FaultKind.HIDDEN_PERMANENT -> "Hidden (cleared)" to ATTENTION
        FaultKind.PENDING -> "Developing" to WATCH
    }

    private fun ecuKmText(report: PrePurchaseReport): String = report.odometer.ecuKm?.let { km(it) } ?: when (report.odometer.ecuSupported) {
        true -> "No reading this scan"
        false -> "Not reported by this car"
        null -> "Not read"
    }

    private fun km(value: Double) = "%,d km".format(value.toLong())

    /** "₹4.25 L" from a lakh up, "₹45,000" below; [symbol] false drops the ₹ for the second half of a range. */
    private fun rupees(value: Double, symbol: Boolean = true): String {
        val prefix = if (symbol) "₹" else ""
        return if (value >= 100000) prefix + "%.2f L".format(value / 100000) else prefix + "%,.0f".format(value)
    }

    private fun tint(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** Largest text size up to [max] at which [text] fits [width] — tiles are narrow and a cut-off amount would mislead. */
    private fun fitSize(text: String, width: Float, max: Float): Float {
        var size = max
        while (size > 8f && textPaint(INK, size, bold = true).measureText(text) > width) size -= 0.5f
        return size
    }
}
