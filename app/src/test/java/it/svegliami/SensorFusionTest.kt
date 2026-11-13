package it.svegliami

import it.svegliami.sensors.SensorFusion
import it.svegliami.sensors.SensorSignal
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorFusionTest {
    @Test fun `reliable modality has more influence`() {
        val result = SensorFusion.combine(SensorSignal(100f, 1f), SensorSignal(0f, 0.25f))
        assertEquals(80f, result.activityScore, 0.01f)
        assertEquals(0.625f, result.confidence, 0.01f)
    }

    @Test fun `unavailable modality is ignored`() {
        val result = SensorFusion.combine(SensorSignal(30f, 0.5f), SensorSignal(100f, 0f))
        assertEquals(30f, result.activityScore, 0.01f)
        assertEquals(0.5f, result.confidence, 0.01f)
    }

    @Test fun `no sensor data returns zero confidence`() {
        val result = SensorFusion.combine(null, SensorSignal(70f, 0f))
        assertEquals(0f, result.activityScore, 0f)
        assertEquals(0f, result.confidence, 0f)
    }
}
