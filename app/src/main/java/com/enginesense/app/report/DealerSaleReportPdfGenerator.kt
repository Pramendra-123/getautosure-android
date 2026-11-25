package com.enginesense.app.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.enginesense.app.ScanResult
import com.enginesense.app.dealerreport.DealerFindings
import com.enginesense.app.dealerreport.DealerInspection
import com.enginesense.app.dealerreport.DealerSaleSummary
import com.enginesense.app.dealerreport.DealerSaleSummaryBuilder
import com.enginesense.app.dealerreport.INSPECTION_ITEMS
import com.enginesense.app.dealerreport.KeyFinding
import com.enginesense.app.dealerreport.indianGrouping
import com.enginesense.app.obd.Severity
import com.enginesense.app.storage.DealerProfile
import com.enginesense.app.vehicle.CategoryState
import com.enginesense.app.vehicle.FaultKind
import com.enginesense.app.vehicle.OdometerMatch
import com.enginesense.app.vehicle.PrePurchaseReport
import com.enginesense.app.vehiclelookup.RtoRecord
import com.enginesense.app.vehiclelookup.RtoRow
import com.enginesense.app.vehiclelookup.ValidityStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Everything a Dealer Saathi report is made from. [photos] starts with the front photo — the cover
 * picture. [priceInr] only when the dealer added one. [webUrl]/[webSlug] when the report's web page
 * was published first: the PDF then carries its QR code and link, and uses the slug as its ID.
 */
data class DealerSaleReportInput(
    val result: ScanResult,
    val report: PrePurchaseReport,
    val dealer: DealerProfile,
    val dealerLogo: Bitmap?,
    val inspection: DealerInspection,
    val photos: List<ReportPhoto>,
    val priceInr: Double?,
    val webUrl: String? = null,
    val webSlug: String? = null
)

/**
 * The Dealer Saathi PDF (docs/dealer-sathi-v2.md; design brand/reports/dealer-report-v4-cards-mockup.png, rendered brand/reports/dealer-report-v4-pages.png),
 * made by a dealer after refurbishing a car, for the people they're selling it to. It looks like the
 * report's web page: grey page, white rounded cards.
 * Page 1: dealer band with the web report's QR, the studio photo edge to edge (never cropped) with the
 * price, the car's title and chips, then the score, key findings and RTO record cards. Then the scan
 * (system bars, reading alerts, issues and costs, odometer, self-tests, readings), then the dealer's
 * declared inspection, photos, price, a verify-this-report link, and last the "not verified by
 * GetAutoSure" note. A card too long for the page carries on as "(continued)" on the next.
 */
object DealerSaleReportPdfGenerator {

    private const val L = 16f
    private const val R = PAGE_W - 16f
    private const val W = R - L
    /** Inside a card. */
    private const val CL = L + 16f
    private const val CR = R - 16f
    private const val CW = CR - CL
    private const val BOTTOM = PAGE_H - 40f
    private const val GAP = 10f
    private const val TITLE_H = 38f
    private const val PAD = 12f
    private const val PAGE_SCAN = "Scan results"
    private const val PAGE_DEALER = "Dealer-declared inspection"
    private val PAGE_BG = Color.parseColor("#F3F4F6")
    private val INK_BAND = Color.parseColor("#0A0B0D")
    private val BAND_MUTED = Color.parseColor("#9BA1AC")
    private val CARD_LINE = Color.parseColor("#E5E7EB")
    private val ROW_LINE = Color.parseColor("#F3F4F6")
    private val CHIP_BG = Color.parseColor("#E5E7EB")
    private val CHIP_TEXT = Color.parseColor("#374151")
    private val TEXT = Color.parseColor("#111827")
    private val RED = Color.parseColor("#B91C1C")
    private val GREEN = Color.parseColor("#15803D")
    private val AMBER = Color.parseColor("#B45309")
    private val STAR = Color.parseColor("#D97706")

    fun generate(context: Context, input: DealerSaleReportInput): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        return generateTo(dir, input, LocalDate.now())
    }

    /** [generate] into [dir] with a fixed [today] — what the sample-report test calls. */
    internal fun generateTo(dir: File, input: DealerSaleReportInput, today: LocalDate): File {
        val summary = DealerSaleSummaryBuilder.build(input.result, input.report, today)
        val doc = PdfDocument()
        val reportId = input.webSlug ?: reportId(input.result.timestampMillis)
        val scannedOn = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(input.result.timestampMillis))
        val b = ReportBuilder(doc, input.dealer, input.dealerLogo)
        b.footerLeft = "Report $reportId" + (input.webUrl?.let { " · " + it.removePrefix("https://") } ?: "")
        b.pageColor = PAGE_BG
        b.customHeader = { canvas, _ -> drawCompactBand(canvas, input.dealer, input.dealerLogo, summary) }

        drawCover(b, input, summary, scannedOn, today)
        drawScanPages(b, input, scannedOn)
        drawDealerPages(b, input, reportId)
        b.finish()

        val file = File(dir, "$reportId.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** "GAS-20261104-1510" — the ID used when the report has no web page. */
    fun reportId(timestampMillis: Long): String = "GAS-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(timestampMillis))

    // ---------- page 1 ----------

    private fun drawCover(b: ReportBuilder, input: DealerSaleReportInput, summary: DealerSaleSummary, scannedOn: String, today: LocalDate) {
        b.newPage("Vehicle Health & Inspection Report", drawBrandHeader = false)
        val c = b.canvas
        val dealer = input.dealer

        // Dealer band: logo, name, tagline and showroom; phones; the web report's QR.
        val bandH = 80f
        c.drawRect(0f, 0f, PAGE_W.toFloat(), bandH, fillPaint(INK_BAND))
        drawDealerBadge(c, L + 8f, 17f, 46f, input.dealerLogo, dealer.name)
        var phonesRight = R - 8f
        input.webUrl?.let { url ->
            val qr = RectF(R - 62f, 8f, R - 8f, 62f)
            c.drawRoundRect(qr, 4f, 4f, fillPaint(WHITE))
            drawQr(c, url, RectF(qr.left + 3f, qr.top + 3f, qr.right - 3f, qr.bottom - 3f))
            c.drawText("Scan for live report", qr.centerX(), 73f, textPaint(BAND_MUTED, 6f).apply { textAlign = Paint.Align.CENTER })
            phonesRight = qr.left - 12f
        }
        val nameX = L + 66f
        val textRight = phonesRight - 110f
        c.drawText(ellipsize(dealer.name, textRight - nameX, textPaint(WHITE, 16f, bold = true)), nameX, 37f, textPaint(WHITE, 16f, bold = true))
        val sub = listOf(dealer.tagline.trim(), dealer.location.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
        if (sub.isNotEmpty()) c.drawText(ellipsize(sub, textRight - nameX, textPaint(BAND_MUTED, 9f)), nameX, 53f, textPaint(BAND_MUTED, 9f))
        val phonePaint = textPaint(WHITE, 10.5f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        dealer.phones.take(2).forEachIndexed { i, phone -> c.drawText("Tel: $phone", phonesRight, 36f + i * 16f, phonePaint) }
        b.y = bandH

        // Studio photo edge to edge, the whole car, with the price.
        input.photos.firstOrNull()?.let { front ->
            val box = RectF(0f, b.y, PAGE_W.toFloat(), b.y + 220f)
            drawBitmapSmart(c, front.bitmap, box, radius = 0f, trimToCar = front.backgroundCleaned)
            if (front.backgroundCleaned) {
                val tag = "Background cleaned · car not edited"
                val tp = textPaint(WHITE, 6.5f)
                val tw = tp.measureText(tag) + 12f
                c.drawRoundRect(RectF(R - tw, box.top + 10f, R, box.top + 22f), 6f, 6f, fillPaint(Color.argb(150, 0, 0, 0)))
                c.drawText(tag, R - tw + 6f, box.top + 18.5f, tp)
            }
            input.priceInr?.takeIf { it > 0 }?.let { price ->
                val text = com.enginesense.app.dealerreport.priceLabel(price)
                val pp = textPaint(INK_BAND, 13f, bold = true)
                val pw = pp.measureText(text) + 26f
                val pill = RectF(L + 8f, box.bottom - 38f, L + 8f + pw, box.bottom - 12f)
                c.drawRoundRect(pill, 13f, 13f, fillPaint(ORANGE))
                c.drawText(text, pill.left + 13f, pill.bottom - 8.5f, pp)
            }
            b.y = box.bottom
        }
        b.y += 24f

        if (input.result.isDemo) {
            b.y += banner(c, b.y, "Demo data — this report does not reflect a real vehicle scan.", AMBER_BG, AMBER_BORDER, Color.parseColor("#412402")) + 12f
        }

        // Title and chips — the listing pattern buyers know from car portals.
        c.drawText("VEHICLE HEALTH & INSPECTION REPORT · SCANNED ${scannedOn.uppercase()}", L + 8f, b.y, textPaint(ORANGE, 7.5f, bold = true).apply { letterSpacing = 0.12f })
        b.y += 24f
        val bold = textPaint(TEXT, 20f, bold = true)
        val titleW = W - 16f
        val titleBold = ellipsize(listOfNotNull(summary.year?.toString(), summary.carName).joinToString(" "), titleW, bold)
        c.drawText(titleBold, L + 8f, b.y, bold)
        summary.variant?.let { v ->
            val x = L + 8f + bold.measureText(titleBold) + 7f
            if (R - x > 40f) c.drawText(ellipsize(v, R - 8f - x, textPaint(GRAY, 20f)), x, b.y, textPaint(GRAY, 20f))
        }
        b.y += 10f
        val chips = listOfNotNull(
            summary.registration, summary.km?.let { km(it) }, summary.ownerLabel, summary.transmission, summary.fuel, summary.colour
        ).map { it to null }
        b.y += pills(c, L + 8f, R - 8f, b.y, chips, size = 9.5f, h = 20f) + 14f

        drawScoreCard(b, summary, input.inspection, scannedOn)
        drawKeyFindings(b, DealerFindings.keyFindings(input.result, input.report, today))
        input.report.rc?.let { drawRto(b, it, today) }
            ?: run { b.y += banner(b.canvas, b.y, "The RTO record wasn't added to this report.", WHITE, CARD_LINE, GRAY) + GAP }
    }

    private fun drawScoreCard(b: ReportBuilder, summary: DealerSaleSummary, inspection: DealerInspection, scannedOn: String) {
        val h = 118f
        val top = cardAt(b, h, "Vehicle Health & Inspection Report")
        val c = b.canvas
        val scoreColor = when (summary.healthScore) { null -> GRAY; in 80..100 -> GREEN; in 60..79 -> AMBER; else -> RED }
        val cx = CL + 28f
        c.drawCircle(cx, top + 40f, 25f, strokePaint(scoreColor, 5.5f))
        c.drawText(summary.healthScore?.toString() ?: "—", cx, top + 45f, textPaint(TEXT, 17f, bold = true).apply { textAlign = Paint.Align.CENTER })
        if (summary.healthScore != null) c.drawText("/ 100", cx, top + 55f, textPaint(GRAY, 6.5f).apply { textAlign = Paint.Align.CENTER })
        val tx = CL + 68f
        c.drawText("OBD VEHICLE HEALTH SCORE", tx, top + 28f, textPaint(GRAY, 7.5f, bold = true).apply { letterSpacing = 0.1f })
        c.drawText(ellipsize(summary.healthLabel, CR - tx, textPaint(scoreColor, 14f, bold = true)), tx, top + 46f, textPaint(scoreColor, 14f, bold = true))
        c.drawText("Read from the car’s computer on $scannedOn", tx, top + 60f, textPaint(GRAY, 9f))

        val stars = inspection.averageStars
        val tiles = listOf(
            Triple(summary.faultCount.toString(), "Detected issues", if (summary.faultCount == 0) GREEN else AMBER),
            Triple(summary.redFlagCount.toString(), "OBD red flags", if (summary.redFlagCount == 0) GREEN else RED),
            Triple(summary.selfTestsReady?.let { "$it/${summary.selfTestsTotal}" } ?: "—", "Self-tests",
                if (summary.selfTestsReady != null && summary.selfTestsReady == summary.selfTestsTotal) GREEN else if (summary.selfTestsReady == null) GRAY else AMBER),
            Triple(stars?.let { starString(it) } ?: "Not rated", "Dealer", if (stars == null) GRAY else STAR)
        )
        val tileW = (CW - 3 * 8f) / 4
        tiles.forEachIndexed { i, (value, label, color) ->
            val x = CL + i * (tileW + 8f)
            val r = RectF(x, top + 74f, x + tileW, top + 106f)
            c.drawRoundRect(r, 9f, 9f, fillPaint(WHITE))
            c.drawRoundRect(r, 9f, 9f, strokePaint(CARD_LINE, 0.8f))
            c.drawText(value, x + 9f, r.bottom - 11f, textPaint(color, if (value.length > 5) 10.5f else 13f, bold = true))
            c.drawText(label, r.right - 8f, r.bottom - 12f, textPaint(GRAY, 8f).apply { textAlign = Paint.Align.RIGHT })
        }
        b.y = top + h + GAP
    }

    /** Problems first. */
    private fun drawKeyFindings(b: ReportBuilder, findings: List<KeyFinding>) {
        if (findings.isEmpty()) return
        val rowH = 20f
        splitCard(b, "Vehicle Health & Inspection Report", "Key findings", findings.map { rowH }, { c, i, y ->
            val f = findings[i]
            val (mark, color) = when (f.good) { true -> "✓" to GREEN; false -> "!" to RED; null -> "•" to GRAY }
            c.drawText(mark, CL + 2f, y + 12f, textPaint(color, 10f, bold = true))
            c.drawText(ellipsize(f.text, CW - 20f, textPaint(TEXT, 10f)), CL + 18f, y + 12f, textPaint(TEXT, 10f))
            if (i < findings.lastIndex) rowLine(c, CL, CR, y + rowH - 2f)
        })
    }

    /** Paper chips, then the whole record in two columns. */
    private fun drawRto(b: ReportBuilder, rc: com.enginesense.app.vehiclelookup.RcLookupResult, today: LocalDate) {
        val validity = RtoRecord.validity(rc, today)
        val chips = validity.map { line ->
            when (line.status) {
                ValidityStatus.VALID, ValidityStatus.LIFETIME -> "✓ ${line.label}" to GREEN
                ValidityStatus.EXPIRES_SOON -> "! ${line.label} expires soon" to AMBER
                ValidityStatus.EXPIRED -> "! ${line.label} expired" to RED
                ValidityStatus.UNKNOWN -> "! ${line.label} not on record" to AMBER
            }
        } + listOfNotNull(
            rc.financed?.let { if (it) "! Loan on the car" to RED else "✓ No loan" to GREEN },
            rc.blacklistStatus?.let { s -> if (RtoRecord.isBlacklisted(s)) "! Blacklisted" to RED else "✓ Not blacklisted" to GREEN }
        )
        val rows: List<RtoRow> = RtoRecord.vehicleRows(rc) +
            RtoRecord.dealerRows(rc).filterNot { it.label.startsWith("Chassis") || it.label.startsWith("Engine no") } +
            validity.map { RtoRow(it.label, it.display, concern = it.status == ValidityStatus.EXPIRED) }
        val half = (rows.size + 1) / 2
        val colW = (CW - 22f) / 2
        val rowH = 20f
        val chipsH = pills(null, CL, CR, 0f, chips, size = 8f, h = 16f) + 12f
        splitCard(
            b, "RTO record", "RTO record", List(half) { rowH },
            row = { c, i, y ->
                listOfNotNull(rows.getOrNull(i), rows.getOrNull(i + half)).forEachIndexed { col, row ->
                    val x = CL + col * (colW + 22f)
                    val keyP = textPaint(GRAY, 9.5f)
                    val valP = textPaint(if (row.concern) RED else TEXT, 9.5f, bold = true).apply { textAlign = Paint.Align.RIGHT }
                    c.drawText(row.label, x, y + 12f, keyP)
                    c.drawText(ellipsize(row.value, colW - keyP.measureText(row.label) - 10f, valP), x + colW, y + 12f, valP)
                    rowLine(c, x, x + colW, y + rowH - 2f)
                }
            },
            headH = chipsH, head = { c, y -> pills(c, CL, CR, y, chips, size = 8f, h = 16f) },
            tailH = 18f, tail = { c, y -> c.drawText(RtoRecord.sourceLine(rc), CL, y + 14f, textPaint(GRAY, 7f)) }
        )
    }

    // ---------- scan ----------

    private fun drawScanPages(b: ReportBuilder, input: DealerSaleReportInput, scannedOn: String) {
        val result = input.result
        val report = input.report

        val cats = result.health.categories.filter { it.state != CategoryState.UNAVAILABLE }.chunked(2)
        val colW = (CW - 22f) / 2
        splitCard(b, PAGE_SCAN, "Scan results", cats.map { 32f }, { c, i, y ->
            cats[i].forEachIndexed { col, cat ->
                val x = CL + col * (colW + 22f)
                val color = when (cat.state) { CategoryState.GOOD -> GREEN; CategoryState.WATCH -> AMBER; else -> RED }
                val word = when (cat.state) { CategoryState.GOOD -> "Good"; CategoryState.WATCH -> "Watch"; else -> "Needs attention" }
                c.drawText(cat.category.displayName, x, y + 10f, textPaint(TEXT, 10f))
                c.drawText(listOfNotNull(cat.score?.toString(), word).joinToString(" · "), x + colW, y + 10f, textPaint(color, 10f, bold = true).apply { textAlign = Paint.Align.RIGHT })
                val bar = RectF(x, y + 16f, x + colW, y + 21f)
                c.drawRoundRect(bar, 2.5f, 2.5f, fillPaint(CHIP_BG))
                c.drawRoundRect(RectF(bar.left, bar.top, bar.left + colW * ((cat.score ?: 0).coerceIn(0, 100) / 100f), bar.bottom), 2.5f, 2.5f, fillPaint(color))
            }
        }, titleRight = "Read from the car’s computer · $scannedOn")

        DealerFindings.readingAlerts(result.snapshot).forEach { alert ->
            val color = if (alert.serious) RED else AMBER
            val text = "Recommended action: ${alert.action}"
            val h = 28f + measureWrapped(CW, text, textPaint(TEXT, 9f)) + 10f
            val top = cardAt(b, h, PAGE_SCAN, tint(color, 22), tint(color, 90))
            b.canvas.drawText("${alert.name}: ${alert.valueText} — outside the normal range", CL, top + 19f, textPaint(color, 10.5f, bold = true))
            drawWrapped(b.canvas, CL, top + 26f, CW, text, textPaint(TEXT, 9f))
            b.y = top + h + GAP
        }

        drawIssues(b, report)

        if (report.redFlags.isNotEmpty()) {
            val p = textPaint(TEXT, 9f)
            val heights = report.redFlags.map { 20f + measureWrapped(CW - 12f, it.evidence, p) + 8f }
            splitCard(b, PAGE_SCAN, "OBD red flags", heights, { c, i, y ->
                val flag = report.redFlags[i]
                c.drawRect(CL, y + 2f, CL + 3f, y + heights[i] - 8f, fillPaint(RED))
                c.drawText(flag.title, CL + 12f, y + 13f, textPaint(RED, 10f, bold = true))
                drawWrapped(c, CL + 12f, y + 18f, CW - 12f, flag.evidence, p)
            })
        }

        drawOdometerAndSelfTests(b, report, result)
        drawReadings(b, result)
    }

    private fun drawIssues(b: ReportBuilder, report: PrePurchaseReport) {
        val title = "Detected issues & estimated repair costs"
        val faults = report.faults.filter { it.kind != FaultKind.EXTERIOR }
        if (faults.isEmpty()) {
            val top = cardAt(b, TITLE_H + 30f + PAD, PAGE_SCAN)
            val c = b.canvas
            c.drawText(title, CL, top + 24f, titlePaint())
            val r = RectF(CL, top + TITLE_H, CR, top + TITLE_H + 28f)
            c.drawRoundRect(r, 8f, 8f, fillPaint(Color.parseColor("#DCFCE7")))
            c.drawText("No fault codes found — stored, hidden or developing.", CL + 12f, r.top + 18f, textPaint(GREEN, 10f, bold = true))
            b.y = top + TITLE_H + 30f + PAD + GAP
            return
        }
        val cols = floatArrayOf(CL, CL + 52f, CL + 330f, CR)
        val p = textPaint(TEXT, 9.5f)
        val heights = faults.map { maxOf(20f, measureWrapped(cols[2] - cols[1] - 8f, it.title, p) + 8f) }
        val tailH = (if (report.repairMaxInr > 0) 20f else 0f) + 16f
        splitCard(
            b, PAGE_SCAN, title, heights,
            row = { c, i, y ->
                val line = faults[i]
                c.drawText(line.code ?: "—", cols[0], y + 13f, textPaint(TEXT, 9.5f, bold = true))
                drawWrapped(c, cols[1], y + 3f, cols[2] - cols[1] - 8f, line.title, p)
                val (type, color) = when (line.kind) {
                    FaultKind.STORED -> if (line.severity == Severity.CRITICAL) "Serious" to RED else "Stored" to AMBER
                    FaultKind.HIDDEN_PERMANENT -> "Hidden" to RED
                    FaultKind.PENDING -> "Developing" to AMBER
                    FaultKind.EXTERIOR -> "Body / cabin" to GRAY
                    FaultKind.DRIVE -> "Test drive" to AMBER
                }
                c.drawText(type, cols[2], y + 13f, textPaint(color, 9.5f, bold = true))
                val cost = line.cost?.let { "${rupees(it.minInr.toDouble())}–${rupees(it.maxInr.toDouble(), symbol = false)}" } ?: "Workshop quote"
                c.drawText(cost, CR, y + 13f, textPaint(if (line.cost == null) GRAY else TEXT, 9.5f).apply { textAlign = Paint.Align.RIGHT })
                rowLine(c, CL, CR, y + heights[i] - 2f)
            },
            headH = 18f, head = { c, y -> tableHead(c, y, cols, listOf("CODE", "ISSUE", "TYPE", "EST. COST")) },
            tailH = tailH, tail = { c, y ->
                var ty = y
                if (report.repairMaxInr > 0) {
                    c.drawText("Estimated total", CL, ty + 14f, textPaint(TEXT, 10f, bold = true))
                    c.drawText("${rupees(report.repairMinInr.toDouble())}–${rupees(report.repairMaxInr.toDouble(), symbol = false)}", CR, ty + 14f, textPaint(TEXT, 10f, bold = true).apply { textAlign = Paint.Align.RIGHT })
                    ty += 20f
                }
                c.drawText("Tentative workshop ranges, not quotes.", CL, ty + 11f, textPaint(GRAY, 7.5f))
            }
        )
    }

    /** Two cards side by side, like the web page on a wide screen. */
    private fun drawOdometerAndSelfTests(b: ReportBuilder, report: PrePurchaseReport, result: ScanResult) {
        val odo = report.odometer
        val monitors = result.readiness?.monitors?.filter { it.supported }.orEmpty()
        val odoRows = listOf(
            "Dashboard" to (odo.dashboardKm?.let { km(it) } ?: "Not entered"),
            "Engine computer" to (odo.ecuKm?.let { km(it) } ?: if (odo.ecuSupported == false) "Not reported" else "No reading"),
            "Dashboard vs computer" to when (odo.match) {
                OdometerMatch.MATCHES -> "Match"
                OdometerMatch.SMALL_GAP -> "Small gap: ${km(kotlin.math.abs((odo.ecuKm ?: 0.0) - (odo.dashboardKm ?: 0.0)))}"
                OdometerMatch.MISMATCH -> "Differ by ${km(kotlin.math.abs((odo.ecuKm ?: 0.0) - (odo.dashboardKm ?: 0.0)))}"
                null -> "Couldn't compare"
            },
            "Since codes cleared" to (odo.kmSinceCodesCleared?.let { km(it) } ?: "Not reported")
        )
        val halfW = (W - GAP) / 2
        val inner = halfW - 32f
        val note = "A match means OBD shows no odometer red flags; it can't rule out a rollback done on both the dashboard and the engine computer."
        val noteP = textPaint(GRAY, 7f)
        val odoH = odoRows.size * 20f + 4f + measureWrapped(inner, note, noteP)
        val testsH = maxOf(1, monitors.size) * 18f
        val h = TITLE_H + maxOf(odoH, testsH) + PAD
        val top = cardAt(b, h, PAGE_SCAN, left = L, right = L + halfW)
        cardBg(b.canvas, RectF(L + halfW + GAP, top, R, top + h))
        val c = b.canvas

        val ox = L + 16f
        c.drawText("Odometer check", ox, top + 24f, titlePaint())
        odoRows.forEachIndexed { i, (k, v) ->
            val y = top + TITLE_H + i * 20f
            val color = when { v.startsWith("Match") -> GREEN; v.startsWith("Differ") -> RED; else -> TEXT }
            c.drawText(k, ox, y + 12f, textPaint(GRAY, 9.5f))
            c.drawText(v, ox + inner, y + 12f, textPaint(color, 9.5f, bold = true).apply { textAlign = Paint.Align.RIGHT })
            rowLine(c, ox, ox + inner, y + 18f)
        }
        drawWrapped(c, ox, top + TITLE_H + odoRows.size * 20f + 4f, inner, note, noteP)

        val sx = L + halfW + GAP + 16f
        val ready = monitors.count { it.ready }
        c.drawText(if (monitors.isEmpty()) "Self-tests" else "Self-tests · $ready of ${monitors.size}", sx, top + 24f, titlePaint())
        if (monitors.isEmpty()) c.drawText("This car didn't report its self-tests.", sx, top + TITLE_H + 12f, textPaint(GRAY, 9.5f))
        monitors.forEachIndexed { i, m ->
            val y = top + TITLE_H + i * 18f
            c.drawText(ellipsize(m.name, inner - 70f, textPaint(GRAY, 9.5f)), sx, y + 12f, textPaint(GRAY, 9.5f))
            c.drawText(if (m.ready) "✓ Complete" else "Not yet", sx + inner, y + 12f, textPaint(if (m.ready) GREEN else AMBER, 9.5f, bold = true).apply { textAlign = Paint.Align.RIGHT })
            if (i < monitors.lastIndex) rowLine(c, sx, sx + inner, y + 16f)
        }
        b.y = top + h + GAP
    }

    /** The web page has this one folded away; on paper it's always open. */
    private fun drawReadings(b: ReportBuilder, result: ScanResult) {
        val rows = keyReadingRows(result.snapshot)
        if (rows.isEmpty()) return
        val cols = floatArrayOf(CL, CL + 280f, CL + 296f, CR)
        val rowH = 20f
        splitCard(
            b, PAGE_SCAN, "Engine readings at the scan", rows.map { rowH },
            row = { c, i, y ->
                val row = rows[i]
                val off = row.chipColor == ATTENTION || row.chipColor == WATCH
                val color = if (row.chipColor == ATTENTION) RED else if (row.chipColor == WATCH) AMBER else if (row.chipColor == GOOD) GREEN else GRAY
                if (off) c.drawRect(CL - 6f, y, CR + 6f, y + rowH - 2f, fillPaint(tint(color, 22)))
                c.drawText(row.name, cols[0], y + 12f, textPaint(TEXT, 9.5f))
                c.drawText(row.value, cols[1], y + 12f, textPaint(if (off) color else TEXT, 9.5f, bold = true).apply { textAlign = Paint.Align.RIGHT })
                c.drawText(row.range, cols[2], y + 12f, textPaint(GRAY, 9.5f))
                c.drawText(row.chip, CR, y + 12f, textPaint(color, 9.5f, bold = true).apply { textAlign = Paint.Align.RIGHT })
                rowLine(c, CL, CR, y + rowH - 2f)
            },
            headH = 18f, head = { c, y ->
                val p = textPaint(GRAY, 7f, bold = true)
                c.drawText("READING", cols[0], y + 8f, p)
                c.drawText("VALUE", cols[1], y + 8f, textPaint(GRAY, 7f, bold = true).apply { textAlign = Paint.Align.RIGHT })
                c.drawText("NORMAL", cols[2], y + 8f, p)
                c.drawText("STATUS", cols[3], y + 8f, textPaint(GRAY, 7f, bold = true).apply { textAlign = Paint.Align.RIGHT })
            },
            tailH = 16f, tail = { c, y ->
                c.drawText("\"Normal\" is the range for a warmed-up, healthy engine; speed, load and intake pressure change with driving.", CL, y + 11f, textPaint(GRAY, 7f))
            }
        )
    }

    // ---------- the dealer's part ----------

    private fun drawDealerPages(b: ReportBuilder, input: DealerSaleReportInput, reportId: String) {
        val dealerName = input.dealer.name
        val inspection = input.inspection
        if (!inspection.isEmpty) {
            val rated = INSPECTION_ITEMS.filter { it in inspection.ratings }
            val half = (rated.size + 1) / 2
            val colW = (CW - 22f) / 2
            val extras = inspection.extras.map { "✓ $it" to GREEN }
            val noteP = textPaint(TEXT, 10f).apply { textSkewX = -0.2f }
            val noteText = inspection.note.trim().takeIf { it.isNotEmpty() }?.let { "“$it”" }
            val extrasH = if (extras.isEmpty()) 0f else 18f + pills(null, CL, CR, 0f, extras, size = 8.5f, h = 17f) + 6f
            val noteH = noteText?.let { measureWrapped(CW, it, noteP) + 8f } ?: 0f
            splitCard(
                b, PAGE_DEALER, "Dealer-declared inspection", List(half) { 20f },
                row = { c, i, y ->
                    listOfNotNull(rated.getOrNull(i), rated.getOrNull(i + half)).forEachIndexed { col, item ->
                        val x = CL + col * (colW + 22f)
                        c.drawText(item, x, y + 12f, textPaint(TEXT, 9.5f))
                        c.drawText(starString(inspection.ratings.getValue(item)), x + colW, y + 13f, textPaint(STAR, 11.5f).apply { textAlign = Paint.Align.RIGHT })
                        rowLine(c, x, x + colW, y + 18f)
                    }
                },
                headH = 18f, head = { c, y -> c.drawText("Rated by $dealerName. See the note at the end of this report.", CL, y + 6f, textPaint(GRAY, 8.5f)) },
                tailH = extrasH + noteH, tail = { c, y ->
                    var ty = y
                    if (extras.isNotEmpty()) {
                        ty += 6f
                        c.drawText("DECLARED BY THE DEALER", CL, ty + 8f, textPaint(GRAY, 7f, bold = true).apply { letterSpacing = 0.1f })
                        ty += 12f
                        ty += pills(c, CL, CR, ty, extras, size = 8.5f, h = 17f) + 6f
                    }
                    noteText?.let { drawWrapped(c, CL, ty + 4f, CW, it, noteP) }
                }
            )
        }

        val more = input.photos.drop(1).chunked(3)
        if (more.isNotEmpty()) {
            val gap = 8f
            val w = (CW - gap * 2) / 3
            val tileH = 100f
            val cleaned = input.photos.any { it.backgroundCleaned }
            splitCard(
                b, "Photos", "Photos", more.map { tileH + 22f },
                row = { c, i, y ->
                    more[i].forEachIndexed { k, photo ->
                        val x = CL + k * (w + gap)
                        val tile = RectF(x, y, x + w, y + tileH)
                        drawBitmapSmart(c, photo.bitmap, tile, radius = 8f, trimToCar = photo.backgroundCleaned)
                        c.drawText(photo.label, x, y + tileH + 13f, textPaint(TEXT, 9f, bold = true))
                    }
                },
                headH = 14f, head = { c, y -> c.drawText("Taken by $dealerName", CL, y + 4f, textPaint(GRAY, 8.5f)) },
                tailH = if (cleaned) 14f else 0f, tail = { c, y ->
                    if (cleaned) c.drawText("Backgrounds replaced with a studio floor by AI (Gemini); the car isn't edited.", CL, y + 8f, textPaint(GRAY, 7.5f))
                }
            )
        }

        input.priceInr?.takeIf { it > 0 }?.let { price ->
            val h = 50f
            val top = cardAt(b, h, PAGE_DEALER)
            b.canvas.drawText("Price", CL, top + 30f, textPaint(TEXT, 14f, bold = true))
            b.canvas.drawText("Set by $dealerName", CL + 46f, top + 30f, textPaint(GRAY, 9f))
            b.canvas.drawText("₹" + indianGrouping(price.toLong()), CR, top + 32f, textPaint(TEXT, 18f, bold = true).apply { textAlign = Paint.Align.RIGHT })
            b.y = top + h + GAP
        }

        // Last, in plain small print under the cards: the link, what GetAutoSure checked, then what it didn't.
        val closing = "Scan, RTO record and repair costs by GetAutoSure. An OBD scan covers the engine, fuel and emissions systems; it doesn't cover " +
            "bodywork, tyres, brakes or suspension, and it isn't a guarantee of the car's condition or history."
        val note = "The inspection ratings and claims (including accident-free, service records and ownership), the photos and the price are declared by " +
            "$dealerName and not verified by GetAutoSure. GetAutoSure did not inspect the body, paint, tyres, brakes, suspension, AC or accident history."
        val p = textPaint(GRAY, 7.5f)
        val tw = W - 16f
        val h = (if (input.webUrl != null) 20f else 0f) + measureWrapped(tw, closing, p) + measureWrapped(tw, note, p) + 16f
        if (b.y + h > BOTTOM) b.newPage(PAGE_DEALER)
        b.y += 4f
        input.webUrl?.let { url ->
            b.canvas.drawText("Verify this report: ${url.removePrefix("https://")} · Report $reportId", L + 8f, b.y + 9f, textPaint(TEXT, 9.5f, bold = true))
            b.y += 20f
        }
        b.y += drawWrapped(b.canvas, L + 8f, b.y, tw, closing, p) + 4f
        b.y += drawWrapped(b.canvas, L + 8f, b.y, tw, note, p)
    }

    // ---------- pieces ----------

    /** Dark band on pages after the first: dealer and car, so a loose page still says whose car it is. */
    private fun drawCompactBand(c: Canvas, dealer: DealerProfile, logo: Bitmap?, summary: DealerSaleSummary): Float {
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 46f, fillPaint(INK_BAND))
        drawDealerBadge(c, L + 8f, 10f, 26f, logo, dealer.name)
        c.drawText(ellipsize(dealer.name, 200f, textPaint(WHITE, 12f, bold = true)), L + 42f, 28f, textPaint(WHITE, 12f, bold = true))
        val car = listOfNotNull(listOfNotNull(summary.year?.toString(), summary.carName, summary.variant).joinToString(" "), summary.registration).joinToString(" · ")
        c.drawText(ellipsize(car, 280f, textPaint(Color.parseColor("#D1D5DB"), 9f)), R - 8f, 28f, textPaint(Color.parseColor("#D1D5DB"), 9f).apply { textAlign = Paint.Align.RIGHT })
        return 58f
    }

    private fun titlePaint() = textPaint(TEXT, 14f, bold = true)

    private fun cardBg(c: Canvas, r: RectF, fill: Int = WHITE, line: Int = CARD_LINE) {
        c.drawRoundRect(r, 12f, 12f, fillPaint(fill))
        c.drawRoundRect(r, 12f, 12f, strokePaint(line, 0.6f))
    }

    /** Starts a new page if a card of height [h] won't fit, draws its background, returns its top. */
    private fun cardAt(b: ReportBuilder, h: Float, page: String, fill: Int = WHITE, line: Int = CARD_LINE, left: Float = L, right: Float = R): Float {
        if (b.y + h > BOTTOM) b.newPage(page)
        cardBg(b.canvas, RectF(left, b.y, right, b.y + h), fill, line)
        return b.y
    }

    /**
     * A titled card of rows ([rows] are their heights). [head] draws under the title on the first
     * part only, [tail] after the last row. Rows that don't fit go on to the next page in a
     * "(continued)" card, so a long fault list or RTO record never runs off the page.
     */
    private fun splitCard(
        b: ReportBuilder, page: String, title: String,
        rows: List<Float>, row: (Canvas, Int, Float) -> Unit,
        headH: Float = 0f, head: (Canvas, Float) -> Unit = { _, _ -> },
        tailH: Float = 0f, tail: (Canvas, Float) -> Unit = { _, _ -> },
        titleRight: String? = null
    ) {
        var i = 0
        var first = true
        while (true) {
            val topH = TITLE_H + if (first) headH else 0f
            fun tailAfter(j: Int) = if (j >= rows.size) tailH else 0f
            if (b.y + topH + (rows.getOrNull(i) ?: 0f) + tailAfter(i + 1) + PAD > BOTTOM) b.newPage(page)
            val avail = BOTTOM - b.y - topH - PAD
            var used = 0f
            var j = i
            while (j < rows.size && used + rows[j] + tailAfter(j + 1) <= avail) { used += rows[j]; j++ }
            if (j == i && i < rows.size) { used += rows[i]; j = i + 1 }
            // Don't leave a stub of a few rows behind: the whole card moves on instead.
            if (first && j < rows.size && j - i < 4 && b.y > 80f) { b.newPage(page); continue }
            val last = j >= rows.size
            val h = topH + used + (if (last) tailH else 0f) + PAD
            val top = b.y
            val c = b.canvas
            cardBg(c, RectF(L, top, R, top + h))
            c.drawText(if (first) title else "$title (continued)", CL, top + 24f, titlePaint())
            if (first) {
                titleRight?.let { c.drawText(it, CR, top + 24f, textPaint(GRAY, 8f).apply { textAlign = Paint.Align.RIGHT }) }
                head(c, top + TITLE_H)
            }
            var y = top + topH
            for (k in i until j) { row(c, k, y); y += rows[k] }
            if (last) tail(c, y)
            b.y = top + h + GAP
            if (last) return
            i = j
            first = false
        }
    }

    private fun rowLine(c: Canvas, x1: Float, x2: Float, y: Float) = c.drawLine(x1, y, x2, y, strokePaint(ROW_LINE, 0.8f))

    private fun tableHead(c: Canvas, y: Float, cols: FloatArray, heads: List<String>) {
        heads.forEachIndexed { i, h ->
            val p = textPaint(GRAY, 7f, bold = true)
            if (i == heads.lastIndex) p.textAlign = Paint.Align.RIGHT
            c.drawText(h, cols[if (i == heads.lastIndex) cols.lastIndex else i], y + 8f, p)
        }
    }

    /**
     * Rounded pills, wrapping between [x0] and [right]; a null colour is a grey chip. Returns the
     * height used. With a null canvas it only measures.
     */
    private fun pills(c: Canvas?, x0: Float, right: Float, top: Float, items: List<Pair<String, Int?>>, size: Float, h: Float): Float {
        var x = x0
        var y = top
        items.forEach { (text, color) ->
            val p = textPaint(color ?: CHIP_TEXT, size, bold = true)
            val w = p.measureText(text) + h * 0.9f
            if (x + w > right && x > x0) { x = x0; y += h + 5f }
            c?.drawRoundRect(RectF(x, y, x + w, y + h), h / 2, h / 2, fillPaint(color?.let { tint(it, 34) } ?: CHIP_BG))
            c?.drawText(text, x + h * 0.45f, y + h / 2 + size * 0.36f, p)
            x += w + 5f
        }
        return if (items.isEmpty()) 0f else y - top + h
    }

    /** A full-width banner at this report's own margins (drawBanner uses the standard ones). */
    private fun banner(c: Canvas, y: Float, text: String, bg: Int, border: Int, textColor: Int): Float {
        val p = textPaint(textColor, 9f, bold = true)
        val h = measureWrapped(W - 24f, text, p) + 22f
        val r = RectF(L, y, R, y + h)
        c.drawRoundRect(r, 12f, 12f, fillPaint(bg))
        c.drawRoundRect(r, 12f, 12f, strokePaint(border, 0.8f))
        drawWrapped(c, L + 12f, y + 11f, W - 24f, text, p)
        return h
    }

    /** The QR as vector squares, so it stays sharp in the PDF at any zoom. */
    private fun drawQr(c: Canvas, text: String, box: RectF) {
        val matrix = runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        }.getOrNull() ?: return
        val n = matrix.width
        val cell = box.width() / n
        val black = fillPaint(Color.BLACK)
        for (yy in 0 until n) for (xx in 0 until n) {
            if (matrix.get(xx, yy)) c.drawRect(box.left + xx * cell, box.top + yy * cell, box.left + (xx + 1) * cell + 0.3f, box.top + (yy + 1) * cell + 0.3f, black)
        }
    }

    internal fun starString(stars: Int): String = "★".repeat(stars.coerceIn(0, 5)) + "☆".repeat(5 - stars.coerceIn(0, 5))

    private fun km(value: Double) = "%,d km".format(value.toLong())

    private fun rupees(value: Double, symbol: Boolean = true): String {
        val prefix = if (symbol) "₹" else ""
        return if (value >= 100000) prefix + "%.2f L".format(value / 100000) else prefix + "%,.0f".format(value)
    }
}
