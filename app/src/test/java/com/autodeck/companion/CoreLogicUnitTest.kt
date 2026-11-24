package com.autodeck.companion

import com.autodeck.companion.model.PrayerTimesDay
import com.autodeck.companion.util.DistanceCalculator
import com.autodeck.companion.util.LrcParser
import com.autodeck.companion.util.PrayerCalculatorUtil
import com.autodeck.companion.util.QiblaCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class CoreLogicUnitTest {

    @Test
    fun lrcParser_parsesTimestampsAndFindsActiveLine() {
        val sample = """
            [00:10.50] First line
            [00:25.00] Second line
            [01:02.25] Third line
        """.trimIndent()

        val lines = LrcParser.parse(sample)
        assertEquals(3, lines.size)
        assertEquals(10_500L, lines[0].timestampMs)
        assertEquals(0, LrcParser.activeLineIndex(lines, 15_000L))
        assertEquals(1, LrcParser.activeLineIndex(lines, 30_000L))
        assertEquals(2, LrcParser.activeLineIndex(lines, 65_000L))
    }

    @Test
    fun prayerCalculator_identifiesNextPrayerAndCountdown() {
        val day = PrayerTimesDay("SGR01", "2026-11-24", "05:50", "07:00", "13:05", "16:28", "19:02", "20:16", "JAKIM")
        val next = PrayerCalculatorUtil.findNextPrayer(day, LocalTime.of(14, 28))
        assertEquals("Asar", next.name)
        assertEquals("16:28", next.timeText)
        assertEquals("2h 0m", next.remainingText)
    }

    @Test
    fun qiblaCalculator_shahAlamBearingIsApprox292Degrees() {
        val bearing = QiblaCalculator.bearingDegrees(3.0738, 101.5183)
        assertTrue("Expected ~292.5 deg, got $bearing", bearing in 290.0..295.0)
    }

    @Test
    fun distanceCalculator_shahAlamToKlccApprox23Km() {
        val km = DistanceCalculator.haversineKm(3.0738, 101.5183, 3.1579, 101.7116)
        assertTrue("Expected ~22-25 km, got $km", km in 20.0..27.0)
    }
}
