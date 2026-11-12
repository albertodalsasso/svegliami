package it.svegliami

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/** Creazione e modifica di una singola sveglia. */
class EditAlarmActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ID = "alarmId"
        private const val REQ_AUDIO = 5
        private val ADVANCE_OPTIONS = listOf(10, 15, 20, 30, 45, 60, 90)
        private val SMART_OPTIONS = listOf(0, 10, 20, 30, 45)
    }

    private lateinit var p: Prefs
    private lateinit var alarm: Alarm
    private var isNew = true
    private val io = Executors.newSingleThreadExecutor()

    private var hour = 7
    private var minute = 0
    private var ringtoneUri = ""
    private var preview: Ringtone? = null

    private lateinit var etLabel: EditText
    private lateinit var btnTime: Button
    private lateinit var dayBoxes: List<CheckBox>
    private lateinit var tvDaysHint: TextView
    private lateinit var spAdvance: Spinner
    private lateinit var spSmart: Spinner
    private lateinit var sbProb: SeekBar
    private lateinit var tvProb: TextView
    private lateinit var btnRingtone: Button
    private lateinit var btnPreview: Button
    private lateinit var tvStatus: TextView

    private val pickRingtone = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != RESULT_OK) return@registerForActivityResult
        val uri: Uri? = res.data?.let {
            @Suppress("DEPRECATION")
            it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        // "Predefinita" → stringa vuota (segue la suoneria sveglia del telefono)
        ringtoneUri = if (uri == null || RingtoneManager.isDefault(uri)) "" else uri.toString()
        updateRingtoneLabel()
        if (Ringtones.needsStoragePermission(this, ringtoneUri)) {
            requestPermissions(arrayOf(Ringtones.storagePermission()), REQ_AUDIO)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_alarm)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        p = Prefs(this)

        val existing = p.alarm(intent.getIntExtra(EXTRA_ID, -1))
        isNew = existing == null
        alarm = existing?.copy() ?: Alarm(id = p.newAlarmId())
        title = if (isNew) "Nuova sveglia" else "Modifica sveglia"

        etLabel = findViewById(R.id.etLabel)
        btnTime = findViewById(R.id.btnTime)
        dayBoxes = listOf(R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6, R.id.d7).map { findViewById(it) }
        tvDaysHint = findViewById(R.id.tvDaysHint)
        spAdvance = findViewById(R.id.spAdvance)
        spSmart = findViewById(R.id.spSmart)
        sbProb = findViewById(R.id.sbProb)
        tvProb = findViewById(R.id.tvProb)
        btnRingtone = findViewById(R.id.btnRingtone)
        btnPreview = findViewById(R.id.btnPreview)
        tvStatus = findViewById(R.id.tvStatus)

        // Popola il form
        hour = alarm.hour; minute = alarm.minute; ringtoneUri = alarm.ringtoneUri
        etLabel.setText(alarm.label)
        updateTimeLabel()
        dayBoxes.forEachIndexed { i, cb ->
            cb.isChecked = (i + 1) in alarm.days
            cb.setOnCheckedChangeListener { _, _ -> updateDaysHint() }
        }
        updateDaysHint()
        spAdvance.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ADVANCE_OPTIONS.map { "$it minuti" })
        spAdvance.setSelection(ADVANCE_OPTIONS.indexOf(alarm.advanceMin).let { if (it < 0) ADVANCE_OPTIONS.indexOf(30) else it })
        spSmart.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            SMART_OPTIONS.map { if (it == 0) "Disattivata" else "Fino a $it minuti prima" },
        )
        spSmart.setSelection(SMART_OPTIONS.indexOf(alarm.smartWindowMin).coerceAtLeast(0))
        sbProb.max = 9
        sbProb.progress = (alarm.probThreshold / 10 - 1).coerceIn(0, 9)
        updateProbLabel()
        updateRingtoneLabel()
        tvStatus.text = if (isNew) "" else p.state(alarm.id).lastStatus.let { if (it.isEmpty()) "" else "Ultimo controllo: $it" }

        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> hour = h; minute = m; updateTimeLabel() }, hour, minute, true).show()
        }
        sbProb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) = updateProbLabel()
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        btnRingtone.setOnClickListener { openRingtonePicker() }
        btnPreview.setOnClickListener { togglePreview() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { testNow() }
        findViewById<Button>(R.id.btnDelete).apply {
            visibility = if (isNew) View.GONE else View.VISIBLE
            setOnClickListener { confirmDelete() }
        }
    }

    private fun probValue() = (sbProb.progress + 1) * 10
    private fun updateTimeLabel() { btnTime.text = "%02d:%02d".format(hour, minute) }
    private fun updateProbLabel() { tvProb.text = "Anticipa anche se la probabilità di pioggia è ≥ ${probValue()}%" }
    private fun updateRingtoneLabel() { btnRingtone.text = "🔔 ${Ringtones.title(this, ringtoneUri)}" }
    private fun updateDaysHint() {
        tvDaysHint.text = if (dayBoxes.none { it.isChecked }) "Nessun giorno selezionato: suonerà una volta sola" else ""
    }

    /** Sveglia con i valori attuali del form. */
    private fun formAlarm() = alarm.copy(
        hour = hour,
        minute = minute,
        days = dayBoxes.mapIndexedNotNull { i, cb -> if (cb.isChecked) i + 1 else null }.toSet(),
        advanceMin = ADVANCE_OPTIONS[spAdvance.selectedItemPosition],
        probThreshold = probValue(),
        ringtoneUri = ringtoneUri,
        label = etLabel.text.toString().trim(),
        smartWindowMin = SMART_OPTIONS[spSmart.selectedItemPosition],
    )

    private fun save() {
        val a = formAlarm().copy(enabled = true)
        p.saveAlarm(a)
        p.state(a.id).apply { handledOccurrence = 0; lastStatus = "" }
        Scheduler.reschedule(this, a.id)
        val occ = p.state(a.id).currentOccurrence
        if (occ > 0) {
            val mins = (occ - System.currentTimeMillis() + 59_999) / 60_000
            Toast.makeText(this, "Suonerà fra ${mins / 60} h e ${mins % 60} min", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage("Eliminare la sveglia delle ${alarm.timeText}?")
            .setPositiveButton("Elimina") { _, _ ->
                Scheduler.cancelAll(this, alarm.id)
                p.deleteAlarm(alarm.id)
                finish()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /** Mostra subito cosa deciderebbe l'app per la prossima occorrenza di questa sveglia. */
    private fun testNow() {
        if (!p.hasLocation) {
            Toast.makeText(this, "Imposta prima la località nella schermata principale", Toast.LENGTH_LONG).show(); return
        }
        val a = formAlarm()
        val occ = Scheduler.nextOccurrence(a, 0, System.currentTimeMillis()) ?: return
        io.execute {
            val result = runCatching { WeatherCheck.decide(a, WeatherCheck.fetch(p), occ) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val early = occ - a.advanceMin * 60_000L
                val text = result.fold(
                    { d ->
                        "Sveglia: ${Scheduler.fmtDayTime(occ)}\n" +
                            "Finestra controllata: ${Scheduler.fmtTime(early)}–${Scheduler.fmtTime(occ)}\n\n" +
                            "Previsione attuale: ${d.reason}\n\n" +
                            if (d.rainy) "→ Se resta così, suonerà alle ${Scheduler.fmtTime(early)}"
                            else "→ Se resta così, suonerà alle ${Scheduler.fmtTime(occ)}"
                    },
                    { "Errore nel recupero delle previsioni: ${it.message}" },
                )
                AlertDialog.Builder(this).setTitle("Meteo per questa sveglia").setMessage(text)
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    // ---------- suoneria ----------

    private fun openRingtonePicker() {
        stopPreview()
        val current = ringtoneUri.takeIf { it.isNotEmpty() }?.let(Uri::parse)
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
        preview = Ringtones.preview(this, ringtoneUri)
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
