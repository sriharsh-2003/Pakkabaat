package com.pakkabaat.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

private fun deniedPermissions(context: android.content.Context): List<String> =
    requiredPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }

// Human-readable label for whichever permission(s) are still missing, so the screen can
// say exactly what's blocking it instead of a generic "still not granted" dead end.
private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.RECORD_AUDIO -> "Microphone"
    Manifest.permission.CAMERA -> "Camera"
    Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
    Manifest.permission.BLUETOOTH_ADVERTISE -> "Nearby devices (advertise)"
    Manifest.permission.BLUETOOTH_CONNECT -> "Nearby devices (connect)"
    Manifest.permission.BLUETOOTH_SCAN -> "Nearby devices (scan)"
    Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Wi-Fi devices"
    Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
    else -> permission.substringAfterLast('.')
}

@Composable
fun StartSessionScreen(
    qrToken: String?,
    pairingConnected: Boolean,
    partnerName: String?,
    pairingError: String?,
    onBecomeInitiator: () -> Unit,
    onScannedToken: (String) -> Unit,
    onContinueToConsent: () -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var missingPermissions by remember { mutableStateOf(deniedPermissions(context)) }
    val permissionsGranted = missingPermissions.isEmpty()
    // Once the OS has denied a permission and won't show the dialog again (either the
    // user checked "don't ask again", or denied it twice), shouldShowRequestPermissionRationale
    // flips to false for that permission *after* a denial — that's our signal that
    // re-launching the same request will silently no-op, which is the exact "pressing
    // Grant permissions doesn't do anything" bug: the system re-delivers an instant
    // denial with no dialog shown at all, so nothing appears to happen.
    var permanentlyDenied by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf<String?>(null) } // "show" or "scan"

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        missingPermissions = results.filterValues { !it }.keys.toList()
        if (missingPermissions.isNotEmpty() && activity != null) {
            permanentlyDenied = missingPermissions.any { perm ->
                !activity.shouldShowRequestPermissionRationale(perm)
            }
        }
    }

    // "Allow once" (one-time) grants for camera/mic/location can be silently revoked by
    // Android once the app leaves the foreground for any reason — including the QR
    // scanner opening its own Activity, or the user briefly switching to Settings. The
    // launcher callback above only fires right after the system dialog closes, so without
    // this, a later revocation just leaves the screen stuck with no way to notice or
    // recover. Re-checking on every resume catches that and any other external change.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val stillMissing = deniedPermissions(context)
                missingPermissions = stillMissing
                if (stillMissing.isEmpty()) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { onScannedToken(it) }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) permissionLauncher.launch(requiredPermissions())
    }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Text("Start a new session", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        if (!permissionsGranted) {
            Text(
                "PakkaBaat needs microphone, camera, and nearby-device permissions to pair and record.",
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            // Named explicitly, instead of a generic blocked state, so it's obvious exactly
            // which permission(s) are still missing rather than a silent dead end.
            Text(
                "Still needed: " + missingPermissions.joinToString(", ") { permissionLabel(it) },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
            if (permanentlyDenied) {
                // Re-launching RequestMultiplePermissions here would just silently
                // re-deny with no dialog — has to go through the system Settings screen.
                Text(
                    "Android won't show the request again for one or more of these. Open " +
                        "Settings to grant them manually.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) { Text("Open Settings") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    // In case they granted it from Settings and came back. (The ON_RESUME
                    // check above also catches this automatically when this screen resumes.)
                    missingPermissions = deniedPermissions(context)
                    if (missingPermissions.isEmpty()) permanentlyDenied = false
                }) { Text("I granted it — check again") }
            } else {
                Button(onClick = { permissionLauncher.launch(requiredPermissions()) }) { Text("Grant permissions") }
            }
            return@Column
        }

        if (pairingConnected) {
            Text("Connected to ${partnerName ?: "the other person"}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onContinueToConsent, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            return@Column
        }

        // Previously, PairingEvent.Error reached SessionUiState.error but nothing in the
        // UI ever read it — a failed connection (Play services issue, Bluetooth/Wi-Fi off,
        // endpoint mismatch, etc.) just left the user staring at an infinite spinner with
        // no explanation and no way to retry. Surfacing it, plus letting them retry the
        // exact same role, is what actually helps diagnose "can't get 2 phones to connect".
        pairingError?.let { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Connection problem", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(6.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Make sure both phones have Bluetooth and Wi-Fi/Location turned ON " +
                            "(Nearby Connections needs Location Services enabled on Android, " +
                            "even though this app never reads your location) and are within a " +
                            "few metres of each other.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        onDismissError()
                        mode = null
                    }) { Text("Try again") }
                }
            }
            Spacer(Modifier.height(16.dp))
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