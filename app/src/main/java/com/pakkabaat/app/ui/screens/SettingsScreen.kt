package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pakkabaat.app.data.model.LanguageCatalog
import com.pakkabaat.app.data.repository.ApiKeyStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKeyStore: ApiKeyStore,
    currentName: String,
    currentLanguage: String,
    onSaveProfile: (name: String, language: String) -> Unit
) {
    var key by remember { mutableStateOf(apiKeyStore.getGeminiKey() ?: "") }
    var saved by remember { mutableStateOf(false) }

    var name by remember(currentName) { mutableStateOf(currentName) }
    var language by remember(currentLanguage) { mutableStateOf(currentLanguage) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var profileSaved by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // ---------- Profile: name + language (previously not editable anywhere) ----------
        Spacer(Modifier.height(24.dp))
        Text("Your profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "This is the name the other person sees when you pair up to record.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = null; profileSaved = false },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        nameError?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(
            expanded = languageMenuExpanded,
            onExpandedChange = { languageMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = LanguageCatalog.nameFor(language),
                onValueChange = {},
                readOnly = true,
                label = { Text("Preferred language") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { languageMenuExpanded = false }
            ) {
                LanguageCatalog.all.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.displayName) },
                        onClick = {
                            language = lang.code
                            languageMenuExpanded = false
                            profileSaved = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            if (name.isBlank()) {
                nameError = "Please enter your name."
            } else {
                onSaveProfile(name.trim(), language)
                profileSaved = true
            }
        }) { Text("Save profile") }

        if (profileSaved) {
            Spacer(Modifier.height(8.dp))
            Text("Saved.", color = MaterialTheme.colorScheme.primary)
        }

        HorizontalDivider(Modifier.padding(vertical = 24.dp))

        // ---------- Gemini API key ----------
        Text("Gemini API key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
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
