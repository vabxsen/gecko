package com.gecko.feature.settings.providers

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.designsystem.icon.ProviderLogo
import com.gecko.core.designsystem.theme.GeckoMotion
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.domain.error.copyForUser
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.domain.repository.MAX_PROVIDER_CONFIGS
import com.gecko.feature.settings.component.SettingsContentPadding
import com.gecko.feature.settings.component.SettingsRow
import com.gecko.feature.settings.component.SettingsTopBar

@Composable
fun AiProvidersScreen(
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
    onAddProvider: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiProvidersViewModel = hiltViewModel(),
) {
    val rows by viewModel.uiState.collectAsStateWithLifecycle()
    val canAddMore = rows.size < MAX_PROVIDER_CONFIGS

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "AI Providers", onBack = onBack) },
        floatingActionButton = {
            if (canAddMore) {
                ExtendedFloatingActionButton(
                    onClick = onAddProvider,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    ),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add API key") },
                )
            }
        },
    ) { innerPadding ->
        if (rows.isEmpty()) {
            Text(
                text = "No API keys yet. Add one to start chatting — bring your own key from OpenAI, Anthropic, Google, or OpenRouter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(innerPadding).padding(20.dp),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = SettingsContentPadding,
        ) {
            items(rows, key = { it.config.id }) { row ->
                ProviderRow(
                    row = row,
                    onClick = { onOpenProvider(row.config.id) },
                    onToggleEnabled = { enabled -> viewModel.setEnabled(row.config.id, enabled) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(
    row: ProviderRowState,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = row.config
    SettingsRow(
        title = config.label.ifBlank { config.providerId.displayName },
        // The model leads: it's what someone opening this screen came to check, and it used to be
        // on a different screen entirely.
        subtitle = listOfNotNull(row.modelLabel, statusLabel(config)).joinToString(" · "),
        onClick = onClick,
        modifier = modifier,
        leading = {
            Box {
                ProviderLogo(providerId = config.providerId, baseUrlOverride = config.baseUrlOverride)
                StatusDot(
                    status = config.connectionStatus,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp),
                )
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.isInUse) {
                    Text(
                        text = "In use",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
private fun StatusDot(status: ConnectionStatus, modifier: Modifier = Modifier) {
    if (status is ConnectionStatus.Testing) {
        CircularProgressIndicator(modifier = modifier.size(10.dp), strokeWidth = 1.5.dp)
        return
    }
    val targetColor = when (status) {
        ConnectionStatus.Success -> Color(0xFF16A34A)
        is ConnectionStatus.Failure -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingStandard),
        label = "statusDotColor",
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(1.5.dp)
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
        // Two or three words. A raw provider message in a list-row subtitle is unreadable.
        is ConnectionStatus.Failure -> status.error.copyForUser().shortLabel
    }
}
