package com.gecko.feature.settings.providers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.designsystem.icon.ProviderLogo
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.error.copyForUser
import com.gecko.domain.model.friendlyName
import com.gecko.feature.settings.component.*

@Composable
fun ProviderDetailScreen(onBack: () -> Unit, onOpenModelSelection: () -> Unit, modifier: Modifier = Modifier,
    viewModel: ProviderDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var apiKey by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var label by rememberSaveable(state.label) { mutableStateOf(state.label) }
    var baseUrl by rememberSaveable(state.baseUrlOverride) { mutableStateOf(state.baseUrlOverride.orEmpty()) }
    LaunchedEffect(state.isApiKeyLoaded) {
        if (!initialized && state.isApiKeyLoaded) { apiKey = state.apiKeyValue.orEmpty(); initialized = true }
    }
    Scaffold(modifier = modifier.imePadding(), topBar = { SettingsTopBar("Connection details", onBack) }) { padding ->
        SettingsPage(padding) {
            val config = state.config
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.size(28.dp))
                config == null -> Text("This API key was removed.")
                else -> {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProviderLogo(config.providerId, baseUrlOverride = state.baseUrlOverride, size = 48.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(state.label.ifBlank { config.providerId.displayName }, style = MaterialTheme.typography.headlineMedium)
                            ConnectionStatusLabel(state.connectionStatus)
                        }
                    }
                    SettingsSwitchRow("Use this connection", state.enabled, viewModel::setEnabled,
                        subtitle = "Make these models available in chat.")
                    SettingsSectionHeader("Chat model")
                    if (state.availableModels.isNotEmpty()) {
                        ModelSelectorRow(state.availableModels.find { it.modelId == state.selectedModelId }?.friendlyName,
                            onOpenModelSelection)
                    } else {
                        Text("No models loaded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = viewModel::refreshModels, enabled = state.hasApiKey && !state.isLoadingModels) {
                        Text(if (state.isLoadingModels) "Loading models…" else "Refresh models")
                    }
                    SettingsSectionHeader("API key")
                    SettingsPanel {
                        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("API key") },
                            singleLine = true, enabled = state.isApiKeyLoaded && !state.isSavingKey,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { IconButton(onClick = { visible = !visible }) {
                                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (visible) "Hide key" else "Show key")
                            } })
                        Button(onClick = { viewModel.saveApiKey(apiKey) }, modifier = Modifier.fillMaxWidth(),
                            enabled = apiKey.isNotBlank() && apiKey != state.apiKeyValue && !state.isSavingKey) {
                            Text(if (state.isSavingKey) "Saving…" else "Save key")
                        }
                        OutlinedButton(onClick = viewModel::testConnection, modifier = Modifier.fillMaxWidth(),
                            enabled = state.hasApiKey && !state.isSavingKey && state.connectionStatus != ConnectionStatus.Testing) {
                            Text(if (state.connectionStatus == ConnectionStatus.Testing) "Testing…" else "Test connection")
                        }
                        state.saveKeyErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        (state.connectionStatus as? ConnectionStatus.Failure)?.let {
                            Text(it.error.copyForUser().explanation, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TextButton(onClick = { advanced = !advanced }) {
                        Text(if (advanced) "Hide advanced settings" else "Advanced settings")
                    }
                    AnimatedVisibility(advanced) {
                        SettingsPanel {
                            OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("Connection name") },
                                shape = MaterialTheme.shapes.medium, singleLine = true)
                            TextButton(onClick = { viewModel.setLabel(label) }, enabled = label.isNotBlank() && label != state.label) {
                                Text("Save name")
                            }
                            if (state.providerId == ProviderId.OPENAI) {
                                OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Custom base URL") },
                                    supportingText = { Text("Leave blank to use OpenAI.") }, singleLine = true,
                                    shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                                TextButton(onClick = { viewModel.setBaseUrlOverride(baseUrl) },
                                    enabled = baseUrl.trim() != state.baseUrlOverride.orEmpty()) { Text("Save URL") }
                            }
                            if (state.hasApiKey) {
                                TextButton(onClick = { viewModel.clearApiKey(); apiKey = "" }, enabled = !state.isSavingKey) {
                                    Text("Remove saved key", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete connection", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this connection?") }, text = { Text("Its saved API key will be removed. Your conversations will stay on this device.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteProvider(onDeleted = onBack) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
private fun ConnectionStatusLabel(status: ConnectionStatus) {
    val text = when (status) {
        ConnectionStatus.Untested -> "Ready to test"
        ConnectionStatus.Testing -> "Testing connection…"
        ConnectionStatus.Success -> "Connected"
        is ConnectionStatus.Failure -> status.error.copyForUser().shortLabel
    }
    Text(text, style = MaterialTheme.typography.bodyMedium,
        color = if (status is ConnectionStatus.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}
