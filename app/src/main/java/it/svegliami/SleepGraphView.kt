package it.svegliami

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Ipnogramma a tre corsie (sveglio, leggero, profondo): ogni tratto continuo di una fase
 * è una barra arrotondata nella sua corsia, collegata alla successiva da una linea sottile.
 */
class SleepGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    companion object {
        fun stageColor(ctx: Context, st: SleepStage) = ContextCompat.getColor(
            ctx,
            when (st) {
                SleepStage.AWAKE -> R.color.stage_awake
                SleepStage.LIGHT -> R.color.stage_light
                SleepStage.DEEP -> R.color.stage_deep
            },
        )
    }

    var session: SleepSession? = null
        set(v) { field = v; invalidate() }

    private val dp = resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f * dp; alpha = 110 }
    private val gridPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.outline)
        strokeWidth = dp
        pathEffect = DashPathEffect(floatArrayOf(4 * dp, 4 * dp), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_surface_variant)
        textSize = sp(11f)
    }
    private val snorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.snore) }
    private val smartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.smart_wake)
        strokeWidth = 2 * dp
    }
    private val rect = RectF()

    private val lanes = listOf(SleepStage.AWAKE to "Sveglio", SleepStage.LIGHT to "Leggero", SleepStage.DEEP to "Profondo")

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = session ?: return
        val stages = SleepAnalyzer.stages(s.scores)
        if (stages.isEmpty()) return

        val labelW = lanes.maxOf { textPaint.measureText(it.second) } + 10 * dp
        val left = labelW
        val right = width.toFloat()
        val snoreH = if (s.snore.any { it }) 10 * dp else 0f
        val top = snoreH
        val bottom = height - 22 * dp
        val laneH = (bottom - top) / 3
        val barH = laneH * 0.5f
        val minW = (right - left) / stages.size
        fun laneY(st: SleepStage) = top + laneH * lanes.indexOfFirst { it.first == st } + laneH / 2

        // Corsie
        lanes.forEach { (st, label) ->
            val y = laneY(st)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(label, 0f, y + textPaint.textSize / 3, textPaint)
        }

        // Tratti continui di ogni fase
        var i = 0
        var prevEnd: Pair<Float, SleepStage>? = null
        while (i < stages.size) {
            val st = stages[i]
            var j = i
            while (j + 1 < stages.size && stages[j + 1] == st) j++
            val x0 = left + i * minW
            val x1 = left + (j + 1) * minW
            val y = laneY(st)
            val color = stageColor(context, st)
            prevEnd?.let { (px, pst) ->
                linkPaint.color = color
                linkPaint.alpha = 110
                canvas.drawLine(px, laneY(pst), px, y, linkPaint)
            }
            barPaint.color = color
            rect.set(x0, y - barH / 2, maxOf(x1, x0 + 2 * dp), y + barH / 2)
            canvas.drawRoundRect(rect, 4 * dp, 4 * dp, barPaint)
            prevEnd = x1 to st
            i = j + 1
        }

        // Russare: tacche in cima
        if (snoreH > 0) {
            s.snore.forEachIndexed { k, on ->
                if (!on) return@forEachIndexed
                rect.set(left + k * minW, 2 * dp, left + (k + 1) * minW + 0.5f, snoreH * 0.75f)
                canvas.drawRoundRect(rect, 2 * dp, 2 * dp, snorePaint)
            }
        }

        // Ore
        val zone = ZoneId.systemDefault()
        var t = Instant.ofEpochMilli(s.start).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val step = if (stages.size > 8 * 60) 2L else 1L
        while (true) {
            val m = (t.toInstant().toEpochMilli() - s.start) / 60_000f
            if (m >= stages.size) break
            val x = left + m * minW
            val label = "%02d".format(t.hour)
            canvas.drawText(label, x - textPaint.measureText(label) / 2, height - 4 * dp, textPaint)
            t = t.plusHours(step)
        }

        // Sveglia intelligente
        if (s.smartWakeAt > 0) {
            val x = left + (s.smartWakeAt - s.start) / 60_000f * minW
            canvas.drawLine(x, top, x, bottom, smartPaint)
            canvas.drawCircle(x, top + 3 * dp, 4 * dp, smartPaint)
        }
    }
}
