package com.gecko.feature.settings.providers

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.designsystem.icon.ProviderLogo
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.error.copyForUser
import com.gecko.feature.settings.component.SettingsTopBar

@Composable
fun AddProviderScreen(
    onBack: () -> Unit,
    onSaved: (configId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddProviderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AddProviderContent(state, onBack, viewModel::updateApiKey, viewModel::selectOption,
        viewModel::updateLabel, viewModel::updateBaseUrlOverride, { viewModel.save(onSaved) }, modifier)
}

@Composable
internal fun AddProviderContent(
    state: AddProviderUiState,
    onBack: () -> Unit,
    onKeyChange: (String) -> Unit,
    onSelectProvider: (AddProviderOption) -> Unit,
    onLabelChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var keyVisible by remember { mutableStateOf(false) }
    var chooseProvider by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    Scaffold(modifier = modifier, topBar = { SettingsTopBar("Connect your AI", onBack) }) { padding ->
        Box(Modifier.padding(padding).imePadding().fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.padding(16.dp).size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your key. Ready to chat.", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Paste your API key. Gecko will find a working model and get you straight into chat.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = state.apiKey, onValueChange = onKeyChange,
                    label = { Text("API key") }, placeholder = { Text("Paste your key here") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                if (keyVisible) "Hide key" else "Show key")
                        }
                    },
                )
                TextButton(onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        clip.getItemAt(0).text?.toString()?.let(onKeyChange)
                    }
                }, enabled = !state.isSaving) {
                    Text("Paste from clipboard")
                }
                if (state.apiKey.isNotBlank() || state.selectedProviderId != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.selectedProviderId?.let {
                                ProviderLogo(providerId = it, baseUrlOverride = state.baseUrlOverride.ifBlank { null }, size = 28.dp)
                            }
                            Text(state.providerLabel.ifBlank { "Which provider is this key from?" },
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Box {
                                TextButton(onClick = { chooseProvider = true }, enabled = !state.isSaving) {
                                    Text(if (state.selectedProviderId == null) "Choose" else "Change")
                                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                                }
                                DropdownMenu(expanded = chooseProvider, onDismissRequest = { chooseProvider = false }) {
                                    ADD_PROVIDER_OPTIONS.forEach { option ->
                                        DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                            onSelectProvider(option)
                                            chooseProvider = false
                                        })
                                    }
                                }
                            }
                        }
                        if (state.selectedProviderId == null) {
                            Text("Some keys share the same format. Choose the service that issued yours.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.error?.let { error ->
                    val copy = error.copyForUser()
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(copy.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(if (error.kind == ErrorKind.ModelUnavailable)
                                "We couldn't find an available chat model for this key. Check model access with your provider, then try again."
                                else copy.explanation, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                Button(onClick = onConnect, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.padding(end = 10.dp).size(18.dp), strokeWidth = 2.dp)
                    Text(if (state.isSaving) "Connecting…" else "Connect & start chatting", Modifier.padding(vertical = 8.dp))
                }
                if (state.isSaving) Text("Finding a model that works with your key…",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Your key is encrypted and stored on this device.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { advanced = !advanced }, enabled = !state.isSaving) {
                    Text(if (advanced) "Hide advanced settings" else "Advanced settings")
                }
                AnimatedVisibility(advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(state.label, onLabelChange, Modifier.fillMaxWidth(),
                            label = { Text("Name (optional)") }, singleLine = true, enabled = !state.isSaving)
                        if (state.selectedProviderId == ProviderId.OPENAI) {
                            OutlinedTextField(state.baseUrlOverride, onUrlChange, Modifier.fillMaxWidth(),
                                label = { Text("Custom base URL") }, singleLine = true, enabled = !state.isSaving,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                        }
                    }
                }
            }
        }
    }
}
