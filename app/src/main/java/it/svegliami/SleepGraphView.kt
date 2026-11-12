package it.svegliami

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Ipnogramma: una barra per minuto, alta e colorata in base alla fase stimata. */
class SleepGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    companion object {
        val COLOR_DEEP = Color.parseColor("#5C6BC0")
        val COLOR_LIGHT = Color.parseColor("#4FC3F7")
        val COLOR_AWAKE = Color.parseColor("#FFB74D")
    }

    var session: SleepSession? = null
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private val barPaint = Paint()
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        strokeWidth = density
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E91E63")
        strokeWidth = 2 * density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = session ?: return
        val stages = SleepAnalyzer.stages(s.scores)
        if (stages.isEmpty()) return

        val labelH = 20 * density
        val chartH = height - labelH
        val barW = width.toFloat() / stages.size

        stages.forEachIndexed { i, st ->
            val (h, c) = when (st) {
                SleepStage.AWAKE -> 1f to COLOR_AWAKE
                SleepStage.LIGHT -> 0.62f to COLOR_LIGHT
                SleepStage.DEEP -> 0.3f to COLOR_DEEP
            }
            barPaint.color = c
            val x = i * barW
            canvas.drawRect(x, chartH * (1 - h), x + barW + 0.5f, chartH, barPaint)
        }

        // Ore intere sull'asse orizzontale
        val zone = ZoneId.systemDefault()
        var t = Instant.ofEpochMilli(s.start).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val minutes = stages.size
        val step = if (minutes > 8 * 60) 2L else 1L
        while (true) {
            val m = (t.toInstant().toEpochMilli() - s.start) / 60_000f
            if (m >= minutes) break
            val x = m * barW
            canvas.drawLine(x, chartH, x, chartH + 4 * density, axisPaint)
            val label = "%02d".format(t.hour)
            canvas.drawText(label, x - axisPaint.measureText(label) / 2, height - 4 * density, axisPaint)
            t = t.plusHours(step)
        }

        // Momento della sveglia intelligente
        if (s.smartWakeAt > 0) {
            val x = (s.smartWakeAt - s.start) / 60_000f * barW
            canvas.drawLine(x, 0f, x, chartH, markPaint)
        }
    }
}
