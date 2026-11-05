package com.yoav.busradar.data

import com.yoav.busradar.BoundingBox
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Fetches the buses around a point, and the line details for them. */
class BusRepository(private val api: StrideApi = StrideApi.create()) {

    /** Line details by internal line ref. Kept for the whole app session, per service day. */
    private val routes = mutableMapOf<Long, RouteInfo>()
    private var routesDay: LocalDate? = null

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

    /**
     * Line details for [buses], fetching only line refs not seen yet today. If the lookup fails the
     * map still shows the buses, just without line numbers, so errors here are swallowed.
     */
    suspend fun routesFor(buses: List<Bus>, now: Instant = Instant.now()): Map<Long, RouteInfo> {
        val today = now.atZone(ISRAEL).toLocalDate()
        if (routesDay != today) {
            routes.clear()
            routesDay = today
        }
        val missing = buses.mapNotNull { it.lineRef }.distinct().filter { it !in routes }
        if (missing.isNotEmpty()) {
            try {
                missing.chunked(100).forEach { chunk ->
                    api.gtfsRoutes(chunk.joinToString(","), today.toString(), today.toString())
                        .forEach { dto ->
                            val ref = dto.lineRef ?: return@forEach
                            dto.toRouteInfo()?.let { routes[ref] = it }
                        }
                }
            } catch (e: Exception) {
                // Leave them missing; we try again on the next refresh.
            }
        }
        return routes.toMap()
    }

    companion object {
        /**
         * How far back to look. Buses that stopped reporting stay visible (faded) for this long,
         * as decided in the plan; anything older is gone from the map.
         */
        val LOOKBACK: Duration = Duration.ofMinutes(10)

        private val ISRAEL: ZoneId = ZoneId.of("Asia/Jerusalem")
    }
}
