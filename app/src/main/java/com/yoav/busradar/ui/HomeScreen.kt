package com.yoav.busradar.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yoav.busradar.location.hasLocationPermission
import com.yoav.busradar.location.rememberLocation
import com.yoav.busradar.map.BusMap

/** Default search radius, per the plan. Becomes user-adjustable in version 3. */
const val DEFAULT_RADIUS_KM = 1.0

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(hasLocationPermission(context)) }
    var asked by remember { mutableStateOf(false) }
    var recenter by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        asked = true
        permitted = result.values.any { it }
    }

    LaunchedEffect(Unit) {
        if (!permitted) {
            launcher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    val location by rememberLocation(enabled = permitted)

    MaterialTheme {
        Scaffold(
            floatingActionButton = {
                if (permitted) {
                    SmallFloatingActionButton(onClick = { recenter++ }) { Text("◎") }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                BusMap(
                    location = location,
                    radiusKm = DEFAULT_RADIUS_KM,
                    recenterRequests = recenter,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!permitted && asked) {
                    PermissionNeeded(
                        onRetry = {
                            launcher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
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
