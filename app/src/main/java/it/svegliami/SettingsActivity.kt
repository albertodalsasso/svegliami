package it.svegliami

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup

/** Impostazioni disponibili oggi, senza esporre controlli che non hanno ancora un effetto reale. */
class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var modeGroup: MaterialButtonToggleGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        SystemBars.apply(this)
        prefs = Prefs(this)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        NavigationUi.bind(this, findViewById<BottomNavigationView>(R.id.bottomNavigation), R.id.navSettings)

        modeGroup = findViewById(R.id.settingsSleepMode)
        showTrackingMode(prefs.sleepTrackingMode)
        modeGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (!checked) return@addOnButtonCheckedListener
            prefs.sleepTrackingMode = if (checkedId == R.id.settingsModeNightstand) {
                SleepTrackingMode.NIGHTSTAND
            } else {
                SleepTrackingMode.MATTRESS
            }
            renderTrackingDescription()
        }

        findViewById<View>(R.id.btnOpenLocationSettings).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(NavigationUi.EXTRA_OPEN_SETTINGS, true),
            )
        }
        renderLocation()
        renderTrackingDescription()
        findViewById<TextView>(R.id.tvSettingsVersion).text =
            "Versione ${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "sconosciuta"}"
    }

    private fun showTrackingMode(mode: SleepTrackingMode) {
        modeGroup.check(
            if (mode == SleepTrackingMode.NIGHTSTAND) R.id.settingsModeNightstand
            else R.id.settingsModeMattress,
        )
    }

    private fun renderTrackingDescription() {
        findViewById<TextView>(R.id.tvTrackingDescription).text = when (prefs.sleepTrackingMode) {
            SleepTrackingMode.NIGHTSTAND -> "Usa il microfono per rilevare l'attività sonora. L'audio non viene caricato."
            SleepTrackingMode.MATTRESS, SleepTrackingMode.COMBINED -> "Usa l'accelerometro per rilevare i movimenti. Le fasi del sonno restano stime."
        }
    }

    private fun renderLocation() {
        findViewById<TextView>(R.id.tvSettingsLocation).text =
            if (prefs.hasLocation) "Località meteo: ${prefs.placeName.ifBlank { "Posizione salvata" }}"
            else "Nessuna località meteo configurata"
    }
}
