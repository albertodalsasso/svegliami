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
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/** Elenco delle sveglie e località per il meteo. */
class MainActivity : AppCompatActivity() {
    private lateinit var p: Prefs
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var alarmList: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var etCity: EditText
    private lateinit var tvPlace: TextView
    private lateinit var btnGps: Button
    private lateinit var cbAutoLoc: CheckBox
    private lateinit var tvPerms: TextView
    private lateinit var btnPerms: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        p = Prefs(this)
        AlarmService.ensureChannel(this)

        alarmList = findViewById(R.id.alarmList)
        tvEmpty = findViewById(R.id.tvEmpty)
        etCity = findViewById(R.id.etCity)
        tvPlace = findViewById(R.id.tvPlace)
        btnGps = findViewById(R.id.btnGps)
        cbAutoLoc = findViewById(R.id.cbAutoLoc)
        tvPerms = findViewById(R.id.tvPerms)
        btnPerms = findViewById(R.id.btnPerms)

        updatePlaceLabel()
        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, EditAlarmActivity::class.java))
        }
        findViewById<Button>(R.id.btnSearch).setOnClickListener { searchCity() }
        btnPerms.setOnClickListener { issues().firstOrNull()?.fix?.invoke() }
        btnGps.setOnClickListener {
            if (LocationHelper.hasForeground(this)) locateNow(manual = true)
            else requestPermissions(FOREGROUND_LOC, REQ_LOC_GPS)
        }
        cbAutoLoc.isChecked = p.autoLocation
        cbAutoLoc.setOnCheckedChangeListener { _, checked ->
            p.autoLocation = checked
            if (checked) ensureAutoLocationPermissions()
            Scheduler.rescheduleAll(this)
            renderAlarms()
            refreshPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        renderAlarms()
        refreshPermissions()
        // Con l'aggiornamento automatico attivo, ogni apertura dell'app rileva la posizione fresca.
        if (p.autoLocation && LocationHelper.hasForeground(this)) locateNow(manual = false)
    }

    // ---------- elenco sveglie ----------

    private fun renderAlarms() {
        alarmList.removeAllViews()
        val alarms = p.alarms
        tvEmpty.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        for (a in alarms) {
            val v = inflater.inflate(R.layout.item_alarm, alarmList, false)
            v.findViewById<TextView>(R.id.tvTime).text = a.timeText
            v.findViewById<TextView>(R.id.tvLabel).apply {
                text = a.label
                visibility = if (a.label.isBlank()) View.GONE else View.VISIBLE
            }
            v.findViewById<TextView>(R.id.tvDays).text = a.daysText
            v.findViewById<TextView>(R.id.tvInfo).text = statusLine(a)
            v.alpha = if (a.enabled) 1f else 0.55f
            v.findViewById<SwitchCompat>(R.id.swEnabled).apply {
                isChecked = a.enabled
                setOnCheckedChangeListener { _, checked ->
                    a.enabled = checked
                    if (checked) p.state(a.id).handledOccurrence = 0
                    p.saveAlarm(a)
                    Scheduler.reschedule(this@MainActivity, a.id)
                    alarmList.post { renderAlarms() }
                }
            }
            v.setOnClickListener {
                startActivity(Intent(this, EditAlarmActivity::class.java).putExtra(EditAlarmActivity.EXTRA_ID, a.id))
            }
            alarmList.addView(v)
        }
    }

    private fun statusLine(a: Alarm): String {
        val st = p.state(a.id)
        val occ = st.currentOccurrence
        if (!a.enabled || occ == 0L) return "Disattivata"
        val sb = StringBuilder("Prossima: ${Scheduler.fmtDayTime(occ)}")
        when {
            st.earlyAt > 0 -> sb.append("\n☔ Anticipata alle ${Scheduler.fmtTime(st.earlyAt)} per pioggia")
            p.hasLocation || p.autoLocation ->
                sb.append("\nSe piove: alle ${Scheduler.fmtTime(occ - a.advanceMin * 60_000L)}")
            else -> sb.append("\nMeteo non controllato: imposta la località")
        }
        return sb.toString()
    }

    // ---------- località ----------

    private fun updatePlaceLabel() {
        tvPlace.text = if (p.hasLocation) "📍 ${p.placeName}" else "Nessuna località impostata"
    }

    private fun onLocationChanged() {
        updatePlaceLabel()
        Scheduler.rescheduleAll(this)
        renderAlarms()
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
                                cbAutoLoc.isChecked = false // località scelta a mano: niente aggiornamento GPS
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
            !LocationHelper.hasBackground(this) -> askBackgroundLocation()
            else -> locateNow(manual = false)
        }
    }

    private fun askBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        AlertDialog.Builder(this)
            .setTitle("Posizione anche in background")
            .setMessage(
                "Per aggiornare la località prima del controllo meteo notturno, scegli " +
                    "«Consenti sempre» nella schermata dei permessi di posizione.",
            )
            .setPositiveButton("Continua") { _, _ ->
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_LOC_BG)
            }
            .setNegativeButton("Annulla", null)
            .show()
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
        if (p.autoLocation && !LocationHelper.hasBackground(this)) {
            list += Issue("Posizione in background non consentita (aggiornamento automatico)") { askBackgroundLocation() }
        }
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            list += Issue("Ottimizzazione batteria attiva (il controllo meteo notturno potrebbe non avere rete)") {
                @Suppress("BatteryLife")
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
        return list
    }

    private fun refreshPermissions() {
        val list = issues()
        val visible = if (list.isEmpty()) View.GONE else View.VISIBLE
        tvPerms.visibility = visible
        btnPerms.visibility = visible
        tvPerms.text = list.joinToString("\n") { "⚠ ${it.label}" }
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
                else cbAutoLoc.isChecked = false
            REQ_LOC_BG -> if (ok) locateNow(manual = false)
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
        private const val REQ_LOC_BG = 4
        private const val REQ_AUDIO = 5
        private val FOREGROUND_LOC = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
