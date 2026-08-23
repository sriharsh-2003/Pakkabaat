package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pakkabaat.app.data.repository.ApiKeyStore

@Composable
fun SettingsScreen(apiKeyStore: ApiKeyStore) {
    var key by remember { mutableStateOf(apiKeyStore.getGeminiKey() ?: "") }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Text(
            "This test build asks each person to use their own free Gemini key, so no " +
                "shared key gets rate-limited or exposed. Get one at aistudio.google.com/apikey " +
                "— no card needed. It's stored encrypted on this device only.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = key,
            onValueChange = { key = it; saved = false },
            label = { Text("Gemini API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = {
                apiKeyStore.setGeminiKey(key)
                saved = true
            }) { Text("Save") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = {
                apiKeyStore.clearGeminiKey()
                key = ""
                saved = false
            }) { Text("Clear") }
        }

        if (saved) {
            Spacer(Modifier.height(12.dp))
            Text("Saved.", color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Speech-to-text runs fully on this device (whisper.cpp) — only the final " +
                "document-structuring step needs this key and an internet connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
