package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pakkabaat.app.data.model.LanguageCatalog

@Composable
fun SplashLanguageScreen(onLanguageChosen: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("पक्का बात", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("PakkaBaat", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Choose your language / अपनी भाषा चुनें", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(LanguageCatalog.all) { lang ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    onClick = { onLanguageChosen(lang.code) }
                ) {
                    Text(
                        lang.displayName,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
