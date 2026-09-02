package com.gecko.feature.settings.component

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.model.curatedForSelection
import com.gecko.domain.model.detailLine
import com.gecko.domain.model.friendlyName
import com.gecko.domain.model.trait

/**
 * Row showing the current selection with a trailing ">" — tapping it navigates to a dedicated
 * model-selection screen (built with [modelPickerItems]) rather than expanding in place, so the
 * picker doesn't dump every model onto the current screen.
 */
@Composable
fun ModelSelectorRow(
    selectedModelName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRow(
        title = selectedModelName ?: "Choose a model",
        modifier = modifier,
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

/**
 * Shared by the AI Providers per-key model-selection screen and the Model preferences
 * default-model-selection screen so both stay in sync: a curated, sorted list up front, with
 * anything a provider returns that didn't make the cut (previews, TTS/image/audio variants,
 * etc.) reachable behind a "Show all models" toggle rather than lost.
 */
fun LazyListScope.modelPickerItems(
    models: List<ModelInfo>,
    providerId: ProviderId,
    baseUrlOverride: String?,
    selectedModelId: String?,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    onSelectModel: (String) -> Unit,
    emptyContent: @Composable () -> Unit,
) {
    if (models.isEmpty()) {
        item { emptyContent() }
        return
    }

    val curated = models.curatedForSelection(providerId, baseUrlOverride)

    items(curated.primary, key = { it.modelId }) { model ->
        ModelRow(model = model, selected = model.modelId == selectedModelId, onClick = { onSelectModel(model.modelId) })
    }

    if (curated.hasMore) {
        item { HorizontalDivider() }
        item {
            ShowMoreModelsRow(
                count = curated.remainder.size,
                expanded = showAll,
                onClick = onToggleShowAll,
            )
        }
        if (showAll) {
            items(curated.remainder, key = { it.modelId }) { model ->
                ModelRow(model = model, selected = model.modelId == selectedModelId, onClick = { onSelectModel(model.modelId) })
            }
        }
    }
}

@Composable
private fun ShowMoreModelsRow(count: Int, expanded: Boolean, onClick: () -> Unit) {
    SettingsRow(
        title = if (expanded) "Show fewer models" else "Show all $count models",
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun ModelRow(model: ModelInfo, selected: Boolean, onClick: () -> Unit) {
    // Same wording as the in-chat picker, so a model doesn't describe itself two different ways
    // depending on which screen you reached it from.
    SettingsRow(
        title = model.friendlyName,
        subtitle = listOfNotNull(model.trait?.label, model.detailLine.ifBlank { null }).joinToString(" · "),
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
