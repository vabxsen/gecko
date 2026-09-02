package com.gecko.feature.settings.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.designsystem.component.GeckoErrorDialog
import com.gecko.domain.error.copyForUser
import com.gecko.feature.settings.component.SettingsContentPadding
import com.gecko.feature.settings.component.SettingsTopBar
import com.gecko.feature.settings.component.modelPickerItems

/**
 * The single "which model do I chat with" screen. Replaces two near-identical pickers that lived
 * under separate Settings entries and wrote to different places.
 */
@Composable
fun ModelSelectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAllModels by rememberSaveable { mutableStateOf(false) }

    uiState.error?.let { error ->
        val copy = error.copyForUser()
        GeckoErrorDialog(
            title = copy.title,
            explanation = copy.explanation,
            // Every fix from here is either "try loading again" or something the user does
            // elsewhere, so the button only ever retries.
            fixLabel = "Try again".takeIf { error.isRetryable },
            technicalDetail = error.technicalDetail,
            onFix = {
                viewModel.dismissError()
                viewModel.refreshModels()
            },
            onDismiss = viewModel::dismissError,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "Choose a model", onBack = onBack) },
    ) { innerPadding ->
        val providerId = uiState.providerId

        if (uiState.models.isEmpty() || providerId == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(
                        text = "Loading models…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    Text(
                        text = "No models loaded for ${uiState.providerLabel.ifBlank { "this key" }} yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The old copy here read "Open it in AI Providers and tap Refresh" — advice
                    // for a screen the user had no way to reach from this one.
                    TextButton(onClick = viewModel::refreshModels) { Text("Load models") }
                }
            }
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
