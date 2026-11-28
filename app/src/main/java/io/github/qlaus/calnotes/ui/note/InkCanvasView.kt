package io.github.qlaus.calnotes.ui.note

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import io.github.qlaus.calnotes.core.ink.InkStroke
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class InkTool { PEN, HIGHLIGHTER, ERASER }

/**
 * Transparent drawing layer over the note page. Wet ink is rendered by androidx.ink's
 * InProgressStrokesView (low latency); finished strokes are converted to page units and handed to
 * [onStroke], which renders them in the page and then calls [removeWet].
 *
 * Palm rejection: with [stylusOnly], only stylus pointers draw. Finger touches fall through to the
 * page (scrolling), except shortly after stylus activity, when they are swallowed (resting palm).
 */
@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class InkCanvasView(context: Context) : FrameLayout(context) {

    private val strokesView = InProgressStrokesView(context)

    var penMode = false
    var stylusOnly = true
    var tool = InkTool.PEN
    var colorHex = "#1C1C1E"
    /** Pen width in page units. */
    var sizePage = 2.5f

    /** View pixels per page unit. */
    var scale: () -> Float = { 1f }
    /** Current vertical scroll of the page in view pixels. */
    var scrollPx: () -> Int = { 0 }

    var onStroke: (id: InProgressStrokeId, stroke: InkStroke) -> Unit = { _, _ -> }
    var onErase: (x: Float, y: Float, radius: Float) -> Unit = { _, _, _ -> }

    private data class Pending(val scrollPx: Int, val scale: Float, val tool: InkTool, val color: String, val size: Float)

    private val pending = HashMap<InProgressStrokeId, Pending>()
    private var current: InProgressStrokeId? = null
    private var pointerId = -1
    private var erasing = false
    private var lastStylusAt = 0L

    init {
        addView(strokesView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        strokesView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                for ((id, stroke) in strokes) {
                    val meta = pending.remove(id)
                    if (meta == null) {
                        strokesView.removeFinishedStrokes(setOf(id))
                        continue
                    }
                    val page = toPage(stroke, meta)
                    if (page == null) strokesView.removeFinishedStrokes(setOf(id)) else onStroke(id, page)
                }
            }
        })
    }

    fun removeWet(id: InProgressStrokeId) = strokesView.removeFinishedStrokes(setOf(id))

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!penMode) return false
        val index = e.actionIndex
        val toolType = e.getToolType(index)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val now = SystemClock.uptimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (current != null || erasing) return true // a second pointer (palm) while writing: ignore
                if (stylusOnly && !isStylus) {
                    // Resting palm right after writing is swallowed; otherwise let the page scroll.
                    return now - lastStylusAt < PALM_WINDOW_MS
                }
                if (isStylus) lastStylusAt = now
                pointerId = e.getPointerId(index)
                val eraser = tool == InkTool.ERASER || toolType == MotionEvent.TOOL_TYPE_ERASER ||
                    (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                if (eraser) {
                    erasing = true
                    erase(e.getX(index), e.getY(index))
                } else {
                    requestUnbufferedDispatch(e)
                    val s = scale()
                    val id = strokesView.startStroke(e, pointerId, brush(s))
                    pending[id] = Pending(scrollPx(), s, tool, colorHex, sizeFor(tool))
                    current = id
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isStylus) lastStylusAt = now
                val i = e.findPointerIndex(pointerId)
                if (i < 0) return true
                if (erasing) {
                    for (h in 0 until e.historySize) erase(e.getHistoricalX(i, h), e.getHistoricalY(i, h))
                    erase(e.getX(i), e.getY(i))
                } else {
                    current?.let { strokesView.addToStroke(e, pointerId, it) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(index) != pointerId) return true
                if (isStylus) lastStylusAt = now
                if (!erasing) current?.let { strokesView.finishStroke(e, pointerId, it) }
                current = null
                erasing = false
                pointerId = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                current?.let {
                    strokesView.cancelStroke(it, e)
                    pending.remove(it)
                }
                current = null
                erasing = false
                pointerId = -1
                return true
            }
        }
        return true
    }

    private fun sizeFor(t: InkTool) = if (t == InkTool.HIGHLIGHTER) sizePage * 5f else sizePage

    private fun brush(scale: Float): Brush {
        val base = Color.parseColor(colorHex)
        return if (tool == InkTool.HIGHLIGHTER) {
            val translucent = Color.argb(90, Color.red(base), Color.green(base), Color.blue(base))
            Brush.createWithColorIntArgb(StockBrushes.highlighterLatest, translucent, sizeFor(tool) * scale, 0.1f)
        } else {
            Brush.createWithColorIntArgb(StockBrushes.pressurePenLatest, base, sizePage * scale, 0.1f)
        }
    }

    private fun erase(x: Float, y: Float) {
        val s = scale()
        onErase(x / s, (y + scrollPx()) / s, ERASER_RADIUS)
    }

    /** View-pixel stroke → page units, thinned to points at least 0.6 units apart. */
    private fun toPage(stroke: Stroke, meta: Pending): InkStroke? {
        val inputs = stroke.inputs
        if (inputs.size == 0) return null
        val pts = ArrayList<Float>(inputs.size * 3)
        var lx = Float.NaN
        var ly = Float.NaN
        for (i in 0 until inputs.size) {
            val input = inputs.get(i)
            val x = input.x / meta.scale
            val y = (input.y + meta.scrollPx) / meta.scale
            if (!lx.isNaN() && hypot(x - lx, y - ly) < 0.6f && i != inputs.size - 1) continue
            val pressure = if (input.pressure < 0f) 0.5f else input.pressure
            pts += round1(x)
            pts += round1(y)
            pts += round2(pressure)
            lx = x
            ly = y
        }
        return InkStroke(
            id = UUID.randomUUID().toString(),
            tool = if (meta.tool == InkTool.HIGHLIGHTER) "highlighter" else "pen",
            color = meta.color,
            size = meta.size,
            points = pts,
        )
    }

    private fun round1(v: Float) = (v * 10f).roundToInt() / 10f
    private fun round2(v: Float) = (v * 100f).roundToInt() / 100f

    private companion object {
        const val PALM_WINDOW_MS = 600L
        const val ERASER_RADIUS = 8f
    }
}
