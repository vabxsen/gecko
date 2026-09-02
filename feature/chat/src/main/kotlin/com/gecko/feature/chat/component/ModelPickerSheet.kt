package com.gecko.feature.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.icon.ProviderLogo
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.domain.model.curatedForSelection
import com.gecko.domain.model.detailLine
import com.gecko.domain.model.friendlyName
import com.gecko.domain.model.matchesQuery
import com.gecko.domain.model.trait
import kotlinx.coroutines.launch

/** Below this many models in total, a search field is more clutter than help. */
private const val SEARCH_THRESHOLD = 10

/**
 * One provider's slice of the picker. [recommended] is what's shown by default (the curated
 * shortlist); [more] is the rest of that provider's catalog, behind a toggle so a 100-model
 * NVIDIA catalog can't bury the three models most people want.
 */
private data class PickerSection(
    val config: ProviderConfig,
    val recommended: List<ModelInfo>,
    val more: List<ModelInfo>,
) {
    val isEmpty: Boolean get() = recommended.isEmpty() && more.isEmpty()
}

/**
 * The model picker: a full-height bottom sheet listing every model from every saved API key at
 * once, grouped by provider, searchable, with each model described in plain English.
 *
 * The design is driven by what a first-time user needs, which is different from what the old
 * top-bar dropdown offered:
 *  - **Everything is loaded up front.** The previous dropdown could only show models for the
 *    *currently selected* provider and made every other one read "Tap to load models…", so
 *    switching providers took two round trips through a menu that closed itself in between. Here
 *    the caller supplies every provider's catalog and choosing a model sets provider + model in
 *    one tap.
 *  - **Nothing is a dead end.** A provider whose catalog was never fetched gets an inline "Load
 *    models" action instead of a greyed-out "No models loaded yet", and having no providers at
 *    all leads to the screen where you add one.
 *  - **Names are explained.** Each row carries a plain-English tag and a context/vision line, so
 *    a newcomer can tell `gemini-3.6-flash` from `gemini-pro-latest` without knowing Google's
 *    naming scheme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<ProviderConfig>,
    modelCatalog: Map<String, List<ModelInfo>>,
    loadingConfigIds: Set<String>,
    selectedConfigId: String?,
    selectedModelId: String?,
    onSelect: (configId: String, modelId: String) -> Unit,
    onLoadModels: (configId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    // Which providers the user has explicitly expanded/collapsed. Absent means "use the default"
    // (expanded only when this provider holds the current selection deep in its full catalog),
    // so an explicit collapse still wins over that default.
    val expansionOverrides = remember { mutableStateMapOf<String, Boolean>() }

    /**
     * Runs [action], then animates the sheet away rather than letting it vanish mid-gesture.
     * The action deliberately goes first: hanging it off the animation's completion would drop
     * the user's choice entirely if that animation were ever interrupted.
     */
    fun dismissThen(action: () -> Unit = {}) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val sections = remember(providers, modelCatalog, query) {
        buildSections(providers, modelCatalog, query)
    }
    val totalModels = remember(modelCatalog) { modelCatalog.values.sumOf { it.size } }
    val showSearch = providers.isNotEmpty() && totalModels >= SEARCH_THRESHOLD

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            SheetHeader(providerCount = providers.size, modelCount = totalModels)

            if (showSearch) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            LazyColumn(
                // `fill = false` lets a short list keep the sheet short while still capping a
                // long one at the space the sheet actually has.
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                when {
                    providers.isEmpty() -> item(key = "no-providers") {
                        NoProvidersState(onAddProvider = { dismissThen(onOpenSettings) })
                    }

                    sections.all { it.isEmpty } && query.isNotBlank() -> item(key = "no-results") {
                        NoResultsState(query = query)
                    }

                    else -> sections.forEach { section ->
                        pickerSection(
                            section = section,
                            searching = query.isNotBlank(),
                            loading = section.config.id in loadingConfigIds,
                            selectedConfigId = selectedConfigId,
                            selectedModelId = selectedModelId,
                            expansionOverrides = expansionOverrides,
                            onSelect = { modelId -> dismissThen { onSelect(section.config.id, modelId) } },
                            onLoadModels = { onLoadModels(section.config.id) },
                        )
                    }
                }

                if (providers.isNotEmpty()) {
                    item(key = "manage-providers") {
                        ManageProvidersFooter(onClick = { dismissThen(onOpenSettings) })
                    }
                }
            }
        }
    }
}

private fun buildSections(
    providers: List<ProviderConfig>,
    modelCatalog: Map<String, List<ModelInfo>>,
    query: String,
): List<PickerSection> = providers.map { config ->
    val models = modelCatalog[config.id].orEmpty()
    if (query.isNotBlank()) {
        // Search deliberately ignores curation and searches the provider's whole catalog: if you
        // typed a name you already know what you're after, and hiding it behind "Show all" would
        // make the search look broken.
        PickerSection(config, recommended = models.filter { it.matchesQuery(query) }, more = emptyList())
    } else {
        val curated = models.curatedForSelection(config.providerId, config.baseUrlOverride)
        PickerSection(config, recommended = curated.primary, more = curated.remainder)
    }
}

private fun LazyListScope.pickerSection(
    section: PickerSection,
    searching: Boolean,
    loading: Boolean,
    selectedConfigId: String?,
    selectedModelId: String?,
    expansionOverrides: MutableMap<String, Boolean>,
    onSelect: (String) -> Unit,
    onLoadModels: () -> Unit,
) {
    val configId = section.config.id
    // A model chosen from the full catalog would otherwise be invisible when the sheet reopens.
    val selectionIsHidden = configId == selectedConfigId && section.more.any { it.modelId == selectedModelId }
    val expanded = expansionOverrides[configId] ?: selectionIsHidden

    if (searching && section.isEmpty) return

    item(key = "header-$configId") { ProviderHeader(section.config) }

    if (section.isEmpty) {
        item(key = "empty-$configId") {
            CatalogNotLoadedRow(loading = loading, onLoadModels = onLoadModels)
        }
        return
    }

    items(section.recommended, key = { "model-$configId-${it.modelId}" }) { model ->
        ModelRow(
            model = model,
            selected = configId == selectedConfigId && model.modelId == selectedModelId,
            onClick = { onSelect(model.modelId) },
        )
    }

    if (section.more.isNotEmpty()) {
        item(key = "toggle-$configId") {
            ShowAllRow(
                count = section.more.size,
                expanded = expanded,
                onClick = { expansionOverrides[configId] = !expanded },
            )
        }
        if (expanded) {
            items(section.more, key = { "more-$configId-${it.modelId}" }) { model ->
                ModelRow(
                    model = model,
                    selected = configId == selectedConfigId && model.modelId == selectedModelId,
                    onClick = { onSelect(model.modelId) },
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(providerCount: Int, modelCount: Int) {
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
        Text(
            text = "Choose a model",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                providerCount == 0 -> "Gecko chats through an AI provider's API key."
                modelCount == 0 -> "Loading the models your saved keys can use…"
                // Deliberately not a count. Announcing "39 models" advertises the exact pile the
                // shortlist exists to hide, and it's not something anyone needs to know.
                else -> "Pick the one you want to chat with. You can switch at any time."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text("Search models") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
    )
}

@Composable
private fun ProviderHeader(config: ProviderConfig) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProviderLogo(
            providerId = config.providerId,
            baseUrlOverride = config.baseUrlOverride,
            size = 24.dp,
        )
        Text(
            text = config.label.ifBlank { config.providerId.displayName },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModelRow(model: ModelInfo, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = model.friendlyName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    model.trait?.let { TraitPill(label = it.label) }
                }
                val detail = model.detailLine
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Currently selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Small neutral pill; deliberately one colour for every trait so it reads as a label, not a rank. */
@Composable
private fun TraitPill(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ShowAllRow(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (expanded) "Show fewer" else "Show all $count models",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * A saved key whose model catalog isn't cached yet. The old dropdown showed a disabled "No models
 * loaded yet" here, which left the user stuck; this offers the fetch directly.
 */
@Composable
private fun CatalogNotLoadedRow(loading: Boolean, onLoadModels: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (loading) "Loading models…" else "No models loaded for this key yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
        } else {
            TextButton(onClick = onLoadModels) { Text("Load models") }
        }
    }
}

@Composable
private fun NoProvidersState(onAddProvider: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp).size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No AI providers yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Add an API key from a provider like Google Gemini, OpenAI or Anthropic, and " +
                "its models will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAddProvider, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Add an API key")
        }
    }
}

@Composable
private fun NoResultsState(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No models match \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Try a shorter search, like the provider or family name.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ManageProvidersFooter(onClick: () -> Unit) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SettingsSuggest,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Manage API keys",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
