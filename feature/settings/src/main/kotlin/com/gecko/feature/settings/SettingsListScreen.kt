package com.gecko.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.feature.settings.component.SettingsContentPadding
import com.gecko.feature.settings.component.SettingsRow
import com.gecko.feature.settings.component.SettingsSectionHeader
import com.gecko.feature.settings.component.SettingsTopBar
import com.gecko.feature.settings.update.UpdateCheckFab
import com.gecko.feature.settings.update.UpdateCheckState
import com.gecko.feature.settings.update.UpdateResultDialog
import com.gecko.feature.settings.update.UpdateViewModel

private data class SettingsDestination(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun SettingsListScreen(
    onBack: () -> Unit,
    onNavigateAppearance: () -> Unit,
    onNavigateChatPreferences: () -> Unit,
    onNavigateAiProviders: () -> Unit,
    onNavigateModelPreferences: () -> Unit,
    onNavigateDataPrivacy: () -> Unit,
    onNavigateAbout: () -> Unit,
    modifier: Modifier = Modifier,
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is UpdateCheckState.UpToDate -> {
                snackbarHostState.showSnackbar("You're on the latest version")
                updateViewModel.dismiss()
            }
            is UpdateCheckState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                updateViewModel.dismiss()
            }
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "Settings", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            UpdateCheckFab(state = updateState, onClick = updateViewModel::checkForUpdate)
        },
    ) { innerPadding ->
        val general = listOf(
            SettingsDestination("Appearance", "Theme and color", Icons.Outlined.Palette, onNavigateAppearance),
            SettingsDestination("Chat preferences", "Sending and streaming behavior", Icons.Outlined.Tune, onNavigateChatPreferences),
        )
        val ai = listOf(
            SettingsDestination("AI Providers", "API keys and connections", Icons.Outlined.SmartToy, onNavigateAiProviders),
            SettingsDestination("Model preferences", "Default provider and model", Icons.Outlined.Widgets, onNavigateModelPreferences),
        )
        val other = listOf(
            SettingsDestination("Data & Privacy", "Export and clear local data", Icons.Outlined.PrivacyTip, onNavigateDataPrivacy),
            SettingsDestination("About", "Version and information", Icons.Outlined.Info, onNavigateAbout),
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), contentPadding = SettingsContentPadding) {
            item { SettingsSectionHeader("General") }
            items(general) { DestinationRow(it) }
            item { SettingsSectionHeader("AI") }
            items(ai) { DestinationRow(it) }
            item { SettingsSectionHeader("Other") }
            items(other) { DestinationRow(it) }
        }
    }

    UpdateResultDialog(
        state = updateState,
        onDownload = {
            (updateState as? UpdateCheckState.Available)?.let { updateViewModel.downloadAndInstall(it.update) }
        },
        onOpenInstallSettings = updateViewModel::openInstallPermissionSettings,
        onDismiss = updateViewModel::dismiss,
    )
}

@Composable
private fun DestinationRow(destination: SettingsDestination) {
    SettingsRow(
        title = destination.title,
        subtitle = destination.subtitle,
        leading = { Icon(destination.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = destination.onClick,
    )
}
