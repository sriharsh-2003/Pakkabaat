package com.pakkabaat.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pakkabaat.app.pairing.QrCodeUtil

private fun requiredPermissions(): Array<String> {
    val base = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        base += Manifest.permission.BLUETOOTH_ADVERTISE
        base += Manifest.permission.BLUETOOTH_CONNECT
        base += Manifest.permission.BLUETOOTH_SCAN
    }
    base += Manifest.permission.ACCESS_FINE_LOCATION
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        base += Manifest.permission.NEARBY_WIFI_DEVICES
        base += Manifest.permission.POST_NOTIFICATIONS
    }
    return base.toTypedArray()
}

@Composable
fun StartSessionScreen(
    qrToken: String?,
    pairingConnected: Boolean,
    partnerName: String?,
    onBecomeInitiator: () -> Unit,
    onScannedToken: (String) -> Unit,
    onContinueToConsent: () -> Unit
) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf<String?>(null) } // "show" or "scan"

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> permissionsGranted = results.values.all { it } }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { onScannedToken(it) }
    }

    LaunchedEffect(Unit) { permissionLauncher.launch(requiredPermissions()) }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Text("Start a new session", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        if (!permissionsGranted) {
            Text(
                "PakkaBaat needs microphone, camera, and nearby-device permissions to pair and record.",
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permissionLauncher.launch(requiredPermissions()) }) { Text("Grant permissions") }
            return@Column
        }

        if (pairingConnected) {
            Text("Connected to ${partnerName ?: "the other person"}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onContinueToConsent, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            return@Column
        }

        when (mode) {
            null -> {
                Button(
                    onClick = { mode = "show"; onBecomeInitiator() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Show my code (I'm starting this session)") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        mode = "scan"
                        scanLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Scan the other person's PakkaBaat code")
                                .setBeepEnabled(false)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Scan the other person's code") }
            }
            "show" -> {
                Text(
                    "Ask the other person to scan this code with their PakkaBaat app.",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                qrToken?.let { token ->
                    val bitmap: Bitmap = remember(token) { QrCodeUtil.generate(token) }
                    Image(bitmap.asImageBitmap(), contentDescription = "Session QR code")
                }
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Waiting for the other person to join…")
            }
            "scan" -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Waiting for the other person to join…")
            }
        }
    }
}
