package com.yoav.busradar.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * How recent a bus's position is, shown as its colour on the map.
 * The feed itself runs about 1.5 minutes behind, so "fresh" covers up to 2 minutes.
 */
enum class Freshness(val color: String) {
    FRESH("#2B9348"), // green: under 2 minutes
    AGING("#F2C200"), // yellow: 2 to 3 minutes
    STALE("#D62828"), // red: over 3 minutes
    ;

    companion object {
        val FRESH_UNTIL: Duration = Duration.ofMinutes(2)
        val AGING_UNTIL: Duration = Duration.ofMinutes(3)

        fun of(recordedAt: Instant, now: Instant): Freshness {
            val age = Duration.between(recordedAt, now)
            return when {
                age < FRESH_UNTIL -> FRESH
                age <= AGING_UNTIL -> AGING
                else -> STALE
            }
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Jerusalem"))

/** Exact Israel-time clock reading of a position, to the second, e.g. "19:42:07". */
fun clockTime(instant: Instant): String = TIME_FORMAT.format(instant)

/** Age as "m:ss", e.g. "1:23". */
fun ageText(recordedAt: Instant, now: Instant): String {
    val seconds = Duration.between(recordedAt, now).seconds.coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
