package it.svegliami

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Schermata notturna mostrata dopo «Buonanotte»: nera e con luminosità minima, per non
 * illuminare la stanza. Mostra da quando è attivo il monitoraggio, la modalità, lo stato del
 * sensore e dell'audio, la sveglia prevista; la sessione si termina tenendo premuto il pulsante.
 * Lo schermo può spegnersi normalmente: il monitoraggio continua nel servizio.
 */
class NightActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 30_000)
        }
    }

    /** Il servizio parte in modo asincrono: appena aperta, la schermata lo aspetta qualche secondo. */
    private val openedAt = System.currentTimeMillis()

    private lateinit var tvClock: TextView
    private lateinit var tvSince: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvSensor: TextView
    private lateinit var tvAudio: TextView
    private lateinit var tvAlarm: TextView
    private lateinit var tvHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_night)
        SystemBars.apply(this)
        // Luminosità al minimo solo per questa schermata: torna normale appena la si lascia.
        window.attributes = window.attributes.apply { screenBrightness = 0.01f }

        tvClock = findViewById(R.id.tvNightClock)
        tvSince = findViewById(R.id.tvNightSince)
        tvMode = findViewById(R.id.tvNightMode)
        tvSensor = findViewById(R.id.tvNightSensor)
        tvAudio = findViewById(R.id.tvNightAudio)
        tvAlarm = findViewById(R.id.tvNightAlarm)
        tvHint = findViewById(R.id.tvNightHint)

        findViewById<MaterialButton>(R.id.btnNightStop).apply {
            // Un tocco solo non basta, per non chiudere la notte per sbaglio al buio.
            setOnClickListener {
                tvHint.text = "Tieni premuto il pulsante per terminare la sessione"
                tvHint.setTextColor(getColor(R.color.on_surface_variant))
            }
            setOnLongClickListener {
                endSession()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun render() {
        // Sessione terminata altrove (per esempio dalla sveglia o dalla notifica): niente da mostrare.
        if (!SleepService.isRunning) {
            if (System.currentTimeMillis() - openedAt < 5_000) {
                tvSince.text = "Avvio del monitoraggio…"
                handler.postDelayed({ if (!isFinishing) render() }, 500)
            } else finish()
            return
        }
        val now = System.currentTimeMillis()
        val start = SleepService.startedAt
        tvClock.text = Scheduler.fmtTime(now)
        tvSince.text = "Monitoraggio dalle ${Scheduler.fmtTime(start)} · ${duration(now - start)}"

        val mode = SleepService.mode
        tvMode.text = when (mode) {
            SleepTrackingMode.NIGHTSTAND -> "🎙  Comodino"
            else -> "🛏  Materasso"
        }
        tvSensor.text = when {
            SleepService.usingMic -> "Microfono attivo"
            mode == SleepTrackingMode.NIGHTSTAND && SleepService.usingAccelerometer ->
                "Microfono non disponibile: misuro i movimenti con l'accelerometro"
            SleepService.usingAccelerometer -> "Accelerometro attivo"
            else -> "Sensore non disponibile: la notte non viene misurata"
        }
        // L'app non ha suoni ambientali né registrazioni: si dice solo cosa succede all'audio.
        tvAudio.visibility = if (SleepService.usingMic) View.VISIBLE else View.GONE
        tvAudio.text = "Audio analizzato sul telefono, nessuna registrazione"

        tvAlarm.text = nextAlarmText()
    }

    private fun nextAlarmText(): String {
        val p = Prefs(this)
        val next = p.alarms.filter { it.enabled }
            .map { it to p.state(it.id) }
            .filter { (_, st) -> st.currentOccurrence > 0 }
            .minByOrNull { (_, st) -> if (st.earlyAt > 0) st.earlyAt else st.currentOccurrence }
            ?: return "Nessuna sveglia attiva"
        val (a, st) = next
        val t = if (st.earlyAt > 0) st.earlyAt else st.currentOccurrence
        val smart = if (a.smartWindowMin > 0) " · intelligente dalle ${Scheduler.fmtTime(t - a.smartWindowMin * 60_000L)}" else ""
        return "Sveglia alle ${Scheduler.fmtTime(t)}$smart"
    }

    private fun duration(ms: Long): String {
        val min = (ms / 60_000).coerceAtLeast(0)
        return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
    }

    private fun endSession() {
        SleepService.stop()
        startActivity(Intent(this, SleepActivity::class.java))
        finish()
    }
}
