package com.gecko.feature.settings.update

import com.gecko.core.designsystem.theme.geckoPopIn
import com.gecko.core.designsystem.component.GeckoTextButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.gecko.core.designsystem.theme.geckoPress
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpdateCheckFab(state: UpdateCheckState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val checking = state is UpdateCheckState.Checking
    val press = remember { MutableInteractionSource() }
    ExtendedFloatingActionButton(
        onClick = onClick,
        interactionSource = press,
        modifier = modifier.geckoPress(press),
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
        is UpdateCheckState.Available -> AlertDialog(modifier = Modifier.geckoPopIn(),
            onDismissRequest = onDismiss,
            title = { Text("Update available") },
            text = { Text("Version ${state.update.versionName} is available. Download and install it now?") },
            confirmButton = { GeckoTextButton(onClick = onDownload) { Text("Download") } },
            dismissButton = { GeckoTextButton(onClick = onDismiss) { Text("Not now") } },
        )
        is UpdateCheckState.Downloading -> AlertDialog(modifier = Modifier.geckoPopIn(),
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
        is UpdateCheckState.NeedsInstallPermission -> AlertDialog(modifier = Modifier.geckoPopIn(),
            onDismissRequest = onDismiss,
            title = { Text("Allow installing updates") },
            text = { Text("Gecko needs permission to install app updates. Turn on \"Allow from this source\" on the next screen, then check for updates again.") },
            confirmButton = { GeckoTextButton(onClick = onOpenInstallSettings) { Text("Open settings") } },
            dismissButton = { GeckoTextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        else -> Unit
    }
}
