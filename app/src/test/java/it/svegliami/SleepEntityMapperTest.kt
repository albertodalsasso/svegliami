package it.svegliami

import it.svegliami.storage.SleepEntityMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepEntityMapperTest {
    @Test fun `legacy mattress history maps to movement samples without guessed audio`() {
        val start = 1_700_000_000_000L
        val bundle = SleepEntityMapper.map(SleepSession(start, start + 120_000, listOf(0f, 20f), snore = listOf(false, false)))
        assertEquals(start, bundle.session.id)
        assertEquals(2, bundle.samples.size)
        assertEquals(0f, bundle.samples[0].movementScore!!, 0f)
        assertNull(bundle.samples[0].soundScore)
        assertEquals(0, bundle.events.size)
    }

    @Test fun `microphone history maps sound values and snore events`() {
        val start = 1_700_000_000_000L
        val session = SleepSession(
            start, start + 120_000, listOf(0f, 15f), mic = true,
            snore = listOf(false, true), soundScores = listOf(0f, 15f),
        )
        val bundle = SleepEntityMapper.map(session)
        assertNull(bundle.samples[0].movementScore)
        assertEquals(15f, bundle.samples[1].soundScore!!, 0f)
        assertEquals("SNORING_ESTIMATE", bundle.events.single().eventType)
        assertNull(bundle.events.single().confidence)
    }
}
