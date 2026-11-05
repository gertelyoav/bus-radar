package com.yoav.busradar.map

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yoav.busradar.circlePoints
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Free vector map tiles from OpenFreeMap (OpenStreetMap data). No API key needed. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val RADIUS_SOURCE = "radius-source"
private const val RADIUS_FILL = "radius-fill"
private const val RADIUS_LINE = "radius-line"

private const val ZOOM_FOR_1_KM = 14.0

/**
 * The MapLibre map, wrapped for Compose. Shows the user's [location] as a blue dot, a ring of
 * [radiusKm] around it, and centres on the user the first time a location arrives and whenever
 * [recenterRequests] changes.
 */
@Composable
fun BusMap(
    location: Location?,
    radiusKm: Double,
    recenterRequests: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var hasCentred by remember { mutableStateOf(false) }

    // Forward the screen's lifecycle to the MapView, as MapLibre requires.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            // Start over central Israel until the first location fix arrives.
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(32.0853, 34.7818), 11.0))
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
                addRadiusLayers(loaded)
                enableUserDot(context, m, loaded)
                style = loaded
            }
            map = m
        }
    }

    // Move the blue dot and the radius ring whenever the location or radius changes.
    LaunchedEffect(location, radiusKm, style) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        val loc = location ?: return@LaunchedEffect
        m.locationComponent.forceLocationUpdate(loc)
        s.getSourceAs<GeoJsonSource>(RADIUS_SOURCE)?.setGeoJson(radiusPolygon(loc, radiusKm))
        if (!hasCentred) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), zoomFor(radiusKm)))
            hasCentred = true
        }
    }

    LaunchedEffect(recenterRequests) {
        if (recenterRequests == 0) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val loc = location ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), zoomFor(radiusKm)))
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Zoom level that keeps the whole radius ring on a phone screen. */
private fun zoomFor(radiusKm: Double): Double =
    ZOOM_FOR_1_KM - kotlin.math.log2(radiusKm.coerceAtLeast(0.1))

private fun radiusPolygon(loc: Location, radiusKm: Double): Polygon {
    val ring = circlePoints(loc.latitude, loc.longitude, radiusKm).map { (lat, lon) ->
        Point.fromLngLat(lon, lat)
    }
    return Polygon.fromLngLats(listOf(ring))
}

private fun addRadiusLayers(style: Style) {
    style.addSource(GeoJsonSource(RADIUS_SOURCE))
    style.addLayer(
        FillLayer(RADIUS_FILL, RADIUS_SOURCE).withProperties(
            PropertyFactory.fillColor("#1B8A5A"),
            PropertyFactory.fillOpacity(0.08f),
        ),
    )
    style.addLayer(
        LineLayer(RADIUS_LINE, RADIUS_SOURCE).withProperties(
            PropertyFactory.lineColor("#1B8A5A"),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineOpacity(0.7f),
        ),
    )
}

/** Blue "you are here" dot, fed from our own FusedLocationProvider updates. */
@SuppressLint("MissingPermission")
private fun enableUserDot(context: android.content.Context, map: MapLibreMap, style: Style) {
    val options = LocationComponentActivationOptions.builder(context, style)
        .useDefaultLocationEngine(false)
        .build()
    map.locationComponent.apply {
        activateLocationComponent(options)
        isLocationComponentEnabled = true
        cameraMode = CameraMode.NONE
        renderMode = RenderMode.NORMAL
    }
}
