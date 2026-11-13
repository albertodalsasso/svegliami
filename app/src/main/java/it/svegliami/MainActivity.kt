package it.svegliami

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/** Schermata principale: prossima sveglia, elenco sveglie, sonno e località. */
class MainActivity : AppCompatActivity() {
    private lateinit var p: Prefs
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            renderHero()
            handler.postDelayed(this, 30_000)
        }
    }

    private lateinit var tvToday: TextView
    private lateinit var cardPerms: MaterialCardView
    private lateinit var tvPerms: TextView
    private lateinit var hero: View
    private lateinit var tvHeroTime: TextView
    private lateinit var tvHeroWhen: TextView
    private lateinit var tvHeroRain: TextView
    private lateinit var tvHeroSmart: TextView
    private lateinit var alarmList: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var tvSleep: TextView
    private lateinit var btnSleep: Button
    private lateinit var toggleSleepMode: MaterialButtonToggleGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SystemBars.apply(this)
        p = Prefs(this)
        AlarmService.ensureChannel(this)

        tvToday = findViewById(R.id.tvToday)
        cardPerms = findViewById(R.id.cardPerms)
        tvPerms = findViewById(R.id.tvPerms)
        hero = findViewById(R.id.hero)
        tvHeroTime = findViewById(R.id.tvHeroTime)
        tvHeroWhen = findViewById(R.id.tvHeroWhen)
        tvHeroRain = findViewById(R.id.tvHeroRain)
        tvHeroSmart = findViewById(R.id.tvHeroSmart)
        alarmList = findViewById(R.id.alarmList)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvSleep = findViewById(R.id.tvSleep)
        btnSleep = findViewById(R.id.btnSleep)
        toggleSleepMode = findViewById(R.id.toggleSleepMode)
        NavigationUi.bind(this, findViewById<BottomNavigationView>(R.id.bottomNavigation), R.id.navHome)

        findViewById<View>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, EditAlarmActivity::class.java))
        }
        findViewById<Button>(R.id.btnPerms).setOnClickListener { issues().firstOrNull()?.fix?.invoke() }

        // Sonno
        btnSleep.setOnClickListener { toggleSleep() }
        toggleSleepMode.check(when (p.sleepTrackingMode) {
            SleepTrackingMode.NIGHTSTAND -> R.id.btnModeNightstand
            SleepTrackingMode.MATTRESS -> R.id.btnModeBed
        })
        toggleSleepMode.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                p.sleepTrackingMode = when (id) {
                    R.id.btnModeNightstand -> SleepTrackingMode.NIGHTSTAND
                    else -> SleepTrackingMode.MATTRESS
                }
                renderSleep()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tvToday.text = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN).format(LocalDate.now())
        renderAll()
        val navigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        // markSelected non fa scattare i listener (che riporterebbero in cima la pagina a ogni ritorno).
        NavigationUi.markSelected(navigation, R.id.navHome)
        handler.post(ticker)
        // Con l'aggiornamento automatico attivo, ogni apertura dell'app rileva la posizione fresca.
        if (p.autoLocation && LocationHelper.hasForeground(this)) refreshLocation()
    }

    fun scrollToTop() = findViewById<androidx.core.widget.NestedScrollView>(R.id.mainScroll).smoothScrollTo(0, 0)

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun renderAll() {
        renderHero()
        renderAlarms()
        renderSleep()
        refreshPermissions()
    }

    // ---------- prossima sveglia ----------

    /** Orario effettivo della prossima suoneria di una sveglia (anticipata se piove). */
    private fun target(st: AlarmState) = if (st.earlyAt > 0) st.earlyAt else st.currentOccurrence

    private fun renderHero() {
        val now = System.currentTimeMillis()
        val next = p.alarms.filter { it.enabled }
            .map { it to p.state(it.id) }
            .filter { (_, st) -> st.currentOccurrence > 0 }
            .minByOrNull { (_, st) -> target(st) }

        if (next == null) {
            hero.setBackgroundResource(R.drawable.bg_hero_night)
            tvHeroTime.text = "--:--"
            tvHeroWhen.text = "Nessuna sveglia attiva"
            tvHeroRain.visibility = View.GONE
            tvHeroSmart.visibility = View.GONE
            return
        }
        val (a, st) = next
        val t = target(st)
        val rainy = st.earlyAt > 0
        hero.setBackgroundResource(if (rainy) R.drawable.bg_hero_rain else R.drawable.bg_hero_night)
        tvHeroTime.text = Scheduler.fmtTime(t)
        tvHeroWhen.text = "${dayWord(t)} · tra ${duration(t - now)}" + if (a.label.isNotBlank()) " · ${a.label}" else ""

        tvHeroRain.visibility = View.VISIBLE
        tvHeroRain.text = when {
            rainy -> "☔  Anticipata per pioggia (era alle ${Scheduler.fmtTime(st.currentOccurrence)})"
            p.hasLocation || p.autoLocation -> "☔  Se piove: alle ${Scheduler.fmtTime(st.currentOccurrence - a.advanceMin * 60_000L)}"
            else -> "☁  Meteo non controllato: imposta la località"
        }
        tvHeroSmart.visibility = if (a.smartWindowMin > 0) View.VISIBLE else View.GONE
        tvHeroSmart.text = "🌙  Sveglia intelligente dalle ${Scheduler.fmtTime(t - a.smartWindowMin * 60_000L)}"
    }

    private fun dayWord(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (d) {
            today -> "Oggi"
            today.plusDays(1) -> "Domani"
            else -> DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN).format(d).replaceFirstChar { it.uppercase() }
        }
    }

    private fun duration(ms: Long): String {
        val min = ((ms + 59_999) / 60_000).coerceAtLeast(0)
        return when {
            min >= 24 * 60 -> "${min / (24 * 60)} g ${min % (24 * 60) / 60} h"
            min >= 60 -> "${min / 60} h ${min % 60} min"
            else -> "$min min"
        }
    }

    // ---------- elenco sveglie ----------

    private fun renderAlarms() {
        alarmList.removeAllViews()
        val alarms = p.alarms
        tvEmpty.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        for (a in alarms) {
            val v = inflater.inflate(R.layout.item_alarm, alarmList, false)
            val st = p.state(a.id)
            v.findViewById<TextView>(R.id.tvTime).text = a.timeText
            v.findViewById<TextView>(R.id.tvLabel).text = a.label.ifBlank { "Sveglia" }
            fillDayDots(v.findViewById(R.id.dayRow), a)

            val occ = st.currentOccurrence
            v.findViewById<TextView>(R.id.tvInfo).text = when {
                !a.enabled -> "Disattivata"
                occ == 0L -> "Nessuna occorrenza programmata"
                else -> "Prossima: ${Scheduler.fmtDayTime(occ)}"
            }
            val active = a.enabled && occ > 0
            v.findViewById<TextView>(R.id.badgeRain).apply {
                when {
                    !active || !(p.hasLocation || p.autoLocation) -> visibility = View.GONE
                    st.earlyAt > 0 -> {
                        visibility = View.VISIBLE
                        text = "☔ Anticipata alle ${Scheduler.fmtTime(st.earlyAt)}"
                        setBackgroundResource(R.drawable.bg_badge_early)
                        setTextColor(getColor(R.color.on_primary_container))
                    }
                    else -> {
                        visibility = View.VISIBLE
                        text = "☔ Se piove: ${Scheduler.fmtTime(occ - a.advanceMin * 60_000L)}"
                        setBackgroundResource(R.drawable.bg_badge_rain)
                        setTextColor(getColor(R.color.on_secondary_container))
                    }
                }
            }
            v.findViewById<TextView>(R.id.badgeSmart).apply {
                visibility = if (active && a.smartWindowMin > 0) View.VISIBLE else View.GONE
                text = "🌙 Intelligente · ${a.smartWindowMin} min"
            }
            v.alpha = if (a.enabled) 1f else 0.5f
            v.findViewById<MaterialSwitch>(R.id.swEnabled).apply {
                isChecked = a.enabled
                setOnCheckedChangeListener { _, checked ->
                    a.enabled = checked
                    if (checked) p.state(a.id).handledOccurrence = 0
                    p.saveAlarm(a)
                    Scheduler.reschedule(this@MainActivity, a.id)
                    alarmList.post { renderAll() }
                }
            }
            v.setOnClickListener {
                startActivity(Intent(this, EditAlarmActivity::class.java).putExtra(EditAlarmActivity.EXTRA_ID, a.id))
            }
            alarmList.addView(v)
        }
    }

    /** Sette pallini L M M G V S D, pieni per i giorni attivi. */
    private fun fillDayDots(row: LinearLayout, a: Alarm) {
        row.removeAllViews()
        val size = (26 * resources.displayMetrics.density).toInt()
        val gap = (6 * resources.displayMetrics.density).toInt()
        listOf("L", "M", "M", "G", "V", "S", "D").forEachIndexed { i, letter ->
            val on = (i + 1) in a.days
            row.addView(TextView(this).apply {
                text = letter
                gravity = Gravity.CENTER
                textSize = 12f
                setBackgroundResource(if (on) R.drawable.bg_day_on else R.drawable.bg_day_off)
                setTextColor(getColor(if (on) R.color.on_primary else R.color.on_surface_variant))
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = gap }
            })
        }
        if (a.days.isEmpty()) {
            row.addView(TextView(this).apply {
                text = "Una volta"
                setTextColor(getColor(R.color.on_surface_variant))
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, size)
            })
        }
    }

    // ---------- sonno ----------

    private fun renderSleep() {
        val running = SleepService.isRunning
        for (i in 0 until toggleSleepMode.childCount) toggleSleepMode.getChildAt(i).isEnabled = !running
        if (running) {
            val how = if (SleepService.usingMic) "col microfono" else "con l'accelerometro"
            tvSleep.text = "🌙 Monitoraggio $how attivo dalle ${Scheduler.fmtTime(SleepService.startedAt)}.\n" +
                "Si ferma da solo quando suona la sveglia."
            btnSleep.text = "Ferma il monitoraggio"
        } else {
            val where = when (p.sleepTrackingMode) {
                SleepTrackingMode.NIGHTSTAND -> "sul comodino, con il microfono verso il letto"
                SleepTrackingMode.MATTRESS -> "sul materasso, vicino al cuscino"
            }
            tvSleep.text = if (p.alarms.none { it.enabled && it.smartWindowMin > 0 }) {
                "Registra la tua notte. Attiva la sveglia intelligente in una sveglia per svegliarti nel sonno leggero."
            } else {
                "Prima di dormire premi «Buonanotte» e metti il telefono $where (meglio in carica)."
            }
            btnSleep.text = "🌙  Buonanotte"
        }
    }

    private fun toggleSleep() {
        when {
            SleepService.isRunning -> {
                SleepService.stop()
                btnSleep.postDelayed({ if (!isFinishing) renderSleep() }, 500)
            }
            p.sleepTrackingMode.usesMicrophone && !SleepService.hasMicPermission(this) ->
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            else -> startSleep()
        }
    }

    private fun startSleep() {
        SleepService.start(this)
        val where = when (p.sleepTrackingMode) {
            SleepTrackingMode.NIGHTSTAND -> "sul comodino, vicino al letto"
            SleepTrackingMode.MATTRESS -> "sul materasso, vicino al cuscino"
        }
        Toast.makeText(this, "Metti il telefono $where. Buonanotte!", Toast.LENGTH_LONG).show()
        btnSleep.postDelayed({ if (!isFinishing) renderSleep() }, 500)
    }

    // ---------- località ----------

    /** Aggiornamento automatico a ogni apertura (si imposta in Opzioni): in silenzio, senza messaggi. */
    private fun refreshLocation() {
        LocationHelper.getCurrent(this) { loc ->
            if (loc == null || isFinishing || isDestroyed) return@getCurrent
            io.execute {
                LocationHelper.apply(this, p, loc)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Scheduler.rescheduleAll(this)
                    renderAll()
                }
            }
        }
    }

    // ---------- permessi ----------

    private data class Issue(val label: String, val fix: () -> Unit)

    private fun issues(): List<Issue> {
        val list = mutableListOf<Issue>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            list += Issue("Notifiche non consentite") { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF) }
        }
        if (!Scheduler.canExact(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Issue("Sveglie esatte non consentite") {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            list += Issue("Schermata a tutto schermo non consentita") {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
        if (Ringtones.anyNeedsStoragePermission(this)) {
            list += Issue("Accesso ai file audio non consentito (una suoneria scelta non può suonare)") {
                requestPermissions(arrayOf(Ringtones.storagePermission()), REQ_AUDIO)
            }
        }
        if (!p.hasLocation && !p.autoLocation) {
            list += Issue("Località per il meteo non impostata: la sveglia non si anticipa se piove") {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            list += Issue("Ottimizzazione batteria attiva: il controllo meteo notturno potrebbe non avere rete") {
                @Suppress("BatteryLife")
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
        return list
    }

    private fun refreshPermissions() {
        val list = issues()
        cardPerms.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        tvPerms.text = list.joinToString("\n") { "⚠  ${it.label}" }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            REQ_MIC ->
                if (ok) startSleep()
                else Toast.makeText(this, "Senza microfono usa la modalità «Materasso»", Toast.LENGTH_LONG).show()
        }
        refreshPermissions()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_NOTIF = 1
        private const val REQ_AUDIO = 5
        private const val REQ_MIC = 6
    }
}
