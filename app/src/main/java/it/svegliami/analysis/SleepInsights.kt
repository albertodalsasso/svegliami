package it.svegliami.analysis

import it.svegliami.SleepAnalyzer
import it.svegliami.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SleepInsightPoint(val timestamp: Long, val sleepMinutes: Int, val score: Int)

data class SleepInsightsReport(
    val nightCount: Int,
    val averageSleepMinutes: Int,
    val averageScore: Int,
    val sevenNightDebtMinutes: Int,
    val points: List<SleepInsightPoint>,
)

object SleepInsightsCalculator {
    const val DEFAULT_SLEEP_GOAL_MINUTES = 8 * 60

    fun calculate(sessions: List<SleepSession>, zoneId: ZoneId = ZoneId.systemDefault()): SleepInsightsReport {
        val ordered = sessions.sortedBy { it.start }
        val points = ordered.mapIndexed { index, session ->
            val summary = SleepAnalyzer.summary(session.scores)
            val score = SleepScoreCalculator.calculate(
                SleepScoreInputs(
                    sleepMinutes = summary.asleepMin,
                    inBedMinutes = summary.inBedMin,
                    awakeningCount = summary.awakenings,
                    bedtimeEpochMillis = session.start,
                    snoringMinutes = if (session.mic && session.snore.isNotEmpty()) session.snoreMinutes else null,
                ),
                previousBedtimes = ordered.take(index).takeLast(13).map { it.start },
            ).score
            SleepInsightPoint(session.start, summary.asleepMin, score)
        }
        val average = if (points.isEmpty()) 0 else points.map { it.sleepMinutes }.average().toInt()
        // Debito sulle ultime 7 notti registrate: le sessioni della stessa notte (interrotta e
        // ripresa, o un pisolino) si sommano, e l'obiettivo di 8 ore vale una volta per notte.
        val sleptPerNight = points.groupBy { nightOf(it.timestamp, zoneId) }
            .mapValues { (_, nightPoints) -> nightPoints.sumOf { it.sleepMinutes } }
        val debt = sleptPerNight.keys.sorted().takeLast(7)
            .sumOf { (DEFAULT_SLEEP_GOAL_MINUTES - sleptPerNight.getValue(it)).coerceAtLeast(0) }
        val averageScore = if (points.isEmpty()) 0 else points.map { it.score }.average().toInt()
        return SleepInsightsReport(points.size, average, averageScore, debt, points)
    }

    /** La "notte" di una sessione: la data di 12 ore prima dell'inizio (23:00 e 02:00 contano per la stessa sera). */
    fun nightOf(start: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(start - 12 * 3_600_000L).atZone(zoneId).toLocalDate()
}
