package com.gecko.feature.settings.modelprefs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
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
import com.gecko.feature.settings.component.SettingsContentPadding
import com.gecko.feature.settings.component.SettingsRow
import com.gecko.feature.settings.component.SettingsSectionHeader
import com.gecko.feature.settings.component.SettingsTopBar

@Composable
fun ModelPreferencesScreen(
    onBack: () -> Unit,
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

        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), contentPadding = SettingsContentPadding) {
            item { SettingsSectionHeader("Default provider") }
            items(uiState.enabledProviders, key = { it.providerId }) { config ->
                ProviderOptionRow(config, selected = config.providerId == uiState.defaultProviderId, onClick = { viewModel.selectDefaultProvider(config.providerId) })
            }

            if (uiState.defaultProviderId != null) {
                item { HorizontalDivider() }
                item { SettingsSectionHeader("Default model") }
                if (uiState.modelsForDefaultProvider.isEmpty()) {
                    item {
                        Text(
                            text = "No models loaded for this provider yet. Open it in AI Providers and tap Refresh.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    items(uiState.modelsForDefaultProvider, key = { it.modelId }) { model ->
                        SettingsRow(
                            title = model.displayName,
                            onClick = { viewModel.selectDefaultModel(model.modelId) },
                            trailing = {
                                Icon(
                                    imageVector = if (model.modelId == uiState.defaultModelId) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (model.modelId == uiState.defaultModelId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderOptionRow(config: ProviderConfig, selected: Boolean, onClick: () -> Unit) {
    SettingsRow(
        title = config.providerId.displayName,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = if (selected) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
