package eu.studiodeanna.openpantrymanager.ocr

import eu.studiodeanna.openpantrymanager.receipt.OcrLine
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Numbers in the shape of a tensor, row-major: an image going into a network, or what comes out. */
class Tensor(val shape: IntArray, val data: FloatArray)

/** A neural network, run by the platform: a tensor in, a tensor out. */
fun interface Network {
    fun run(input: Tensor): Tensor
}

/**
 * Reads the text on a photo, on the device, with PaddleOCR's networks (PP-OCRv6): the [detector] finds
 * where the lines of text are, and the [recognizer] reads each one, writing the [characters] it knows.
 * The steps around the networks are PaddleOCR's, as RapidOCR runs them, written again in Kotlin so
 * that every platform shares them.
 */
class TextReader(
    private val detector: Network,
    private val recognizer: Network,
    characters: List<String>,
) {
    /** What each output of the recognizer stands for: first the gap between letters, last a space. */
    private val alphabet = listOf("") + characters + " "

    /** The lines of text on [picture], with their boxes in its pixels. */
    fun read(picture: Picture): List<OcrLine> {
        val image = withinBounds(picture)
        val boxes = detect(image)
        val texts = recognize(boxes.map { crop(image, it) })
        val scaleX = picture.width.toFloat() / image.width
        val scaleY = picture.height.toFloat() / image.height
        return boxes.zip(texts).mapNotNull { (box, text) ->
            if (text == null) return@mapNotNull null
            val (topLeft, topRight) = box
            OcrLine(
                text,
                box.minOf { it.x } * scaleX,
                box.minOf { it.y } * scaleY,
                box.maxOf { it.x } * scaleX,
                box.maxOf { it.y } * scaleY,
                atan2((topRight.y - topLeft.y) * scaleY, (topRight.x - topLeft.x) * scaleX) * 180 / PI.toFloat(),
            )
        }
    }

    /** A large photo made smaller, its sides multiples of 32 as the detector needs. */
    private fun withinBounds(picture: Picture): Picture {
        val longest = max(picture.width, picture.height)
        if (longest <= MAX_SIDE) return picture
        val ratio = MAX_SIDE.toFloat() / longest
        return picture.resized(multipleOf32(picture.width * ratio), multipleOf32(picture.height * ratio))
    }

    /** The corners of each line of text found on [image]: top-left, top-right, bottom-right, bottom-left. */
    private fun detect(image: Picture): List<List<Point>> {
        // Small photos are enlarged for the detector, which finds small print badly; large ones made
        // smaller, as it takes the longest on a phone and finds lines as well. The lines themselves are
        // read on the photo as it was.
        val shortest = min(image.width, image.height)
        val longest = max(image.width, image.height)
        val ratio = when {
            shortest < DETECT_MIN_SIDE -> DETECT_MIN_SIDE.toFloat() / shortest
            longest > DETECT_MAX_SIDE -> DETECT_MAX_SIDE.toFloat() / longest
            else -> 1f
        }
        val input = image.resized(multipleOf32(image.width * ratio), multipleOf32(image.height * ratio))
        val output = detector.run(Tensor(intArrayOf(1, 3, input.height, input.width), input.channels(::toUnit)))
        val mapHeight = output.shape[2]
        val mapWidth = output.shape[3]
        val probability = output.data
        val mask = dilated(BooleanArray(mapWidth * mapHeight) { probability[it] > PIXEL_THRESHOLD }, mapWidth, mapHeight)
        val boxes = mutableListOf<List<Point>>()
        for (region in regions(mask, mapWidth, mapHeight).take(MAX_CANDIDATES)) {
            val rect = smallestRect(convexHull(region))
            if (rect.shortSide < MIN_SIZE) continue
            if (meanInside(probability, mapWidth, mapHeight, rect.corners()) < BOX_THRESHOLD) continue
            // The detector marks only the middle of the letters; the box is grown to take them whole.
            val grown = rect.grown(rect.width * rect.height * UNCLIP_RATIO / (2 * (rect.width + rect.height)))
            if (grown.shortSide < MIN_SIZE + 2) continue
            val corners = clockwise(grown.corners().map { p ->
                Point(
                    (p.x / mapWidth * image.width).roundToInt().coerceIn(0, image.width - 1).toFloat(),
                    (p.y / mapHeight * image.height).roundToInt().coerceIn(0, image.height - 1).toFloat(),
                )
            })
            if (distance(corners[0], corners[1]).toInt() <= 3 || distance(corners[0], corners[3]).toInt() <= 3) continue
            boxes += corners
        }
        return boxes
    }

    /** The line of text inside [corners], laid flat. */
    private fun crop(image: Picture, corners: List<Point>): Picture {
        val (a, b, c, d) = corners
        val width = max(distance(a, b), distance(c, d)).toInt()
        val height = max(distance(a, d), distance(b, c)).toInt()
        val flat = image.cropped(corners, width, height)
        return if (height >= 1.5f * width) flat.turnedLeft() else flat
    }

    /** The text of each line, or null where the recognizer was unsure; read a few lines at a time. */
    private fun recognize(lines: List<Picture>): List<String?> {
        val texts = arrayOfNulls<String>(lines.size)
        // Lines of like widths together, so little of each batch is padding.
        val order = lines.indices.sortedBy { lines[it].width.toFloat() / lines[it].height }
        var start = 0
        while (start < order.size) {
            var end = start + 1
            while (end < order.size && end - start < BATCH && (end - start + 1) * batchWidth(lines, order, start, end + 1) <= MAX_BATCH_WIDTH) end++
            val batch = order.subList(start, end)
            val width = batchWidth(lines, order, start, end)
            val data = FloatArray(batch.size * 3 * LINE_HEIGHT * width)
            batch.forEachIndexed { n, index ->
                val line = lines[index]
                val scaledWidth = min(ceil(LINE_HEIGHT * line.width.toFloat() / line.height).toInt(), width)
                line.resized(scaledWidth, LINE_HEIGHT).channels(::toUnit).let { pixels ->
                    // Each line at the left of its slot; the rest stays 0, the middle grey.
                    for (channel in 0 until 3) {
                        for (y in 0 until LINE_HEIGHT) {
                            pixels.copyInto(
                                data,
                                ((n * 3 + channel) * LINE_HEIGHT + y) * width,
                                (channel * LINE_HEIGHT + y) * scaledWidth,
                                (channel * LINE_HEIGHT + y + 1) * scaledWidth,
                            )
                        }
                    }
                }
            }
            val output = recognizer.run(Tensor(intArrayOf(batch.size, 3, LINE_HEIGHT, width), data))
            batch.forEachIndexed { n, index -> texts[index] = decode(output, n) }
            start = end
        }
        return texts.asList()
    }

    /** The widest line from [from] until [until] in [order], at the recognizer's height; never narrower than it was trained on. */
    private fun batchWidth(lines: List<Picture>, order: List<Int>, from: Int, until: Int): Int {
        val widest = (from until until).maxOf { lines[order[it]].width.toFloat() / lines[order[it]].height }
        return (LINE_HEIGHT * max(widest, MIN_LINE_RATIO)).toInt()
    }

    /**
     * The text the recognizer read for line [n]: its likeliest character at each step, repeats and gaps
     * between letters dropped. Null if, on average, it was less than sure of those characters.
     */
    private fun decode(output: Tensor, n: Int): String? {
        val steps = output.shape[1]
        val classes = output.shape[2]
        check(classes == alphabet.size) { "The recognizer writes $classes characters, but ${alphabet.size} were given" }
        val text = StringBuilder()
        var sure = 0f
        var kept = 0
        var previous = -1
        for (t in 0 until steps) {
            val offset = (n * steps + t) * classes
            var best = 0
            var bestValue = output.data[offset]
            for (c in 1 until classes) {
                val v = output.data[offset + c]
                if (v > bestValue) {
                    bestValue = v
                    best = c
                }
            }
            if (best != 0 && best != previous) {
                text.append(alphabet[best])
                sure += bestValue
                kept++
            }
            previous = best
        }
        val result = text.toString()
        return result.takeIf { kept > 0 && sure / kept >= TEXT_THRESHOLD && it.isNotBlank() }
    }

    private companion object {
        const val MAX_SIDE = 2000
        const val DETECT_MIN_SIDE = 736
        const val DETECT_MAX_SIDE = 1280
        const val PIXEL_THRESHOLD = 0.3f
        const val BOX_THRESHOLD = 0.5f
        const val UNCLIP_RATIO = 1.6f
        const val MIN_SIZE = 3
        const val MAX_CANDIDATES = 1000
        const val LINE_HEIGHT = 48
        const val MIN_LINE_RATIO = 160f / 48
        const val BATCH = 6
        /** Lines × width per batch, which bounds what the recognizer returns at once (its steps × 18 710 characters). */
        const val MAX_BATCH_WIDTH = 2400
        const val TEXT_THRESHOLD = 0.5f

        /** A colour channel of 0–255 as the networks take it, from -1 to 1. */
        fun toUnit(value: Int) = value / 127.5f - 1f

        fun multipleOf32(size: Float) = max(32, (size / 32).roundToInt() * 32)
    }
}

/** The picture's channels one after another, blue, green, red, as PaddleOCR's networks were trained, each through [convert]. */
internal fun Picture.channels(convert: (Int) -> Float): FloatArray {
    val plane = width * height
    val out = FloatArray(3 * plane)
    for (i in 0 until plane) {
        val p = pixels[i]
        out[i] = convert(p and 0xFF)
        out[plane + i] = convert(p shr 8 and 0xFF)
        out[2 * plane + i] = convert(p shr 16 and 0xFF)
    }
    return out
}

/** The [mask] with each marked pixel also marking the ones right and below it, as PaddleOCR widens what its detector marks. */
private fun dilated(mask: BooleanArray, width: Int, height: Int): BooleanArray = BooleanArray(mask.size) { i ->
    val x = i % width
    val y = i / width
    mask[i] || (x > 0 && mask[i - 1]) || (y > 0 && mask[i - width]) || (x > 0 && y > 0 && mask[i - width - 1])
}

/**
 * The separate patches of the [mask], each given by the outermost pixels of its rows, which is all a
 * convex hull needs. Pixels touching at a corner are one patch.
 */
private fun regions(mask: BooleanArray, width: Int, height: Int): List<List<Point>> {
    val seen = BooleanArray(mask.size)
    val stack = IntArrayList()
    val regions = mutableListOf<List<Point>>()
    for (start in mask.indices) {
        if (!mask[start] || seen[start]) continue
        seen[start] = true
        stack.add(start)
        val rowMin = HashMap<Int, Int>()
        val rowMax = HashMap<Int, Int>()
        while (stack.size > 0) {
            val i = stack.removeLast()
            val x = i % width
            val y = i / width
            if (x < (rowMin[y] ?: Int.MAX_VALUE)) rowMin[y] = x
            if (x > (rowMax[y] ?: -1)) rowMax[y] = x
            for (dy in -1..1) {
                val ny = y + dy
                if (ny < 0 || ny >= height) continue
                for (dx in -1..1) {
                    val nx = x + dx
                    if (nx < 0 || nx >= width) continue
                    val j = ny * width + nx
                    if (mask[j] && !seen[j]) {
                        seen[j] = true
                        stack.add(j)
                    }
                }
            }
        }
        regions += rowMin.flatMap { (y, minX) ->
            listOf(Point(minX.toFloat(), y.toFloat()), Point(rowMax.getValue(y).toFloat(), y.toFloat()))
        }
    }
    return regions
}

/** The mean of [values] (a map of [width] × [height]) over the pixels inside [corners]. */
private fun meanInside(values: FloatArray, width: Int, height: Int, corners: List<Point>): Float {
    val x0 = corners.minOf { it.x }.toInt().coerceIn(0, width - 1)
    val x1 = ceil(corners.maxOf { it.x }).toInt().coerceIn(0, width - 1)
    val y0 = corners.minOf { it.y }.toInt().coerceIn(0, height - 1)
    val y1 = ceil(corners.maxOf { it.y }).toInt().coerceIn(0, height - 1)
    var sum = 0f
    var count = 0
    for (y in y0..y1) {
        for (x in x0..x1) {
            if (inside(corners, Point(x.toFloat(), y.toFloat()))) {
                sum += values[y * width + x]
                count++
            }
        }
    }
    return if (count == 0) 0f else sum / count
}

/** A growable list of ints, for the flood fill, without boxing each pixel. */
private class IntArrayList {
    private var items = IntArray(1024)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == items.size) items = items.copyOf(size * 2)
        items[size++] = value
    }

    fun removeLast(): Int = items[--size]
}
