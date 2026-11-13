package it.svegliami

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import it.svegliami.analysis.SleepScoreCalculator
import it.svegliami.analysis.SleepScoreInputs
import it.svegliami.storage.SleepJournalEntryEntity
import it.svegliami.storage.SleepRoomRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Morning report computed from the selected locally stored session. */
class MorningReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getLongExtra(EXTRA_SESSION_START, -1L)
        val session = SleepStore.load(this).firstOrNull { it.start == sessionId }
            ?: SleepStore.load(this).firstOrNull()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ContextCompat.getColor(this@MorningReportActivity, R.color.night_bg)) }
        setContentView(root)
        root.addView(MaterialToolbar(this).apply { title = "Report del risveglio"; setTitleTextColor(ContextCompat.getColor(this@MorningReportActivity, R.color.on_surface)); setNavigationIcon(R.drawable.ic_arrow_back); setNavigationOnClickListener { finish() } })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(28)) }
        root.addView(androidx.core.widget.NestedScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        if (session == null) {
            body.addView(text("Non ci sono ancora sessioni del sonno salvate.", 18))
            return
        }
        val summary = SleepAnalyzer.summary(session.scores)
        val history = SleepStore.load(this)
        val score = SleepScoreCalculator.calculate(
            SleepScoreInputs(summary.asleepMin, summary.inBedMin, summary.awakenings, session.start,
                if (session.trackingMode.usesMicrophone && session.snore.isNotEmpty()) session.snoreMinutes else null),
            history.filter { it.start != session.start }.take(13).map { it.start },
        )
        val date = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN).format(Instant.ofEpochMilli(session.end).atZone(ZoneId.systemDefault()))
        body.addView(text("${date.replaceFirstChar { it.uppercase() }} · buongiorno", 24))
        body.addView(card("${score.score} / 100", "Sleep Score sperimentale · ${score.explanation}"))
        body.addView(card("${summary.asleepMin / 60} h ${summary.asleepMin % 60} min", "Sonno stimato · ${Scheduler.fmtTime(session.start)}–${Scheduler.fmtTime(session.end)}"))
        body.addView(card("${summary.awakenings} risvegli · ${summary.awakeMin} min sveglio", "Continuità stimata dai segnali raccolti sul dispositivo"))
        body.addView(card("${session.snoreMinutes} minuti con russamento rilevato", if (session.trackingMode.usesMicrophone) "Analisi audio locale; nessun frammento audio è stato salvato." else "Modalità materasso: non è stata eseguita analisi audio."))
        body.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = "Apri timeline sonora"
            setOnClickListener { startActivity(android.content.Intent(this@MorningReportActivity, SoundTimelineActivity::class.java).putExtra(EXTRA_SESSION_START, session.start)) }
        })
        history.firstOrNull { it.start != session.start }?.let { previous ->
            val prev = SleepAnalyzer.summary(previous.scores)
            body.addView(card("Confronto con la notte precedente", "${(summary.asleepMin - prev.asleepMin).let { if (it >= 0) "+$it" else "$it" }} minuti di sonno stimato"))
        }
        body.addView(TextView(this).apply { text = "Come ti senti questa mattina?"; textSize = 17f; setTextColor(ContextCompat.getColor(this@MorningReportActivity, R.color.on_surface)); setPadding(0, dp(16), 0, dp(8)) })
        val moods = listOf("Molto riposato", "Riposato", "Normale", "Stanco", "Molto stanco")
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        moods.forEachIndexed { index, mood -> row.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = mood
            setOnClickListener { saveMood(session.start, listOf("VERY_RESTED", "RESTED", "NORMAL", "TIRED", "VERY_TIRED")[index]) }
        }) }
        body.addView(row)
        body.addView(com.google.android.material.button.MaterialButton(this).apply { text = "Apri storico del sonno"; setOnClickListener { startActivity(android.content.Intent(this@MorningReportActivity, SleepActivity::class.java)) } })
    }

    private fun saveMood(sessionId: Long, mood: String) {
        SleepRoomRepository.loadJournal(this, sessionId) { old ->
            SleepRoomRepository.saveJournal(this, SleepJournalEntryEntity(
                sessionId, old?.caffeine, old?.exercise, old?.stress, old?.mealTime, mood, old?.notes,
            )) { saved -> runOnUiThread { android.widget.Toast.makeText(this, if (saved) "Sensazione salvata" else "Impossibile salvare la sensazione", android.widget.Toast.LENGTH_SHORT).show() } }
        }
    }
    private fun card(title: String, subtitle: String) = MaterialCardView(this).apply {
        radius = dp(22).toFloat(); cardElevation = dp(2).toFloat(); setCardBackgroundColor(ContextCompat.getColor(this@MorningReportActivity, R.color.surface))
        val column = LinearLayout(this@MorningReportActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)) }
        column.addView(text(title, 20)); column.addView(TextView(this@MorningReportActivity).apply { text = subtitle; textSize = 13f; setTextColor(ContextCompat.getColor(this@MorningReportActivity, R.color.on_surface_variant)) })
        addView(column); val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(10); layoutParams = lp
    }
    private fun text(value: String, size: Int) = TextView(this).apply { text = value; textSize = size.toFloat(); gravity = Gravity.START; setTextColor(ContextCompat.getColor(this@MorningReportActivity, R.color.on_surface)); setPadding(0, dp(7), 0, dp(7)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    companion object { const val EXTRA_SESSION_START = "session_start" }
}
