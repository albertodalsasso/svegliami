package it.svegliami

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepAnalyzerTest {
    private fun minutes(n: Int, score: Float) = List(n) { score }

    @Test fun `notte tranquilla e quasi tutta sonno profondo`() {
        // 10 min svegli (molto movimento), 6 ore ferme, 10 min svegli
        val scores = minutes(10, 30f) + minutes(360, 0f) + minutes(10, 30f)
        val s = SleepAnalyzer.summary(scores)
        assertEquals(380, s.inBedMin)
        assertTrue(s.deepMin >= 350)
        assertEquals(0, s.awakenings)
    }

    @Test fun `movimento lieve e sonno leggero`() {
        val st = SleepAnalyzer.stages(minutes(30, 3f))
        assertTrue(st.all { it == SleepStage.LIGHT })
    }

    @Test fun `risveglio notturno viene contato`() {
        val scores = minutes(120, 0f) + minutes(8, 40f) + minutes(120, 0f)
        assertEquals(1, SleepAnalyzer.summary(scores).awakenings)
    }

    @Test fun `mai addormentato`() {
        val s = SleepAnalyzer.summary(minutes(60, 50f))
        assertEquals(0, s.asleepMin)
        assertEquals(60, s.awakeMin)
    }

    @Test fun `sveglia intelligente solo con movimento`() {
        assertFalse(SleepAnalyzer.shouldWake(0f))
        assertFalse(SleepAnalyzer.shouldWake(1f))
        assertTrue(SleepAnalyzer.shouldWake(5f))
    }
}
