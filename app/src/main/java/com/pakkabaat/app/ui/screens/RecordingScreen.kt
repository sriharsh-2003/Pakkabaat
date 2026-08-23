package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pakkabaat.app.util.KeepScreenOn

@Composable
fun RecordingScreen(
    elapsedSeconds: Int,
    stopRequestedByMe: Boolean,
    stopRequestedByOtherName: String?,
    stopTimeoutRemaining: Int?,
    onStop: () -> Unit,
    onConfirmStop: () -> Unit
) {
    // Recording can run for many minutes with no touch input at all — never let the
    // screen lock mid-recording.
    KeepScreenOn()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("● Recording", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(formatElapsed(elapsedSeconds), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(48.dp))

        when {
            stopRequestedByOtherName != null -> {
                Card {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$stopRequestedByOtherName wants to stop recording")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onConfirmStop) { Text("Confirm stop") }
                    }
                }
            }
            stopRequestedByMe -> {
                Text("You asked to stop. Waiting for the other person to confirm…")
                stopTimeoutRemaining?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Auto-stop in ${it}s if there is no response", style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) {
                    Text("Stop")
                }
            }
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
