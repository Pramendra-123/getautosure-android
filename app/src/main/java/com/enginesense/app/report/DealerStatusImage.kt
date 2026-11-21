package com.enginesense.app.report

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.enginesense.app.dealerreport.DealerSaleSummary
import com.enginesense.app.dealerreport.SaleBadge
import com.enginesense.app.dealerreport.priceLabel
import com.enginesense.app.storage.DealerProfile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The WhatsApp Status picture a dealer posts for a car (sample: brand/reports/dealer-status-sample.png).
 * 1080×1920, black with white text: a slim dealer line, then the dealer's front photo filling most
 * of the picture with the "scanned & verified" date on it, then the car, only the RTO code of its number (e.g. KA02 — the rest stays private), four
 * badges from [DealerSaleSummary] and the dealer's phone. Status can't carry a PDF, so this is the
 * shop window and the PDF goes to whoever replies.
 */
object DealerStatusImage {

    const val WIDTH = 1080
    const val HEIGHT = 1920

    private val BG = Color.parseColor("#0A0B0D")
    private val TILE = Color.parseColor("#16181C")
    private val MUTED = Color.parseColor("#9BA1AC")
    private val GREEN = Color.parseColor("#34D399")
    private val AMBER = Color.parseColor("#FBBF24")
    private val BRAND_ORANGE = Color.parseColor("#FF6B2C")

    /**
     * The 1080×1920 Status picture (design: brand/reports/dealer-status-v5-preview.png). [badges] are
     * the 4 the dealer picked (health score first); the phone number appears once, in the call strip
     * — the caption doesn't repeat it and the address sits under the dealer's name.
     */
    fun render(
        summary: DealerSaleSummary,
        dealer: DealerProfile,
        dealerLogo: Bitmap?,
        frontPhoto: ReportPhoto?,
        scannedAtMillis: Long,
        priceInr: Double? = null,
        badges: List<SaleBadge> = summary.badges
    ): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)
        val left = 70f
        val right = WIDTH - 70f

        // Layout: a slim dealer line, then the car filling ~65% of the picture (the photo sells the
        // car), and the details packed into the bottom quarter.
        val photoTop = 160f
        val photoBottom = 1380f

        // Dealer and showroom, on one slim line
        drawDealerBadge(c, left, 38f, 92f, dealerLogo, dealer.name)
        val textLeft = left + 112f
        c.drawText(ellipsize(dealer.name, right - textLeft, paint(Color.WHITE, 46f, bold = true)), textLeft, 86f, paint(Color.WHITE, 46f, bold = true))
        if (dealer.location.isNotBlank()) {
            c.drawText(ellipsize(dealer.location.trim(), right - textLeft, paint(MUTED, 26f)), textLeft, 124f, paint(MUTED, 26f))
        }

        // The car, full width; the whole car always shows (fit, never cropped).
        val photoBox = RectF(24f, photoTop, WIDTH - 24f, photoBottom)
        c.drawRoundRect(photoBox, 40f, 40f, fill(Color.parseColor("#D9DBDE")))
        if (frontPhoto != null) {
            drawBitmapSmart(c, frontPhoto.bitmap, photoBox, radius = 40f, trimToCar = frontPhoto.backgroundCleaned, carMargin = 0.12f)
            if (frontPhoto.backgroundCleaned) {
                val label = "Background cleaned · car not edited"
                val labelPaint = paint(Color.WHITE, 22f)
                val w = labelPaint.measureText(label) + 24f
                c.drawRoundRect(RectF(photoBox.right - 24f - w, photoBox.top + 28f, photoBox.right - 24f, photoBox.top + 64f), 18f, 18f, fill(Color.argb(150, 0, 0, 0)))
                c.drawText(label, photoBox.right - 12f - w, photoBox.top + 54f, labelPaint)
            }
        }

        // Scanned & verified — a pill on the photo's top-left corner
        val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(scannedAtMillis))
        val pillText = "✓ Scanned & verified · $date"
        val pillPaint = paint(GREEN, 30f, bold = true)
        val pillW = pillPaint.measureText(pillText) + 52f
        val pill = RectF(photoBox.left + 24f, photoBox.top + 24f, photoBox.left + 24f + pillW, photoBox.top + 86f)
        c.drawRoundRect(pill, 31f, 31f, fill(Color.argb(215, 10, 11, 13)))
        c.drawText(pillText, pill.left + 26f, pill.centerY() + 11f, pillPaint)

        // The dealer's price, only when they added one — an orange pill inside the photo's bottom-right corner
        priceInr?.let { price ->
            val pricePaint = paint(BG, 48f, bold = true).apply { textAlign = Paint.Align.CENTER }
            val text = priceLabel(price)
            val w = pricePaint.measureText(text) + 80f
            val r = RectF(photoBox.right - 24f - w, photoBox.bottom - 108f, photoBox.right - 24f, photoBox.bottom - 24f)
            c.drawRoundRect(r, 42f, 42f, fill(BRAND_ORANGE))
            c.drawText(text, r.centerX(), r.centerY() + 17f, pricePaint)
        }

        // "2020 Kia Seltos GTX PLUS" — year and car in bold, the variant lighter — then grey chips:
        // km, owner, gearbox, fuel, RTO code (the listing pattern buyers know from car portals).
        val titleY = photoBottom + 72f
        val titleBold = listOfNotNull(summary.year?.toString(), summary.carName).joinToString(" ")
        val boldPaint = paint(Color.WHITE, 56f, bold = true)
        val variantPaint = paint(Color.parseColor("#D1D5DB"), 56f)
        val boldText = ellipsize(titleBold, right - left, boldPaint)
        c.drawText(boldText, left, titleY, boldPaint)
        summary.variant?.let { v ->
            val x = left + boldPaint.measureText(boldText) + 16f
            if (right - x > 80f) c.drawText(ellipsize(v, right - x, variantPaint), x, titleY, variantPaint)
        }
        val chips = listOfNotNull(
            summary.km?.let { "%,d km".format(it.toLong()) },
            summary.ownerLabel,
            summary.transmission,
            summary.fuel,
            summary.rtoCode?.let { if (it.length > 2 && it[1].isLetter() && it[2].isDigit()) it.take(2) + "-" + it.drop(2) else it }
        )
        val chipPaint = paint(Color.parseColor("#D1D5DB"), 28f)
        var cx = left
        val chipTop = titleY + 26f
        chips.forEach { chip ->
            val w = chipPaint.measureText(chip) + 32f
            if (cx + w <= right) {
                c.drawRoundRect(RectF(cx, chipTop, cx + w, chipTop + 48f), 12f, 12f, fill(TILE))
                c.drawText(chip, cx + 16f, chipTop + 34f, chipPaint)
                cx += w + 12f
            }
        }

        // The 4 badges the dealer picked, two by two, compact
        val gap = 14f
        val tileW = (right - left - gap) / 2
        val tileH = 92f
        val badgesTop = chipTop + 72f
        badges.take(4).forEachIndexed { i, badge ->
            val tx = left + (i % 2) * (tileW + gap)
            val ty = badgesTop + (i / 2) * (tileH + gap)
            drawBadge(c, RectF(tx, ty, tx + tileW, ty + tileH), badge, isScore = i == 0 && summary.healthScore != null, score = summary.healthScore)
        }

        // Call strip — the only place the number appears
        val box = RectF(left, badgesTop + 2 * tileH + gap + 20f, right, badgesTop + 2 * tileH + gap + 112f)
        c.drawRoundRect(box, 30f, 30f, fill(BRAND_ORANGE))
        dealer.phones.firstOrNull()?.let { c.drawText("Call $it", box.left + 36f, box.centerY() + 17f, paint(BG, 48f, bold = true)) }
        val ask = paint(BG, 26f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("Ask for the", box.right - 36f, box.centerY() - 5f, ask)
        c.drawText("full report", box.right - 36f, box.centerY() + 27f, ask)

        // Report by GetAutoSure
        val by = paint(Color.parseColor("#6B7280"), 22f)
        val prefix = "Report by Get"
        val auto = "Auto"
        val sure = "Sure"
        val autoPaint = paint(BRAND_ORANGE, 22f, bold = true)
        val total = by.measureText(prefix) + autoPaint.measureText(auto) + by.measureText(sure)
        var bx = (WIDTH - total) / 2
        val footerY = box.bottom + 36f
        c.drawText(prefix, bx, footerY, by); bx += by.measureText(prefix)
        c.drawText(auto, bx, footerY, autoPaint); bx += autoPaint.measureText(auto)
        c.drawText(sure, bx, footerY, by)
        return bmp
    }

    /** One badge tile; everything is sized from the tile's height (designed at 128px, drawn smaller now the photo is bigger). */
    private fun drawBadge(c: Canvas, r: RectF, badge: SaleBadge, isScore: Boolean, score: Int?) {
        val s = r.height() / 128f
        c.drawRoundRect(r, 28f * s, 28f * s, fill(TILE))
        val color = if (badge.good) GREEN else AMBER
        val cx = r.left + 64f * s
        val cy = r.centerY()
        if (isScore && score != null) {
            c.drawCircle(cx, cy, 34f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f * s; this.color = color })
            c.drawText("$score", cx, cy + 11f * s, paint(Color.WHITE, 30f * s, bold = true).apply { textAlign = Paint.Align.CENTER })
        } else {
            c.drawCircle(cx, cy, 38f * s, fill(tint(color, 40)))
            c.drawText(if (badge.good) "✓" else "!", cx, cy + 14f * s, paint(color, 40f * s, bold = true).apply { textAlign = Paint.Align.CENTER })
        }
        val textX = r.left + 124f * s
        val maxW = r.right - textX - 18f
        // Long titles shrink (to 80%) before they are cut short
        var size = 40f * s
        while (size > 32f * s && paint(Color.WHITE, size, bold = true).measureText(badge.title) > maxW) size -= 1f
        val titlePaint = paint(Color.WHITE, size, bold = true)
        c.drawText(ellipsize(badge.title, maxW, titlePaint), textX, cy - 4f * s, titlePaint)
        c.drawText(ellipsize(badge.detail, maxW, paint(MUTED, 27f * s)), textX, cy + 36f * s, paint(MUTED, 27f * s))
    }

    /** Saves [bitmap] next to the reports (so the FileProvider can share it) and returns the file. */
    fun save(context: Context, bitmap: Bitmap, name: String): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /**
     * Opens WhatsApp's share screen with the picture and [caption] (the dealer then taps "My status").
     * Android can't post to Status directly. Falls back to the normal share sheet when WhatsApp
     * (or WhatsApp Business) isn't installed.
     */
    fun shareToWhatsApp(context: Context, file: File, caption: String) {
        val uri = reportContentUri(context, file)
        fun intent(pkg: String?) = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (pkg != null) setPackage(pkg)
        }
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                context.startActivity(intent(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // try the next one
            }
        }
        context.startActivity(Intent.createChooser(intent(null), "Share to Status"))
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false) = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
}
