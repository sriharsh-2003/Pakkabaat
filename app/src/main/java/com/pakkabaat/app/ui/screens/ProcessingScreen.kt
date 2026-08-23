package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pakkabaat.app.util.KeepScreenOn

@Composable
fun ProcessingScreen(
    draftTranscript: String?,
    isOnline: Boolean,
    stopViaTimeout: Boolean,
    errorMessage: String? = null
) {
    // On-device transcription + the Gemini structuring call can both take a while —
    // keep the screen alive so the user isn't left staring at a black screen wondering
    // if the app has hung.
    KeepScreenOn()
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
        when {
            // Previously a failure here (bad/missing API key, Gemini error, parsing
            // failure) was swallowed silently by the worker and just left this screen
            // spinning forever with no document ever arriving — this is almost
            // certainly what "processing doesn't seem to work" was actually seeing.
            errorMessage != null -> {
                Text(
                    "Something went wrong preparing the official record:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(errorMessage, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your draft above is saved. Check your Gemini API key in Settings, or your connection, and try again.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            isOnline -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Preparing your official record…")
            }
            else -> {
                Text("Saved — this will finish processing when you're back online.")
            }
        }
    }
}
