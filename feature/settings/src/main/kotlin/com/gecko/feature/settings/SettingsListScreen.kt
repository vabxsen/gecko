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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.gecko.feature.settings.component.SettingsContentPadding
import com.gecko.feature.settings.component.SettingsRow
import com.gecko.feature.settings.component.SettingsSectionHeader
import com.gecko.feature.settings.component.SettingsTopBar

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
) {
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "Settings", onBack = onBack) },
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
