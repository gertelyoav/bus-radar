package com.yoav.busradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundingBoxTest {
    // Tel Aviv, roughly
    private val lat = 32.08
    private val lon = 34.78

    @Test
    fun boxIsCentredOnTheUser() {
        val box = BoundingBox.around(lat, lon, 1.0)
        assertEquals(lat, (box.minLat + box.maxLat) / 2, 1e-9)
        assertEquals(lon, (box.minLon + box.maxLon) / 2, 1e-9)
    }

    @Test
    fun edgesAreAboutOneRadiusAway() {
        val box = BoundingBox.around(lat, lon, 1.0)
        assertEquals(1.0, distanceKm(lat, lon, box.maxLat, lon), 0.02)
        assertEquals(1.0, distanceKm(lat, lon, lat, box.maxLon), 0.02)
    }

    @Test
    fun longitudeSpanIsWiderThanLatitudeSpanAwayFromEquator() {
        val box = BoundingBox.around(lat, lon, 1.0)
        assertTrue(box.maxLon - box.minLon > box.maxLat - box.minLat)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveRadius() {
        BoundingBox.around(lat, lon, 0.0)
    }

    @Test
    fun distanceBetweenSamePointIsZero() {
        assertEquals(0.0, distanceKm(lat, lon, lat, lon), 1e-9)
    }
}
