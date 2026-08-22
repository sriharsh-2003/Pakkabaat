package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ConsentScreen(
    partnerName: String?,
    myConsent: Boolean,
    otherConsent: Boolean,
    onAgree: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Before we start", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Card {
            Text(
                "By tapping Agree, you and ${partnerName ?: "the other person"} are both agreeing " +
                    "to record this conversation. The recording will be turned into a written " +
                    "summary you can both see and download. Indian law doesn't require both " +
                    "people to agree to a recording — but we do, because a record both people " +
                    "trust is worth more than one only one side controls.",
                modifier = Modifier.padding(20.dp),
                textAlign = TextAlign.Start
            )
        }
        Spacer(Modifier.height(28.dp))

        if (myConsent) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Waiting for the other person to agree…")
        } else {
            Button(onClick = onAgree, modifier = Modifier.fillMaxWidth()) {
                Text("I agree to record this conversation")
            }
        }
    }
}
