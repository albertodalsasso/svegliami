package it.svegliami

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
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
import android.widget.CompoundButton
import android.widget.EditText
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
    private lateinit var tvPlace: TextView
    private lateinit var btnGps: Button
    private lateinit var swAutoLoc: CompoundButton
    private lateinit var etCity: EditText

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
        tvPlace = findViewById(R.id.tvPlace)
        btnGps = findViewById(R.id.btnGps)
        swAutoLoc = findViewById<MaterialSwitch>(R.id.swAutoLoc)
        etCity = findViewById(R.id.etCity)
        NavigationUi.bind(this, findViewById<BottomNavigationView>(R.id.bottomNavigation), R.id.navHome)

        findViewById<View>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, EditAlarmActivity::class.java))
        }
        findViewById<Button>(R.id.btnPerms).setOnClickListener { issues().firstOrNull()?.fix?.invoke() }

        // Sonno
        btnSleep.setOnClickListener { toggleSleep() }
        findViewById<Button>(R.id.btnSleepHistory).setOnClickListener {
            startActivity(Intent(this, SleepActivity::class.java))
        }
        findViewById<Button>(R.id.btnJournal).setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
        }
        findViewById<Button>(R.id.btnInsights).setOnClickListener {
            startActivity(Intent(this, InsightsActivity::class.java))
        }
        toggleSleepMode.check(when (p.sleepTrackingMode) {
            SleepTrackingMode.NIGHTSTAND -> R.id.btnModeNightstand
            SleepTrackingMode.MATTRESS -> R.id.btnModeBed
            SleepTrackingMode.COMBINED -> R.id.btnModeCombined
        })
        toggleSleepMode.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                p.sleepTrackingMode = when (id) {
                    R.id.btnModeNightstand -> SleepTrackingMode.NIGHTSTAND
                    R.id.btnModeCombined -> SleepTrackingMode.COMBINED
                    else -> SleepTrackingMode.MATTRESS
                }
                renderSleep()
            }
        }

        // Località
        updatePlaceLabel()
        findViewById<Button>(R.id.btnSearch).setOnClickListener { searchCity() }
        btnGps.setOnClickListener {
            if (LocationHelper.hasForeground(this)) locateNow(manual = true)
            else requestPermissions(FOREGROUND_LOC, REQ_LOC_GPS)
        }
        swAutoLoc.isChecked = p.autoLocation
        swAutoLoc.setOnCheckedChangeListener { _, checked ->
            p.autoLocation = checked
            if (checked) ensureAutoLocationPermissions()
            Scheduler.rescheduleAll(this)
            renderAll()
        }
    }

    override fun onResume() {
        super.onResume()
        tvToday.text = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN).format(LocalDate.now())
        renderAll()
        val navigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        if (intent.getBooleanExtra(NavigationUi.EXTRA_OPEN_SETTINGS, false)) {
            navigation.selectedItemId = R.id.navSettings
            scrollToSettings()
            intent.removeExtra(NavigationUi.EXTRA_OPEN_SETTINGS)
        } else {
            navigation.selectedItemId = R.id.navHome
        }
        handler.post(ticker)
        // Con l'aggiornamento automatico attivo, ogni apertura dell'app rileva la posizione fresca.
        if (p.autoLocation && LocationHelper.hasForeground(this)) locateNow(manual = false)
    }

    fun scrollToTop() = findViewById<androidx.core.widget.NestedScrollView>(R.id.mainScroll).smoothScrollTo(0, 0)

    fun scrollToSettings() {
        val scroll = findViewById<androidx.core.widget.NestedScrollView>(R.id.mainScroll)
        scroll.post { scroll.smoothScrollTo(0, findViewById<View>(R.id.tvPlace).top) }
    }

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
        return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
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
                SleepTrackingMode.COMBINED -> "sul materasso vicino al cuscino, con il microfono attivo"
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
            SleepTrackingMode.COMBINED -> "sul materasso vicino al cuscino; il microfono analizzerà i rumori"
            SleepTrackingMode.MATTRESS -> "sul materasso, vicino al cuscino"
        }
        Toast.makeText(this, "Metti il telefono $where. Buonanotte!", Toast.LENGTH_LONG).show()
        btnSleep.postDelayed({ if (!isFinishing) renderSleep() }, 500)
    }

    // ---------- località ----------

    private fun updatePlaceLabel() {
        tvPlace.text = if (p.hasLocation) "📍 ${p.placeName}" else "📍 Nessuna località impostata"
    }

    private fun onLocationChanged() {
        updatePlaceLabel()
        Scheduler.rescheduleAll(this)
        renderAll()
    }

    private fun searchCity() {
        val q = etCity.text.toString()
        if (q.isBlank()) return
        io.execute {
            val result = runCatching { WeatherClient.searchPlaces(q) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onFailure { Toast.makeText(this, "Errore di rete: ${it.message}", Toast.LENGTH_LONG).show() }
                result.onSuccess { places ->
                    if (places.isEmpty()) {
                        Toast.makeText(this, "Nessun risultato", Toast.LENGTH_SHORT).show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Scegli la località")
                            .setItems(places.map { it.name }.toTypedArray()) { _, i ->
                                val pl = places[i]
                                p.placeName = pl.name; p.lat = pl.lat; p.lon = pl.lon
                                p.locationTime = 0
                                swAutoLoc.isChecked = false // località scelta a mano: niente aggiornamento GPS
                                onLocationChanged()
                            }
                            .show()
                    }
                }
            }
        }
    }

    private fun locateNow(manual: Boolean) {
        if (manual) {
            btnGps.isEnabled = false
            btnGps.text = "Ricerca posizione…"
        }
        LocationHelper.getCurrent(this) { loc -> onLocation(loc, manual) }
    }

    private fun onLocation(loc: Location?, manual: Boolean) {
        if (isFinishing || isDestroyed) return
        btnGps.isEnabled = true
        btnGps.text = "📍 Usa la mia posizione (GPS)"
        if (loc == null) {
            if (manual) Toast.makeText(this, "Posizione non disponibile: attiva la localizzazione e riprova", Toast.LENGTH_LONG).show()
            return
        }
        io.execute {
            LocationHelper.apply(this, p, loc, force = manual)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                onLocationChanged()
                if (manual) Toast.makeText(this, "Posizione impostata: ${p.placeName}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensureAutoLocationPermissions() {
        when {
            !LocationHelper.hasForeground(this) -> requestPermissions(FOREGROUND_LOC, REQ_LOC_AUTO)
            else -> locateNow(manual = false)
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
            REQ_LOC_GPS ->
                if (ok) locateNow(manual = true)
                else Toast.makeText(this, "Permesso di posizione negato", Toast.LENGTH_SHORT).show()
            REQ_LOC_AUTO ->
                if (ok) ensureAutoLocationPermissions()
                else swAutoLoc.isChecked = false
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
        private const val REQ_LOC_GPS = 2
        private const val REQ_LOC_AUTO = 3
        private const val REQ_AUDIO = 5
        private const val REQ_MIC = 6
        private val FOREGROUND_LOC = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
