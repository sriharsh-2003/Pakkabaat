package com.pakkabaat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pakkabaat.app.data.repository.OtpService

@Composable
fun OnboardingScreen(
    otpService: OtpService,
    onVerified: (phone: String, name: String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var devCode by remember { mutableStateOf<String?>(null) }
    var enteredCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Text("Welcome to PakkaBaat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Turn a spoken agreement into a written record you can both trust.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(), enabled = !otpSent
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phone, onValueChange = { phone = it },
            label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth(), enabled = !otpSent
        )

        if (!otpSent) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (phone.isBlank() || name.isBlank()) {
                        error = "Please enter your name and phone number."
                    } else {
                        devCode = otpService.requestOtp(phone)
                        otpSent = true
                        error = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send OTP") }
        } else {
            Spacer(Modifier.height(16.dp))
            devCode?.let {
                AssistChip(onClick = {}, label = { Text("DEV MODE — your code is $it (see OtpService.kt)") })
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = enteredCode, onValueChange = { enteredCode = it },
                label = { Text("Enter the 6-digit code") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (otpService.verifyOtp(phone, enteredCode)) {
                        onVerified(phone, name)
                    } else {
                        error = "That code doesn't match. Please try again."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Verify") }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
