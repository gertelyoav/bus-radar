package com.yoav.busradar.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yoav.busradar.data.Bus
import com.yoav.busradar.data.BusRepository
import com.yoav.busradar.data.RouteInfo
import com.yoav.busradar.data.RouteStop
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.yoav.busradar.location.hasLocationPermission
import com.yoav.busradar.location.rememberLocation
import com.yoav.busradar.map.BusMap
import kotlinx.coroutines.launch
import java.time.Instant

/** Default search radius, per the plan. Becomes user-adjustable in version 3. */
const val DEFAULT_RADIUS_KM = 1.0

/** Auto refresh interval while the app is on screen, per the plan. */
const val REFRESH_INTERVAL_MS = 30_000L

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { BusRepository() }

    var permitted by remember { mutableStateOf(hasLocationPermission(context)) }
    var asked by remember { mutableStateOf(false) }
    var recenter by remember { mutableIntStateOf(0) }

    var buses by remember { mutableStateOf<List<Bus>>(emptyList()) }
    var fetchedAt by remember { mutableStateOf(Instant.now()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var routes by remember { mutableStateOf<Map<Long, RouteInfo>>(emptyMap()) }
    var selected by remember { mutableStateOf<String?>(null) }
    // When set, the map shows every bus on this line (any distance) instead of the buses around me.
    var lineMode by remember { mutableStateOf<Long?>(null) }
    var routeStops by remember { mutableStateOf<List<RouteStop>>(emptyList()) }
    var fitRoute by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        asked = true
        permitted = result.values.any { it }
    }
    val askPermission = {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) { if (!permitted) askPermission() }

    val location by rememberLocation(enabled = permitted)

    // Each refresh gets a number; only the newest one may update the screen, so a slow answer for
    // the old mode can't overwrite the new one after switching to (or from) a line.
    var generation by remember { mutableIntStateOf(0) }

    fun refresh() {
        val loc = location ?: return
        val gen = ++generation
        loading = true
        scope.launch {
            try {
                val now = Instant.now()
                val line = lineMode
                val fresh = if (line != null) {
                    repository.busesOnLine(line, now)
                } else {
                    repository.busesAround(loc.latitude, loc.longitude, DEFAULT_RADIUS_KM, now)
                }
                if (gen != generation) return@launch
                buses = fresh
                fetchedAt = now
                error = null
                val names = repository.routesFor(fresh, now)
                if (gen == generation) routes = names
            } catch (e: Exception) {
                if (gen == generation) error = "לא הצלחתי לקבל נתונים מממשק הסדנא. נסה לרענן שוב."
            } finally {
                if (gen == generation) loading = false
            }
        }
    }

    // Refresh every 30 s, but only while the app is on screen (stops in the background to spare
    // the volunteer-run API). Starts as soon as the first location arrives.
    LaunchedEffect(location != null, lifecycleOwner) {
        if (location == null) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                hasLoadedOnce = true
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    val selectedBus = buses.firstOrNull { it.vehicleRef == selected }

    // Draw the route of the selected bus's line, or of the line being followed.
    val routeLineRef = lineMode ?: selectedBus?.lineRef
    LaunchedEffect(routeLineRef) {
        routeStops = emptyList()
        if (routeLineRef == null) return@LaunchedEffect
        routeStops = try {
            repository.routeStops(routeLineRef)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun enterLineMode(lineRef: Long) {
        lineMode = lineRef
        buses = emptyList()
        fitRoute++
        refresh()
    }

    fun exitLineMode() {
        lineMode = null
        selected = null
        buses = emptyList()
        recenter++
        refresh()
    }

    MaterialTheme {
        Scaffold(
            floatingActionButton = {
                if (permitted) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                        SmallFloatingActionButton(onClick = { recenter++ }) { Text("◎") }
                        SmallFloatingActionButton(onClick = { refresh() }) {
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("⟳")
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                BusMap(
                    location = location,
                    radiusKm = DEFAULT_RADIUS_KM,
                    recenterRequests = recenter,
                    buses = buses,
                    routes = routes,
                    now = fetchedAt,
                    selectedVehicle = selected,
                    onBusClick = { selected = it },
                    routeStops = routeStops,
                    fitRouteRequests = fitRoute,
                    modifier = Modifier.fillMaxSize(),
                )
                if (selectedBus != null) {
                    BusDetails(
                        bus = selectedBus,
                        route = selectedBus.lineRef?.let { routes[it] },
                        now = Instant.now(),
                        inLineMode = lineMode != null,
                        onShowLine = selectedBus.lineRef?.let { ref -> { enterLineMode(ref) } },
                        onClose = { selected = null },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    )
                }
                if (permitted && hasLoadedOnce) {
                    val lineName = lineMode?.let { routes[it]?.lineNumber }
                    val text = when {
                        error != null -> error!!
                        loading && buses.isEmpty() -> "טוען אוטובוסים…"
                        lineMode != null -> "קו ${lineName ?: ""}: ${buses.size} אוטובוסים בדרך"
                        else -> "${buses.size} אוטובוסים ברדיוס"
                    }
                    StatusBar(
                        text = text,
                        isError = error != null,
                        onExitLine = if (lineMode != null) ({ exitLineMode() }) else null,
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    )
                }
                if (!permitted && asked) {
                    PermissionNeeded(onRetry = askPermission, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun StatusBar(text: String, isError: Boolean, onExitLine: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            if (onExitLine != null) {
                TextButton(onClick = onExitLine) { Text("חזרה לסביבה שלי") }
            }
        }
    }
}

@Composable
private fun PermissionNeeded(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "כדי להציג אוטובוסים סביבך, האפליקציה צריכה גישה למיקום.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("לאשר גישה למיקום") }
    }
}

@Composable
private fun BusDetails(
    bus: Bus,
    route: RouteInfo?,
    now: Instant,
    inLineMode: Boolean,
    onShowLine: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    route?.let { "קו ${it.lineNumber}" } ?: "קו לא ידוע",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("סגור") }
            }
            route?.destination?.let { Text("יעד: $it", style = MaterialTheme.typography.bodyLarge) }
            route?.operator?.let { Text("מפעיל: $it", style = MaterialTheme.typography.bodyMedium) }
            Text(updatedAgo(bus, now), style = MaterialTheme.typography.bodyMedium)
            if (!inLineMode && onShowLine != null) {
                Button(onClick = onShowLine, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("הצג את כל האוטובוסים בקו")
                }
            }
        }
    }
}

private fun updatedAgo(bus: Bus, now: Instant): String {
    val seconds = java.time.Duration.between(bus.recordedAt, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "עודכן לפני פחות מדקה"
        seconds < 120 -> "עודכן לפני דקה"
        else -> "עודכן לפני ${seconds / 60} דקות"
    }
}
