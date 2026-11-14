package it.svegliami

import it.svegliami.analysis.JournalInsights
import it.svegliami.analysis.JournalNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class JournalInsightsTest {
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 10, h, m).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun night(sleep: Int, caffeine: Boolean? = null, meal: String? = null, start: Long = at(23)) =
        JournalNight(start, sleep, 70, caffeine, null, null, meal)

    @Test fun `habit appears only with enough nights on both sides`() {
        val few = listOf(night(400, true), night(420, false), night(440, false))
        assertTrue(JournalInsights.compare(few, ZoneOffset.UTC).isEmpty())

        val enough = few + night(380, true)
        val c = JournalInsights.compare(enough, ZoneOffset.UTC).single { it.habit == "Caffeina" }
        assertEquals(390, c.withSleepMinutes)
        assertEquals(430, c.withoutSleepMinutes)
        assertEquals(-40, c.sleepDifferenceMinutes)
    }

    @Test fun `unannotated nights are ignored`() {
        val nights = listOf(night(400, true), night(400, true), night(500), night(500), night(450, false), night(450, false))
        val c = JournalInsights.compare(nights, ZoneOffset.UTC).single()
        assertEquals(2, c.withoutNights)
        assertEquals(450, c.withoutSleepMinutes)
    }

    @Test fun `late meal is measured against bedtime, also after midnight`() {
        assertEquals(true, JournalInsights.lateMeal(night(0, meal = "22:00", start = at(23)), ZoneOffset.UTC))
        assertEquals(false, JournalInsights.lateMeal(night(0, meal = "20.00", start = at(23)), ZoneOffset.UTC))
        assertEquals(false, JournalInsights.lateMeal(night(0, meal = "21", start = at(0, 30)), ZoneOffset.UTC))
        assertNull(JournalInsights.lateMeal(night(0, meal = "boh"), ZoneOffset.UTC))
    }
}
