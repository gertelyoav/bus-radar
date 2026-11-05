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
