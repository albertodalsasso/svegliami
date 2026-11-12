package it.svegliami

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NextOccurrenceTest {
    private val zone = ZoneId.systemDefault()
    private fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    @Test fun `sveglia singola suona entro 24 ore`() {
        val now = System.currentTimeMillis()
        val occ = Scheduler.nextOccurrence(Alarm(id = 1, hour = 6, minute = 30, days = emptySet()), 0, now)!!
        assertTrue(occ > now)
        assertTrue(occ - now <= 25 * 3600_000L)
    }

    @Test fun `occorrenza gia suonata viene saltata`() {
        val now = System.currentTimeMillis()
        val a = Alarm(id = 1, hour = 6, minute = 30, days = (1..7).toSet())
        val first = Scheduler.nextOccurrence(a, 0, now)!!
        val second = Scheduler.nextOccurrence(a, first, now)!!
        assertEquals(date(first).plusDays(1), date(second))
    }

    @Test fun `rispetta i giorni selezionati`() {
        val today = LocalDate.now(zone).dayOfWeek.value
        val a = Alarm(id = 1, hour = 0, minute = 0, days = (1..7).toSet() - today)
        val occ = Scheduler.nextOccurrence(a, 0, System.currentTimeMillis())!!
        assertNotEquals(today, date(occ).dayOfWeek.value)
    }

    @Test fun `nessuna occorrenza se la gestita e oltre la settimana`() {
        val a = Alarm(id = 1, hour = 6, minute = 30, days = setOf(1))
        val far = System.currentTimeMillis() + 30L * 24 * 3600_000L
        assertNull(Scheduler.nextOccurrence(a, far, System.currentTimeMillis()))
    }
}
