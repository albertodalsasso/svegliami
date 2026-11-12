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
)

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
            SleepSession(
                start = o.getLong("start"),
                end = o.getLong("end"),
                scores = (0 until s.length()).map { s.getDouble(it).toFloat() },
                smartWakeAt = o.optLong("smartWakeAt", 0),
                mic = o.optBoolean("mic", false),
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
                .put("scores", JSONArray(s.scores.map { Math.round(it * 10) / 10.0 }))
        })
        file(ctx).writeText(arr.toString())
    }
}
