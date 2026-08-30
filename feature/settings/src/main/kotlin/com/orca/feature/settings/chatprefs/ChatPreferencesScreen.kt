package com.orca.feature.settings.chatprefs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orca.feature.settings.component.SettingsSwitchRow
import com.orca.feature.settings.component.SettingsTopBar

@Composable
fun ChatPreferencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "Chat preferences", onBack = onBack) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SettingsSwitchRow(
                title = "Send on Enter",
                subtitle = "The keyboard's action button sends your message instead of adding a new line",
                checked = uiState.sendOnEnter,
                onCheckedChange = viewModel::setSendOnEnter,
            )
            SettingsSwitchRow(
                title = "Stream responses",
                subtitle = "Show assistant replies as they're generated, token by token",
                checked = uiState.streamingEnabled,
                onCheckedChange = viewModel::setStreamingEnabled,
            )
        }
    }
}
