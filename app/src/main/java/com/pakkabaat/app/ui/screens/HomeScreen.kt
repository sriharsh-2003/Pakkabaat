package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pakkabaat.app.data.db.SessionEntity
import com.pakkabaat.app.data.model.SessionStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    sessions: List<SessionEntity>,
    onRecordNew: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<SessionEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PakkaBaat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // The one thing most people came here to do - big, unmissable, hard to miss even
        // for someone unfamiliar with apps in general.
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                onClick = onRecordNew,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(140.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Record a new agreement",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Record a new agreement", style = MaterialTheme.typography.titleMedium)
        }

        HorizontalDivider()

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Recent conversations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No agreements recorded yet. Tap the button above to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                items(sessions, key = { it.sessionId }) { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        onClick = { onOpenSession(session.sessionId) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${session.partyAName.ifBlank { "Party A" }} & ${session.partyBName?.ifBlank { "Party B" } ?: "Party B"}",
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(statusLabel(session.status), style = MaterialTheme.typography.bodySmall)
                                session.startedAt?.let {
                                    Text(
                                        SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(it)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { pendingDelete = session }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete this conversation")
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this conversation?") },
            text = { Text("This removes the recording, transcript, and document from this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(session.sessionId)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

private fun statusLabel(status: SessionStatus): String = when (status) {
    SessionStatus.PENDING_CONSENT -> "Waiting for consent"
    SessionStatus.RECORDING -> "Recording"
    SessionStatus.STOPPED -> "Stopped — draft ready"
    SessionStatus.PROCESSING -> "Preparing official record…"
    SessionStatus.COMPLETED -> "Record ready"
    SessionStatus.FAILED -> "Processing failed — will retry"
}
