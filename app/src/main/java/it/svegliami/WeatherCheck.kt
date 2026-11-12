package it.svegliami

import android.content.Context

object WeatherCheck {
    private const val CACHE_MAX_AGE_MS = 12 * 3600_000L

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

    fun cached(p: Prefs): Forecast? {
        val fresh = System.currentTimeMillis() - p.cacheTime < CACHE_MAX_AGE_MS
        if (!fresh || p.cacheKey != cacheKey(p) || p.cacheJson.isEmpty()) return null
        return runCatching { WeatherClient.parseForecast(p.cacheJson) }.getOrNull()
    }

    fun decide(p: Prefs, forecast: Forecast, occ: Long): Decision {
        val early = occ - p.advanceMin * 60_000L
        return RainDecider.decide(forecast, early / 1000, occ / 1000, p.probThreshold)
    }

    /** Eseguito dal CheckWeatherReceiver (thread in background). */
    fun runScheduledCheck(ctx: Context, occ: Long) {
        val p = Prefs(ctx)
        if (!p.enabled || occ != p.currentOccurrence) return
        runCatching { LocationHelper.refreshInBackground(ctx, p) }
        if (!p.hasLocation) {
            p.lastStatus = "Posizione sconosciuta: suonerà all'orario normale"
            return
        }
        val now = System.currentTimeMillis()
        val early = occ - p.advanceMin * 60_000L
        if (early <= now) return

        val forecast = try {
            fetch(p)
        } catch (e: Exception) {
            val retryAt = now + Scheduler.RETRY_MIN * 60_000L
            if (retryAt < early - 60_000L) {
                Scheduler.scheduleCheckRetry(ctx, retryAt, occ)
                p.lastStatus = "Rete non disponibile alle ${Scheduler.fmtTime(now)}, riprovo alle ${Scheduler.fmtTime(retryAt)}"
                return
            }
            cached(p) ?: run {
                p.lastStatus = "Previsioni non disponibili: suonerà all'orario normale (${Scheduler.fmtTime(occ)})"
                return
            }
        }

        val d = decide(p, forecast, occ)
        if (d.rainy) {
            Scheduler.scheduleEarly(ctx, occ, early)
            p.earlyReason = "Tra le ${Scheduler.fmtTime(early)} e le ${Scheduler.fmtTime(occ)}: ${d.reason}"
            p.lastStatus = "Controllo delle ${Scheduler.fmtTime(now)}: ${d.reason} → sveglia anticipata alle ${Scheduler.fmtTime(early)}"
        } else {
            p.lastStatus = "Controllo delle ${Scheduler.fmtTime(now)}: ${d.reason} → sveglia normale alle ${Scheduler.fmtTime(occ)}"
        }
    }
}
