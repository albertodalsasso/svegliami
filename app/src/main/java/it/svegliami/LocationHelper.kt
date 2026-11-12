package it.svegliami

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

/** Posizione tramite il LocationManager di Android (GPS, con la rete come ripiego). */
object LocationHelper {
    private const val GPS_TIMEOUT_MS = 20_000L
    /** Sotto questa distanza non si rifà la ricerca del nome della località. */
    private const val SAME_PLACE_METERS = 1500f

    private fun granted(ctx: Context, perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    fun hasFine(ctx: Context) = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasForeground(ctx: Context) = hasFine(ctx) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun hasBackground(ctx: Context) =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) hasForeground(ctx)
        else granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun lm(ctx: Context) = ctx.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): Location? {
        if (!hasForeground(ctx)) return null
        val lm = lm(ctx)
        return lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /**
     * Rileva la posizione attuale. Attende il GPS fino a 20 s; se nel frattempo arriva solo la
     * posizione da rete (o nessuna), usa la migliore disponibile. Il callback gira sul main thread.
     */
    @SuppressLint("MissingPermission")
    fun getCurrent(ctx: Context, callback: (Location?) -> Unit) {
        val lm = lm(ctx)
        val handler = Handler(Looper.getMainLooper())
        val providers = buildList {
            if (hasFine(ctx) && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (hasForeground(ctx) && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) {
            callback(lastKnown(ctx)); return
        }

        val signals = mutableListOf<CancellationSignal>()
        val listeners = mutableListOf<LocationListener>()
        var done = false
        var best: Location? = null

        fun finish(loc: Location?) {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            signals.forEach { it.cancel() }
            listeners.forEach { lm.removeUpdates(it) }
            callback(loc ?: best ?: lastKnown(ctx))
        }

        fun onResult(provider: String, loc: Location?) {
            if (loc == null || done) return
            if (provider == LocationManager.GPS_PROVIDER || LocationManager.GPS_PROVIDER !in providers) finish(loc)
            else best = loc
        }

        handler.postDelayed({ finish(null) }, GPS_TIMEOUT_MS)

        for (provider in providers) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal().also { signals += it }
                lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { onResult(provider, it) }
            } else {
                // Su Android < 11 i metodi di LocationListener non hanno implementazione di default.
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = onResult(provider, location)
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }
                listeners += listener
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
    }

    /** Nome leggibile della località (richiede rete su molti dispositivi: chiamare fuori dal main thread). */
    fun placeName(ctx: Context, loc: Location): String {
        val fallback = String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)
        if (!Geocoder.isPresent()) return fallback
        return runCatching {
            @Suppress("DEPRECATION")
            val a = Geocoder(ctx, Locale.ITALIAN).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            listOfNotNull(a?.locality ?: a?.subAdminArea, a?.adminArea).distinct().joinToString(", ").ifBlank { null }
        }.getOrNull() ?: fallback
    }

    /**
     * Salva la posizione nelle preferenze. Rifà la ricerca del nome solo se ci si è spostati,
     * o se [force] è vero. Da chiamare fuori dal main thread.
     */
    fun apply(ctx: Context, p: Prefs, loc: Location, force: Boolean = false) {
        val moved = !p.hasLocation || FloatArray(1).also {
            Location.distanceBetween(p.lat, p.lon, loc.latitude, loc.longitude, it)
        }[0] > SAME_PLACE_METERS
        if (force || moved) p.placeName = "${placeName(ctx, loc)} (GPS)"
        p.lat = loc.latitude
        p.lon = loc.longitude
        p.locationTime = loc.time
    }

    /** Aggiornamento automatico in background: usa l'ultima posizione nota, se più recente. */
    fun refreshInBackground(ctx: Context, p: Prefs) {
        if (!p.autoLocation || !hasBackground(ctx)) return
        val loc = lastKnown(ctx) ?: return
        if (loc.time > p.locationTime) apply(ctx, p, loc)
    }
}
