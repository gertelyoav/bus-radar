package com.yoav.busradar.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BusTest {
    private val now = Instant.parse("2026-11-05T17:30:00Z")
    private val lat = 32.0853
    private val lon = 34.7818

    private fun row(ref: String?, time: String?, la: Double? = lat, lo: Double? = lon) =
        VehicleLocationDto(recordedAtTime = time, lat = la, lon = lo, bearing = 90, vehicleRef = ref, lineRef = 1, operatorRef = 5)

    @Test
    fun keepsNewestPositionPerVehicle() {
        val rows = listOf(
            row("A", "2026-11-05T17:25:00+00:00", la = lat + 0.001),
            row("A", "2026-11-05T17:28:00+00:00", la = lat + 0.002),
            row("B", "2026-11-05T17:27:00+00:00"),
        )
        val buses = latestBusesWithin(rows, lat, lon, 1.0, now)
        assertEquals(listOf("A", "B"), buses.map { it.vehicleRef })
        assertEquals(lat + 0.002, buses.first().lat, 1e-9)
    }

    @Test
    fun dropsFutureTimestamps() {
        // The live feed really contains rows dated 2034.
        val rows = listOf(row("A", "2034-09-09T14:37:52+00:00"), row("A", "2026-11-05T17:29:00+00:00"))
        val buses = latestBusesWithin(rows, lat, lon, 1.0, now)
        assertEquals(Instant.parse("2026-11-05T17:29:00Z"), buses.single().recordedAt)
    }

    @Test
    fun dropsRowsWithMissingFields() {
        val rows = listOf(row(null, "2026-11-05T17:29:00+00:00"), row("B", null), row("C", "2026-11-05T17:29:00+00:00", la = null))
        assertTrue(latestBusesWithin(rows, lat, lon, 1.0, now).isEmpty())
    }

    @Test
    fun dropsBusesInTheCornersOfTheSquare() {
        // ~1.3 km diagonally: inside the bounding square, outside the 1 km circle.
        val rows = listOf(row("far", "2026-11-05T17:29:00+00:00", la = lat + 0.0085, lo = lon + 0.0100))
        assertTrue(latestBusesWithin(rows, lat, lon, 1.0, now).isEmpty())
    }

    @Test
    fun ageInMinutes() {
        val bus = latestBusesWithin(listOf(row("A", "2026-11-05T17:26:30+00:00")), lat, lon, 1.0, now).single()
        assertEquals(3, bus.ageMinutes(now))
    }

    @Test
    fun badTimestampParsesToNull() {
        assertNull(parseInstant("not a date"))
    }

    @Test
    fun parsesRealApiRecord() {
        // Trimmed copy of a real record from the API, 2026-11-05.
        val json = """[{"id":8954376633,"recorded_at_time":"2026-11-05T17:28:05+00:00","lon":34.782348,
            "lat":32.088588,"bearing":188,"velocity":0,"siri_route__line_ref":9843,
            "siri_route__operator_ref":15,"siri_ride__vehicle_ref":"31900703","siri_ride__gtfs_ride_id":null}]"""
        val rows = Gson().fromJson(json, Array<VehicleLocationDto>::class.java).toList()
        val bus = latestBusesWithin(rows, lat, lon, 1.0, now).single()
        assertEquals("31900703", bus.vehicleRef)
        assertEquals(188, bus.bearing)
        assertEquals(9843L, bus.lineRef)
    }
}
