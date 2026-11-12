package it.svegliami

/** Intervallo di previsione [start, end) in secondi epoch con un valore (mm o %). */
data class Slot(val startSec: Long, val endSec: Long, val value: Double)

data class Forecast(
    /** Precipitazione (mm) nei 15 minuti precedenti, a risoluzione 15 min. */
    val precip15: List<Slot>,
    /** Precipitazione (mm) nell'ora precedente. */
    val precipHourly: List<Slot>,
    /** Probabilità (%) di precipitazione > 0,1 mm nell'ora precedente. */
    val probHourly: List<Slot>,
)

data class Decision(val rainy: Boolean, val reason: String)

/** Logica pura (senza dipendenze Android): decide se piove nella finestra [fromSec, toSec). */
object RainDecider {
    const val DEFAULT_MM_THRESHOLD = 0.1

    fun decide(
        forecast: Forecast,
        fromSec: Long,
        toSec: Long,
        probThreshold: Int,
        mmThreshold: Double = DEFAULT_MM_THRESHOLD,
    ): Decision {
        fun List<Slot>.overlapping() = filter { it.endSec > fromSec && it.startSec < toSec && !it.value.isNaN() }

        val mm15 = forecast.precip15.overlapping()
        val mmH = forecast.precipHourly.overlapping()
        val prob = forecast.probHourly.overlapping()
        if (mm15.isEmpty() && mmH.isEmpty() && prob.isEmpty()) {
            return Decision(false, "nessun dato di previsione per la finestra")
        }

        // Preferisce la risoluzione a 15 minuti; ripiega sull'orario se manca.
        val maxMm = (if (mm15.isNotEmpty()) mm15 else mmH).maxOfOrNull { it.value } ?: 0.0
        val maxProb = prob.maxOfOrNull { it.value } ?: 0.0

        val byMm = maxMm >= mmThreshold
        val byProb = maxProb >= probThreshold
        val details = "pioggia max %.1f mm, probabilità max %d%%".format(java.util.Locale.ITALY, maxMm, maxProb.toInt())
        return when {
            byMm || byProb -> Decision(true, "pioggia prevista ($details)")
            else -> Decision(false, "niente pioggia ($details)")
        }
    }
}
