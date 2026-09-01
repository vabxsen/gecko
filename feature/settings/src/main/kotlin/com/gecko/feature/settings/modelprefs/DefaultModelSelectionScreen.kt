package com.gecko.feature.settings.modelprefs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.feature.settings.component.SettingsContentPadding
import com.gecko.feature.settings.component.SettingsTopBar
import com.gecko.feature.settings.component.modelPickerItems

@Composable
fun DefaultModelSelectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DefaultModelSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAllModels by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "Select default model", onBack = onBack) },
    ) { innerPadding ->
        val providerId = uiState.providerId
        if (uiState.models.isEmpty() || providerId == null) {
            Text(
                text = "No models loaded for this provider yet. Open it in AI Providers and tap Refresh.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(innerPadding).padding(20.dp),
            )
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), contentPadding = SettingsContentPadding) {
            modelPickerItems(
                models = uiState.models,
                providerId = providerId,
                baseUrlOverride = uiState.baseUrlOverride,
                selectedModelId = uiState.selectedModelId,
                showAll = showAllModels,
                onToggleShowAll = { showAllModels = !showAllModels },
                onSelectModel = { modelId ->
                    viewModel.selectModel(modelId)
                    onBack()
                },
                emptyContent = {},
            )
        }
    }
}
