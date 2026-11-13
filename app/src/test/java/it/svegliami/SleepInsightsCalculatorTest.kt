package it.svegliami

import it.svegliami.analysis.SleepInsightsCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class SleepInsightsCalculatorTest {
    @Test fun `empty history has no fabricated points or debt`() {
        val report = SleepInsightsCalculator.calculate(emptyList())
        assertEquals(0, report.nightCount)
        assertEquals(0, report.averageSleepMinutes)
        assertEquals(0, report.sevenNightDebtMinutes)
        assertEquals(0, report.points.size)
    }

    @Test fun `insights use only observed session minutes`() {
        val start = 1_700_000_000_000L
        val sessions = listOf(
            SleepSession(start, start + 120_000, List(120) { 0f }),
            SleepSession(start + 86_400_000, start + 86_400_000 + 60_000, List(60) { 0f }),
        )
        val report = SleepInsightsCalculator.calculate(sessions)
        assertEquals(2, report.nightCount)
        assertEquals(90, report.averageSleepMinutes)
        assertEquals(2, report.points.size)
        assertEquals((480 - 120) + (480 - 60), report.sevenNightDebtMinutes)
    }

    @Test fun `sessions of the same night share one sleep goal`() {
        val evening = 1_700_000_000_000L - 1_700_000_000_000L % 86_400_000L + 22 * 3_600_000L // 22:00 UTC
        val sessions = listOf(
            SleepSession(evening, evening + 180 * 60_000L, List(180) { 0f }),
            SleepSession(evening + 4 * 3_600_000L, evening + 7 * 3_600_000L, List(180) { 0f }), // ripresa alle 02:00
        )
        val report = SleepInsightsCalculator.calculate(sessions, ZoneOffset.UTC)
        assertEquals(480 - 360, report.sevenNightDebtMinutes)
    }
}
