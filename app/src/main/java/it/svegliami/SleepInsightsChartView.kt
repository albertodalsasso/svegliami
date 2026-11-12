package it.svegliami

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

class SleepInsightsChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    enum class Kind { SCORE, HOURS }

    var kind: Kind = Kind.SCORE
        set(value) { field = value; invalidate() }
    var values: List<Float> = emptyList()
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_surface_variant)
        textSize = 10f * density
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tertiary)
        strokeWidth = 2.5f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.secondary)
        style = Paint.Style.FILL
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.outline)
        strokeWidth = density
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tertiary)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val left = 34f * density
        val right = width - 8f * density
        val top = 12f * density
        val bottom = height - 22f * density
        if (right <= left || bottom <= top) return
        val maximum = if (kind == Kind.SCORE) 100f else 12f
        val labels = if (kind == Kind.SCORE) listOf("0", "50", "100") else listOf("0h", "6h", "12h")
        for (i in 0..2) {
            val y = bottom - (bottom - top) * i / 2f
            canvas.drawLine(left, y, right, y, grid)
            canvas.drawText(labels[i], 0f, y + 3f * density, text)
        }
        val step = if (values.size > 1) (right - left) / (values.size - 1) else 0f
        if (kind == Kind.HOURS) {
            val gap = 4f * density
            val count = values.size
            val barWidth = max(4f * density, ((right - left + gap) / count) - gap)
            values.forEachIndexed { index, value ->
                val center = if (count == 1) (left + right) / 2f else left + index * step
                val barHeight = ((bottom - top) * value.coerceIn(0f, maximum) / maximum)
                canvas.drawRoundRect(
                    RectF(center - barWidth / 2f, bottom - barHeight, center + barWidth / 2f, bottom),
                    4f * density, 4f * density, fill,
                )
            }
        } else {
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = if (values.size == 1) (left + right) / 2f else left + index * step
                val y = bottom - (bottom - top) * value.coerceIn(0f, maximum) / maximum
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, line)
            values.forEachIndexed { index, value ->
                val x = if (values.size == 1) (left + right) / 2f else left + index * step
                val y = bottom - (bottom - top) * value.coerceIn(0f, maximum) / maximum
                canvas.drawCircle(x, y, 3f * density, pointPaint)
            }
        }
    }
}
