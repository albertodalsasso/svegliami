package it.svegliami.sensors

data class SensorSignal(val activityScore: Float, val quality: Float)

data class FusedSleepSignal(val activityScore: Float, val confidence: Float)

/** Reliability-weighted fusion for activity signals; quality is bounded to 0..1. */
object SensorFusion {
    fun combine(accelerometer: SensorSignal?, microphone: SensorSignal?): FusedSleepSignal {
        val signals = listOfNotNull(accelerometer, microphone)
            .map { it.copy(activityScore = it.activityScore.coerceIn(0f, 100f), quality = it.quality.coerceIn(0f, 1f)) }
            .filter { it.quality > 0f }
        val totalQuality = signals.sumOf { it.quality.toDouble() }.toFloat()
        if (totalQuality == 0f) return FusedSleepSignal(0f, 0f)
        val activity = signals.sumOf { (it.activityScore * it.quality).toDouble() }.toFloat() / totalQuality
        val confidence = if (signals.size == 1) signals.single().quality
        else (signals.sumOf { it.quality.toDouble() } / 2.0).toFloat().coerceIn(0f, 1f)
        return FusedSleepSignal(activity.coerceIn(0f, 100f), confidence)
    }
}
