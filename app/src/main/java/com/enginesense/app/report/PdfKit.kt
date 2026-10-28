package com.enginesense.app.report

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
import com.enginesense.app.storage.DealerProfile
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

internal fun drawField(canvas: Canvas, x: Float, y: Float, label: String, value: String) {
    canvas.drawText(label, x, y + 8f, textPaint(GRAY, 7.5f))
    canvas.drawText(value, x, y + 20f, textPaint(INK, 9.5f, bold = true))
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
