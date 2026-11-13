package it.svegliami

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SleepSession(
    val start: Long,
    val end: Long,
    /** Punteggio di movimento per minuto (0–100). */
    val scores: List<Float>,
    /** Orario in cui è scattata la sveglia intelligente (0 = no). */
    val smartWakeAt: Long = 0,
    /** Registrata col microfono (telefono sul comodino) invece che con l'accelerometro. */
    val mic: Boolean = false,
    /** Per ogni minuto: russare rilevato (solo in modalità microfono). */
    val snore: List<Boolean> = emptyList(),
    val trackingMode: SleepTrackingMode = if (mic) SleepTrackingMode.NIGHTSTAND else SleepTrackingMode.MATTRESS,
    val movementScores: List<Float?> = emptyList(),
    val soundScores: List<Float?> = emptyList(),
) {
    val snoreMinutes get() = snore.count { it }
}

enum class SleepTrackingMode {
    /** Comodino (microfono) oppure materasso (accelerometro). */
    NIGHTSTAND, MATTRESS;

    val usesMicrophone get() = this == NIGHTSTAND
}

/** Storico delle notti, in un file JSON nella memoria privata dell'app. */
object SleepStore {
    private const val MAX_SESSIONS = 30
    private fun file(ctx: Context) = File(ctx.filesDir, "sleep_sessions.json")

    @Synchronized
    fun load(ctx: Context): List<SleepSession> = runCatching {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val s = o.getJSONArray("scores")
            val movement = o.optJSONArray("movementScores")
            val sound = o.optJSONArray("soundScores")
            SleepSession(
                start = o.getLong("start"),
                end = o.getLong("end"),
                scores = (0 until s.length()).map { s.getDouble(it).toFloat() },
                smartWakeAt = o.optLong("smartWakeAt", 0),
                mic = o.optBoolean("mic", false),
                trackingMode = runCatching {
                    SleepTrackingMode.valueOf(o.optString("trackingMode", if (o.optBoolean("mic", false)) "NIGHTSTAND" else "MATTRESS"))
                }.getOrDefault(if (o.optBoolean("mic", false)) SleepTrackingMode.NIGHTSTAND else SleepTrackingMode.MATTRESS),
                snore = o.optString("snore", "").map { it == '1' },
                movementScores = movement?.let { a -> (0 until a.length()).map { i -> if (a.isNull(i)) null else a.optDouble(i).toFloat() } }.orEmpty(),
                soundScores = sound?.let { a -> (0 until a.length()).map { i -> if (a.isNull(i)) null else a.optDouble(i).toFloat() } }.orEmpty(),
            )
        }.sortedByDescending { it.start }
    }.getOrDefault(emptyList())

    /** Salva o aggiorna la sessione (identificata dall'orario di inizio). */
    @Synchronized
    fun upsert(ctx: Context, session: SleepSession) {
        val list = (load(ctx).filter { it.start != session.start } + session)
            .sortedByDescending { it.start }.take(MAX_SESSIONS)
        val arr = JSONArray(list.map { s ->
            JSONObject().put("start", s.start).put("end", s.end).put("smartWakeAt", s.smartWakeAt).put("mic", s.mic)
                .put("trackingMode", s.trackingMode.name)
                .put("snore", s.snore.joinToString("") { if (it) "1" else "0" })
                .put("scores", JSONArray(s.scores.map { Math.round(it * 10) / 10.0 }))
                .put("movementScores", JSONArray(s.movementScores.map { it?.toDouble() ?: JSONObject.NULL }))
                .put("soundScores", JSONArray(s.soundScores.map { it?.toDouble() ?: JSONObject.NULL }))
        })
        file(ctx).writeText(arr.toString())
    }
}
