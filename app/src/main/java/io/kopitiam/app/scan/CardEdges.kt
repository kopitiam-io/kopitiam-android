package io.kopitiam.app.scan

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Finds the four edges of an ID card inside a photo that has already been
 * cropped to the on-screen card guide (plus a little slack).
 *
 * Plain Kotlin on a brightness grid, so it runs anywhere (including JVM unit
 * tests) and needs no OpenCV. Approach: blur lightly, take edge strength, then
 * for each side search every straight line in a band near that side (tilted up
 * to [MAX_TILT_DEG]) and keep the one with the strongest average edge along its
 * middle 80% (the middle part skips the card's rounded corners). The four lines
 * are intersected into corners and sanity-checked; anything doubtful returns
 * null so the caller keeps the plain guide crop.
 */
object CardEdges {

    /** Corners in the input grid's pixel coordinates. */
    data class Quad(
        val tlX: Float, val tlY: Float,
        val trX: Float, val trY: Float,
        val brX: Float, val brY: Float,
        val blX: Float, val blY: Float,
    ) {
        fun scaled(s: Float) = Quad(tlX * s, tlY * s, trX * s, trY * s, brX * s, brY * s, blX * s, blY * s)
        fun toArray() = floatArrayOf(tlX, tlY, trX, trY, brX, brY, blX, blY)
    }

    private const val MAX_TILT_DEG = 10.0
    private const val BAND = 0.30f          // search this fraction of the image from each side
    private const val MIN_CONTRAST = 2.5f   // best line must beat the band's average edge by this much
    private const val MIN_EDGE = 6f         // and have at least this much edge (0-255 scale)
    private const val MIN_AREA = 0.40f      // card must fill at least this much of the crop
    private const val RATIO_TOLERANCE = 0.25f

    /** A straight line through (a0 at the start) and (a1 at the end) of the side. */
    private data class Line(val start: Float, val end: Float, val score: Float)

    /**
     * [gray] is row-major brightness 0..255, [w] x [h]. Returns null when the
     * card's edges can't be found with confidence.
     */
    fun detect(gray: FloatArray, w: Int, h: Int, cardRatio: Float = IdCardPdf.RATIO): Quad? {
        if (w < 32 || h < 32 || gray.size != w * h) return null
        val blurred = boxBlur(gray, w, h)
        // Horizontal edges (top/bottom) respond to vertical change, and vice versa.
        val gy = FloatArray(w * h)
        val gx = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                gy[i] = abs(blurred[i + w] - blurred[i - w])
                gx[i] = abs(blurred[i + 1] - blurred[i - 1])
            }
        }

        val bandH = (h * BAND).toInt()
        val bandW = (w * BAND).toInt()
        val top = bestHorizontal(gy, w, h, 1, bandH) ?: return null
        val bottom = bestHorizontal(gy, w, h, h - 1 - bandH, h - 2) ?: return null
        val left = bestVertical(gx, w, h, 1, bandW) ?: return null
        val right = bestVertical(gx, w, h, w - 1 - bandW, w - 2) ?: return null

        // Horizontal lines: y = start + (end - start) * x / (w - 1)
        // Vertical lines:   x = start + (end - start) * y / (h - 1)
        val tl = intersect(top, left, w, h) ?: return null
        val tr = intersect(top, right, w, h) ?: return null
        val br = intersect(bottom, right, w, h) ?: return null
        val bl = intersect(bottom, left, w, h) ?: return null
        val quad = Quad(tl.first, tl.second, tr.first, tr.second, br.first, br.second, bl.first, bl.second)
        return if (plausible(quad, w, h, cardRatio)) quad else null
    }

    private fun bestHorizontal(g: FloatArray, w: Int, h: Int, from: Int, to: Int): Line? {
        val maxDy = (tan(Math.toRadians(MAX_TILT_DEG)) * w).roundToInt()
        val x0 = (w * 0.1f).toInt()
        val x1 = (w * 0.9f).toInt()
        var best: Line? = null
        var bandSum = 0f
        var bandCount = 0
        for (ys in from..to) {
            for (dy in -maxDy..maxDy) {
                val ye = ys + dy
                if (ye < from || ye > to) continue
                var sum = 0f
                var n = 0
                var x = x0
                while (x < x1) {
                    val y = (ys + (ye - ys).toFloat() * x / (w - 1)).roundToInt()
                    if (y in 0 until h) { sum += g[y * w + x]; n++ }
                    x += 2
                }
                if (n == 0) continue
                val score = sum / n
                bandSum += score; bandCount++
                if (best == null || score > best.score) best = Line(ys.toFloat(), ye.toFloat(), score)
            }
        }
        return confident(best, bandSum, bandCount)
    }

    private fun bestVertical(g: FloatArray, w: Int, h: Int, from: Int, to: Int): Line? {
        val maxDx = (tan(Math.toRadians(MAX_TILT_DEG)) * h).roundToInt()
        val y0 = (h * 0.1f).toInt()
        val y1 = (h * 0.9f).toInt()
        var best: Line? = null
        var bandSum = 0f
        var bandCount = 0
        for (xs in from..to) {
            for (dx in -maxDx..maxDx) {
                val xe = xs + dx
                if (xe < from || xe > to) continue
                var sum = 0f
                var n = 0
                var y = y0
                while (y < y1) {
                    val x = (xs + (xe - xs).toFloat() * y / (h - 1)).roundToInt()
                    if (x in 0 until w) { sum += g[y * w + x]; n++ }
                    y += 2
                }
                if (n == 0) continue
                val score = sum / n
                bandSum += score; bandCount++
                if (best == null || score > best.score) best = Line(xs.toFloat(), xe.toFloat(), score)
            }
        }
        return confident(best, bandSum, bandCount)
    }

    private fun confident(best: Line?, bandSum: Float, bandCount: Int): Line? {
        if (best == null || bandCount == 0) return null
        val mean = bandSum / bandCount
        return if (best.score >= MIN_EDGE && best.score >= mean * MIN_CONTRAST) best else null
    }

    /** Intersect a horizontal-ish line with a vertical-ish line. */
    private fun intersect(hz: Line, vt: Line, w: Int, h: Int): Pair<Float, Float>? {
        // y = a + b x   (horizontal);   x = c + d y   (vertical)
        val b = (hz.end - hz.start) / (w - 1)
        val a = hz.start
        val d = (vt.end - vt.start) / (h - 1)
        val c = vt.start
        val denom = 1f - b * d
        if (abs(denom) < 1e-4f) return null
        val y = (a + b * c) / denom
        val x = c + d * y
        return x to y
    }

    private fun plausible(q: Quad, w: Int, h: Int, ratio: Float): Boolean {
        val pts = q.toArray()
        // Corners inside (or just outside) the crop.
        for (i in pts.indices step 2) {
            if (pts[i] < -0.05f * w || pts[i] > 1.05f * w) return false
            if (pts[i + 1] < -0.05f * h || pts[i + 1] > 1.05f * h) return false
        }
        // Convex, consistently wound.
        var sign = 0
        for (i in 0 until 4) {
            val ax = pts[i * 2]; val ay = pts[i * 2 + 1]
            val bx = pts[(i + 1) % 4 * 2]; val by = pts[(i + 1) % 4 * 2 + 1]
            val cx = pts[(i + 2) % 4 * 2]; val cy = pts[(i + 2) % 4 * 2 + 1]
            val cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s == 0 || (sign != 0 && s != sign)) return false
            sign = s
        }
        // Big enough.
        var area = 0f
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            area += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1]
        }
        if (abs(area) / 2f < MIN_AREA * w * h) return false
        // Card-shaped.
        val width = (dist(q.tlX, q.tlY, q.trX, q.trY) + dist(q.blX, q.blY, q.brX, q.brY)) / 2f
        val height = (dist(q.tlX, q.tlY, q.blX, q.blY) + dist(q.trX, q.trY, q.brX, q.brY)) / 2f
        if (height <= 0f) return false
        return abs(width / height - ratio) / ratio <= RATIO_TOLERANCE
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** 3x3 box blur, edges clamped. */
    private fun boxBlur(src: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (dy in -1..1) {
                    val yy = min(h - 1, max(0, y + dy))
                    for (dx in -1..1) {
                        val xx = min(w - 1, max(0, x + dx))
                        sum += src[yy * w + xx]
                    }
                }
                out[y * w + x] = sum / 9f
            }
        }
        return out
    }
}
