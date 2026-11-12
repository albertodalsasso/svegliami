package it.svegliami

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Storico delle notti con ipnogramma e riepilogo. */
class SleepActivity : AppCompatActivity() {
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

    private lateinit var graph: SleepGraphView
    private lateinit var tvNight: TextView
    private lateinit var tvSummary: TextView
    private lateinit var nightList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        graph = findViewById(R.id.graph)
        tvNight = findViewById(R.id.tvNight)
        tvSummary = findViewById(R.id.tvSummary)
        nightList = findViewById(R.id.nightList)
        findViewById<TextView>(R.id.tvLegend).text = legend()
    }

    override fun onResume() {
        super.onResume()
        val sessions = SleepStore.load(this)
        if (sessions.isEmpty()) {
            tvNight.text = "Nessuna notte registrata"
            tvSummary.text = "La sera premi «Buonanotte» nella schermata principale e lascia il telefono sul materasso."
            graph.visibility = View.GONE
            return
        }
        graph.visibility = View.VISIBLE
        show(sessions.first())
        nightList.removeAllViews()
        for (s in sessions) {
            val sum = SleepAnalyzer.summary(s.scores)
            nightList.addView(TextView(this).apply {
                text = "${night(s)} · ${Scheduler.fmtTime(s.start)}–${Scheduler.fmtTime(s.end)} · sonno ${hm(sum.asleepMin)}"
                textSize = 15f
                setPadding(0, 24, 0, 24)
                setOnClickListener { show(s) }
            })
        }
    }

    /** La notte prende il nome dal giorno del risveglio. */
    private fun night(s: SleepSession) =
        dayFmt.format(Instant.ofEpochMilli(s.end).atZone(ZoneId.systemDefault())).replaceFirstChar { it.uppercase() }

    private fun hm(min: Int) = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"

    private fun show(s: SleepSession) {
        val sum = SleepAnalyzer.summary(s.scores)
        tvNight.text = night(s)
        graph.session = s
        tvSummary.text = buildString {
            append("A letto: ${Scheduler.fmtTime(s.start)}–${Scheduler.fmtTime(s.end)} (${hm(sum.inBedMin)})\n")
            append("Sonno stimato: ${hm(sum.asleepMin)}\n")
            append("Profondo ${hm(sum.deepMin)} · Leggero ${hm(sum.lightMin)} · Sveglio ${hm(sum.awakeMin)}\n")
            append("Risvegli: ${sum.awakenings}\n")
            append(if (s.mic) "Telefono sul comodino (microfono)" else "Telefono sul materasso (accelerometro)")
            if (s.smartWakeAt > 0) append("\n🌙 Sveglia intelligente alle ${Scheduler.fmtTime(s.smartWakeAt)} (linea rosa)")
        }
    }

    private fun legend(): CharSequence = SpannableStringBuilder().apply {
        fun item(color: Int, label: String) {
            val start = length
            append("■ ")
            setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append("$label   ")
        }
        item(SleepGraphView.COLOR_DEEP, "Profondo")
        item(SleepGraphView.COLOR_LIGHT, "Leggero")
        item(SleepGraphView.COLOR_AWAKE, "Sveglio")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
