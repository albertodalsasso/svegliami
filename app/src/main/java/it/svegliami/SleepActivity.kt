package it.svegliami

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Storico delle notti: numeri principali, ipnogramma ed elenco. */
class SleepActivity : AppCompatActivity() {
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

    private lateinit var graph: SleepGraphView
    private lateinit var tvNight: TextView
    private lateinit var tvNightRange: TextView
    private lateinit var tvDetails: TextView
    private lateinit var nightList: LinearLayout
    private var selected: SleepSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep)
        SystemBars.apply(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        graph = findViewById(R.id.graph)
        tvNight = findViewById(R.id.tvNight)
        tvNightRange = findViewById(R.id.tvNightRange)
        tvDetails = findViewById(R.id.tvDetails)
        nightList = findViewById(R.id.nightList)
        findViewById<TextView>(R.id.tvLegend).text = legend()
        stat(R.id.statSleep, "Sonno")
        stat(R.id.statDeep, "Profondo")
        stat(R.id.statWake, "Risvegli")
        stat(R.id.statSnore, "Russare")
    }

    override fun onResume() {
        super.onResume()
        val sessions = SleepStore.load(this)
        val visible = if (sessions.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.statsBlock).visibility = visible
        findViewById<View>(R.id.graphCard).visibility = visible
        findViewById<View>(R.id.tvNightsTitle).visibility = visible
        if (sessions.isEmpty()) {
            tvNight.text = "🌙  Nessuna notte registrata"
            tvNightRange.text = ""
            tvDetails.text = "La sera premi «Buonanotte» nella schermata principale e lascia il telefono sul materasso o sul comodino, come scelto lì."
            nightList.removeAllViews()
            return
        }
        show(selected?.let { sel -> sessions.firstOrNull { it.start == sel.start } } ?: sessions.first(), sessions)
    }

    private fun stat(id: Int, label: String) {
        findViewById<View>(id).findViewById<TextView>(R.id.statLabel).text = label
    }

    private fun setStat(id: Int, value: String, sub: String) {
        val v = findViewById<View>(id)
        v.findViewById<TextView>(R.id.statValue).text = value
        v.findViewById<TextView>(R.id.statSub).text = sub
    }

    /** La notte prende il nome dal giorno del risveglio. */
    private fun night(s: SleepSession) =
        dayFmt.format(Instant.ofEpochMilli(s.end).atZone(ZoneId.systemDefault())).replaceFirstChar { it.uppercase() }

    private fun hm(min: Int) = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
    private fun hmShort(min: Int) = if (min >= 60) "${min / 60}h %02d".format(min % 60) else "${min}m"

    private fun show(s: SleepSession, sessions: List<SleepSession>) {
        selected = s
        val sum = SleepAnalyzer.summary(s.scores)
        tvNight.text = night(s)
        tvNightRange.text = "A letto ${Scheduler.fmtTime(s.start)}–${Scheduler.fmtTime(s.end)} · ${hm(sum.inBedMin)}"
        graph.session = s

        val pct = { m: Int -> if (sum.asleepMin > 0) m * 100 / sum.asleepMin else 0 }
        setStat(R.id.statSleep, hmShort(sum.asleepMin), "leggero ${hm(sum.lightMin)}")
        setStat(R.id.statDeep, hmShort(sum.deepMin), "${pct(sum.deepMin)}% del sonno")
        setStat(R.id.statWake, "${sum.awakenings}", "sveglio ${hm(sum.awakeMin)}")
        if (s.mic) setStat(R.id.statSnore, if (s.snoreMinutes == 0) "—" else hmShort(s.snoreMinutes),
            if (s.snoreMinutes == 0) "non rilevato" else "${pct(s.snoreMinutes)}% del sonno")
        else setStat(R.id.statSnore, "—", "serve il comodino")

        tvDetails.text = buildString {
            append(if (s.mic) "🎙 Telefono sul comodino (microfono)" else "🛏 Telefono sul materasso (accelerometro)")
            if (s.smartWakeAt > 0) append("\n🌙 Sveglia intelligente alle ${Scheduler.fmtTime(s.smartWakeAt)} (linea rosa)")
        }
        renderList(sessions)
    }

    private fun renderList(sessions: List<SleepSession>) {
        nightList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (s in sessions) {
            val sum = SleepAnalyzer.summary(s.scores)
            val v = inflater.inflate(R.layout.item_night, nightList, false) as MaterialCardView
            v.findViewById<TextView>(R.id.tvNightDate).text = night(s)
            v.findViewById<TextView>(R.id.tvNightSleep).text = hmShort(sum.asleepMin)
            v.findViewById<TextView>(R.id.tvNightInfo).text =
                "${Scheduler.fmtTime(s.start)}–${Scheduler.fmtTime(s.end)}" +
                    (if (s.snoreMinutes > 0) " · russato ${hm(s.snoreMinutes)}" else "") +
                    (if (s.smartWakeAt > 0) " · 🌙 ${Scheduler.fmtTime(s.smartWakeAt)}" else "")
            v.findViewById<LinearProgressIndicator>(R.id.barNight).progress = sum.asleepMin.coerceAtMost(600)
            v.strokeWidth = if (s.start == selected?.start) (2 * resources.displayMetrics.density).toInt() else 0
            v.setOnClickListener { show(s, sessions) }
            nightList.addView(v)
        }
    }

    private fun legend(): CharSequence = SpannableStringBuilder().apply {
        fun item(colorRes: Int, label: String) {
            val start = length
            append("●  ")
            setSpan(ForegroundColorSpan(ContextCompat.getColor(this@SleepActivity, colorRes)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append("$label     ")
        }
        item(R.color.stage_awake, "Sveglio")
        item(R.color.stage_light, "Leggero")
        item(R.color.stage_deep, "Profondo")
        item(R.color.snore, "Russare")
    }
}
