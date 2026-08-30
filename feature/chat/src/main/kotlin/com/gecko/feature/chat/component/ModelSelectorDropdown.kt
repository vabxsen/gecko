package com.gecko.feature.chat.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.model.provider.ProviderId

@Composable
fun ModelSelectorDropdown(
    enabledProviders: List<ProviderConfig>,
    selectedProviderId: ProviderId?,
    selectedModelId: String?,
    modelsForSelectedProvider: List<ModelInfo>,
    onSelectProvider: (ProviderId) -> Unit,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        selectedProviderId == null -> "Select a model"
        selectedModelId == null -> selectedProviderId.displayName
        else -> selectedModelId
    }

    Surface(
        onClick = { expanded = true },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(imageVector = Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
        }
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (enabledProviders.isEmpty()) {
            DropdownMenuItem(text = { Text("No providers configured") }, onClick = { expanded = false }, enabled = false)
            return@DropdownMenu
        }
        enabledProviders.forEachIndexed { index, provider ->
            if (index > 0) HorizontalDivider()
            Text(
                text = provider.providerId.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (provider.providerId != selectedProviderId) {
                DropdownMenuItem(
                    text = { Text("Tap to load models…") },
                    onClick = { onSelectProvider(provider.providerId) },
                )
            } else if (modelsForSelectedProvider.isEmpty()) {
                DropdownMenuItem(text = { Text("No models loaded yet") }, onClick = {}, enabled = false)
            } else {
                modelsForSelectedProvider.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName) },
                        onClick = {
                            onSelectModel(model.modelId)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
