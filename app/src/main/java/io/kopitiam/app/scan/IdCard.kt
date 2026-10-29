package io.kopitiam.app.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import io.kopitiam.app.pdf.PdfEngine
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class IdPageLayout(val key: String, val title: String, val subtitle: String) {
    ONE_PAGE("onePage", "One page", "For photocopies"),
    SEPARATE_PAGES("separatePages", "Separate pages", "For uploads");

    companion object {
        fun from(key: String?): IdPageLayout = entries.firstOrNull { it.key == key } ?: ONE_PAGE
    }
}

/** Page sizes in PDF points (1/72 inch). */
enum class PaperSize(val key: String, val title: String, val widthPt: Int, val heightPt: Int) {
    A4("a4", "A4", 595, 842),
    LETTER("letter", "US Letter", 612, 792);

    companion object {
        fun from(key: String?): PaperSize = entries.firstOrNull { it.key == key } ?: regionDefault()

        /** Letter in the US and Canada (and a few others), A4 everywhere else. */
        fun regionDefault(): PaperSize =
            if (Locale.getDefault().country in setOf("US", "CA", "MX", "PH")) LETTER else A4
    }
}

object IdCardPdf {
    /** ISO/IEC 7810 ID-1 (ID cards, licences, bank cards): 85.6 x 54 mm. */
    const val CARD_W_PT = 85.6f / 25.4f * 72f
    const val CARD_H_PT = 54f / 25.4f * 72f
    const val RATIO = 85.6f / 54f
    const val TOP_MARGIN_PT = 72f
    const val GAP_PT = 36f

    /** True when an image has the ID-1 shape (either orientation), within 8%. */
    fun looksLikeCard(bmp: Bitmap): Boolean {
        if (bmp.width <= 0 || bmp.height <= 0) return false
        val ratio = max(bmp.width, bmp.height).toFloat() / min(bmp.width, bmp.height)
        return abs(ratio - RATIO) / RATIO < 0.08f
    }

    /** Cards print landscape; rotate portrait captures a quarter turn. */
    fun landscape(bmp: Bitmap): Bitmap {
        if (bmp.height <= bmp.width) return bmp
        val m = Matrix().apply { postRotate(-90f) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** Front (and back) at the card's true printed size, centred horizontally. */
    fun make(
        context: Context,
        images: List<Bitmap>,
        layout: IdPageLayout,
        paper: PaperSize,
        outputName: String = "ID-Card.pdf",
    ): File {
        if (images.isEmpty()) throw PdfEngine.EngineException("The operation produced no pages.")
        val cards = images.map(::landscape)
        val doc = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        fun draw(canvas: Canvas, bmp: Bitmap, top: Float) {
            val left = (paper.widthPt - CARD_W_PT) / 2f
            // Aspect-fit inside the card slot so a slightly off crop never stretches.
            val scale = min(CARD_W_PT / bmp.width, CARD_H_PT / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            val cx = left + CARD_W_PT / 2f
            val cy = top + CARD_H_PT / 2f
            canvas.drawBitmap(bmp, null, RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), paint)
        }

        when (layout) {
            IdPageLayout.ONE_PAGE -> {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(paper.widthPt, paper.heightPt, 0).create())
                page.canvas.drawColor(Color.WHITE)
                var y = TOP_MARGIN_PT
                for (card in cards) {
                    draw(page.canvas, card, y)
                    y += CARD_H_PT + GAP_PT
                }
                doc.finishPage(page)
            }
            IdPageLayout.SEPARATE_PAGES -> {
                cards.forEachIndexed { i, card ->
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(paper.widthPt, paper.heightPt, i).create())
                    page.canvas.drawColor(Color.WHITE)
                    draw(page.canvas, card, TOP_MARGIN_PT)
                    doc.finishPage(page)
                }
            }
        }
        val dest = PdfEngine.outFile(context, outputName)
        FileOutputStream(dest).use { doc.writeTo(it) }
        doc.close()
        return dest
    }

    /** Drawn placeholder cards for screenshots on an emulator with no camera. */
    fun sampleCards(): List<Bitmap> {
        fun card(top: Int, bottom: Int, back: Boolean): Bitmap {
            val w = 856; val h = 540
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val bg = Paint().apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
            val ink = Paint().apply { color = Color.argb(72, 26, 26, 26) }
            if (back) {
                for (i in 0 until 34) {
                    val bw = if (i % 3 == 0) 8f else 4f
                    c.drawRect(60f + i * 21f, 380f, 60f + i * 21f + bw, 470f, ink)
                }
                c.drawRect(60f, 80f, 580f, 102f, ink)
                c.drawRect(60f, 130f, 440f, 152f, ink)
            } else {
                c.drawRect(60f, 130f, 270f, 400f, Paint().apply { color = Color.argb(153, 255, 255, 255) })
                c.drawRect(320f, 150f, 740f, 176f, ink)
                c.drawRect(320f, 210f, 620f, 232f, ink)
                c.drawRect(320f, 260f, 660f, 282f, ink)
                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(153, 26, 26, 26); textSize = 34f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                c.drawText("SAMPLE IDENTITY CARD", 60f, 84f, label)
            }
            return bmp
        }
        return listOf(
            card(Color.rgb(207, 227, 220), Color.rgb(143, 188, 174), back = false),
            card(Color.rgb(232, 220, 200), Color.rgb(200, 176, 138), back = true),
        )
    }
}
