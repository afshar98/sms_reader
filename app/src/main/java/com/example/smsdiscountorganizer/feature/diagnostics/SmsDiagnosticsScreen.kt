package com.example.smsdiscountorganizer.feature.diagnostics

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsdiscountorganizer.core.permission.SmsPermissionManager
import com.example.smsdiscountorganizer.core.permission.SmsPermissionStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SmsDiagnosticsScreen(viewModel: SmsDiagnosticsViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val permissionManager = remember(context) { SmsPermissionManager(context.applicationContext) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val status = activity?.let(permissionManager::status) ?: SmsPermissionStatus.Unknown
        viewModel.updatePermissionStatus(status)
    }

    fun launchPermissionRequest() {
        permissionManager.markRequestAttempted()
        permissionLauncher.launch(Manifest.permission.READ_SMS)
    }

    fun refreshPermissionStatus() {
        val status = activity?.let(permissionManager::status) ?: SmsPermissionStatus.Unknown
        viewModel.updatePermissionStatus(status)
    }

    LaunchedEffect(activity) {
        refreshPermissionStatus()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshPermissionStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("SMS Inbox diagnostics", style = MaterialTheme.typography.headlineMedium)
        Text(
            "READ_SMS lets the app inspect Inbox messages on this device. It never sends, edits, or deletes messages. Message bodies are used only during a scan and are not saved or logged.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Permission status", style = MaterialTheme.typography.titleMedium)
                Text(state.permissionStatus.toDisplayText())
            }
        }

        when (state.permissionStatus) {
            SmsPermissionStatus.Granted -> Button(
                onClick = viewModel::scanInbox,
                enabled = !state.isScanning,
            ) {
                Text(if (state.isScanning) "Scanning Inbox…" else "Scan Inbox")
            }

            SmsPermissionStatus.NotRequested -> Button(
                onClick = ::launchPermissionRequest,
                enabled = activity != null,
            ) {
                Text("Request READ_SMS permission")
            }

            SmsPermissionStatus.RationaleRequired -> Button(
                onClick = { showRationale = true },
                enabled = activity != null,
            ) {
                Text("Review permission")
            }

            SmsPermissionStatus.PermanentlyDenied -> Button(
                onClick = { activity?.let(permissionManager::openAppSettings) },
                enabled = activity != null,
            ) {
                Text("Open app settings")
            }

            SmsPermissionStatus.InstallerRestricted -> Text(
                "This installation is not allowlisted for READ_SMS. Android blocks this hard-restricted permission; install through an authorized installer. The app cannot change this restriction.",
                color = MaterialTheme.colorScheme.error,
            )

            SmsPermissionStatus.Unknown -> Text("Checking permission status…")
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Inbox message count", style = MaterialTheme.typography.titleMedium)
                Text(state.inboxMessageCount?.toString() ?: "Not scanned yet")
                Text("Last successful scan", style = MaterialTheme.typography.titleMedium)
                Text(state.lastSuccessfulScanAt?.let(::formatTimestamp) ?: "Never")
            }
        }

        state.readError?.let { error ->
            Text("Read error: $error", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Allow Inbox access?") },
            text = {
                Text(
                    "READ_SMS is used to scan Inbox messages locally. The app will not modify messages, and raw message bodies are not saved or logged.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        launchPermissionRequest()
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun SmsPermissionStatus.toDisplayText(): String = when (this) {
    SmsPermissionStatus.Unknown -> "Checking"
    SmsPermissionStatus.Granted -> "Granted"
    SmsPermissionStatus.NotRequested -> "Not requested"
    SmsPermissionStatus.RationaleRequired -> "Denied; an explanation is required before asking again"
    SmsPermissionStatus.PermanentlyDenied -> "Denied; change this in app settings"
    SmsPermissionStatus.InstallerRestricted -> "Blocked by Android installer restriction"
}

private fun formatTimestamp(timestamp: java.time.Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(timestamp)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
