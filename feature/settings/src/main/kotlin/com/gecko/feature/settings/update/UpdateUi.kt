package com.gecko.feature.settings.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpdateCheckFab(state: UpdateCheckState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val checking = state is UpdateCheckState.Checking
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        ),
        icon = {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
            }
        },
        text = { Text(if (checking) "Checking…" else "Check for update") },
    )
}

@Composable
fun UpdateResultDialog(
    state: UpdateCheckState,
    onDownload: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateCheckState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Update available") },
            text = { Text("Version ${state.update.versionName} is available. Download and install it now?") },
            confirmButton = { TextButton(onClick = onDownload) { Text("Download") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
        )
        is UpdateCheckState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update…") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {},
        )
        is UpdateCheckState.NeedsInstallPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Allow installing updates") },
            text = { Text("Gecko needs permission to install app updates. Turn on \"Allow from this source\" on the next screen, then check for updates again.") },
            confirmButton = { TextButton(onClick = onOpenInstallSettings) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        else -> Unit
    }
}
