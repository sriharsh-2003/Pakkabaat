package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pakkabaat.app.data.model.ProcessingStage
import com.pakkabaat.app.util.KeepScreenOn

@Composable
fun ProcessingScreen(
    draftTranscript: String?,
    isOnline: Boolean,
    stopViaTimeout: Boolean,
    stage: ProcessingStage = ProcessingStage.TRANSCRIBING,
    processingDeviceLabel: String? = null,
    isHost: Boolean = true,
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
        Spacer(Modifier.height(16.dp))

        if (errorMessage == null) {
            // "Which phone is doing this" — only the host phone ever transcribes/calls
            // Gemini; the other phone is just waiting for DraftReady over the Nearby
            // link. Making that explicit avoids the client phone's screen looking like
            // it's silently hung when it's actually meant to sit idle.
            Text(
                if (isHost) "Processing on: $processingDeviceLabel"
                else "Waiting for ${processingDeviceLabel ?: "the host"} to finish — no processing needed on this phone",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))

            val stageLabel = when (stage) {
                ProcessingStage.TRANSCRIBING -> "Step 1 of 2 — transcribing audio on-device"
                ProcessingStage.STRUCTURING -> "Step 2 of 2 — preparing the official record with Gemini"
                ProcessingStage.WAITING_FOR_HOST -> "Waiting for the host's draft"
                ProcessingStage.DONE -> "Done"
            }
            val progress = when (stage) {
                ProcessingStage.TRANSCRIBING -> 0.35f
                ProcessingStage.STRUCTURING -> 0.75f
                ProcessingStage.WAITING_FOR_HOST -> 0.5f
                ProcessingStage.DONE -> 1f
            }
            Text(stageLabel, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
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
                // Stage progress bar above already covers "what's happening" —
                // just a small spinner here so it doesn't look stalled.
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            else -> {
                Text("Saved — this will finish processing when you're back online.")
            }
        }
    }
}
