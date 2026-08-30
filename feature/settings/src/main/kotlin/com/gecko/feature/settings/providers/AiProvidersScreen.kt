package com.gecko.feature.settings.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.feature.settings.component.SettingsContentPadding
import com.gecko.feature.settings.component.SettingsRow
import com.gecko.feature.settings.component.SettingsTopBar

@Composable
fun AiProvidersScreen(
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiProvidersViewModel = hiltViewModel(),
) {
    val configs by viewModel.providerConfigs.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "AI Providers", onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = SettingsContentPadding,
        ) {
            items(configs, key = { it.providerId }) { config ->
                ProviderRow(
                    config = config,
                    onClick = { onOpenProvider(config.providerId.slug) },
                    onToggleEnabled = { enabled -> viewModel.setEnabled(config.providerId, enabled) },
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(config: ProviderConfig, onClick: () -> Unit, onToggleEnabled: (Boolean) -> Unit) {
    SettingsRow(
        title = config.providerId.displayName,
        subtitle = statusLabel(config),
        onClick = onClick,
        leading = { StatusDot(config.connectionStatus) },
        trailing = {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = config.enabled, onCheckedChange = onToggleEnabled)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun StatusDot(status: ConnectionStatus) {
    if (status is ConnectionStatus.Testing) {
        CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
        return
    }
    val color = when (status) {
        ConnectionStatus.Success -> Color(0xFF2DD4BF)
        is ConnectionStatus.Failure -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(10.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color),
    )
}

private fun statusLabel(config: ProviderConfig): String {
    if (!config.hasApiKey) return "No API key"
    return when (val status = config.connectionStatus) {
        ConnectionStatus.Untested -> "Not tested"
        ConnectionStatus.Testing -> "Testing…"
        ConnectionStatus.Success -> "Connected"
        is ConnectionStatus.Failure -> status.message
    }
}
