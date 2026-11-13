package it.svegliami

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.Executors

/**
 * Opzioni: località per il meteo (GPS, aggiornamento automatico, ricerca per nome), privacy e versione.
 * La modalità del monitoraggio si sceglie nella Home, accanto a «Buonanotte».
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var p: Prefs
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var tvPlace: TextView
    private lateinit var btnGps: Button
    private lateinit var swAutoLoc: CompoundButton
    private lateinit var etCity: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        SystemBars.apply(this)
        p = Prefs(this)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        NavigationUi.bind(this, findViewById<BottomNavigationView>(R.id.bottomNavigation), R.id.navSettings)

        tvPlace = findViewById(R.id.tvPlace)
        btnGps = findViewById(R.id.btnGps)
        swAutoLoc = findViewById(R.id.swAutoLoc)
        etCity = findViewById(R.id.etCity)

        updatePlaceLabel()
        findViewById<Button>(R.id.btnSearch).setOnClickListener { searchCity() }
        btnGps.setOnClickListener {
            if (LocationHelper.hasForeground(this)) locateNow(manual = true)
            else requestPermissions(FOREGROUND_LOC, REQ_LOC_GPS)
        }
        swAutoLoc.isChecked = p.autoLocation
        swAutoLoc.setOnCheckedChangeListener { _, checked ->
            p.autoLocation = checked
            if (checked) {
                if (LocationHelper.hasForeground(this)) locateNow(manual = false)
                else requestPermissions(FOREGROUND_LOC, REQ_LOC_AUTO)
            }
            Scheduler.rescheduleAll(this)
        }

        findViewById<TextView>(R.id.tvSettingsVersion).text =
            "Versione ${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "sconosciuta"}"
    }

    private fun updatePlaceLabel() {
        tvPlace.text = if (p.hasLocation) "📍 ${p.placeName}" else "📍 Nessuna località impostata"
    }

    private fun onLocationChanged() {
        updatePlaceLabel()
        Scheduler.rescheduleAll(this)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            REQ_LOC_GPS ->
                if (ok) locateNow(manual = true)
                else Toast.makeText(this, "Permesso di posizione negato", Toast.LENGTH_SHORT).show()
            REQ_LOC_AUTO ->
                if (ok) locateNow(manual = false)
                else swAutoLoc.isChecked = false
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_LOC_GPS = 2
        private const val REQ_LOC_AUTO = 3
        private val FOREGROUND_LOC = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
