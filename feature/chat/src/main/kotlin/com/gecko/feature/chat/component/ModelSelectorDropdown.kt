package com.gecko.feature.chat.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.icon.providerLogoRes
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.domain.model.curatedForSelection

@Composable
fun ModelSelectorDropdown(
    enabledProviders: List<ProviderConfig>,
    selectedConfigId: String?,
    selectedModelId: String?,
    modelsForSelectedProvider: List<ModelInfo>,
    onSelectProviderConfig: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showAllModels by remember { mutableStateOf(false) }
    val hasSelection = selectedModelId != null
    val selectedProvider = enabledProviders.find { it.id == selectedConfigId }
    val selectedLabel = selectedProvider?.label
    val label = when {
        selectedConfigId == null -> "Select a model"
        selectedModelId == null -> selectedLabel ?: "Select a model"
        else -> selectedModelId
    }

    Surface(
        onClick = { expanded = true },
        shape = RoundedCornerShape(50),
        color = if (hasSelection) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (selectedProvider != null) {
                Image(
                    painter = painterResource(id = providerLogoRes(selectedProvider.providerId, selectedProvider.baseUrlOverride)),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                color = if (hasSelection) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 6.dp),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = if (hasSelection) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp).size(18.dp),
            )
        }
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (enabledProviders.isEmpty()) {
            DropdownMenuItem(text = { Text("No providers configured") }, onClick = { expanded = false }, enabled = false)
            return@DropdownMenu
        }
        enabledProviders.forEachIndexed { index, provider ->
            if (index > 0) HorizontalDivider()
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = providerLogoRes(provider.providerId, provider.baseUrlOverride)),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = provider.label.ifBlank { provider.providerId.displayName },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (provider.id != selectedConfigId) {
                DropdownMenuItem(
                    text = { Text("Tap to load models…") },
                    onClick = { onSelectProviderConfig(provider.id) },
                )
            } else if (modelsForSelectedProvider.isEmpty()) {
                DropdownMenuItem(text = { Text("No models loaded yet") }, onClick = {}, enabled = false)
            } else {
                val curated = modelsForSelectedProvider.curatedForSelection(provider.providerId)
                curated.primary.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName) },
                        onClick = {
                            onSelectModel(model.modelId)
                            expanded = false
                        },
                    )
                }
                if (curated.hasMore) {
                    DropdownMenuItem(
                        text = { Text(if (showAllModels) "Show fewer models" else "Show all ${curated.remainder.size} models") },
                        trailingIcon = {
                            Icon(
                                imageVector = if (showAllModels) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        },
                        onClick = { showAllModels = !showAllModels },
                    )
                    if (showAllModels) {
                        curated.remainder.forEach { model ->
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
    }
}
