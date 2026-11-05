package com.yoav.busradar.map

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.location.Location
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yoav.busradar.circlePoints
import com.yoav.busradar.data.Bus
import com.yoav.busradar.data.Freshness
import androidx.compose.runtime.withFrameNanos
import com.yoav.busradar.data.RouteInfo
import com.yoav.busradar.data.RouteStop
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.LineString
import java.time.Instant
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Free vector map tiles from OpenFreeMap (OpenStreetMap data). No API key needed. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val RADIUS_SOURCE = "radius-source"
private const val RADIUS_FILL = "radius-fill"
private const val RADIUS_LINE = "radius-line"
private const val ROUTE_SOURCE = "route-source"
private const val ROUTE_LINE = "route-line"
private const val STOPS_SOURCE = "stops-source"
private const val STOPS_DOTS = "stops-dots"
private const val BUS_SOURCE = "bus-source"
private const val BUS_DOTS = "bus-dots"
private const val BUS_ARROWS = "bus-arrows"
private const val BUS_LABELS = "bus-labels"
private const val ARROW_IMAGE = "bus-arrow"

/** How long a bus takes to glide from its old position to its new one after a refresh. */
private const val MOVE_ANIMATION_MS = 1_500L

private const val ZOOM_FOR_1_KM = 14.0

/**
 * The MapLibre map, wrapped for Compose. Shows the user's [location] as a blue dot, a ring of
 * [radiusKm] around it, and centres on the user the first time a location arrives and whenever
 * [recenterRequests] changes. Each of [buses] is an orange dot with its line number and an arrow
 * for its heading, coloured by how recent its position is (green, yellow, red). When new positions
 * arrive, buses glide from where they were to where they are. Tapping a bus calls [onBusClick] with its
 * vehicle ref; tapping elsewhere calls it with null. [routeStops], if not empty, are drawn as the
 * line's route (straight segments between stops); the camera fits them when [fitRouteRequests] changes.
 */
@Composable
fun BusMap(
    location: Location?,
    radiusKm: Double,
    recenterRequests: Int,
    buses: List<Bus>,
    routes: Map<Long, RouteInfo>,
    now: Instant,
    selectedVehicle: String?,
    onBusClick: (String?) -> Unit,
    routeStops: List<RouteStop>,
    fitRouteRequests: Int,
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
    val currentOnBusClick by rememberUpdatedState(onBusClick)
    val routesState = rememberUpdatedState(routes)
    val nowState = rememberUpdatedState(now)
    val selectedState = rememberUpdatedState(selectedVehicle)

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
                addRouteLayers(loaded)
                addBusLayers(loaded)
                enableUserDot(context, m, loaded)
                style = loaded
            }
            m.addOnMapClickListener { point ->
                val screen = m.projection.toScreenLocation(point)
                val touch = 28f
                val hits = m.queryRenderedFeatures(
                    RectF(screen.x - touch, screen.y - touch, screen.x + touch, screen.y + touch),
                    BUS_DOTS,
                )
                currentOnBusClick(hits.firstOrNull()?.getStringProperty("vehicle"))
                true
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

    // Where each bus is drawn right now (may be mid-animation), by vehicle ref.
    val drawn = remember { mutableMapOf<String, Pair<Double, Double>>() }

    LaunchedEffect(buses, style) {
        val s = style ?: return@LaunchedEffect
        val source = s.getSourceAs<GeoJsonSource>(BUS_SOURCE) ?: return@LaunchedEffect
        val from = buses.associate { it.vehicleRef to (drawn[it.vehicleRef] ?: (it.lat to it.lon)) }
        val start = withFrameNanos { it }
        while (true) {
            val elapsedMs = (withFrameNanos { it } - start) / 1_000_000
            val t = (elapsedMs.toDouble() / MOVE_ANIMATION_MS).coerceIn(0.0, 1.0)
            val eased = 1 - (1 - t) * (1 - t) // ease-out
            drawn.clear()
            buses.forEach { bus ->
                val (fromLat, fromLon) = from.getValue(bus.vehicleRef)
                drawn[bus.vehicleRef] = (fromLat + (bus.lat - fromLat) * eased) to (fromLon + (bus.lon - fromLon) * eased)
            }
            source.setGeoJson(busFeatures(buses, drawn, routesState.value, nowState.value, selectedState.value))
            if (t >= 1.0) break
        }
    }

    // Colours, labels and selection change without moving anything: just redraw in place.
    LaunchedEffect(routes, now, selectedVehicle, style) {
        val s = style ?: return@LaunchedEffect
        if (drawn.isEmpty()) return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(BUS_SOURCE)?.setGeoJson(busFeatures(buses, drawn, routes, now, selectedVehicle))
    }

    LaunchedEffect(routeStops, style) {
        val s = style ?: return@LaunchedEffect
        val points = routeStops.map { Point.fromLngLat(it.lon, it.lat) }
        s.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(
            if (points.size >= 2) FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points)))
            else FeatureCollection.fromFeatures(emptyList()),
        )
        s.getSourceAs<GeoJsonSource>(STOPS_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(it) }),
        )
    }

    LaunchedEffect(fitRouteRequests, routeStops) {
        if (fitRouteRequests == 0 || routeStops.size < 2) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val bounds = LatLngBounds.Builder().apply {
            routeStops.forEach { include(LatLng(it.lat, it.lon)) }
            location?.let { include(LatLng(it.latitude, it.longitude)) }
        }.build()
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
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

private fun busFeatures(
    buses: List<Bus>,
    positions: Map<String, Pair<Double, Double>>,
    routes: Map<Long, RouteInfo>,
    now: Instant,
    selectedVehicle: String?,
): FeatureCollection =
    FeatureCollection.fromFeatures(
        buses.map { bus ->
            val (lat, lon) = positions[bus.vehicleRef] ?: (bus.lat to bus.lon)
            val freshness = Freshness.of(bus.recordedAt, now)
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                addStringProperty("vehicle", bus.vehicleRef)
                addStringProperty("line", bus.lineRef?.let { routes[it]?.lineNumber } ?: "")
                addStringProperty("color", freshness.color)
                // Dark text on yellow, white on green and red, for contrast.
                addStringProperty("textColor", if (freshness == Freshness.AGING) "#222222" else "#FFFFFF")
                addBooleanProperty("selected", bus.vehicleRef == selectedVehicle)
                bus.bearing?.let { addNumberProperty("bearing", it) }
            }
        },
    )

private fun addRouteLayers(style: Style) {
    style.addSource(GeoJsonSource(ROUTE_SOURCE))
    style.addSource(GeoJsonSource(STOPS_SOURCE))
    style.addLayer(
        LineLayer(ROUTE_LINE, ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#0B5FA5"),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineOpacity(0.75f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer(STOPS_DOTS, STOPS_SOURCE).withProperties(
            PropertyFactory.circleRadius(4f),
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleStrokeColor("#0B5FA5"),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
}

private fun addBusLayers(style: Style) {
    style.addImage(ARROW_IMAGE, arrowBitmap())
    style.addSource(GeoJsonSource(BUS_SOURCE))

    // Heading arrow: a small triangle pushed out from the dot and rotated to the bus's bearing.
    style.addLayer(
        SymbolLayer(BUS_ARROWS, BUS_SOURCE).withProperties(
            PropertyFactory.iconImage(ARROW_IMAGE),
            PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconOffset(arrayOf(0f, -17f)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).withFilter(Expression.has("bearing")),
    )
    style.addLayer(
        CircleLayer(BUS_DOTS, BUS_SOURCE).withProperties(
            PropertyFactory.circleRadius(
                Expression.switchCase(Expression.get("selected"), Expression.literal(15f), Expression.literal(12f)),
            ),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            // Selected bus: thick blue ring, so its age colour stays visible.
            PropertyFactory.circleStrokeColor(
                Expression.switchCase(Expression.get("selected"), Expression.color(0xFF0B5FA5.toInt()), Expression.color(0xFFFFFFFF.toInt())),
            ),
            PropertyFactory.circleStrokeWidth(
                Expression.switchCase(Expression.get("selected"), Expression.literal(4f), Expression.literal(2f)),
            ),
        ),
    )
    style.addLayer(
        SymbolLayer(BUS_LABELS, BUS_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get("line")),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textColor(Expression.toColor(Expression.get("textColor"))),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ),
    )
}

/** A filled triangle pointing up (north before rotation). */
private fun arrowBitmap(): Bitmap {
    val size = 36
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val path = Path().apply {
        moveTo(size / 2f, 2f)
        lineTo(size - 6f, size - 8f)
        lineTo(6f, size - 8f)
        close()
    }
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF333333.toInt() })
    canvas.drawPath(
        path,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
        },
    )
    return bitmap
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
