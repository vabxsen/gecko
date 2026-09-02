package com.gecko.feature.settings.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gecko.core.designsystem.component.GeckoErrorDialog
import com.gecko.domain.error.copyForUser
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.designsystem.icon.ProviderLogo
import com.gecko.core.model.provider.ProviderId
import com.gecko.feature.settings.component.SettingsRow
import com.gecko.feature.settings.component.SettingsSectionHeader
import com.gecko.feature.settings.component.SettingsTopBar

@Composable
fun AddProviderScreen(
    onBack: () -> Unit,
    onSaved: (configId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddProviderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var keyVisible by remember { mutableStateOf(false) }

    // A rejected key is worth stopping for and explaining — it used to be red text under the
    // field carrying the provider's raw JSON.
    uiState.error?.let { error ->
        val copy = error.copyForUser()
        GeckoErrorDialog(
            title = copy.title,
            explanation = copy.explanation,
            fixLabel = null,
            technicalDetail = error.technicalDetail,
            onFix = viewModel::dismissError,
            onDismiss = viewModel::dismissError,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "Add API key", onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(ADD_PROVIDER_OPTIONS) { option ->
                val selected = option.providerId == uiState.selectedProviderId && option.baseUrl.orEmpty() == uiState.baseUrlOverride.trim()
                SettingsRow(
                    title = option.label,
                    onClick = { viewModel.selectOption(option) },
                    leading = { ProviderLogo(providerId = option.providerId, baseUrlOverride = option.baseUrl) },
                    trailing = {
                        Icon(
                            imageVector = if (selected) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            if (uiState.selectedProviderId == ProviderId.OPENAI) {
                item {
                    OutlinedTextField(
                        value = uiState.baseUrlOverride,
                        onValueChange = viewModel::updateBaseUrlOverride,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        label = { Text("Base URL") },
                        placeholder = { Text("Leave blank for OpenAI itself") },
                        singleLine = true,
                    )
                }
            }

            item {
                SettingsSectionHeader("Details")
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    OutlinedTextField(
                        value = uiState.label,
                        onValueChange = viewModel::updateLabel,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Label") },
                        placeholder = { Text("e.g. Work OpenAI key") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uiState.apiKey,
                        onValueChange = viewModel::updateApiKey,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (keyVisible) "Hide key" else "Show key",
                                )
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = { viewModel.save(onSaved = onSaved) },
                            enabled = uiState.canSave,
                        ) {
                            Text(if (uiState.isSaving) "Saving…" else "Save")
                        }
                    }
                }
            }
        }
    }
}
