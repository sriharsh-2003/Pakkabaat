package com.pakkabaat.app.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pakkabaat.app.data.db.AudioRecordingEntity
import com.pakkabaat.app.data.db.CertificateEntity
import com.pakkabaat.app.data.db.StructuredDocumentEntity
import com.pakkabaat.app.pdf.PdfExporter
import java.io.File
import kotlinx.coroutines.delay

// PlayArrow ships in the small "core" icon set bundled with material3, but Pause does
// not — it's only in material-icons-extended, a separate and fairly large dependency
// this project doesn't otherwise need. Rather than add that whole library for one icon,
// build the two-bar pause glyph by hand as a tiny custom vector.
private val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(6f, 5f)
            horizontalLineTo(10f)
            verticalLineTo(19f)
            horizontalLineTo(6f)
            close()
        }
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(14f, 5f)
            horizontalLineTo(18f)
            verticalLineTo(19f)
            horizontalLineTo(14f)
            close()
        }
    }.build()
}

@Composable
fun DocumentScreen(
    document: StructuredDocumentEntity?,
    certificate: CertificateEntity?,
    audioRecording: AudioRecordingEntity? = null
) {
    val context = LocalContext.current
    var proofExpanded by remember { mutableStateOf(false) }

    if (document == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Agreement record", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Text(document.conditions, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(20.dp))

        // In-app playback of the recording that backs this draft — previously this only
        // handed off to whatever external app the phone had for .wav files, with no way
        // to just listen to it inside PakkaBaat itself.
        if (audioRecording != null) {
            AudioPlayerCard(audioRecording)
            Spacer(Modifier.height(20.dp))
        }

        Button(
            onClick = {
                val file = PdfExporter.export(context, document, certificate)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Download PDF") }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { proofExpanded = !proofExpanded }) {
            Text(if (proofExpanded) "Hide proof details" else "Proof details")
        }
        if (proofExpanded && certificate != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Recording hash (SHA-256): ${certificate.audioSha256}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text("Transcript hash (SHA-256): ${certificate.transcriptSha256}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(certificate.certificateStatement, style = MaterialTheme.typography.bodySmall)
                    // Share/open-in-another-app is still useful (backing it up, sending it
                    // to someone), kept alongside the in-app player above rather than
                    // replaced by it.
                    if (audioRecording != null) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                val file = File(audioRecording.storagePath)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "audio/wav")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(intent) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Share / open in another app") }
                    }
                }
            }
        }
    }
}

/** Simple in-app play/pause + seek bar for the recording, backed by a plain MediaPlayer
 *  (the file is a local on-device .wav, so nothing fancier than MediaPlayer is needed). */
@Composable
private fun AudioPlayerCard(audioRecording: AudioRecordingEntity) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }
    var loadError by remember { mutableStateOf(false) }

    // Build the player once per recording file, and always release it when this screen
    // goes away — leaking a MediaPlayer keeps the audio decoder resource held forever.
    DisposableEffect(audioRecording.storagePath) {
        val player = MediaPlayer()
        runCatching {
            player.setDataSource(audioRecording.storagePath)
            player.setOnCompletionListener {
                isPlaying = false
                positionMs = 0
            }
            player.prepare()
            durationMs = player.duration
        }.onFailure { loadError = true }
        mediaPlayer = player
        onDispose {
            player.release()
            mediaPlayer = null
        }
    }

    // Poll playback position while playing, to move the slider — MediaPlayer has no
    // position-changed callback of its own.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = mediaPlayer?.currentPosition ?: 0
            delay(200)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Recording", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (loadError) {
                Text(
                    "Couldn't load the recording for playback.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val player = mediaPlayer ?: return@IconButton
                        if (isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            // If playback already reached the end, restart from the beginning
                            // rather than doing nothing on the next tap.
                            if (player.currentPosition >= player.duration) player.seekTo(0)
                            player.start()
                            isPlaying = true
                        }
                    }) {
                        Icon(
                            if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = positionMs.toFloat(),
                        onValueChange = { newValue ->
                            positionMs = newValue.toInt()
                            mediaPlayer?.seekTo(newValue.toInt())
                        },
                        valueRange = 0f..(durationMs.coerceAtLeast(1)).toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

