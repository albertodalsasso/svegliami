package it.svegliami

import it.svegliami.analysis.SleepScoreCalculator
import it.svegliami.analysis.SleepScoreInputs
import it.svegliami.analysis.SleepScoreWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SleepScoreCalculatorTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun time(day: Int, hour: Int, minute: Int = 0) =
        LocalDate.of(2026, 10, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test fun `weights are normalized and score stays within bounds`() {
        val score = SleepScoreCalculator.calculate(
            SleepScoreInputs(480, 480, 0, time(10, 23), 0),
            listOf(time(8, 23), time(9, 23)), zoneId = zone,
        )
        assertEquals(100, score.score)
        assertEquals(5, score.components.size)
    }

    @Test fun `missing history and audio are omitted instead of treated as zero`() {
        val score = SleepScoreCalculator.calculate(
            SleepScoreInputs(420, 450, 1, time(10, 23)), emptyList(), zoneId = zone,
        )
        assertEquals(3, score.components.size)
        assertFalse(score.components.any { it.name == "Regolarità" || it.name == "Russamento" })
    }

    @Test fun `irregular bedtime lowers regularity score across midnight`() {
        val regular = listOf(time(8, 23, 30), time(9, 23, 35))
        val same = SleepScoreCalculator.calculate(SleepScoreInputs(480, 490, 0, time(10, 23, 32)), regular, zoneId = zone)
        val late = SleepScoreCalculator.calculate(SleepScoreInputs(480, 490, 0, time(10, 1, 30)), regular, zoneId = zone)
        assertTrue(same.components.first { it.name == "Regolarità" }.score > late.components.first { it.name == "Regolarità" }.score)
    }

    @Test fun `invalid weights are rejected`() {
        assertTrue(runCatching { SleepScoreWeights(duration = 0, regularity = 0, continuity = 0, awakenings = 0, environment = 0) }.isFailure)
    }
}
