package it.svegliami

enum class SleepStage { AWAKE, LIGHT, DEEP }

data class SleepSummary(
    val inBedMin: Int,
    val asleepMin: Int,
    val deepMin: Int,
    val lightMin: Int,
    val awakeMin: Int,
    val awakenings: Int,
)

/**
 * Logica pura (senza Android) per l'attigrafia: ogni minuto ha un punteggio di movimento
 * da 0 a 100, la percentuale di campioni dell'accelerometro in cui il telefono si è mosso.
 * Le fasi sono una stima basata sul movimento, non una misura clinica.
 */
object SleepAnalyzer {
    /** Variazione minima (m/s²) tra due campioni consecutivi per considerarli "movimento". */
    const val MOVING_DELTA = 0.1f
    /** Punteggio minimo dell'ultimo minuto per considerare il sonno leggero (sveglia intelligente). */
    const val SMART_MIN_SCORE = 2f

    private const val AWAKE_SCORE = 10f
    private const val LIGHT_SCORE = 0.8f

    /** Media mobile centrata su 2·radius+1 minuti. */
    fun smoothed(scores: List<Float>, radius: Int = 2): List<Float> = scores.indices.map { i ->
        val from = maxOf(0, i - radius)
        val to = minOf(scores.lastIndex, i + radius)
        (from..to).sumOf { scores[it].toDouble() }.toFloat() / (to - from + 1)
    }

    fun stages(scores: List<Float>): List<SleepStage> = smoothed(scores).map {
        when {
            it >= AWAKE_SCORE -> SleepStage.AWAKE
            it >= LIGHT_SCORE -> SleepStage.LIGHT
            else -> SleepStage.DEEP
        }
    }

    fun summary(scores: List<Float>): SleepSummary {
        val st = stages(scores)
        // Addormentamento: primi 3 minuti consecutivi non svegli.
        val onset = (0..st.size - 3).firstOrNull { i -> (i until i + 3).all { st[it] != SleepStage.AWAKE } }
            ?: return SleepSummary(st.size, 0, 0, 0, st.size, 0)
        val afterOnset = st.drop(onset)
        // Risvegli: tratti svegli di almeno 2 minuti dopo l'addormentamento, escluso il risveglio finale.
        var awakenings = 0
        var run = 0
        for (s in afterOnset) {
            if (s == SleepStage.AWAKE) run++ else {
                if (run >= 2) awakenings++
                run = 0
            }
        }
        val deep = afterOnset.count { it == SleepStage.DEEP }
        val light = afterOnset.count { it == SleepStage.LIGHT }
        return SleepSummary(
            inBedMin = st.size,
            asleepMin = deep + light,
            deepMin = deep,
            lightMin = light,
            awakeMin = st.size - deep - light,
            awakenings = awakenings,
        )
    }

    /** Nella finestra della sveglia intelligente: c'è abbastanza movimento per svegliare ora? */
    fun shouldWake(lastMinuteScore: Float) = lastMinuteScore >= SMART_MIN_SCORE
}
