package uiam.p5g

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uiam.p5g.qos.GatewayPingProbe

class JitterCalculatorTest {

    @Test
    fun testEmptyOrSingleSample() {
        assertEquals(0.0, GatewayPingProbe.calculateRfc3550Jitter(emptyList()), 0.001)
        assertEquals(0.0, GatewayPingProbe.calculateRfc3550Jitter(listOf(10.0)), 0.001)
    }

    @Test
    fun testConstantDelayZeroJitter() {
        val delays = listOf(15.0, 15.0, 15.0, 15.0, 15.0)
        assertEquals(0.0, GatewayPingProbe.calculateRfc3550Jitter(delays), 0.001)
    }

    @Test
    fun testJitterCalculation() {
        // Delays: 10, 26 -> diff is 16 -> jitter = (16 - 0) / 16 = 1.0
        val delays = listOf(10.0, 26.0)
        val jitter = GatewayPingProbe.calculateRfc3550Jitter(delays)
        assertEquals(1.0, jitter, 0.01)

        // Multiple fluctuating samples
        val fluctuating = listOf(12.0, 28.0, 14.0, 30.0, 11.0)
        val j2 = GatewayPingProbe.calculateRfc3550Jitter(fluctuating)
        assertTrue("Jitter should be positive", j2 > 0.0)
    }
}
