package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
    onOpenSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your agreements", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRecordNew, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Record a new agreement")
        }
        Spacer(Modifier.height(20.dp))

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No agreements recorded yet. Tap the button above to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(sessions) { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        onClick = { onOpenSession(session.sessionId) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
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
                    }
                }
            }
        }
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
