package com.yoav.busradar

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A latitude/longitude rectangle, used to ask the data API for buses in an area. */
data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    companion object {
        private const val KM_PER_DEGREE_LAT = 111.0

        /**
         * The square that encloses a circle of [radiusKm] around ([lat], [lon]).
         * Δlat ≈ r / 111 and Δlon ≈ r / (111 · cos(lat)), as in the project plan.
         */
        fun around(lat: Double, lon: Double, radiusKm: Double): BoundingBox {
            require(radiusKm > 0) { "radiusKm must be positive" }
            val dLat = radiusKm / KM_PER_DEGREE_LAT
            val dLon = radiusKm / (KM_PER_DEGREE_LAT * cos(lat * PI / 180.0))
            return BoundingBox(lat - dLat, lat + dLat, lon - dLon, lon + dLon)
        }
    }
}

/** Great-circle distance in kilometres, used to drop buses in the corners of the square. */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val p1 = lat1 * PI / 180.0
    val p2 = lat2 * PI / 180.0
    val dp = (lat2 - lat1) * PI / 180.0
    val dl = (lon2 - lon1) * PI / 180.0
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * r * asin(sqrt(a))
}

/**
 * Points (lat, lon) of a circle of [radiusKm] around ([lat], [lon]), first point repeated at
 * the end so it closes as a polygon. Used to draw the radius ring on the map.
 */
fun circlePoints(lat: Double, lon: Double, radiusKm: Double, segments: Int = 64): List<Pair<Double, Double>> {
    require(segments >= 3) { "segments must be at least 3" }
    val dLat = radiusKm / 111.0
    val dLon = radiusKm / (111.0 * cos(lat * PI / 180.0))
    val points = (0 until segments).map { i ->
        val angle = 2 * PI * i / segments
        Pair(lat + dLat * sin(angle), lon + dLon * cos(angle))
    }
    return points + points.first()
}
