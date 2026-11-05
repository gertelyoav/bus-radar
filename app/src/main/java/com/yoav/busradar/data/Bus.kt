package com.yoav.busradar.data

import com.yoav.busradar.distanceKm
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** A bus as the app shows it: its latest known position. */
data class Bus(
    val vehicleRef: String,
    val lat: Double,
    val lon: Double,
    val bearing: Int?,
    val recordedAt: Instant,
    val lineRef: Long?,
    val operatorRef: Long?,
) {
    fun ageMinutes(now: Instant): Long = Duration.between(recordedAt, now).toMinutes().coerceAtLeast(0)
}

/** How far in the future a timestamp may be before we treat it as corrupt (clock skew allowance). */
private val FUTURE_TOLERANCE: Duration = Duration.ofMinutes(2)

fun parseInstant(text: String?): Instant? = try {
    text?.let { OffsetDateTime.parse(it).toInstant() }
} catch (e: DateTimeParseException) {
    null
}

/**
 * Turns raw feed rows into one [Bus] per vehicle: drops rows with missing fields or impossible
 * timestamps, keeps each vehicle's newest position, and keeps only buses inside the circle.
 */
fun latestBusesWithin(
    rows: List<VehicleLocationDto>,
    centerLat: Double,
    centerLon: Double,
    radiusKm: Double,
    now: Instant,
): List<Bus> = rows.mapNotNull { row ->
    val ref = row.vehicleRef ?: return@mapNotNull null
    val lat = row.lat ?: return@mapNotNull null
    val lon = row.lon ?: return@mapNotNull null
    val time = parseInstant(row.recordedAtTime) ?: return@mapNotNull null
    if (time.isAfter(now.plus(FUTURE_TOLERANCE))) return@mapNotNull null
    Bus(ref, lat, lon, row.bearing, time, row.lineRef, row.operatorRef)
}
    .groupBy { it.vehicleRef }
    .map { (_, positions) -> positions.maxBy { it.recordedAt } }
    .filter { distanceKm(centerLat, centerLon, it.lat, it.lon) <= radiusKm }
    .sortedBy { it.vehicleRef }
