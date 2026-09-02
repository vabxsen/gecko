package com.gecko.feature.settings.modelprefs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.domain.model.friendlyName
import com.gecko.feature.settings.component.SettingsContentPadding
import com.gecko.feature.settings.component.SettingsRow
import com.gecko.feature.settings.component.SettingsTopBar

/**
 * One row per enabled provider — the row itself carries the ">" into that provider's model
 * picker. There is no separate "default provider" step: picking a model there makes its
 * provider the default too, so this screen doesn't need its own provider-only selection UI.
 */
@Composable
fun ModelPreferencesScreen(
    onBack: () -> Unit,
    onOpenModelSelection: (configId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "Model preferences", onBack = onBack) },
    ) { innerPadding ->
        if (uiState.enabledProviders.isEmpty()) {
            Text(
                text = "Enable a provider with an API key in AI Providers to set a default model.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(innerPadding).padding(20.dp),
            )
            return@Scaffold
        }

        val defaultModelName = uiState.modelsForDefaultProvider.find { it.modelId == uiState.defaultModelId }?.friendlyName

        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), contentPadding = SettingsContentPadding) {
            items(uiState.enabledProviders, key = { it.id }) { config ->
                val isDefault = config.id == uiState.defaultProviderConfigId
                ProviderModelRow(
                    config = config,
                    subtitle = if (isDefault) defaultModelName ?: "Choose a model" else config.providerId.displayName,
                    onClick = { onOpenModelSelection(config.id) },
                )
            }
        }
    }
}

@Composable
private fun ProviderModelRow(config: ProviderConfig, subtitle: String, onClick: () -> Unit) {
    SettingsRow(
        title = config.label.ifBlank { config.providerId.displayName },
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Select model",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
