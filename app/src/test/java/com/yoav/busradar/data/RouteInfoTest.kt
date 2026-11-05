package com.yoav.busradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteInfoTest {
    @Test
    fun destinationFromRealLongName() {
        // Real value from the API for line 26 (Metropoline), 2026-11-05.
        assertEquals(
            "ת.רק''ל הקוממיות/דרך בגין, בת ים",
            destinationFromLongName("קריית חינוך-תל אביב יפו<->ת.רק''ל הקוממיות/דרך בגין-בת ים-2#"),
        )
    }

    @Test
    fun destinationWithoutCityKeepsStop() {
        assertEquals("תחנה מרכזית", destinationFromLongName("א-ב<->תחנה מרכזית-1"))
    }

    @Test
    fun unexpectedFormatGivesNull() {
        assertNull(destinationFromLongName("no arrow here"))
        assertNull(destinationFromLongName(null))
    }

    @Test
    fun routeInfoNeedsALineNumber() {
        assertNull(GtfsRouteDto(lineRef = 1, shortName = " ").toRouteInfo())
        val info = GtfsRouteDto(lineRef = 1, shortName = "26", longName = "x-y<->z-w-1#", agencyName = "מטרופולין").toRouteInfo()
        assertEquals(RouteInfo("26", "מטרופולין", "z, w"), info)
    }
}
