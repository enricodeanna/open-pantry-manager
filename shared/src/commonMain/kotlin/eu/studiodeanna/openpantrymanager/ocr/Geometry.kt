package eu.studiodeanna.openpantrymanager.ocr

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** A point on a picture, in pixels; a pixel's centre is at its whole coordinates. */
data class Point(val x: Float, val y: Float)

/** A rectangle at any angle: its centre, its sides and the angle (radians) of its [width] side. */
internal class TiltedRect(val cx: Float, val cy: Float, val width: Float, val height: Float, val angle: Float) {
    val shortSide get() = min(width, height)

    /** The same rectangle grown by [by] on every side. */
    fun grown(by: Float) = TiltedRect(cx, cy, width + 2 * by, height + 2 * by, angle)

    /** Its corners: top-left, top-right, bottom-right, bottom-left, as they lie on the picture. */
    fun corners(): List<Point> {
        val ux = cos(angle) * width / 2
        val uy = sin(angle) * width / 2
        val vx = -sin(angle) * height / 2
        val vy = cos(angle) * height / 2
        return clockwise(
            listOf(
                Point(cx - ux - vx, cy - uy - vy),
                Point(cx + ux - vx, cy + uy - vy),
                Point(cx + ux + vx, cy + uy + vy),
                Point(cx - ux + vx, cy - uy + vy),
            ),
        )
    }
}

/**
 * The four [points] as top-left, top-right, bottom-right, bottom-left: the two leftmost are the left
 * side, the higher of each side its top.
 */
internal fun clockwise(points: List<Point>): List<Point> {
    val byX = points.sortedWith(compareBy({ it.x }, { it.y }))
    val (topLeft, bottomLeft) = byX.take(2).sortedBy { it.y }
    val (topRight, bottomRight) = byX.drop(2).sortedBy { it.y }
    return listOf(topLeft, topRight, bottomRight, bottomLeft)
}

/** The smallest convex polygon around [points], its corners in order. */
internal fun convexHull(points: List<Point>): List<Point> {
    val sorted = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
    if (sorted.size < 3) return sorted
    fun cross(o: Point, a: Point, b: Point) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val hull = ArrayList<Point>(sorted.size * 2)
    for (pass in 0..1) {
        val start = hull.size
        for (p in if (pass == 0) sorted else sorted.asReversed()) {
            while (hull.size >= start + 2 && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) hull.removeAt(hull.size - 1)
            hull += p
        }
        hull.removeAt(hull.size - 1)
    }
    return hull
}

/** The rectangle of least area around a convex [hull]: one of its sides lies along a side of the hull. */
internal fun smallestRect(hull: List<Point>): TiltedRect {
    if (hull.size == 1) return TiltedRect(hull[0].x, hull[0].y, 0f, 0f, 0f)
    var best: TiltedRect? = null
    var bestArea = Float.MAX_VALUE
    for (i in hull.indices) {
        val a = hull[i]
        val b = hull[(i + 1) % hull.size]
        val length = hypot(b.x - a.x, b.y - a.y)
        if (length == 0f) continue
        val ux = (b.x - a.x) / length
        val uy = (b.y - a.y) / length
        var minU = Float.MAX_VALUE
        var maxU = -Float.MAX_VALUE
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for (p in hull) {
            val u = p.x * ux + p.y * uy
            val v = -p.x * uy + p.y * ux
            if (u < minU) minU = u
            if (u > maxU) maxU = u
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }
        val area = (maxU - minU) * (maxV - minV)
        if (area < bestArea) {
            bestArea = area
            val mu = (minU + maxU) / 2
            val mv = (minV + maxV) / 2
            best = TiltedRect(mu * ux - mv * uy, mu * uy + mv * ux, maxU - minU, maxV - minV, kotlin.math.atan2(uy, ux))
        }
    }
    return best ?: TiltedRect(hull[0].x, hull[0].y, 0f, 0f, 0f)
}

/** Whether [p] lies in the convex polygon [corners], edges included. */
internal fun inside(corners: List<Point>, p: Point): Boolean {
    var sign = 0
    for (i in corners.indices) {
        val a = corners[i]
        val b = corners[(i + 1) % corners.size]
        val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        if (abs(cross) < 1e-3f) continue
        val s = if (cross > 0) 1 else -1
        if (sign == 0) sign = s else if (s != sign) return false
    }
    return true
}

internal fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
