package io.kopitiam.app.scan

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class CardEdgesTest {

    private val ratio = 85.6f / 54f
    private val w = 400
    private val h = 252

    /** Deterministic noise so tests never flake. */
    private class Lcg(var s: Long) {
        fun next(): Float {
            s = (s * 6364136223846793005L + 1442695040888963407L)
            return ((s ushr 33) % 1000).toFloat() / 1000f
        }
    }

    /** Corners of a card centred at (cx, cy), cw wide, rotated by [deg]. */
    private fun corners(cx: Float, cy: Float, cw: Float, deg: Double): FloatArray {
        val ch = cw / ratio
        val a = Math.toRadians(deg)
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        val local = floatArrayOf(-cw / 2, -ch / 2, cw / 2, -ch / 2, cw / 2, ch / 2, -cw / 2, ch / 2)
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val x = local[i * 2]; val y = local[i * 2 + 1]
            out[i * 2] = cx + x * c - y * s
            out[i * 2 + 1] = cy + x * s + y * c
        }
        return out
    }

    private fun inside(px: Float, py: Float, q: FloatArray): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val ax = q[i * 2]; val ay = q[i * 2 + 1]
            val bx = q[(i + 1) % 4 * 2]; val by = q[(i + 1) % 4 * 2 + 1]
            val cross = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
            val s = if (cross >= 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /**
     * Render a card photo: background, a filled card with a few dark "text"
     * lines inside it, plus noise. [stripes] adds a wood-grain-like pattern.
     */
    private fun photo(
        q: FloatArray, bg: Float, card: Float, noise: Float = 6f, stripes: Boolean = false, seed: Long = 1,
    ): FloatArray {
        val rnd = Lcg(seed)
        val img = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var v = if (inside(x + 0.5f, y + 0.5f, q)) card else bg
            if (stripes && !inside(x + 0.5f, y + 0.5f, q)) v += if ((y / 9) % 2 == 0) 8f else -8f
            img[y * w + x] = (v + (rnd.next() - 0.5f) * 2f * noise).coerceIn(0f, 255f)
        }
        // Text-like dark lines inside the card (must not be mistaken for its edges).
        val cx = (q[0] + q[2] + q[4] + q[6]) / 4f
        val cy = (q[1] + q[3] + q[5] + q[7]) / 4f
        for (line in 0 until 3) {
            val ty = (cy - 20 + line * 18).toInt()
            for (x in (cx - 60).toInt()..(cx + 40).toInt()) {
                for (t in 0 until 3) {
                    val yy = ty + t
                    if (x in 0 until w && yy in 0 until h && inside(x.toFloat(), yy.toFloat(), q)) {
                        img[yy * w + x] = card - 90f
                    }
                }
            }
        }
        return img
    }

    private fun assertCorners(expected: FloatArray, got: CardEdges.Quad?, tol: Float) {
        assertNotNull("card should be found", got)
        val g = got!!.toArray()
        for (i in 0 until 4) {
            val d = hypot(g[i * 2] - expected[i * 2], g[i * 2 + 1] - expected[i * 2 + 1])
            assertTrue("corner $i off by $d px (tolerance $tol)", d <= tol)
        }
    }

    @Test fun straightCard() {
        val q = corners(200f, 126f, 340f, 0.0)
        assertCorners(q, CardEdges.detect(photo(q, 70f, 210f), w, h, ratio), 3f)
    }

    @Test fun tiltedCardClockwise() {
        val q = corners(202f, 125f, 330f, 4.0)
        assertCorners(q, CardEdges.detect(photo(q, 60f, 200f, seed = 2), w, h, ratio), 4f)
    }

    @Test fun tiltedCardAnticlockwise() {
        val q = corners(198f, 128f, 320f, -6.0)
        assertCorners(q, CardEdges.detect(photo(q, 80f, 220f, seed = 3), w, h, ratio), 4f)
    }

    @Test fun darkCardOnLightTable() {
        val q = corners(200f, 126f, 336f, 2.0)
        assertCorners(q, CardEdges.detect(photo(q, 215f, 95f, seed = 4), w, h, ratio), 4f)
    }

    @Test fun stripedBackground() {
        val q = corners(200f, 126f, 334f, -3.0)
        assertCorners(q, CardEdges.detect(photo(q, 110f, 225f, stripes = true, seed = 5), w, h, ratio), 4f)
    }

    @Test fun lowContrastCardStillFound() {
        val q = corners(200f, 126f, 338f, 1.5)
        assertCorners(q, CardEdges.detect(photo(q, 150f, 185f, noise = 4f, seed = 6), w, h, ratio), 4f)
    }

    @Test fun blankPhotoGivesNoCard() {
        val q = corners(200f, 126f, 340f, 0.0)
        assertNull(CardEdges.detect(photo(q, 120f, 120f, seed = 7), w, h, ratio))
    }

    @Test fun tinyCardRejected() {
        // Card fills well under the minimum area, so the guide crop should be kept.
        val q = corners(200f, 126f, 120f, 0.0)
        assertNull(CardEdges.detect(photo(q, 70f, 210f, seed = 8), w, h, ratio))
    }

    @Test fun wrongSizeInputRejected() {
        assertNull(CardEdges.detect(FloatArray(10), w, h, ratio))
    }
}
