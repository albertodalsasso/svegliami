package it.svegliami.analysis

import java.time.Instant
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Weights for a descriptive, non-medical sleep score. Values are percentages. */
data class SleepScoreWeights(
    val duration: Int = 30,
    val regularity: Int = 20,
    val continuity: Int = 20,
    val awakenings: Int = 15,
    val environment: Int = 15,
) {
    init {
        require(listOf(duration, regularity, continuity, awakenings, environment).all { it >= 0 })
        require(listOf(duration, regularity, continuity, awakenings, environment).sum() > 0)
    }
}

data class SleepScoreInputs(
    val sleepMinutes: Int,
    val inBedMinutes: Int,
    val awakeningCount: Int,
    val bedtimeEpochMillis: Long,
    /** Snoring minutes are only a partial sound proxy; null means not measured. */
    val snoringMinutes: Int? = null,
    val targetSleepMinutes: Int = 8 * 60,
)

data class SleepScoreComponent(val name: String, val score: Int, val weight: Int)

data class SleepScoreResult(val score: Int, val components: List<SleepScoreComponent>) {
    val explanation: String
        get() = components.joinToString(" · ") { "${it.name} ${it.score}" }
}

/**
 * A transparent heuristic, not a clinical assessment. Missing signals are omitted and the
 * remaining weights are normalized, so a bed-mode night is not penalized for lacking audio.
 */
object SleepScoreCalculator {
    fun calculate(
        input: SleepScoreInputs,
        previousBedtimes: List<Long>,
        weights: SleepScoreWeights = SleepScoreWeights(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): SleepScoreResult {
        val components = buildList {
            add(SleepScoreComponent("Durata", ratioScore(input.sleepMinutes, input.targetSleepMinutes), weights.duration))
            regularityScore(input.bedtimeEpochMillis, previousBedtimes, zoneId)?.let {
                add(SleepScoreComponent("Regolarità", it, weights.regularity))
            }
            val inBed = input.inBedMinutes.coerceAtLeast(0)
            val asleep = input.sleepMinutes.coerceIn(0, inBed)
            if (inBed > 0) add(SleepScoreComponent("Continuità", (asleep * 100f / inBed).roundToInt(), weights.continuity))
            add(SleepScoreComponent("Risvegli", (100 - input.awakeningCount.coerceAtLeast(0) * 12).coerceIn(0, 100), weights.awakenings))
            input.snoringMinutes?.let { snoring ->
                val denominator = input.sleepMinutes.coerceAtLeast(1)
                val share = (snoring.coerceAtLeast(0).toFloat() / denominator).coerceIn(0f, 1f)
                add(SleepScoreComponent("Russamento", ((1f - share) * 100).roundToInt(), weights.environment))
            }
        }.filter { it.weight > 0 }

        val weightTotal = components.sumOf { it.weight }
        val score = if (weightTotal == 0) 0 else
            (components.sumOf { it.score * it.weight } / weightTotal.toFloat()).roundToInt().coerceIn(0, 100)
        return SleepScoreResult(score, components)
    }

    private fun ratioScore(value: Int, target: Int): Int =
        if (target <= 0) 0 else (value.coerceAtLeast(0) * 100f / target).roundToInt().coerceIn(0, 100)

    private fun regularityScore(bedtime: Long, history: List<Long>, zoneId: ZoneId): Int? {
        if (history.isEmpty()) return null
        val minutes = (history + bedtime).map { timestamp ->
            val time = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalTime()
            time.hour * 60 + time.minute
        }
        if (minutes.size < 2) return null
        val radians = minutes.map { it * 2.0 * Math.PI / 1440.0 }
        val mean = atan2(radians.sumOf(::sin), radians.sumOf(::cos))
        val current = bedtime.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime() }
        val currentRadians = (current.hour * 60 + current.minute) * 2.0 * Math.PI / 1440.0
        var deviationMinutes = kotlin.math.abs(currentRadians - mean) * 1440.0 / (2.0 * Math.PI)
        deviationMinutes = minOf(deviationMinutes, 1440.0 - deviationMinutes)
        // Heuristic: each 2 minutes from this user's recent circular mean costs one point.
        return (100.0 - deviationMinutes / 2.0).roundToInt().coerceIn(0, 100)
    }
}
