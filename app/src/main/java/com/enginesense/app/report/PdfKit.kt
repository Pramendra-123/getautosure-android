package com.enginesense.app.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.enginesense.app.storage.DealerProfile
import com.enginesense.app.vehicle.PidId
import com.enginesense.app.vehicle.ReadinessStatus
import com.enginesense.app.vehicle.SensorRating
import com.enginesense.app.vehicle.VehicleSnapshot
import com.enginesense.app.vehicle.benchmark
import com.enginesense.app.vehicle.rate
import com.enginesense.app.vehiclelookup.RcLookupResult
import com.enginesense.app.vehiclelookup.RtoRecord
import com.enginesense.app.vehiclelookup.RtoRow
import com.enginesense.app.vehiclelookup.ValidityStatus
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/*
 * Drawing pieces shared by every PDF this app makes — the advanced report
 * ([VehicleReportPdfGenerator]), the pre-purchase report ([PrePurchaseReportPdfGenerator]) and
 * the dealer-branded variant of either (a [DealerProfile] passed to [ReportBuilder]). Page size,
 * palette, header/footer and dealer branding live here so the reports can't drift apart.
 */

internal const val PAGE_W = 595
internal const val PAGE_H = 842
internal const val MARGIN = 40f
internal const val CONTENT_RIGHT = PAGE_W - MARGIN
internal const val CONTENT_BOTTOM = PAGE_H - MARGIN - 20f

internal val ORANGE = Color.parseColor("#FF6B2C")
internal val INK = Color.parseColor("#12151A")
internal val GRAY = Color.parseColor("#6B7280")
internal val GRAY_LIGHT = Color.parseColor("#F8F9FB")
internal val BORDER = Color.parseColor("#E6E8EB")
internal val GOOD = Color.parseColor("#1AA168")
internal val WATCH = Color.parseColor("#B45309")
internal val ATTENTION = Color.parseColor("#B91C1C")
internal val AMBER_BG = Color.parseColor("#FFFBEB")
internal val AMBER_BORDER = Color.parseColor("#F0DBA8")
internal val WHITE = Color.WHITE
internal val DEALER_BADGE = Color.parseColor("#1E3A8A")

internal fun textPaint(color: Int, size: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    textSize = size
    isFakeBoldText = bold
}

internal fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color; style = Paint.Style.STROKE; strokeWidth = width
}

internal fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color; style = Paint.Style.FILL
}

internal fun drawField(canvas: Canvas, x: Float, y: Float, label: String, value: String, valueColor: Int = INK) {
    canvas.drawText(label, x, y + 8f, textPaint(GRAY, 7.5f))
    canvas.drawText(value, x, y + 20f, textPaint(valueColor, 9.5f, bold = true))
}

internal fun drawTableHeader(canvas: Canvas, y: Float, colX: FloatArray, headers: List<String>): Float {
    val h = 20f
    canvas.drawRect(MARGIN, y, CONTENT_RIGHT, y + h, fillPaint(INK))
    headers.forEachIndexed { i, text -> canvas.drawText(text, colX[i] + 6f, y + 14f, textPaint(WHITE, 8.5f, bold = true)) }
    return h + 4f
}

/** Height [text] would take if drawn by [drawWrapped] — for sizing a box before drawing into it. */
internal fun measureWrapped(width: Float, text: String, paint: TextPaint): Float {
    if (text.isBlank()) return 0f
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(10))
        .setLineSpacing(1f, 1.15f).build().height.toFloat()
}

/** Draws wrapped body text and returns the height consumed, so callers can advance their y cursor. */
internal fun drawWrapped(canvas: Canvas, x: Float, y: Float, width: Float, text: String, paint: TextPaint): Float {
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
internal fun drawBanner(canvas: Canvas, y: Float, text: String, bg: Int, border: Int, textColor: Int): Float {
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

/** Same geometry as brand/getautosure-icon.svg — orange badge, white arc + checkmark, black wheels. */
internal fun drawLogo(canvas: Canvas, x: Float, y: Float, size: Float) {
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

/** The plain (non-dealer) cover header: logo + wordmark on the left, report metadata on the right. Returns the height consumed. */
internal fun drawBrandCoverHeader(canvas: Canvas, y: Float, reportId: String, scannedOn: String): Float {
    drawLogo(canvas, MARGIN, y, 34f)
    val wordX = MARGIN + 42f
    canvas.drawText("GetAuto", wordX, y + 23f, textPaint(INK, 15f, bold = true))
    val getAutoW = textPaint(INK, 15f, bold = true).measureText("GetAuto")
    canvas.drawText("Sure", wordX + getAutoW, y + 23f, textPaint(ORANGE, 15f, bold = true))

    val metaPaint = textPaint(GRAY, 8f)
    metaPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText("Report ID: $reportId", CONTENT_RIGHT, y + 7f, metaPaint)
    canvas.drawText("Scanned: $scannedOn", CONTENT_RIGHT, y + 17f, metaPaint)
    canvas.drawText("Device: GetAutoSure OBD2 Scanner", CONTENT_RIGHT, y + 27f, metaPaint)
    return 44f
}

/** Up to the first two words' initials, uppercased — "AutoTrust Motors" -> "AM". Falls back to "?" for a blank name, which [DealerProfileScreen] already prevents via its save button, but this stays defensive regardless. */
internal fun dealerInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("").ifBlank { "?" }

/** Draws [logo] scaled into a rounded square if present, else an initials badge in [DEALER_BADGE] — same visual slot either way. */
internal fun drawDealerBadge(canvas: Canvas, x: Float, y: Float, size: Float, logo: Bitmap?, dealerName: String) {
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
internal fun drawDealerCoverHeader(
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
internal fun drawDealerCompactHeader(canvas: Canvas, y: Float, dealer: DealerProfile, logo: Bitmap?, title: String) {
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

// ---------- sections shared by both PDFs ----------

/** A section heading with a one-line explanation under it. */
internal fun drawSectionTitle(b: ReportBuilder, title: String, subtitle: String, pageTitle: String) {
    val subPaint = textPaint(GRAY, 8.5f)
    b.ensure(20f + measureWrapped(CONTENT_RIGHT - MARGIN, subtitle, subPaint) + 40f, pageTitle)
    b.canvas.drawText(title, MARGIN, b.y + 14f, textPaint(INK, 15f, bold = true))
    b.y += 20f
    b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, subtitle, subPaint)
    b.y += 10f
}

/** A small rounded label right-aligned at [right]. Returns its width. */
internal fun drawChip(canvas: Canvas, right: Float, top: Float, text: String, color: Int): Float {
    val paint = textPaint(color, 8f, bold = true)
    val w = paint.measureText(text) + 16f
    canvas.drawRoundRect(RectF(right - w, top, right, top + 14f), 7f, 7f, fillPaint(tint(color, 30)))
    canvas.drawText(text, right - w + 8f, top + 10f, paint)
    return w
}

internal fun tint(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

private const val GRID_ROW_H = 27f

/** Label/value pairs in two columns — denser than one row per line, so a long RC record doesn't eat a page. */
private fun drawRowGrid(b: ReportBuilder, rows: List<RtoRow>, pageTitle: String) {
    val colW = (CONTENT_RIGHT - MARGIN) / 2
    rows.chunked(2).forEach { pair ->
        b.ensure(GRID_ROW_H, pageTitle)
        pair.forEachIndexed { i, row ->
            val x = MARGIN + i * colW
            val value = ellipsize(row.value, colW - 12f, textPaint(INK, 9.5f, bold = true))
            drawField(b.canvas, x, b.y, row.label, value, if (row.concern) ATTENTION else INK)
        }
        b.y += GRID_ROW_H
    }
}

/** Cuts [text] with "…" so it fits [width] — RTO strings (financer names, RTO offices) can be long. */
internal fun ellipsize(text: String, width: Float, paint: TextPaint): String {
    if (paint.measureText(text) <= width) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + "…") > width) end--
    return text.substring(0, end).trimEnd() + "…"
}

/**
 * The car's RTO record: what it is, its insurance/PUC/fitness/tax dates with what each means today,
 * and — for a dealer with full RC access — ownership, loan and legal status. Shared by every report
 * so the record reads the same everywhere. The owner's name is never part of [RcLookupResult].
 */
internal fun drawRtoSection(
    b: ReportBuilder, rc: RcLookupResult, today: LocalDate, pageTitle: String,
    /** One closing line in [footnoteColor], e.g. the buyer report's chassis-match result — kept on the same page as the dates. */
    footnote: String? = null, footnoteColor: Int = GRAY,
    subtitle: String = "The official registration record for this car, from the RTO (VAHAN). Check it against the RC the seller hands you."
) {
    drawSectionTitle(b, "RTO record", subtitle, pageTitle)
    drawRowGrid(b, RtoRecord.vehicleRows(rc), pageTitle)
    b.y += 4f

    val dealerRows = RtoRecord.dealerRows(rc)
    val sourceLine = RtoRecord.sourceLine(rc)
    val footnotePaint = textPaint(footnoteColor, 9f, bold = true)
    val tailH = 14f + measureWrapped(CONTENT_RIGHT - MARGIN, sourceLine, textPaint(GRAY, 7.5f)) +
        (footnote?.let { measureWrapped(CONTENT_RIGHT - MARGIN, it, footnotePaint) + 10f } ?: 0f)

    // Validity — one row each, with a status chip so an expired date can't be missed. Without
    // ownership rows to follow, the source line and footnote come right after, so keep them together.
    b.ensure(24f + 4 * 22f + (if (dealerRows.isEmpty()) tailH else 0f), pageTitle)
    b.canvas.drawText("VALID UNTIL", MARGIN, b.y + 8f, textPaint(ORANGE, 8.5f, bold = true))
    b.y += 14f
    RtoRecord.validity(rc, today).forEach { line ->
        b.ensure(22f, pageTitle)
        b.canvas.drawText(line.label, MARGIN + 4f, b.y + 13f, textPaint(GRAY, 9f))
        b.canvas.drawText(line.display, MARGIN + 170f, b.y + 13f, textPaint(if (line.status == ValidityStatus.UNKNOWN) GRAY else INK, 9f, bold = true))
        val (label, color) = when (line.status) {
            ValidityStatus.VALID -> "✓ Valid" to GOOD
            ValidityStatus.LIFETIME -> "✓ Lifetime" to GOOD
            ValidityStatus.EXPIRES_SOON -> "! Expires soon" to WATCH
            ValidityStatus.EXPIRED -> "! Expired" to ATTENTION
            ValidityStatus.UNKNOWN -> "– Not on record" to GRAY
        }
        drawChip(b.canvas, CONTENT_RIGHT, b.y + 3f, label, color)
        b.canvas.drawLine(MARGIN, b.y + 20f, CONTENT_RIGHT, b.y + 20f, strokePaint(BORDER, 0.5f))
        b.y += 22f
    }
    b.y += 10f

    if (dealerRows.isNotEmpty()) {
        b.ensure(14f + ((dealerRows.size + 1) / 2) * GRID_ROW_H + tailH, pageTitle)
        b.canvas.drawText("OWNERSHIP & LEGAL STATUS", MARGIN, b.y + 8f, textPaint(ORANGE, 8.5f, bold = true))
        b.y += 14f
        drawRowGrid(b, dealerRows, pageTitle)
        b.y += 4f
    }

    b.ensure(tailH, pageTitle)
    if (footnote != null) {
        b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, footnote, footnotePaint)
        b.y += 10f
    }
    b.y += drawWrapped(b.canvas, MARGIN, b.y, CONTENT_RIGHT - MARGIN, sourceLine, textPaint(GRAY, 7.5f))
    b.y += 18f
}

/** The readings a non-mechanic can judge, in this order — the rest stay in the full sensor table. */
internal val KEY_READING_PIDS = listOf(
    PidId.CONTROL_MODULE_VOLTAGE, PidId.COOLANT_TEMP_C, PidId.SHORT_TERM_FUEL_TRIM_1, PidId.LONG_TERM_FUEL_TRIM_1,
    PidId.RPM, PidId.ENGINE_LOAD, PidId.INTAKE_MANIFOLD_PRESSURE, PidId.ENGINE_OIL_TEMP_C
)

private val KEY_READING_NAMES = mapOf(
    PidId.CONTROL_MODULE_VOLTAGE to "Battery / charging",
    PidId.COOLANT_TEMP_C to "Coolant temperature",
    PidId.SHORT_TERM_FUEL_TRIM_1 to "Fuel mixture (short-term)",
    PidId.LONG_TERM_FUEL_TRIM_1 to "Fuel mixture (long-term)",
    PidId.RPM to "Engine speed",
    PidId.ENGINE_LOAD to "Engine load",
    PidId.INTAKE_MANIFOLD_PRESSURE to "Intake pressure",
    PidId.ENGINE_OIL_TEMP_C to "Oil temperature"
)

/** Rows of (name, value, normal range, chip label, chip color) for [KEY_READING_PIDS] the car actually reported. */
internal fun keyReadingRows(snapshot: VehicleSnapshot): List<KeyReadingRow> = KEY_READING_PIDS.mapNotNull { pid ->
    val value = snapshot.verifiedValue(pid) ?: return@mapNotNull null
    val benchmark = pid.benchmark()
    val unit = pid.unit
    val valueText = when (pid) {
        PidId.RPM -> "${value.toInt()} rpm"
        PidId.CONTROL_MODULE_VOLTAGE -> "%.1f V".format(value)
        PidId.SHORT_TERM_FUEL_TRIM_1, PidId.LONG_TERM_FUEL_TRIM_1 -> (if (value >= 0) "+" else "") + "%.1f%%".format(value)
        else -> "${value.toInt()} $unit".trim()
    }
    val rangeText = benchmark?.typicalRange?.let { r ->
        when (pid) {
            PidId.SHORT_TERM_FUEL_TRIM_1, PidId.LONG_TERM_FUEL_TRIM_1 -> "within ±${r.endInclusive.toInt()}%"
            PidId.CONTROL_MODULE_VOLTAGE -> "%.1f–%.1f V".format(r.start, r.endInclusive)
            else -> "${r.start.toInt()}–${r.endInclusive.toInt()} $unit".trim()
        }
    } ?: "—"
    // Below the warm range right after a start just means the engine hasn't warmed up — not a fault.
    val warmingUp = (pid == PidId.COOLANT_TEMP_C || pid == PidId.ENGINE_OIL_TEMP_C) && benchmark != null && value < benchmark.typicalRange.start
    val (chip, color) = when {
        warmingUp -> "Warming up" to GRAY
        benchmark?.contextDependent == true -> (if (value in benchmark.typicalRange) "✓ Typical" to GOOD else "Varies with driving" to GRAY)
        else -> when (pid.rate(value)) {
            SensorRating.GOOD -> "✓ Normal" to GOOD
            SensorRating.WATCH -> "! Watch" to WATCH
            SensorRating.ATTENTION -> "! Check" to ATTENTION
            SensorRating.NOT_RATED -> "–" to GRAY
        }
    }
    KeyReadingRow(KEY_READING_NAMES[pid] ?: pid.displayName, valueText, rangeText, chip, color)
}

internal data class KeyReadingRow(val name: String, val value: String, val range: String, val chip: String, val chipColor: Int)

/** The key readings as a table with normal ranges — what a buyer or owner can actually judge. Draws nothing when the car reported none. */
internal fun drawKeyReadings(b: ReportBuilder, snapshot: VehicleSnapshot, pageTitle: String, title: String = "Key readings") {
    val rows = keyReadingRows(snapshot)
    if (rows.isEmpty()) return
    drawSectionTitle(
        b, title,
        "Taken with the engine running during the scan. \"Normal\" is the range for a warmed-up, healthy engine; engine speed, load and intake pressure change with how the car is being driven.",
        pageTitle
    )
    val colX = floatArrayOf(MARGIN, MARGIN + 175f, MARGIN + 265f)
    b.ensure(24f + 22f, pageTitle)
    b.y += drawTableHeader(b.canvas, b.y, colX, listOf("Reading", "Value", "Normal"))
    rows.forEach { row ->
        b.ensure(22f, pageTitle)
        b.canvas.drawText(row.name, colX[0] + 6f, b.y + 13f, textPaint(INK, 9f))
        b.canvas.drawText(row.value, colX[1] + 6f, b.y + 13f, textPaint(INK, 9f, bold = true))
        b.canvas.drawText(row.range, colX[2] + 6f, b.y + 13f, textPaint(GRAY, 8.5f))
        drawChip(b.canvas, CONTENT_RIGHT, b.y + 3f, row.chip, row.chipColor)
        b.canvas.drawLine(MARGIN, b.y + 20f, CONTENT_RIGHT, b.y + 20f, strokePaint(BORDER, 0.5f))
        b.y += 22f
    }
    b.y += 14f
}

/** Each emissions self-test the car supports, as a grid of ✓ / not-yet tiles, plus the PUC-readiness line. */
internal fun drawReadiness(b: ReportBuilder, readiness: ReadinessStatus?, pageTitle: String) {
    drawSectionTitle(
        b, "Self-tests (readiness monitors)",
        "The car's computer re-runs these checks while it's driven. All complete means nothing was reset recently and the car is ready for a PUC test.",
        pageTitle
    )
    if (readiness == null) {
        b.y += drawBanner(b.canvas, b.y, "This car didn't report its self-tests on this scan.", GRAY_LIGHT, BORDER, GRAY)
        b.y += 14f
        return
    }
    val supported = readiness.monitors.filter { it.supported }
    val ready = supported.count { it.ready }
    val summary = "Warning light ${if (readiness.milOn) "ON" else "off"} · $ready of ${supported.size} self-tests complete · " +
        if (readiness.readyForEmissionsTest) "ready for a PUC test" else "not ready for a PUC test yet"
    b.ensure(20f, pageTitle)
    b.canvas.drawText(summary, MARGIN, b.y + 11f, textPaint(if (readiness.milOn || !readiness.readyForEmissionsTest) WATCH else GOOD, 9.5f, bold = true))
    b.y += 20f

    val cols = 3
    val gap = 8f
    val w = (CONTENT_RIGHT - MARGIN - gap * (cols - 1)) / cols
    val h = 26f
    supported.chunked(cols).forEach { line ->
        b.ensure(h + gap, pageTitle)
        line.forEachIndexed { i, m ->
            val x = MARGIN + i * (w + gap)
            val color = if (m.ready) GOOD else WATCH
            b.canvas.drawRoundRect(RectF(x, b.y, x + w, b.y + h), 5f, 5f, fillPaint(tint(color, 22)))
            b.canvas.drawText(if (m.ready) "✓" else "…", x + 9f, b.y + 17f, textPaint(color, 10f, bold = true))
            b.canvas.drawText(ellipsize(m.name, w - 60f, textPaint(INK, 8.5f)), x + 24f, b.y + 17f, textPaint(INK, 8.5f))
            val state = textPaint(color, 7.5f, bold = true).apply { textAlign = Paint.Align.RIGHT }
            b.canvas.drawText(if (m.ready) "Complete" else "Not yet", x + w - 8f, b.y + 17f, state)
        }
        b.y += h + gap
    }
    b.y += 8f
}

/** Scales [bitmap] to fit inside [box] (no cropping — a car photo shouldn't lose its bumper), centred, clipped to the rounded tile. */
internal fun drawBitmapFit(canvas: Canvas, bitmap: Bitmap, box: RectF, radius: Float = 8f) {
    val scale = minOf(box.width() / bitmap.width, box.height() / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) })
    canvas.drawBitmap(bitmap, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
}

/**
 * Shows the whole car in [box]: scaled to fit (a [maxZoom] above 1 enlarges it, trimming the edges —
 * off by default, as even 15% cut off bumpers and wheels), and the gap on either side filled with
 * the photo's own edge colour — on a studio photo that's the grey wall, so the photo looks wider
 * instead of floating in white. Cropping to fill cut off half the car on a real phone photo.
 *
 * An upright photo of a car on the studio floor is mostly wall and floor, so the photo is first cut
 * down to the car (found by [carBounds]) plus a margin, shaped like the box — the car fills the
 * picture instead of sitting small between grey bars. Only for a cleaned studio photo ([trimToCar]):
 * on a raw photo the busy background can fool [carBounds] into trimming part of the car.
 */
internal fun drawBitmapSmart(canvas: Canvas, bitmap: Bitmap, box: RectF, radius: Float = 8f, maxZoom: Float = 1f, trimToCar: Boolean = true, carMargin: Float = 0.12f) {
    val src = if (trimToCar) carCrop(bitmap, box.width() / box.height(), carMargin) else Rect(0, 0, bitmap.width, bitmap.height)
    val fit = minOf(box.width() / src.width(), box.height() / src.height())
    val cover = maxOf(box.width() / src.width(), box.height() / src.height())
    val scale = minOf(cover, fit * maxZoom)
    val w = src.width() * scale
    val h = src.height() * scale
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) })
    // Left and right halves take the colour of the photo's left and right edges at mid-height.
    val mid = src.centerY()
    canvas.drawRect(box.left, box.top, box.centerX(), box.bottom, fillPaint(bitmap.getPixel(src.left + 2, mid)))
    canvas.drawRect(box.centerX(), box.top, box.right, box.bottom, fillPaint(bitmap.getPixel(src.right - 3, mid)))
    val left = box.centerX() - w / 2
    val top = box.centerY() - h / 2
    canvas.drawBitmap(bitmap, src, RectF(left, top, left + w, top + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
}

/**
 * The part of [bitmap] to show for a box of [aspect] (width / height): the car plus a margin,
 * ([margin] of the car's size on each side), widened or heightened to the box's shape while it stays inside the photo. The whole photo when no
 * car stands out (an uncleaned photo has detail everywhere, so it comes back whole).
 */
internal fun carCrop(bitmap: Bitmap, aspect: Float, margin: Float = 0.12f): Rect {
    val whole = Rect(0, 0, bitmap.width, bitmap.height)
    val car = carBounds(bitmap) ?: return whole
    var w = car.width() * (1 + 2 * margin)
    var h = car.height() * (1 + 2 * margin)
    if (w / h < aspect) w = h * aspect else h = w / aspect
    // Never more than the photo itself; if one side runs out, the box's edge fill covers the rest.
    w = w.coerceAtMost(bitmap.width.toFloat())
    h = h.coerceAtMost(bitmap.height.toFloat())
    val cx = car.exactCenterX().coerceIn(w / 2, bitmap.width - w / 2)
    val cy = car.exactCenterY().coerceIn(h / 2, bitmap.height - h / 2)
    val crop = Rect((cx - w / 2).toInt(), (cy - h / 2).toInt(), (cx + w / 2).toInt(), (cy + h / 2).toInt())
    crop.intersect(whole)
    // Only worth it when the car really was small in the frame.
    return if (crop.width() * crop.height() < whole.width() * whole.height() * 0.85f) crop else whole
}

/**
 * Where the car is in a studio photo. A pixel is car when it differs from the studio at both ends
 * of its row (the wall and floor run the same colour across the photo) and is also sharp detail,
 * dark, or coloured — that leaves out the soft ceiling light and the floor's shading. Then the
 * longest band of rows with enough car, and the longest band of columns within those rows; the
 * turntable's edge line is a separate band, so it's left out. Sharp detail alone missed the smooth
 * bonnet and roof of a black car and cut the car in half. Null when nothing stands out or
 * everything does. It may take in some floor under the car, which only shows a bit more floor.
 */
internal fun carBounds(bitmap: Bitmap): Rect? {
    val sw = 160
    val sh = (bitmap.height * sw / bitmap.width.toFloat()).toInt().coerceAtLeast(2)
    val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
    val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
    if (small !== bitmap) small.recycle()
    fun lum(c: Int) = (Color.red(c) * 3 + Color.green(c) * 6 + Color.blue(c)) / 10
    val car = BooleanArray(sw * sh)
    val ends = 6
    for (y in 0 until sh) {
        val row = y * sw
        fun avg(xs: IntRange, channel: (Int) -> Int) = xs.sumOf { channel(px[row + it]) } / xs.count()
        val left = intArrayOf(avg(0 until ends, Color::red), avg(0 until ends, Color::green), avg(0 until ends, Color::blue))
        val right = (sw - ends until sw).let { intArrayOf(avg(it, Color::red), avg(it, Color::green), avg(it, Color::blue)) }
        for (x in 0 until sw) {
            val c = px[row + x]
            val rgb = intArrayOf(Color.red(c), Color.green(c), Color.blue(c))
            fun diff(b: IntArray) = (0..2).maxOf { kotlin.math.abs(rgb[it] - b[it]) }
            if (minOf(diff(left), diff(right)) <= 28) continue
            val l = lum(c)
            val edge = x < sw - 1 && y < sh - 1 &&
                maxOf(kotlin.math.abs(l - lum(px[row + x + 1])), kotlin.math.abs(l - lum(px[row + sw + x]))) > 20
            car[row + x] = edge || l < 90 || rgb.max() - rgb.min() > 60
        }
    }
    val rows = IntArray(sh) { y -> (0 until sw).count { car[y * sw + it] } }
    val (top, bottom) = longestBand(rows, sw * 0.06f) ?: return null
    val cols = IntArray(sw) { x -> (top..bottom).count { car[it * sw + x] } }
    val (left, right) = longestBand(cols, (bottom - top) * 0.06f) ?: return null
    if (bottom - top < sh * 0.1f || right - left < sw * 0.15f) return null
    val f = bitmap.width / sw.toFloat()
    val r = Rect((left * f).toInt(), (top * f).toInt(), ((right + 1) * f).toInt().coerceAtMost(bitmap.width), ((bottom + 1) * f).toInt().coerceAtMost(bitmap.height))
    if (r.width() > bitmap.width * 0.97f && r.height() > bitmap.height * 0.97f) return null
    return r
}

/** First and last index of the longest stretch where [counts] is above [minCount], allowing gaps of up to 3. */
private fun longestBand(counts: IntArray, minCount: Float): Pair<Int, Int>? {
    val threshold = maxOf(minCount, 2f)
    var best: Pair<Int, Int>? = null
    var start = -1
    var last = -1
    counts.forEachIndexed { i, n ->
        if (n > threshold) {
            if (start < 0 || i - last > 3) start = i
            last = i
            if (best == null || last - start > best!!.second - best!!.first) best = start to last
        }
    }
    return best
}

/** Fills [box] with [bitmap], cropping the overflow — a little below centre, where the car usually sits — instead of shrinking it. */
internal fun drawBitmapCrop(canvas: Canvas, bitmap: Bitmap, box: RectF, radius: Float = 8f) {
    val scale = maxOf(box.width() / bitmap.width, box.height() / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    val left = box.centerX() - w / 2
    val top = (box.top - (h - box.height()) * 0.6f).coerceAtMost(box.top)
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) })
    canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
}

/** Tracks the current page/canvas/y-cursor across a variable-length report, starting a new page (re-drawing the header) whenever content would overflow. */
internal class ReportBuilder(
    private val doc: PdfDocument,
    private val dealerProfile: DealerProfile?,
    private val dealerLogo: Bitmap?
) {
    private var pageNum = 0
    lateinit var page: PdfDocument.Page
    lateinit var canvas: Canvas
    var y = 0f
    private var currentTitle = ""
    /** Draws a report's own header on every page after the first (returns the height used); null = the standard header. */
    var customHeader: ((Canvas, String) -> Float)? = null
    /** Left footer text on every page; null = the standard dealer line. */
    var footerLeft: String? = null
    /** Fills every page before anything is drawn; null = white. */
    var pageColor: Int? = null

    fun newPage(title: String, drawBrandHeader: Boolean = true) {
        if (::page.isInitialized) {
            drawFooter()
            doc.finishPage(page)
        }
        pageNum++
        currentTitle = title
        page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        canvas = page.canvas
        pageColor?.let { canvas.drawColor(it) }
        y = MARGIN
        if (drawBrandHeader && customHeader != null) {
            y = customHeader!!.invoke(canvas, title)
        } else if (drawBrandHeader) {
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
        val custom = footerLeft
        if (custom != null) {
            canvas.drawText(custom, MARGIN, PAGE_H - 24f, textPaint(Color.parseColor("#9BA1AC"), 7.5f))
        } else if (dealerProfile != null) {
            canvas.drawText(
                "Shared by ${dealerProfile.name} · Report data verified by GetAutoSure",
                MARGIN, PAGE_H - 24f, textPaint(Color.parseColor("#9BA1AC"), 7.5f)
            )
        }
    }
}
