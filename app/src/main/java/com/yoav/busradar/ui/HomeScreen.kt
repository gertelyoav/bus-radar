package com.yoav.busradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yoav.busradar.BuildConfigInfo

/** Placeholder screen for the project skeleton. The map replaces it in the next stage. */
@Composable
fun HomeScreen() {
    MaterialTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Bus Radar", style = MaterialTheme.typography.headlineMedium)
                Text("השלד עובד. המפה מגיעה בשלב הבא.", style = MaterialTheme.typography.bodyLarge)
                Text("גרסה ${BuildConfigInfo.VERSION}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() = HomeScreen()
