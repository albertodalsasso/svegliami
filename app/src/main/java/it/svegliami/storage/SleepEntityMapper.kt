package it.svegliami.storage

import it.svegliami.SleepAnalyzer
import it.svegliami.SleepSession
import it.svegliami.SleepStage

data class SleepSessionBundle(
    val session: SleepSessionEntity,
    val samples: List<SleepSampleEntity>,
    val events: List<SleepEventEntity>,
)

/** Converts only measurements that exist; absent sensor streams remain null. */
object SleepEntityMapper {
    fun map(source: SleepSession): SleepSessionBundle {
        val summary = SleepAnalyzer.summary(source.scores)
        val stages = SleepAnalyzer.stages(source.scores)
        val firstSleep = stages.indexOfFirst { it != SleepStage.AWAKE }.takeIf { it >= 0 }
        val lastSleep = stages.indexOfLast { it != SleepStage.AWAKE }.takeIf { it >= 0 }
        val session = SleepSessionEntity(
            id = source.start,
            startTime = source.start,
            endTime = source.end,
            estimatedSleepStart = firstSleep?.let { source.start + it * 60_000L },
            estimatedSleepEnd = lastSleep?.let { source.start + (it + 1) * 60_000L },
            trackingMode = source.trackingMode.name,
            recordingMode = "NONE",
            totalSleepMinutes = summary.asleepMin,
            awakeMinutes = summary.awakeMin,
            score = null,
        )
        val samples = source.scores.indices.map { index ->
            val legacyMovement = if (source.movementScores.isEmpty() && source.trackingMode == it.svegliami.SleepTrackingMode.MATTRESS)
                source.scores[index] else null
            SleepSampleEntity(
                sessionId = source.start,
                timestamp = source.start + index * 60_000L,
                movementScore = source.movementScores.getOrNull(index) ?: legacyMovement,
                soundScore = source.soundScores.getOrNull(index),
                estimatedStage = stages.getOrNull(index)?.name,
            )
        }
        val events = source.snore.indices.filter { source.snore[it] }.map { index ->
            SleepEventEntity(
                sessionId = source.start,
                timestamp = source.start + index * 60_000L,
                eventType = "SNORING_ESTIMATE",
                durationMillis = 60_000L,
                confidence = null,
                audioFileId = null,
            )
        }
        return SleepSessionBundle(session, samples, events)
    }
}
