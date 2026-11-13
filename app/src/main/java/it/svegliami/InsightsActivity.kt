package it.svegliami

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import it.svegliami.analysis.SleepInsightsCalculator
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
