package com.yoav.busradar.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The parts of the Open Bus Stride API (Hasadna) that the app uses.
 * Docs: https://open-bus-stride-api.hasadna.org.il/docs
 */
interface StrideApi {
    @GET("siri_vehicle_locations/list")
    suspend fun vehicleLocations(
        @Query("lat__greater_or_equal") minLat: Double,
        @Query("lat__lower_or_equal") maxLat: Double,
        @Query("lon__greater_or_equal") minLon: Double,
        @Query("lon__lower_or_equal") maxLon: Double,
        // Always bounded on both sides: the feed contains a few rows with timestamps years in the future.
        @Query("recorded_at_time_from") from: String,
        @Query("recorded_at_time_to") to: String,
        @Query("order_by") orderBy: String = "recorded_at_time desc",
        @Query("limit") limit: Int = 2000,
    ): List<VehicleLocationDto>

    /** Static route info (line number, operator, names) for the given internal line refs. */
    @GET("gtfs_routes/list")
    suspend fun gtfsRoutes(
        @Query("line_refs") lineRefs: String, // comma-separated
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
        @Query("limit") limit: Int = 1000,
    ): List<GtfsRouteDto>

    /** Live positions of every bus on the given line(s), anywhere in the country. */
    @GET("siri_vehicle_locations/list")
    suspend fun vehicleLocationsForLine(
        @Query("siri_routes__line_ref") lineRefs: String,
        @Query("recorded_at_time_from") from: String,
        @Query("recorded_at_time_to") to: String,
        @Query("order_by") orderBy: String = "recorded_at_time desc",
        @Query("limit") limit: Int = 2000,
    ): List<VehicleLocationDto>

    /** Planned rides of a line on a given day; we use one to get the line's stop sequence. */
    @GET("gtfs_rides/list")
    suspend fun gtfsRides(
        @Query("gtfs_route__line_refs") lineRefs: String,
        @Query("gtfs_route__date_from") dateFrom: String,
        @Query("gtfs_route__date_to") dateTo: String,
        @Query("limit") limit: Int = 1,
    ): List<GtfsRideDto>

    @GET("gtfs_ride_stops/list")
    suspend fun gtfsRideStops(
        @Query("gtfs_ride_ids") rideIds: String,
        @Query("order_by") orderBy: String = "stop_sequence asc",
        @Query("limit") limit: Int = 500,
    ): List<GtfsRideStopDto>

    companion object {
        const val BASE_URL = "https://open-bus-stride-api.hasadna.org.il/"

        fun create(): StrideApi = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StrideApi::class.java)
    }
}

/** One reported position of one bus. Fields can be null in the feed, so all are nullable here. */
data class VehicleLocationDto(
    @SerializedName("recorded_at_time") val recordedAtTime: String? = null,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lon") val lon: Double? = null,
    @SerializedName("bearing") val bearing: Int? = null,
    @SerializedName("velocity") val velocity: Int? = null,
    @SerializedName("siri_ride__vehicle_ref") val vehicleRef: String? = null,
    @SerializedName("siri_route__line_ref") val lineRef: Long? = null,
    @SerializedName("siri_route__operator_ref") val operatorRef: Long? = null,
)

data class GtfsRouteDto(
    @SerializedName("line_ref") val lineRef: Long? = null,
    @SerializedName("route_short_name") val shortName: String? = null,
    @SerializedName("route_long_name") val longName: String? = null,
    @SerializedName("agency_name") val agencyName: String? = null,
)

data class GtfsRideDto(
    @SerializedName("id") val id: Long? = null,
)

data class GtfsRideStopDto(
    @SerializedName("stop_sequence") val sequence: Int? = null,
    @SerializedName("gtfs_stop__lat") val lat: Double? = null,
    @SerializedName("gtfs_stop__lon") val lon: Double? = null,
    @SerializedName("gtfs_stop__name") val name: String? = null,
)
