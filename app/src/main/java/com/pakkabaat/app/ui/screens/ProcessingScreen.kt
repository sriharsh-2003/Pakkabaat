package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ProcessingScreen(
    draftTranscript: String?,
    isOnline: Boolean,
    stopViaTimeout: Boolean
) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        if (stopViaTimeout) {
            AssistChip(onClick = {}, label = { Text("Stopped automatically after no response (60s)") })
            Spacer(Modifier.height(12.dp))
        }
        Text("Draft — not yet verified", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            Text(
                draftTranscript ?: "Preparing your draft…",
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        if (isOnline) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text("Preparing your official record…")
        } else {
            Text("Saved — this will finish processing when you're back online.")
        }
    }
}
