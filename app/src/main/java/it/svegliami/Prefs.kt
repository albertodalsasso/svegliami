package it.svegliami

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Configurazione di una sveglia. Nessun giorno selezionato = suona una volta sola. */
data class Alarm(
    val id: Int,
    var enabled: Boolean = true,
    var hour: Int = 7,
    var minute: Int = 0,
    /** DayOfWeek.value: 1 = lunedì … 7 = domenica. */
    var days: Set<Int> = setOf(1, 2, 3, 4, 5),
    var advanceMin: Int = 30,
    var probThreshold: Int = 50,
    /** Vuoto = suoneria sveglia predefinita del telefono. */
    var ringtoneUri: String = "",
    var label: String = "",
) {
    val timeText get() = "%02d:%02d".format(hour, minute)
    val title get() = label.ifBlank { "Sveglia delle $timeText" }

    val daysText: String
        get() = when {
            days.isEmpty() -> "Una volta"
            days.size == 7 -> "Tutti i giorni"
            days == setOf(1, 2, 3, 4, 5) -> "Dal lunedì al venerdì"
            days == setOf(6, 7) -> "Sabato e domenica"
            else -> days.sorted().joinToString(", ") { DAY_NAMES[it - 1] }
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("enabled", enabled).put("hour", hour).put("minute", minute)
        .put("days", days.sorted().joinToString(",")).put("advanceMin", advanceMin)
        .put("probThreshold", probThreshold).put("ringtoneUri", ringtoneUri).put("label", label)

    companion object {
        val DAY_NAMES = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

        fun parseDays(s: String?): Set<Int> = s.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

        fun fromJson(o: JSONObject) = Alarm(
            id = o.getInt("id"),
            enabled = o.optBoolean("enabled", true),
            hour = o.optInt("hour", 7),
            minute = o.optInt("minute", 0),
            days = parseDays(o.optString("days", "")),
            advanceMin = o.optInt("advanceMin", 30),
            probThreshold = o.optInt("probThreshold", 50),
            ringtoneUri = o.optString("ringtoneUri", ""),
            label = o.optString("label", ""),
        )
    }
}

/** Stato di schedulazione di una singola sveglia. */
class AlarmState(private val sp: SharedPreferences, id: Int) {
    private val k = "a${id}_"

    /** Orario (epoch ms) della sveglia normale attualmente programmata. */
    var currentOccurrence: Long
        get() = sp.getLong(k + "occ", 0)
        set(v) = sp.edit().putLong(k + "occ", v).apply()
    /** Ultima occorrenza già suonata (normale o anticipata): non va riprogrammata. */
    var handledOccurrence: Long
        get() = sp.getLong(k + "handled", 0)
        set(v) = sp.edit().putLong(k + "handled", v).apply()
    /** Orario della sveglia anticipata programmata (0 = nessuna). */
    var earlyAt: Long
        get() = sp.getLong(k + "early", 0)
        set(v) = sp.edit().putLong(k + "early", v).apply()
    var lastStatus: String
        get() = sp.getString(k + "status", "")!!
        set(v) = sp.edit().putString(k + "status", v).apply()
    var earlyReason: String
        get() = sp.getString(k + "reason", "")!!
        set(v) = sp.edit().putString(k + "reason", v).apply()

    fun clear() {
        sp.edit().apply { listOf("occ", "handled", "early", "status", "reason").forEach { remove(k + it) } }.apply()
    }
}

/** Sveglie, località e cache delle previsioni, su SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("svegliami", Context.MODE_PRIVATE)

    init {
        migrateSingleAlarm()
    }

    // --- sveglie ---

    /** Elenco delle sveglie, ordinate per orario. */
    val alarms: List<Alarm>
        get() = runCatching {
            val arr = JSONArray(sp.getString("alarms", "[]"))
            (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList()).sortedBy { it.hour * 60 + it.minute }

    private fun writeAlarms(list: List<Alarm>) {
        sp.edit().putString("alarms", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun alarm(id: Int): Alarm? = alarms.firstOrNull { it.id == id }
    fun saveAlarm(a: Alarm) = writeAlarms(alarms.filter { it.id != a.id } + a)
    fun deleteAlarm(id: Int) {
        writeAlarms(alarms.filter { it.id != id })
        state(id).clear()
    }
    fun newAlarmId() = (alarms.maxOfOrNull { it.id } ?: 0) + 1
    fun state(id: Int) = AlarmState(sp, id)

    /** La prima versione aveva una sola sveglia salvata in chiavi separate: la converte nell'elenco. */
    private fun migrateSingleAlarm() {
        if (sp.contains("alarms") || !sp.contains("hour")) return
        val a = Alarm(
            id = 1,
            enabled = sp.getBoolean("enabled", false),
            hour = sp.getInt("hour", 7),
            minute = sp.getInt("minute", 0),
            days = Alarm.parseDays(sp.getString("days", "1,2,3,4,5")),
            advanceMin = sp.getInt("advanceMin", 30),
            probThreshold = sp.getInt("probThreshold", 50),
            ringtoneUri = sp.getString("ringtoneUri", "")!!,
        )
        writeAlarms(listOf(a))
        sp.edit().apply {
            listOf(
                "enabled", "hour", "minute", "days", "advanceMin", "probThreshold", "ringtoneUri",
                "currentOccurrence", "handledOccurrence", "earlyAt", "lastStatus", "earlyReason",
            ).forEach { remove(it) }
        }.apply()
    }

    // --- località (comune a tutte le sveglie) ---

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
