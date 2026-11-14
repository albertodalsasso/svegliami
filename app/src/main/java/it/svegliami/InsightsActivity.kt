package it.svegliami

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import it.svegliami.analysis.JournalInsights
import it.svegliami.analysis.JournalNight
import it.svegliami.analysis.SleepInsightsCalculator
import it.svegliami.storage.SleepRoomRepository
import android.widget.LinearLayout
import java.util.Locale

class InsightsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insights)
        SystemBars.apply(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        NavigationUi.bind(this, findViewById<BottomNavigationView>(R.id.bottomNavigation), R.id.navInsights)
    }

    override fun onResume() {
        super.onResume()
        val report = SleepInsightsCalculator.calculate(SleepStore.load(this))
        val content = findViewById<View>(R.id.insightsContent)
        val empty = findViewById<TextView>(R.id.tvInsightsEmpty)
        content.visibility = if (report.nightCount == 0) View.GONE else View.VISIBLE
        empty.visibility = if (report.nightCount == 0) View.VISIBLE else View.GONE
        if (report.nightCount == 0) return

        stat(R.id.statAverageSleep, "Media sonno", formatDuration(report.averageSleepMinutes), "${report.nightCount} notti")
        stat(R.id.statAverageScore, "Score medio", "${report.averageScore}", "indicativo · non medico")
        findViewById<SleepInsightsChartView>(R.id.scoreChart).apply {
            kind = SleepInsightsChartView.Kind.SCORE
            values = report.points.map { it.score.toFloat() }
            contentDescription = "Andamento del punteggio del sonno per ${report.nightCount} notti"
        }
        findViewById<SleepInsightsChartView>(R.id.durationChart).apply {
            kind = SleepInsightsChartView.Kind.HOURS
            values = report.points.map { it.sleepMinutes / 60f }
            contentDescription = "Ore di sonno per ${report.nightCount} notti"
        }
        findViewById<TextView>(R.id.tvSleepDebt).text = formatDuration(report.sevenNightDebtMinutes)

        SleepRoomRepository.loadJournals(this) { journals ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val nights = report.points.mapNotNull { pt ->
                    journals[pt.timestamp]?.let { j ->
                        JournalNight(pt.timestamp, pt.sleepMinutes, pt.score, j.caffeine, j.exercise, j.stress, j.mealTime)
                    }
                }
                renderHabits(nights)
            }
        }
    }

    /** Confronti fra notti con e senza un'abitudine annotata nel diario. */
    private fun renderHabits(nights: List<JournalNight>) {
        val list = findViewById<LinearLayout>(R.id.habitsList)
        list.removeAllViews()
        val comparisons = JournalInsights.compare(nights)
        for (c in comparisons) {
            val diff = c.sleepDifferenceMinutes
            val verdict = when {
                kotlin.math.abs(diff) < 10 -> "nessuna differenza di rilievo"
                diff < 0 -> "${formatDuration(-diff)} di sonno in meno"
                else -> "${formatDuration(diff)} di sonno in più"
            }
            list.addView(TextView(this).apply {
                setTextColor(getColor(R.color.on_surface))
                textSize = 16f
                setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, 0)
                text = "${c.habit}: $verdict"
            })
            list.addView(TextView(this).apply {
                setTextColor(getColor(R.color.on_surface_variant))
                textSize = 13f
                text = "${c.withLabel}: ${formatDuration(c.withSleepMinutes)} · punteggio ${c.withScore} (${c.withNights} notti)\n" +
                    "${c.withoutLabel}: ${formatDuration(c.withoutSleepMinutes)} · punteggio ${c.withoutScore} (${c.withoutNights} notti)"
            })
        }
        findViewById<TextView>(R.id.tvHabitsInfo).text = when {
            nights.isEmpty() -> "Compila il diario la mattina (scheda Diario o dallo storico): dopo qualche notte qui vedrai come caffeina, attività fisica, stress e cena tardi cambiano il tuo sonno."
            comparisons.isEmpty() -> "Diario compilato per ${nights.size} ${if (nights.size == 1) "notte" else "notti"}. Per ogni abitudine servono almeno ${JournalInsights.MIN_NIGHTS} notti con e ${JournalInsights.MIN_NIGHTS} senza."
            else -> "Medie delle notti con diario (${nights.size}). Sono confronti, non cause: con poche notti possono cambiare molto."
        }
    }

    private fun stat(id: Int, label: String, value: String, sub: String) {
        val card = findViewById<View>(id)
        card.findViewById<TextView>(R.id.statLabel).text = label.uppercase(Locale.ITALIAN)
        card.findViewById<TextView>(R.id.statValue).text = value
        card.findViewById<TextView>(R.id.statSub).text = sub
    }

    private fun formatDuration(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
