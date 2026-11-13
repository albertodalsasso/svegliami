package it.svegliami

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import it.svegliami.analysis.SleepScoreCalculator
import it.svegliami.analysis.SleepScoreInputs
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Month calendar backed only by recorded sessions; days without data stay unscored. */
class SleepCalendarActivity : AppCompatActivity() {
    private var month = YearMonth.now()
    private lateinit var title: TextView
    private lateinit var grid: GridLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ContextCompat.getColor(this@SleepCalendarActivity, R.color.night_bg)) }
        setContentView(root)
        root.addView(MaterialToolbar(this).apply { title = "Calendario del sonno"; setTitleTextColor(ContextCompat.getColor(this@SleepCalendarActivity, R.color.on_surface)); setNavigationIcon(R.drawable.ic_arrow_back); setNavigationOnClickListener { finish() } })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        root.addView(androidx.core.widget.NestedScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, -1))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        controls.addView(Button(this).apply { text = "‹"; setOnClickListener { month = month.minusMonths(1); render() } })
        title = TextView(this).apply { gravity = Gravity.CENTER; textSize = 19f; setTextColor(ContextCompat.getColor(this@SleepCalendarActivity, R.color.on_surface)) }
        controls.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply { text = "›"; setOnClickListener { month = month.plusMonths(1); render() } })
        body.addView(controls)
        grid = GridLayout(this).apply { columnCount = 7; rowCount = 7 }
        body.addView(grid)
        body.addView(TextView(this).apply { text = "Tocca un giorno con una sessione per aprire il report. I giorni senza dati non ricevono un punteggio."; textSize = 14f; setTextColor(ContextCompat.getColor(this@SleepCalendarActivity, R.color.on_surface_variant)); setPadding(0, dp(16), 0, dp(8)) })
        render()
    }
    override fun onResume() { super.onResume(); if (::grid.isInitialized) render() }
    private fun render() {
        val sessions = SleepStore.load(this)
        val byDate = sessions.associateBy { Instant.ofEpochMilli(it.end).atZone(ZoneId.systemDefault()).toLocalDate() }
        title.text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }
        grid.removeAllViews()
        listOf("L", "M", "M", "G", "V", "S", "D").forEach { addCell(it, false) }
        repeat(month.atDay(1).dayOfWeek.value - 1) { addCell("", false) }
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val session = byDate[date]
            val label = if (session == null) "$day" else {
                val sum = SleepAnalyzer.summary(session.scores)
                val score = SleepScoreCalculator.calculate(
                    SleepScoreInputs(sum.asleepMin, sum.inBedMin, sum.awakenings, session.start,
                        if (session.trackingMode.usesMicrophone && session.snore.isNotEmpty()) session.snoreMinutes else null),
                    sessions.filter { previous -> previous.start != session.start }.map { previous -> previous.start },
                ).score
                "$day\n$score"
            }
            addCell(label, session != null) { session?.let { startActivity(Intent(this, MorningReportActivity::class.java).putExtra(MorningReportActivity.EXTRA_SESSION_START, it.start)) } }
        }
    }
    private fun addCell(value: String, hasData: Boolean, click: () -> Unit = {}) {
        val cell = TextView(this).apply {
            text = value; gravity = Gravity.CENTER; textSize = 14f
            setTextColor(ContextCompat.getColor(this@SleepCalendarActivity, if (hasData) R.color.on_tertiary_container else R.color.on_surface_variant))
            if (hasData) setBackgroundResource(R.drawable.bg_sleep_card)
            setOnClickListener { click() }
            contentDescription = if (hasData) "$value, sessione disponibile" else value
        }
        val lp = GridLayout.LayoutParams().apply { width = 0; height = dp(48); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(2), dp(2), dp(2), dp(2)) }
        grid.addView(cell, lp)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
