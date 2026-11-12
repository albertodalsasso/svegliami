package it.svegliami

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RainDeciderTest {
    // Finestra 06:30–07:00 (secondi relativi, per semplicità)
    private val from = 6 * 3600L + 1800
    private val to = 7 * 3600L

    private fun q(endH: Int, endM: Int, v: Double) = (endH * 3600L + endM * 60).let { Slot(it - 900, it, v) }
    private fun h(endH: Int, v: Double) = (endH * 3600L).let { Slot(it - 3600, it, v) }

    @Test fun `pioggia a 15 minuti nella finestra`() {
        val f = Forecast(listOf(q(6, 30, 0.0), q(6, 45, 0.4), q(7, 0, 0.0)), emptyList(), listOf(h(7, 10.0)))
        assertTrue(RainDecider.decide(f, from, to, 50).rainy)
    }

    @Test fun `pioggia solo dopo la sveglia normale non anticipa`() {
        val f = Forecast(listOf(q(7, 0, 0.0), q(7, 15, 2.0)), emptyList(), listOf(h(7, 10.0), h(8, 90.0)))
        assertFalse(RainDecider.decide(f, from, to, 50).rainy)
    }

    @Test fun `probabilita sopra soglia anticipa`() {
        val f = Forecast(emptyList(), listOf(h(7, 0.0)), listOf(h(7, 70.0)))
        assertTrue(RainDecider.decide(f, from, to, 60).rainy)
        assertFalse(RainDecider.decide(f, from, to, 80).rainy)
    }

    @Test fun `senza dati non anticipa`() {
        assertFalse(RainDecider.decide(Forecast(emptyList(), emptyList(), emptyList()), from, to, 50).rainy)
    }
}
