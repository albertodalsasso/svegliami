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

class MicScoreTest {
    @Test fun `stanza silenziosa punteggio zero`() {
        val levels = List(600) { 30f + (it % 3) }
        val base = SleepAnalyzer.percentile(levels, 0.1f)
        assertEquals(0f, SleepAnalyzer.micScore(levels, base), 0.001f)
    }

    @Test fun `fruscio sopra il fondo conta come movimento`() {
        // 60 decimi di secondo (6 s) di rumore 12 dB sopra il fondo su 600
        val levels = List(540) { 30f } + List(60) { 42f }
        val base = SleepAnalyzer.percentile(levels, 0.1f)
        assertEquals(10f, SleepAnalyzer.micScore(levels, base), 0.01f)
    }

    @Test fun `rumore costante come un ventilatore non conta`() {
        val levels = List(600) { 50f }
        assertEquals(0f, SleepAnalyzer.micScore(levels, SleepAnalyzer.percentile(levels, 0.1f)), 0.001f)
    }
}

class SnoreTest {
    private val base = 30f

    /** Un minuto (600 blocchi) con suoni da [len] blocchi ogni [every] blocchi. */
    private fun minute(every: Int, len: Int, db: Float, ratio: Float, jitter: (Int) -> Int = { 0 }): Pair<List<Float>, List<Float>> {
        val levels = MutableList(600) { base }
        val ratios = MutableList(600) { 0.2f }
        var start = 5
        var k = 0
        while (start + len < 600) {
            for (i in start until start + len) { levels[i] = db; ratios[i] = ratio }
            start += every + jitter(k++)
        }
        return levels to ratios
    }

    @Test fun `suoni gravi a ritmo di respiro sono russare e non movimento`() {
        val (l, r) = minute(every = 40, len = 10, db = 45f, ratio = 0.8f)
        val m = SleepAnalyzer.analyzeMicMinute(l, r, base)
        assertTrue(m.snoring)
        assertEquals(0f, m.score, 0.001f)
    }

    @Test fun `fruscio acuto ritmico non e russare`() {
        val (l, r) = minute(every = 40, len = 10, db = 45f, ratio = 0.2f)
        val m = SleepAnalyzer.analyzeMicMinute(l, r, base)
        assertFalse(m.snoring)
        assertTrue(m.score > 0f)
    }

    @Test fun `suoni gravi irregolari non sono russare`() {
        val gaps = listOf(5, 60, -15, 35, -10, 50, 0, 40)
        val (l, r) = minute(every = 30, len = 8, db = 45f, ratio = 0.8f) { gaps[it % gaps.size] }
        assertFalse(SleepAnalyzer.analyzeMicMinute(l, r, base).snoring)
    }

    @Test fun `silenzio niente russare`() {
        val m = SleepAnalyzer.analyzeMicMinute(List(600) { base }, List(600) { 0.5f }, base)
        assertFalse(m.snoring)
        assertEquals(0f, m.score, 0.001f)
    }
}
