package it.svegliami

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import it.svegliami.storage.SleepJournalEntryEntity
import it.svegliami.storage.SleepRoomRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class JournalActivity : AppCompatActivity() {
    private var sessionId: Long? = null
    private lateinit var form: View
    private lateinit var moodGroup: RadioGroup
    private lateinit var caffeine: MaterialSwitch
    private lateinit var exercise: MaterialSwitch
    private lateinit var stress: SeekBar
    private lateinit var stressLabel: TextView
    private lateinit var mealTime: EditText
    private lateinit var notes: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal)
        SystemBars.apply(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        NavigationUi.bind(this, findViewById<BottomNavigationView>(R.id.bottomNavigation), R.id.navJournal)
        form = findViewById(R.id.journalForm)
        moodGroup = findViewById(R.id.moodGroup)
        caffeine = findViewById(R.id.switchCaffeine)
        exercise = findViewById(R.id.switchExercise)
        stress = findViewById(R.id.seekStress)
        stressLabel = findViewById(R.id.tvStress)
        mealTime = findViewById(R.id.editMealTime)
        notes = findViewById(R.id.editNotes)
        stressLabel.text = stressDescription(stress.progress)
        stress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                stressLabel.text = stressDescription(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        findViewById<View>(R.id.btnSaveJournal).setOnClickListener { save() }

        val latest = SleepStore.load(this).firstOrNull()
        if (latest == null) return
        sessionId = latest.start
        findViewById<TextView>(R.id.tvJournalDate).text =
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
                .format(Instant.ofEpochMilli(latest.end).atZone(ZoneId.systemDefault()))
                .replaceFirstChar { it.uppercase() }
        findViewById<View>(R.id.tvJournalEmpty).visibility = View.GONE
        form.visibility = View.VISIBLE
        SleepRoomRepository.loadJournal(this, latest.start) { entry ->
            runOnUiThread {
                entry ?: return@runOnUiThread
                caffeine.isChecked = entry.caffeine == true
                exercise.isChecked = entry.exercise == true
                entry.stress?.coerceIn(0, 4)?.let { stress.progress = it }
                mealTime.setText(entry.mealTime.orEmpty())
                notes.setText(entry.notes.orEmpty())
                val moodId = when (entry.mood) {
                    "VERY_RESTED" -> R.id.moodVeryRested
                    "RESTED" -> R.id.moodRested
                    "NORMAL" -> R.id.moodNormal
                    "TIRED" -> R.id.moodTired
                    "VERY_TIRED" -> R.id.moodVeryTired
                    else -> View.NO_ID
                }
                if (moodId != View.NO_ID) moodGroup.check(moodId)
            }
        }
    }

    private fun stressDescription(level: Int) = when (level) {
        0 -> "Stress: molto basso"
        1 -> "Stress: basso"
        2 -> "Stress: normale"
        3 -> "Stress: alto"
        else -> "Stress: molto alto"
    }

    private fun save() {
        val id = sessionId ?: return
        val mood = moodGroup.checkedRadioButtonId.takeIf { it != View.NO_ID }
            ?.let { findViewById<View>(it).tag?.toString() }
        val entry = SleepJournalEntryEntity(
            sessionId = id,
            caffeine = caffeine.isChecked,
            exercise = exercise.isChecked,
            stress = stress.progress,
            mealTime = mealTime.text.toString().trim().takeIf { it.isNotEmpty() },
            mood = mood,
            notes = notes.text.toString().trim().takeIf { it.isNotEmpty() },
        )
        findViewById<View>(R.id.btnSaveJournal).isEnabled = false
        SleepRoomRepository.saveJournal(this, entry) { saved ->
            runOnUiThread {
                findViewById<View>(R.id.btnSaveJournal).isEnabled = true
                if (saved) Toast.makeText(this, "Diario salvato", Toast.LENGTH_SHORT).show()
                else findViewById<TextView>(R.id.tvJournalStatus).text = "Sessione non ancora disponibile nel database. Riprova tra poco."
            }
        }
    }
}
