package it.svegliami

import android.content.Context

/** Configurazione della sveglia e stato di schedulazione, su SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("svegliami", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()
    var hour: Int
        get() = sp.getInt("hour", 7)
        set(v) = sp.edit().putInt("hour", v).apply()
    var minute: Int
        get() = sp.getInt("minute", 0)
        set(v) = sp.edit().putInt("minute", v).apply()

    /** Giorni attivi, come DayOfWeek.value (1 = lunedì … 7 = domenica). */
    var days: Set<Int>
        get() = sp.getString("days", "1,2,3,4,5")!!.split(',').mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = sp.edit().putString("days", v.sorted().joinToString(",")).apply()

    /** Minuti di anticipo se piove. */
    var advanceMin: Int
        get() = sp.getInt("advanceMin", 30)
        set(v) = sp.edit().putInt("advanceMin", v).apply()

    /** Soglia di probabilità di pioggia (%). */
    var probThreshold: Int
        get() = sp.getInt("probThreshold", 50)
        set(v) = sp.edit().putInt("probThreshold", v).apply()

    var placeName: String
        get() = sp.getString("placeName", "")!!
        set(v) = sp.edit().putString("placeName", v).apply()
    var lat: Double
        get() = sp.getString("lat", "NaN")!!.toDouble()
        set(v) = sp.edit().putString("lat", v.toString()).apply()
    var lon: Double
        get() = sp.getString("lon", "NaN")!!.toDouble()
        set(v) = sp.edit().putString("lon", v.toString()).apply()
    val hasLocation get() = !lat.isNaN() && !lon.isNaN()

    /** Aggiorna automaticamente la posizione dal GPS prima di ogni controllo meteo. */
    var autoLocation: Boolean
        get() = sp.getBoolean("autoLocation", false)
        set(v) = sp.edit().putBoolean("autoLocation", v).apply()
    /** Timestamp (ms) della posizione GPS salvata. */
    var locationTime: Long
        get() = sp.getLong("locationTime", 0)
        set(v) = sp.edit().putLong("locationTime", v).apply()

    // --- stato ---
    /** Orario (epoch ms) della sveglia normale attualmente programmata. */
    var currentOccurrence: Long
        get() = sp.getLong("currentOccurrence", 0)
        set(v) = sp.edit().putLong("currentOccurrence", v).apply()
    /** Ultima occorrenza già suonata (normale o anticipata): non va riprogrammata. */
    var handledOccurrence: Long
        get() = sp.getLong("handledOccurrence", 0)
        set(v) = sp.edit().putLong("handledOccurrence", v).apply()
    /** Orario della sveglia anticipata programmata (0 = nessuna). */
    var earlyAt: Long
        get() = sp.getLong("earlyAt", 0)
        set(v) = sp.edit().putLong("earlyAt", v).apply()
    var lastStatus: String
        get() = sp.getString("lastStatus", "")!!
        set(v) = sp.edit().putString("lastStatus", v).apply()
    var earlyReason: String
        get() = sp.getString("earlyReason", "")!!
        set(v) = sp.edit().putString("earlyReason", v).apply()

    // --- cache previsioni ---
    var cacheJson: String
        get() = sp.getString("cacheJson", "")!!
        set(v) = sp.edit().putString("cacheJson", v).apply()
    var cacheTime: Long
        get() = sp.getLong("cacheTime", 0)
        set(v) = sp.edit().putLong("cacheTime", v).apply()
    var cacheKey: String
        get() = sp.getString("cacheKey", "")!!
        set(v) = sp.edit().putString("cacheKey", v).apply()
}
