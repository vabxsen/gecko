package com.gecko.feature.chat.component

import com.gecko.core.designsystem.component.GeckoTextButton
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.gecko.core.designsystem.theme.geckoPress
import com.gecko.core.designsystem.theme.geckoReveal
import com.gecko.core.designsystem.theme.geckoPopIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.icon.ProviderLogo
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.domain.model.friendlyName

/** A connection switcher. Catalog listings do not prove a key can use a model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<ProviderConfig>,
    modelCatalog: Map<String, List<ModelInfo>>,
    loadingConfigIds: Set<String>,
    selectedConfigId: String?,
    onSelect: (configId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingConfigId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(providers, selectedConfigId, loadingConfigIds) {
        val pending = pendingConfigId ?: return@LaunchedEffect
        if (pending !in loadingConfigIds && selectedConfigId == pending &&
            providers.any { it.id == pending && it.verifiedModelId != null && it.connectionStatus == ConnectionStatus.Success }) {
            sheetState.hide()
            onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Text("Your AI", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.geckoReveal())
            Spacer(Modifier.height(8.dp))
            Text("Choose a connection. Gecko handles the model.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(providers, key = { it.id }) { config ->
                    val press = remember { MutableInteractionSource() }
                    val loading = config.id in loadingConfigIds || config.connectionStatus == ConnectionStatus.Testing
                    val ready = config.verifiedModelId != null && config.connectionStatus == ConnectionStatus.Success
                    val model = modelCatalog[config.id].orEmpty().find { it.modelId == config.verifiedModelId }
                    Surface(onClick = {
                        pendingConfigId = config.id
                        onSelect(config.id)
                        if (ready) onDismiss()
                    }, enabled = loadingConfigIds.isEmpty() && !loading, interactionSource = press,
                        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().geckoPress(press, loadingConfigIds.isEmpty() && !loading)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProviderLogo(config.providerId, config.baseUrlOverride, size = 28.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(config.label.ifBlank { config.providerId.displayName }, style = MaterialTheme.typography.titleMedium)
                                Text(when {
                                    loading -> "Finding a working model…"
                                    ready -> "Automatic · ${model?.friendlyName ?: config.verifiedModelId}"
                                    else -> "Tap to connect automatically"
                                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else if (ready && config.id == selectedConfigId) Icon(Icons.Default.Check, "Selected connection", Modifier.geckoPopIn())
                        }
                    }
                }
            }
            if (providers.isEmpty()) Text("Paste an API key to start chatting.", style = MaterialTheme.typography.bodyLarge)
            GeckoTextButton(onClick = { onDismiss(); onOpenSettings() }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(if (providers.isEmpty()) "Add an API key" else "Manage API keys")
            }
        }
    }
}
