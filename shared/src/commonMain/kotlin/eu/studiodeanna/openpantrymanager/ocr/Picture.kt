package eu.studiodeanna.openpantrymanager.ocr

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A photo as packed ARGB pixels, row by row. Text is read from these. */
class Picture(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "A picture of ${width}x$height needs ${width * height} pixels" }
    }

    /** This picture at [newWidth] × [newHeight]: averaged when shrunk, so fine print is not lost, interpolated when grown. */
    fun resized(newWidth: Int, newHeight: Int): Picture {
        if (newWidth == width && newHeight == height) return this
        // Average whole blocks of pixels first, then interpolate the rest of the way.
        val blockX = max(1, width / newWidth)
        val blockY = max(1, height / newHeight)
        val reduced = if (blockX > 1 || blockY > 1) averaged(blockX, blockY) else this
        return Picture(newWidth, newHeight, IntArray(newWidth * newHeight) { i ->
            val x = (i % newWidth + 0.5f) * reduced.width / newWidth - 0.5f
            val y = (i / newWidth + 0.5f) * reduced.height / newHeight - 0.5f
            reduced.sample(x, y)
        })
    }

    /**
     * The part of this picture inside [corners] (top-left, top-right, bottom-right, bottom-left), made
     * straight and [outWidth] × [outHeight] pixels: a tilted line of text, laid flat. Mixed from the
     * sixteen pixels around each point, which keeps small print sharper than four.
     */
    fun cropped(corners: List<Point>, outWidth: Int, outHeight: Int): Picture {
        val (a, b, c, d) = corners
        return Picture(outWidth, outHeight, IntArray(outWidth * outHeight) { i ->
            val s = (i % outWidth).toFloat() / outWidth
            val t = (i / outWidth).toFloat() / outHeight
            val x = (1 - s) * (1 - t) * a.x + s * (1 - t) * b.x + s * t * c.x + (1 - s) * t * d.x
            val y = (1 - s) * (1 - t) * a.y + s * (1 - t) * b.y + s * t * c.y + (1 - s) * t * d.y
            sampleCubic(x, y)
        })
    }

    /** This picture turned a quarter left, as text written top to bottom is read. */
    fun turnedLeft(): Picture = Picture(height, width, IntArray(width * height) { i ->
        val x = i % height
        val y = i / height
        pixels[x * width + (width - 1 - y)]
    })

    /** The colour at a point between pixels, mixed from the four around it; outside, the nearest edge's. */
    internal fun sample(x: Float, y: Float): Int {
        val cx = x.coerceIn(0f, (width - 1).toFloat())
        val cy = y.coerceIn(0f, (height - 1).toFloat())
        val x0 = floor(cx).toInt()
        val y0 = floor(cy).toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val fx = cx - x0
        val fy = cy - y0
        val p00 = pixels[y0 * width + x0]
        val p10 = pixels[y0 * width + x1]
        val p01 = pixels[y1 * width + x0]
        val p11 = pixels[y1 * width + x1]
        var argb = 0xFF shl 24
        for (shift in intArrayOf(16, 8, 0)) {
            val top = (p00 shr shift and 0xFF) * (1 - fx) + (p10 shr shift and 0xFF) * fx
            val bottom = (p01 shr shift and 0xFF) * (1 - fx) + (p11 shr shift and 0xFF) * fx
            argb = argb or ((top * (1 - fy) + bottom * fy + 0.5f).toInt().coerceIn(0, 255) shl shift)
        }
        return argb
    }

    /** The colour at a point between pixels, by bicubic interpolation of the 4 × 4 around it; outside, the nearest edge's. */
    private fun sampleCubic(x: Float, y: Float): Int {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val wx = cubicWeights(x - x0)
        val wy = cubicWeights(y - y0)
        var argb = 0xFF shl 24
        for (shift in intArrayOf(16, 8, 0)) {
            var value = 0f
            for (j in 0 until 4) {
                val row = (y0 - 1 + j).coerceIn(0, height - 1) * width
                var line = 0f
                for (k in 0 until 4) {
                    line += (pixels[row + (x0 - 1 + k).coerceIn(0, width - 1)] shr shift and 0xFF) * wx[k]
                }
                value += line * wy[j]
            }
            argb = argb or ((value + 0.5f).toInt().coerceIn(0, 255) shl shift)
        }
        return argb
    }

    private fun averaged(blockX: Int, blockY: Int): Picture {
        val w = width / blockX
        val h = height / blockY
        val n = blockX * blockY
        return Picture(w, h, IntArray(w * h) { i ->
            val ox = (i % w) * blockX
            val oy = (i / w) * blockY
            var r = 0
            var g = 0
            var b = 0
            for (y in oy until oy + blockY) {
                for (x in ox until ox + blockX) {
                    val p = pixels[y * width + x]
                    r += p shr 16 and 0xFF
                    g += p shr 8 and 0xFF
                    b += p and 0xFF
                }
            }
            (0xFF shl 24) or (r / n shl 16) or (g / n shl 8) or (b / n)
        })
    }
}

/** The weights of the four pixels around a point [f] of the way between the middle two, as OpenCV mixes them (a = -0.75). */
private fun cubicWeights(f: Float): FloatArray {
    val a = -0.75f
    fun near(t: Float) = ((a + 2) * t - (a + 3)) * t * t + 1
    fun far(t: Float) = ((a * t - 5 * a) * t + 8 * a) * t - 4 * a
    return floatArrayOf(far(1 + f), near(f), near(1 - f), far(2 - f))
}
