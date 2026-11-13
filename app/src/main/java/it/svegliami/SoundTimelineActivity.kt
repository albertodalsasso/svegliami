package it.svegliami

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView

/** Timeline of sound-related signals currently persisted by the legacy tracker (snore flags). */
class SoundTimelineActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ContextCompat.getColor(this@SoundTimelineActivity, R.color.night_bg)) }
        setContentView(root)
        root.addView(MaterialToolbar(this).apply { title = "Timeline sonora"; setTitleTextColor(ContextCompat.getColor(this@SoundTimelineActivity, R.color.on_surface)); setNavigationIcon(R.drawable.ic_arrow_back); setNavigationOnClickListener { finish() } })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        root.addView(androidx.core.widget.NestedScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val session = intent.getLongExtra(MorningReportActivity.EXTRA_SESSION_START, -1L).let { id ->
            SleepStore.load(this).firstOrNull { it.start == id } ?: SleepStore.load(this).firstOrNull()
        }
        if (session == null || !session.trackingMode.usesMicrophone || session.snore.none { it }) {
            list.addView(label("Nessun evento audio salvato per questa sessione.", 19))
            list.addView(label("Il monitoraggio attuale analizza il segnale in locale e conserva soltanto gli indicatori di russamento per minuto. Non registra clip audio, quindi non è disponibile la riproduzione.", 14))
            return
        }
        val marks = session.snore.withIndex().filter { it.value }
        list.addView(label("${marks.size} minuti con segnale compatibile con russamento", 20))
        list.addView(label("I marcatori sono stime del rilevatore audio, non una diagnosi. Le registrazioni non vengono conservate.", 14))
        marks.forEach { mark ->
            val time = Scheduler.fmtTime(session.start + mark.index * 60_000L)
            val card = MaterialCardView(this).apply { radius = dp(18).toFloat(); setCardBackgroundColor(ContextCompat.getColor(this@SoundTimelineActivity, R.color.surface)) }
            card.addView(label("◉   $time     Russamento rilevato · confidenza non disponibile", 15).apply { setPadding(dp(16), dp(14), dp(16), dp(14)); gravity = Gravity.CENTER_VERTICAL })
            val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(8); list.addView(card, lp)
        }
    }
    private fun label(textValue: String, size: Int) = TextView(this).apply { text = textValue; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@SoundTimelineActivity, R.color.on_surface_variant)); setPadding(0, dp(8), 0, dp(8)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
