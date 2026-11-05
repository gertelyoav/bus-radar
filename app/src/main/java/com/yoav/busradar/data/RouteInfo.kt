package com.yoav.busradar.data

/** What a rider wants to know about a bus's line. */
data class RouteInfo(
    val lineNumber: String,
    val operator: String?,
    val destination: String?,
)

/**
 * Destination from a GTFS long name such as
 * "קריית חינוך-תל אביב יפו<->ת.רק''ל הקוממיות/דרך בגין-בת ים-2#":
 * the part after "<->", minus the trailing direction/alternative code, with the city after a comma.
 * Returns null when the name doesn't follow that pattern.
 */
fun destinationFromLongName(longName: String?): String? {
    if (longName == null) return null
    val to = longName.substringAfter("<->", missingDelimiterValue = "").trim()
    if (to.isEmpty()) return null
    // Drop the "-2#" / "-1" / "-3ב" style suffix (direction number plus alternative marker).
    val withoutCode = to.replace(Regex("-\\d+\\S*$"), "").trim()
    val stop = withoutCode.substringBeforeLast('-', missingDelimiterValue = withoutCode).trim()
    val city = withoutCode.substringAfterLast('-', missingDelimiterValue = "").trim()
    return if (city.isEmpty() || city == stop) stop else "$stop, $city"
}

fun GtfsRouteDto.toRouteInfo(): RouteInfo? {
    val number = shortName?.takeIf { it.isNotBlank() } ?: return null
    return RouteInfo(number, agencyName, destinationFromLongName(longName))
}
