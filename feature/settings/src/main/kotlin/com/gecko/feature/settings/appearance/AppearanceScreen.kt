package com.gecko.feature.settings.appearance

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.designsystem.component.GeckoPageIntro
import com.gecko.core.model.preferences.ThemeMode
import com.gecko.feature.settings.component.*

@Composable
fun AppearanceScreen(onBack: () -> Unit, modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(modifier = modifier, topBar = { SettingsTopBar("Appearance", onBack) }) { padding ->
        SettingsPage(padding) {
            GeckoPageIntro("Set the mood", "The same quiet space, in your favorite light.")
            SettingsSectionHeader("Theme")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeOption("System default", "Follows your device", ThemeMode.SYSTEM, state.themeMode, viewModel::setThemeMode)
                ThemeOption("Light", "A fresh, bright canvas", ThemeMode.LIGHT, state.themeMode, viewModel::setThemeMode)
                ThemeOption("Dark", "Easy on the eyes", ThemeMode.DARK, state.themeMode, viewModel::setThemeMode)
            }
            SettingsSectionHeader("Color")
            SettingsSwitchRow("Use dynamic color", state.dynamicColorEnabled, viewModel::setDynamicColorEnabled,
                subtitle = "Use your wallpaper palette on Android 12 and later.")
        }
    }
}

@Composable
private fun ThemeOption(label: String, description: String, mode: ThemeMode, selectedMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit) {
    val selected = mode == selectedMode
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = { onSelect(mode) })) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ThemePreview(mode)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected, onClick = null)
        }
    }
}

@Composable
private fun ThemePreview(mode: ThemeMode) {
    val dark = mode == ThemeMode.DARK
    val paper = if (dark) Color(0xFF181818) else Color(0xFFF2F2F2)
    val ink = if (dark) Color(0xFFEDEDED) else Color(0xFF202020)
    Column(Modifier.size(58.dp, 70.dp).clip(RoundedCornerShape(12.dp)).background(paper).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(ink))
        Box(Modifier.fillMaxWidth().height(4.dp).background(ink.copy(alpha = 0.3f)))
        Box(Modifier.fillMaxWidth(0.65f).height(4.dp).background(ink.copy(alpha = 0.3f)))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp))
            .background(if (mode == ThemeMode.SYSTEM) Color(0xFF777777) else ink.copy(alpha = 0.12f)))
    }
}
