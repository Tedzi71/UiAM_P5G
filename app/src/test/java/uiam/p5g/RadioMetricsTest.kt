package uiam.p5g

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uiam.p5g.data.model.MeasurementSample
import uiam.p5g.data.model.RadioMetrics
import uiam.p5g.telemetry.RadioCollector

class RadioMetricsTest {

    @Test
    fun testBandEstimation() {
        val n78 = RadioCollector.estimateNrBand(640000)
        assertTrue(n78.contains("n78"))

        val n28 = RadioCollector.estimateNrBand(155000)
        assertTrue(n28.contains("n28"))

        val mmWave = RadioCollector.estimateNrBand(2060000)
        assertTrue(mmWave.contains("n258"))
    }

    @Test
    fun testMeasurementSampleDisplayValues() {
        val sample5G = MeasurementSample(
            sessionId = 1,
            networkType = "5G_SA",
            nrBand = "n78",
            nrPci = 104,
            nrSsRsrp = -82,
            nrSsSinr = 18,
            lteBand = "B3",
            ltePci = 55,
            lteRsrp = -95,
            lteSinr = 10
        )

        assertEquals("n78", sample5G.displayBand)
        assertEquals(104, sample5G.displayPci)
        assertEquals(-82, sample5G.displayRsrp)
        assertEquals(18, sample5G.displaySinr)

        val sampleLte = MeasurementSample(
            sessionId = 2,
            networkType = "LTE",
            nrBand = "",
            nrPci = -1,
            nrSsRsrp = -140,
            nrSsSinr = -23,
            lteBand = "B7",
            ltePci = 210,
            lteRsrp = -92,
            lteSinr = 8
        )

        assertEquals("B7", sampleLte.displayBand)
        assertEquals(210, sampleLte.displayPci)
        assertEquals(-92, sampleLte.displayRsrp)
        assertEquals(8, sampleLte.displaySinr)
    }
}
