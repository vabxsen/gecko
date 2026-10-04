package com.gecko.feature.settings.chatprefs

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.designsystem.component.GeckoPageIntro
import com.gecko.feature.settings.component.SettingsPage
import com.gecko.feature.settings.component.SettingsSectionHeader
import com.gecko.feature.settings.component.SettingsSwitchRow
import com.gecko.feature.settings.component.SettingsTopBar

@Composable
fun ChatPreferencesScreen(onBack: () -> Unit, modifier: Modifier = Modifier,
    viewModel: ChatPreferencesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(modifier = modifier, topBar = { SettingsTopBar("Chat preferences", onBack) }) { padding ->
        SettingsPage(padding) {
            GeckoPageIntro("Find your flow", "Small details that make conversations feel right.")
            SettingsSectionHeader("Messages")
            SettingsSwitchRow("Send on Enter", uiState.sendOnEnter, viewModel::setSendOnEnter,
                subtitle = "Use the keyboard action button to send your message.")
            SettingsSwitchRow("Stream responses", uiState.streamingEnabled, viewModel::setStreamingEnabled,
                subtitle = "Read replies as they arrive, a little at a time.")
        }
    }
}
