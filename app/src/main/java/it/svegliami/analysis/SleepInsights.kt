package it.svegliami.analysis

import it.svegliami.SleepAnalyzer
import it.svegliami.SleepSession

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

    fun calculate(sessions: List<SleepSession>): SleepInsightsReport {
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
        val debt = points.takeLast(7).sumOf { (DEFAULT_SLEEP_GOAL_MINUTES - it.sleepMinutes).coerceAtLeast(0) }
        val averageScore = if (points.isEmpty()) 0 else points.map { it.score }.average().toInt()
        return SleepInsightsReport(points.size, average, averageScore, debt, points)
    }
}
