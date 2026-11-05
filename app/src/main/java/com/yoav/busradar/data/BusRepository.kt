package com.yoav.busradar.data

import com.yoav.busradar.BoundingBox
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Fetches the buses around a point. */
class BusRepository(private val api: StrideApi = StrideApi.create()) {

    suspend fun busesAround(lat: Double, lon: Double, radiusKm: Double, now: Instant = Instant.now()): List<Bus> {
        val box = BoundingBox.around(lat, lon, radiusKm)
        val rows = api.vehicleLocations(
            minLat = box.minLat,
            maxLat = box.maxLat,
            minLon = box.minLon,
            maxLon = box.maxLon,
            from = now.minus(LOOKBACK).truncatedTo(ChronoUnit.SECONDS).toString(),
            to = now.plus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.SECONDS).toString(),
        )
        return latestBusesWithin(rows, lat, lon, radiusKm, now)
    }

    companion object {
        /**
         * How far back to look. Buses that stopped reporting stay visible (faded) for this long,
         * as decided in the plan; anything older is gone from the map.
         */
        val LOOKBACK: Duration = Duration.ofMinutes(10)
    }
}
