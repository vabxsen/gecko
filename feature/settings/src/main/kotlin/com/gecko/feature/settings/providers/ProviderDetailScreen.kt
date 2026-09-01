package com.gecko.feature.settings.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.designsystem.icon.ProviderLogo
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ProviderId
import com.gecko.feature.settings.component.ModelSelectorRow
import com.gecko.feature.settings.component.SettingsSectionHeader
import com.gecko.feature.settings.component.SettingsSwitchRow
import com.gecko.feature.settings.component.SettingsTopBar

@Composable
fun ProviderDetailScreen(
    onBack: () -> Unit,
    onOpenModelSelection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProviderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var apiKeyInitialized by rememberSaveable { mutableStateOf(false) }
    var keyVisible by rememberSaveable { mutableStateOf(true) }
    var labelInput by rememberSaveable { mutableStateOf("") }
    var labelInitialized by rememberSaveable { mutableStateOf(false) }
    var baseUrlInput by rememberSaveable { mutableStateOf("") }
    var baseUrlInitialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.label) {
        if (!labelInitialized && uiState.config != null) {
            labelInput = uiState.label
            labelInitialized = true
        }
    }

    LaunchedEffect(uiState.baseUrlOverride) {
        if (!baseUrlInitialized && uiState.config != null) {
            baseUrlInput = uiState.baseUrlOverride.orEmpty()
            baseUrlInitialized = true
        }
    }

    LaunchedEffect(uiState.isApiKeyLoaded) {
        if (!apiKeyInitialized && uiState.isApiKeyLoaded) {
            apiKeyInput = uiState.apiKeyValue.orEmpty()
            apiKeyInitialized = true
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = uiState.label.ifBlank { "API key" }, onBack = onBack) },
    ) { innerPadding ->
        if (uiState.config == null) {
            Text(
                text = "This API key was removed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(innerPadding).padding(20.dp),
            )
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ProviderLogo(
                        providerId = uiState.config!!.providerId,
                        baseUrlOverride = uiState.baseUrlOverride,
                        size = 44.dp,
                    )
                    Column {
                        Text(
                            text = uiState.label.ifBlank { uiState.config!!.providerId.displayName },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        Text(
                            text = uiState.config!!.providerId.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SettingsSectionHeader("Label")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    TextButton(
                        onClick = { viewModel.setLabel(labelInput) },
                        enabled = labelInput.isNotBlank() && labelInput != uiState.label,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("Save")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                SettingsSwitchRow(
                    title = "Enabled",
                    subtitle = "Show this key in the model selector",
                    checked = uiState.enabled,
                    onCheckedChange = viewModel::setEnabled,
                )
                HorizontalDivider()
                SettingsSectionHeader("API key")
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (keyVisible) "Hide key" else "Show key",
                                )
                            }
                        },
                    )
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.saveApiKey(apiKeyInput) },
                            enabled = apiKeyInput.isNotBlank() && apiKeyInput != uiState.apiKeyValue && !uiState.isSavingKey,
                        ) {
                            Text(if (uiState.isSavingKey) "Saving…" else "Save")
                        }
                        if (uiState.hasApiKey) {
                            OutlinedButton(onClick = { viewModel.clearApiKey(); apiKeyInput = "" }) { Text("Remove") }
                        }
                    }
                }
                if (uiState.providerId == ProviderId.OPENAI) {
                    HorizontalDivider()
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        OPENAI_COMPATIBLE_ENDPOINTS.forEach { endpoint ->
                            val selected = endpoint.baseUrl.orEmpty() == baseUrlInput.trim()
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { baseUrlInput = endpoint.baseUrl.orEmpty() }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (selected) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                                Text(text = endpoint.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        OutlinedTextField(
                            value = baseUrlInput,
                            onValueChange = { baseUrlInput = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            label = { Text("Base URL") },
                            placeholder = { Text("Leave blank for OpenAI itself") },
                            singleLine = true,
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = { viewModel.setBaseUrlOverride(baseUrlInput) },
                                enabled = baseUrlInput.trim() != uiState.baseUrlOverride.orEmpty(),
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    ConnectionStatusLabel(uiState.connectionStatus, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    TextButton(onClick = viewModel::testConnection, enabled = uiState.hasApiKey) {
                        Text("Test connection")
                    }
                }
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SettingsSectionHeader("Model", modifier = Modifier.padding(0.dp))
                    TextButton(onClick = viewModel::refreshModels, enabled = uiState.hasApiKey && !uiState.isLoadingModels) {
                        if (uiState.isLoadingModels) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        }
                        Text("Refresh")
                    }
                }
            }
            if (uiState.availableModels.isEmpty()) {
                item {
                    Text(
                        text = "No models loaded. Save an API key and tap Refresh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            } else {
                item {
                    ModelSelectorRow(
                        selectedModelName = uiState.availableModels.find { it.modelId == uiState.selectedModelId }?.displayName,
                        onClick = onOpenModelSelection,
                    )
                }
            }
            item {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = { viewModel.deleteProvider(onDeleted = onBack) },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Delete this API key")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusLabel(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        ConnectionStatus.Untested -> "Not tested" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionStatus.Testing -> "Testing…" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionStatus.Success -> "Connected" to MaterialTheme.colorScheme.primary
        is ConnectionStatus.Failure -> status.message to MaterialTheme.colorScheme.error
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)
}
