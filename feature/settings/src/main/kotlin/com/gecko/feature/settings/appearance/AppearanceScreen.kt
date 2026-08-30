package com.gecko.feature.settings.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.model.preferences.ThemeMode
import com.gecko.feature.settings.component.SettingsSectionHeader
import com.gecko.feature.settings.component.SettingsSwitchRow
import com.gecko.feature.settings.component.SettingsTopBar

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "Appearance", onBack = onBack) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SettingsSectionHeader("Theme")
            Column(Modifier.selectableGroup()) {
                ThemeOption("System default", ThemeMode.SYSTEM, uiState.themeMode, viewModel::setThemeMode)
                ThemeOption("Light", ThemeMode.LIGHT, uiState.themeMode, viewModel::setThemeMode)
                ThemeOption("Dark", ThemeMode.DARK, uiState.themeMode, viewModel::setThemeMode)
            }
            SettingsSectionHeader("Color")
            SettingsSwitchRow(
                title = "Use dynamic color",
                subtitle = "Match your device's wallpaper-based palette (Android 12+)",
                checked = uiState.dynamicColorEnabled,
                onCheckedChange = viewModel::setDynamicColorEnabled,
            )
        }
    }
}

@Composable
private fun ThemeOption(label: String, mode: ThemeMode, selectedMode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val selected = mode == selectedMode
    Row(
        modifier = Modifier
            .selectable(selected = selected, onClick = { onSelect(mode) }, role = Role.RadioButton)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}
