package it.svegliami

import android.content.Context

object WeatherCheck {
    /** Oltre quest'età la cache non si usa nemmeno come ripiego. */
    private const val CACHE_MAX_AGE_MS = 12 * 3600_000L
    /** Più sveglie vicine non riscaricano le previsioni se sono così recenti. */
    private const val CACHE_REUSE_MS = 15 * 60_000L

    private fun cacheKey(p: Prefs) = "${p.lat},${p.lon}"

    /** Scarica le previsioni (aggiornando la cache). Lancia eccezione se la rete fallisce. */
    fun fetch(p: Prefs): Forecast {
        val json = WeatherClient.fetchForecastJson(p.lat, p.lon)
        val forecast = WeatherClient.parseForecast(json)
        p.cacheJson = json
        p.cacheTime = System.currentTimeMillis()
        p.cacheKey = cacheKey(p)
        return forecast
    }

    fun cached(p: Prefs, maxAgeMs: Long = CACHE_MAX_AGE_MS): Forecast? {
        val fresh = System.currentTimeMillis() - p.cacheTime < maxAgeMs
        if (!fresh || p.cacheKey != cacheKey(p) || p.cacheJson.isEmpty()) return null
        return runCatching { WeatherClient.parseForecast(p.cacheJson) }.getOrNull()
    }

    fun decide(a: Alarm, forecast: Forecast, occ: Long): Decision {
        val early = occ - a.advanceMin * 60_000L
        return RainDecider.decide(forecast, early / 1000, occ / 1000, a.probThreshold)
    }

    /** Eseguito dal CheckWeatherReceiver (thread in background). */
    fun runScheduledCheck(ctx: Context, id: Int, occ: Long) {
        val p = Prefs(ctx)
        val a = p.alarm(id) ?: return
        val st = p.state(id)
        if (!a.enabled || !a.rainEnabled || occ != st.currentOccurrence) return

        runCatching { LocationHelper.refreshInBackground(ctx, p) }
        if (!p.hasLocation) {
            st.lastStatus = "Posizione sconosciuta: suonerà all'orario normale"
            return
        }
        val now = System.currentTimeMillis()
        val early = occ - a.advanceMin * 60_000L
        if (early <= now) return

        val forecast = cached(p, CACHE_REUSE_MS) ?: try {
            fetch(p)
        } catch (e: Exception) {
            val retryAt = now + Scheduler.RETRY_MIN * 60_000L
            if (retryAt < early - 60_000L) {
                Scheduler.scheduleCheckRetry(ctx, id, retryAt, occ)
                st.lastStatus = "Rete non disponibile alle ${Scheduler.fmtTime(now)}, riprovo alle ${Scheduler.fmtTime(retryAt)}"
                return
            }
            cached(p) ?: run {
                st.lastStatus = "Previsioni non disponibili: suonerà all'orario normale (${Scheduler.fmtTime(occ)})"
                return
            }
        }

        val d = decide(a, forecast, occ)
        if (d.rainy) {
            Scheduler.scheduleEarly(ctx, id, occ, early)
            st.earlyReason = "Tra le ${Scheduler.fmtTime(early)} e le ${Scheduler.fmtTime(occ)}: ${d.reason}"
            st.lastStatus = "Controllo delle ${Scheduler.fmtTime(now)}: ${d.reason} → sveglia anticipata alle ${Scheduler.fmtTime(early)}"
        } else {
            st.lastStatus = "Controllo delle ${Scheduler.fmtTime(now)}: ${d.reason} → sveglia normale alle ${Scheduler.fmtTime(occ)}"
        }
    }
}
