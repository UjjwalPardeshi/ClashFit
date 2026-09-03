package com.clashfit.duel

import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.fillMaxSize

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Permission gate for Nearby Connections (Bluetooth + WiFi).
 * Shows a dialog explaining the permissions needed for duel/raid/rep-race gameplay.
 * Only renders the content when all permissions are granted.
 *
 * Note: This is a best-effort composable. On Android 12+, Bluetooth permissions are
 * required for Nearby Connections. On Android 13+, NEARBY_WIFI_DEVICES is also needed.
 */
@Composable
fun NearbyPermissionGate(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Collect required permissions based on Android version
    val permissions = remember {
        mutableListOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    }

    var permissionsGranted by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (!permissionsGranted) showRationale = true
    }

    LaunchedEffect(Unit) {
        val allGranted = permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            permissionsGranted = true
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    if (permissionsGranted) {
        content()
    } else {
        // Never a blank screen: the denial state has a way back in.
        NearbyPermissionFallback(onGrant = { permissionLauncher.launch(permissions.toTypedArray()) })
        if (showRationale) {
            AlertDialog(
                onDismissRequest = { showRationale = false },
                title = { Text("Duel Requires Nearby Permissions") },
                text = {
                    Text(
                        "This app needs Bluetooth and WiFi permissions to detect and connect " +
                                "to other phones for duel, raid, and rep-race modes. All connections " +
                                "are local and on-device; no internet is used."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            permissionLauncher.launch(permissions.toTypedArray())
                            showRationale = false
                        }
                    ) {
                        Text("Grant Permissions")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRationale = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun NearbyPermissionFallback(onGrant: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text("NEARBY PERMISSION NEEDED", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text(
                "Phones find each other over Bluetooth and local Wi-Fi. Nothing leaves the room.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            TextButton(onClick = onGrant) { Text("GRANT") }
        }
    }
}
