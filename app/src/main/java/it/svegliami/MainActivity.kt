package it.svegliami

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.location.Location
import android.media.Ringtone
import android.media.RingtoneManager
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var p: Prefs
    private val io = Executors.newSingleThreadExecutor()
    private val advanceOptions = listOf(10, 15, 20, 30, 45, 60, 90)

    private var hour = 7
    private var minute = 0

    private lateinit var swEnabled: SwitchCompat
    private lateinit var btnTime: Button
    private lateinit var dayBoxes: List<CheckBox>
    private lateinit var spAdvance: Spinner
    private lateinit var sbProb: SeekBar
    private lateinit var tvProb: TextView
    private lateinit var etCity: EditText
    private lateinit var tvPlace: TextView
    private lateinit var btnGps: Button
    private lateinit var cbAutoLoc: CheckBox
    private lateinit var btnRingtone: Button
    private lateinit var btnPreview: Button
    private var preview: Ringtone? = null

    private val pickRingtone = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != RESULT_OK) return@registerForActivityResult
        val uri: Uri? = res.data?.let {
            @Suppress("DEPRECATION")
            it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        // "Predefinita" o nessuna scelta → stringa vuota (segue la suoneria sveglia del telefono)
        p.ringtoneUri = if (uri == null || RingtoneManager.isDefault(uri)) "" else uri.toString()
        updateRingtoneLabel()
        if (Ringtones.needsStoragePermission(this)) requestPermissions(arrayOf(Ringtones.storagePermission()), REQ_AUDIO)
        refreshPermissions()
    }
    private lateinit var tvStatus: TextView
    private lateinit var tvPerms: TextView
    private lateinit var btnPerms: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        p = Prefs(this)
        AlarmService.ensureChannel(this)

        swEnabled = findViewById(R.id.swEnabled)
        btnTime = findViewById(R.id.btnTime)
        dayBoxes = listOf(R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6, R.id.d7).map { findViewById(it) }
        spAdvance = findViewById(R.id.spAdvance)
        sbProb = findViewById(R.id.sbProb)
        tvProb = findViewById(R.id.tvProb)
        etCity = findViewById(R.id.etCity)
        tvPlace = findViewById(R.id.tvPlace)
        btnGps = findViewById(R.id.btnGps)
        cbAutoLoc = findViewById(R.id.cbAutoLoc)
        btnRingtone = findViewById(R.id.btnRingtone)
        btnPreview = findViewById(R.id.btnPreview)
        updateRingtoneLabel()
        btnRingtone.setOnClickListener { openRingtonePicker() }
        btnPreview.setOnClickListener { togglePreview() }
        tvStatus = findViewById(R.id.tvStatus)
        tvPerms = findViewById(R.id.tvPerms)
        btnPerms = findViewById(R.id.btnPerms)

        // Carica configurazione
        hour = p.hour; minute = p.minute
        swEnabled.isChecked = p.enabled
        updateTimeLabel()
        val days = p.days
        dayBoxes.forEachIndexed { i, cb -> cb.isChecked = (i + 1) in days }
        spAdvance.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, advanceOptions.map { "$it minuti" })
        spAdvance.setSelection(advanceOptions.indexOf(p.advanceMin).coerceAtLeast(0))
        sbProb.max = 9
        sbProb.progress = (p.probThreshold / 10 - 1).coerceIn(0, 9)
        updateProbLabel()
        updatePlaceLabel()

        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> hour = h; minute = m; updateTimeLabel() }, hour, minute, true).show()
        }
        sbProb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) = updateProbLabel()
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        findViewById<Button>(R.id.btnSearch).setOnClickListener { searchCity() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { testNow() }
        btnPerms.setOnClickListener { fixNextPermission() }

        btnGps.setOnClickListener {
            if (LocationHelper.hasForeground(this)) locateNow(manual = true)
            else requestPermissions(FOREGROUND_LOC, REQ_LOC_GPS)
        }
        cbAutoLoc.isChecked = p.autoLocation
        cbAutoLoc.setOnCheckedChangeListener { _, checked ->
            p.autoLocation = checked
            if (checked) ensureAutoLocationPermissions()
            Scheduler.reschedule(this)
            refreshStatus()
            refreshPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshPermissions()
        // Con l'aggiornamento automatico attivo, ogni apertura dell'app rileva la posizione fresca.
        if (p.autoLocation && LocationHelper.hasForeground(this)) locateNow(manual = false)
    }

    private fun probValue() = (sbProb.progress + 1) * 10
    private fun updateTimeLabel() { btnTime.text = "%02d:%02d".format(hour, minute) }
    private fun updateProbLabel() { tvProb.text = "Anticipa anche se la probabilità di pioggia è ≥ ${probValue()}%" }
    private fun updatePlaceLabel() {
        tvPlace.text = if (p.hasLocation) "📍 ${p.placeName}" else "Nessuna località impostata"
    }

    // ---------- suoneria ----------

    private fun updateRingtoneLabel() { btnRingtone.text = "🔔 ${Ringtones.title(this)}" }

    private fun openRingtonePicker() {
        stopPreview()
        val current = p.ringtoneUri.takeIf { it.isNotEmpty() }?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Suoneria della sveglia")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
        runCatching { pickRingtone.launch(intent) }
            .onFailure { Toast.makeText(this, "Selettore suonerie non disponibile", Toast.LENGTH_SHORT).show() }
    }

    private fun togglePreview() {
        if (preview?.isPlaying == true) { stopPreview(); return }
        preview = Ringtones.preview(this)
        if (preview == null) Toast.makeText(this, "Impossibile riprodurre la suoneria", Toast.LENGTH_SHORT).show()
        else btnPreview.text = "■ Stop"
    }

    private fun stopPreview() {
        preview?.stop()
        preview = null
        btnPreview.text = "▶ Prova"
    }

    override fun onPause() {
        stopPreview()
        super.onPause()
    }

    // ---------- posizione GPS ----------

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
                updatePlaceLabel()
                Scheduler.reschedule(this)
                refreshStatus()
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

    private fun save() {
        p.hour = hour
        p.minute = minute
        p.days = dayBoxes.mapIndexedNotNull { i, cb -> if (cb.isChecked) i + 1 else null }.toSet()
        p.advanceMin = advanceOptions[spAdvance.selectedItemPosition]
        p.probThreshold = probValue()
        p.enabled = swEnabled.isChecked
        p.handledOccurrence = 0
        p.lastStatus = ""
        Scheduler.reschedule(this)
        refreshStatus()
        val msg = when {
            !p.enabled -> "Sveglia disattivata"
            p.days.isEmpty() -> "Seleziona almeno un giorno"
            !p.hasLocation -> "Salvata, ma senza località il meteo non viene controllato"
            else -> "Sveglia salvata"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun searchCity() {
        val q = etCity.text.toString()
        if (q.isBlank()) return
        io.execute {
            val result = runCatching { WeatherClient.searchPlaces(q) }
            runOnUiThread {
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
                                updatePlaceLabel()
                                Scheduler.reschedule(this)
                                refreshStatus()
                            }
                            .show()
                    }
                }
            }
        }
    }

    /** Mostra subito cosa deciderebbe l'app per la prossima sveglia. */
    private fun testNow() {
        if (!p.hasLocation) {
            Toast.makeText(this, "Imposta prima una località", Toast.LENGTH_SHORT).show(); return
        }
        val occ = p.currentOccurrence.takeIf { it > 0 } ?: Scheduler.nextOccurrence(p, System.currentTimeMillis())
        if (occ == null) {
            Toast.makeText(this, "Nessuna sveglia programmata (salva prima)", Toast.LENGTH_SHORT).show(); return
        }
        io.execute {
            val result = runCatching { WeatherCheck.decide(p, WeatherCheck.fetch(p), occ) }
            runOnUiThread {
                val early = occ - p.advanceMin * 60_000L
                val text = result.fold(
                    { d ->
                        "Sveglia: ${Scheduler.fmtDayTime(occ)}\n" +
                            "Finestra controllata: ${Scheduler.fmtTime(early)}–${Scheduler.fmtTime(occ)}\n\n" +
                            "Previsione attuale: ${d.reason}\n\n" +
                            if (d.rainy) "→ Se resta così, ti sveglierò alle ${Scheduler.fmtTime(early)}"
                            else "→ Se resta così, suonerà alle ${Scheduler.fmtTime(occ)}"
                    },
                    { "Errore nel recupero delle previsioni: ${it.message}" },
                )
                AlertDialog.Builder(this).setTitle("Meteo per la prossima sveglia").setMessage(text)
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    private fun refreshStatus() {
        val sb = StringBuilder()
        val occ = p.currentOccurrence
        if (!p.enabled || occ == 0L) {
            sb.append("Nessuna sveglia programmata.")
        } else {
            sb.append("Prossima sveglia: ${Scheduler.fmtDayTime(occ)}\n")
            if (p.hasLocation) {
                val early = occ - p.advanceMin * 60_000L
                val check = early - Scheduler.CHECK_LEAD_MIN * 60_000L
                if (p.earlyAt > 0) sb.append("☔ Anticipata alle ${Scheduler.fmtTime(p.earlyAt)} per pioggia\n")
                else sb.append("Controllo meteo alle ${Scheduler.fmtTime(check)}: se piove suonerà alle ${Scheduler.fmtTime(early)}\n")
            }
        }
        if (p.lastStatus.isNotEmpty()) sb.append("\nUltimo controllo: ${p.lastStatus}")
        tvStatus.text = sb.toString().trim()
    }

    // ---------- permessi ----------

    private data class Issue(val label: String, val fix: () -> Unit)

    private fun issues(): List<Issue> {
        val list = mutableListOf<Issue>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            list += Issue("Notifiche non consentite") { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
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
        if (Ringtones.needsStoragePermission(this)) {
            list += Issue("Accesso ai file audio non consentito (la suoneria scelta non può suonare)") {
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

    private fun fixNextPermission() {
        issues().firstOrNull()?.fix?.invoke()
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

    companion object {
        private const val REQ_LOC_GPS = 2
        private const val REQ_LOC_AUTO = 3
        private const val REQ_LOC_BG = 4
        private const val REQ_AUDIO = 5
        private val FOREGROUND_LOC = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
