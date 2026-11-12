package it.svegliami

enum class SleepStage { AWAKE, LIGHT, DEEP }

/** Risultato di un minuto in modalità microfono. */
data class MicMinute(val score: Float, val snoring: Boolean)

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

    /** dB sopra il rumore di fondo per considerare un istante "rumore di movimento". */
    const val MIC_EVENT_DB = 8f

    // Russare: suoni gravi, lunghi 0,3–2,5 s, ripetuti al ritmo del respiro (ogni 2–8 s).
    // Unità: blocchi da 100 ms.
    const val SNORE_DB = 6f
    const val SNORE_LOW_RATIO = 0.5f
    private const val SNORE_MIN_CHUNKS = 3
    private const val SNORE_MAX_CHUNKS = 25
    private const val SNORE_MIN_EVENTS = 4
    private const val BREATH_MIN_CHUNKS = 20
    private const val BREATH_MAX_CHUNKS = 80
    private const val BREATH_MAX_CV = 0.35

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

    /** Percentile [q] (0–1) dei valori; 0 se la lista è vuota. */
    fun percentile(values: List<Float>, q: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * q).toInt().coerceIn(0, sorted.lastIndex)]
    }

    /**
     * Punteggio di un minuto dal microfono: percentuale di istanti (livelli in dB) sopra
     * il rumore di fondo della stanza. Stessa scala 0–100 dell'accelerometro.
     */
    fun micScore(levelsDb: List<Float>, baselineDb: Float): Float {
        if (levelsDb.isEmpty()) return 0f
        return levelsDb.count { it > baselineDb + MIC_EVENT_DB } * 100f / levelsDb.size
    }

    /**
     * Analizza un minuto di microfono.
     * @param levelsDb livello di ogni blocco da 100 ms
     * @param lowRatios quota di energia sotto ~800 Hz di ogni blocco (0–1)
     * Se c'è russare, i suoi blocchi non contano come movimento.
     */
    fun analyzeMicMinute(levelsDb: List<Float>, lowRatios: List<Float>, baselineDb: Float): MicMinute {
        val n = levelsDb.size
        if (n == 0) return MicMinute(0f, false)
        val snoreLike = BooleanArray(n) {
            levelsDb[it] > baselineDb + SNORE_DB && lowRatios.getOrElse(it) { 0f } >= SNORE_LOW_RATIO
        }

        // Raggruppa i blocchi in suoni, tollerando un blocco di pausa.
        val events = mutableListOf<IntRange>()
        var i = 0
        while (i < n) {
            if (!snoreLike[i]) { i++; continue }
            var end = i
            var j = i + 1
            while (j < n) {
                if (snoreLike[j]) end = j
                else if (!(j + 1 < n && snoreLike[j + 1])) break
                j++
            }
            events += i..end
            i = end + 1
        }
        val sounds = events.filter { (it.last - it.first + 1) in SNORE_MIN_CHUNKS..SNORE_MAX_CHUNKS }
        val snoring = isBreathingRhythm(sounds.map { it.first })

        val excluded = BooleanArray(n)
        if (snoring) sounds.forEach { r -> r.forEach { excluded[it] = true } }
        val moving = (0 until n).count { !excluded[it] && levelsDb[it] > baselineDb + MIC_EVENT_DB }
        return MicMinute(moving * 100f / n, snoring)
    }

    /** Inizi dei suoni a intervalli regolari compatibili con il respiro? */
    private fun isBreathingRhythm(starts: List<Int>): Boolean {
        if (starts.size < SNORE_MIN_EVENTS) return false
        val intervals = starts.zipWithNext { a, b -> b - a }
        val breath = intervals.filter { it in BREATH_MIN_CHUNKS..BREATH_MAX_CHUNKS }
        if (breath.size < SNORE_MIN_EVENTS - 1 || breath.size < intervals.size * 0.7) return false
        val mean = breath.average()
        val sd = kotlin.math.sqrt(breath.sumOf { (it - mean) * (it - mean) } / breath.size)
        return sd / mean <= BREATH_MAX_CV
    }

    /** Nella finestra della sveglia intelligente: c'è abbastanza movimento per svegliare ora? */
    fun shouldWake(lastMinuteScore: Float) = lastMinuteScore >= SMART_MIN_SCORE
}
