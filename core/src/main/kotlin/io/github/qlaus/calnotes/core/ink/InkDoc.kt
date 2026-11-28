package io.github.qlaus.calnotes.core.ink

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Handwriting layer of a note, format v2 (docs/note-format.md). Coordinates are page units on a page
 * [PAGE_WIDTH] wide, y downwards. Shared with the web app (web/src/editor/ink.ts).
 */
@Serializable
data class InkDoc(
    val formatVersion: Int = INK_FORMAT_VERSION,
    val pageWidth: Int = PAGE_WIDTH,
    val strokes: List<InkStroke> = emptyList(),
) {
    val isEmpty: Boolean get() = strokes.isEmpty()

    /** Bottom of the lowest stroke. */
    fun bottom(): Float = strokes.maxOfOrNull { s -> (1 until s.points.size step 3).maxOfOrNull { s.points[it] + s.size } ?: 0f } ?: 0f

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val PAGE_WIDTH = 640
        const val INK_FORMAT_VERSION = 2
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Null for missing, unreadable or newer-format data. */
        fun parse(text: String?): InkDoc? {
            if (text.isNullOrBlank()) return null
            return runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
                ?.takeIf { it.formatVersion <= INK_FORMAT_VERSION }
                ?.let { d -> d.copy(strokes = d.strokes.filter { it.points.size >= 3 }) }
        }
    }
}

@Serializable
data class InkStroke(
    val id: String,
    /** pen | highlighter */
    val tool: String,
    /** #RRGGBB */
    val color: String,
    /** Nominal width in page units. */
    val size: Float,
    /** Flat x, y, pressure (0..1) triples in page units. */
    val points: List<Float>,
)

object InkGeometry {
    /**
     * Whole-stroke eraser: removes every stroke that passes within [radius] (plus half its width) of
     * the point. Returns the same instance when nothing was hit.
     */
    fun eraseAt(doc: InkDoc, x: Float, y: Float, radius: Float): InkDoc {
        val kept = doc.strokes.filterNot { hits(it, x, y, radius + it.size / 2) }
        return if (kept.size == doc.strokes.size) doc else doc.copy(strokes = kept)
    }

    private fun hits(s: InkStroke, x: Float, y: Float, r: Float): Boolean {
        val p = s.points
        val n = p.size / 3
        if (n == 1) return hypot(p[0] - x, p[1] - y) <= r
        for (i in 0 until n - 1) {
            if (segmentDistance(x, y, p[i * 3], p[i * 3 + 1], p[i * 3 + 3], p[i * 3 + 4]) <= r) return true
        }
        return false
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else min(1f, max(0f, ((px - ax) * dx + (py - ay) * dy) / len2))
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** Filled outline path of a stroke (SVG path data); mirrors strokePath() in the web app. */
    fun svgPath(s: InkStroke): String {
        val p = s.points
        val n = p.size / 3
        fun r(i: Int) = max(0.4f, s.size * widthFactor(s, p[i * 3 + 2]) / 2)
        fun f(v: Float) = String.format(java.util.Locale.ROOT, "%.1f", v)
        if (n == 1) {
            val rr = max(0.5f, r(0))
            return "M${f(p[0] - rr)},${f(p[1])}a${f(rr)},${f(rr)} 0 1,0 ${f(2 * rr)},0a${f(rr)},${f(rr)} 0 1,0 ${f(-2 * rr)},0"
        }
        val left = ArrayList<String>(n)
        val right = ArrayList<String>(n)
        for (i in 0 until n) {
            val x = p[i * 3]
            val y = p[i * 3 + 1]
            val j0 = max(0, i - 1)
            val j1 = min(n - 1, i + 1)
            var dx = p[j1 * 3] - p[j0 * 3]
            var dy = p[j1 * 3 + 1] - p[j0 * 3 + 1]
            val len = hypot(dx, dy).takeIf { it > 0f } ?: 1f
            dx /= len
            dy /= len
            val rr = r(i)
            left += "${f(x - dy * rr)},${f(y + dx * rr)}"
            right += "${f(x + dy * rr)},${f(y - dx * rr)}"
        }
        val rEnd = r(n - 1)
        val rStart = r(0)
        return "M${left[0]}L${left.joinToString("L")}A${f(rEnd)},${f(rEnd)} 0 0,0 ${right[n - 1]}" +
            "L${right.reversed().joinToString("L")}A${f(rStart)},${f(rStart)} 0 0,0 ${left[0]}Z"
    }

    fun opacity(s: InkStroke) = if (s.tool == "highlighter") 0.32f else 1f

    private fun widthFactor(s: InkStroke, pressure: Float) =
        if (s.tool == "highlighter") 1f else 0.55f + 0.9f * min(1f, max(0f, pressure))

    fun svg(doc: InkDoc): String {
        val h = kotlin.math.ceil(doc.bottom() + 10).toInt()
        val paths = doc.strokes.joinToString("") { s -> "<path d=\"${svgPath(s)}\" fill=\"${s.color}\" fill-opacity=\"${opacity(s)}\"/>" }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${InkDoc.PAGE_WIDTH}\" height=\"$h\" viewBox=\"0 0 ${InkDoc.PAGE_WIDTH} $h\">$paths</svg>"
    }
}
