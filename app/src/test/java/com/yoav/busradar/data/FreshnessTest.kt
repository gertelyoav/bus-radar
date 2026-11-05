package com.yoav.busradar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FreshnessTest {
    private val now = Instant.parse("2026-11-05T17:30:00Z")

    @Test
    fun colourBands() {
        assertEquals(Freshness.FRESH, Freshness.of(now.minusSeconds(30), now))
        assertEquals(Freshness.FRESH, Freshness.of(now.minusSeconds(119), now))
        assertEquals(Freshness.AGING, Freshness.of(now.minusSeconds(120), now))
        assertEquals(Freshness.AGING, Freshness.of(now.minusSeconds(180), now))
        assertEquals(Freshness.STALE, Freshness.of(now.minusSeconds(181), now))
    }

    @Test
    fun clockTimeIsIsraelTimeToTheSecond() {
        // 17:28:05 UTC is 19:28:05 in Israel in November (winter time, UTC+2).
        assertEquals("19:28:05", clockTime(Instant.parse("2026-11-05T17:28:05Z")))
    }

    @Test
    fun ageAsMinutesAndSeconds() {
        assertEquals("1:23", ageText(now.minusSeconds(83), now))
        assertEquals("0:00", ageText(now.plusSeconds(5), now))
    }
}
