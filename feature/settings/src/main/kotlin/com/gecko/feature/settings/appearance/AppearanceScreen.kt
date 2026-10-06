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
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.gecko.core.designsystem.theme.geckoPress
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.gecko.core.designsystem.theme.LocalGeckoMotionEnabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.model.preferences.ThemeMode
import com.gecko.feature.settings.component.*

@Composable
fun AppearanceScreen(onBack: () -> Unit, modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(modifier = modifier, topBar = { SettingsTopBar("Appearance", onBack) }) { padding ->
        SettingsPage(padding) {
            SettingsSectionHeader("Theme")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOption("System default", "Follows your device", ThemeMode.SYSTEM, state.themeMode, viewModel::setThemeMode)
                ThemeOption("Light", "Bright background", ThemeMode.LIGHT, state.themeMode, viewModel::setThemeMode)
                ThemeOption("Dark", "Dark background", ThemeMode.DARK, state.themeMode, viewModel::setThemeMode)
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
    val press = remember { MutableInteractionSource() }
    val duration = if (LocalGeckoMotionEnabled.current) 180 else 0
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(duration), label = "themeBorderWidth")
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        tween(duration), label = "themeBorderColor")
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier.fillMaxWidth().geckoPress(press).selectable(selected,
            interactionSource = press, indication = ripple(), role = Role.RadioButton, onClick = { onSelect(mode) })) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
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
    val ink = if (mode == ThemeMode.SYSTEM) Color(0xFF888888) else if (dark) Color(0xFFEDEDED) else Color(0xFF202020)
    val background = if (mode == ThemeMode.SYSTEM) Modifier.background(Brush.horizontalGradient(
        0f to Color(0xFFF2F2F2), 0.5f to Color(0xFFF2F2F2),
        0.5f to Color(0xFF181818), 1f to Color(0xFF181818),
    )) else Modifier.background(paper)
    Column(Modifier.size(44.dp, 52.dp).clip(RoundedCornerShape(8.dp)).then(background).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(ink))
        Box(Modifier.fillMaxWidth().height(3.dp).background(ink.copy(alpha = 0.5f)))
        Box(Modifier.fillMaxWidth(0.65f).height(3.dp).background(ink.copy(alpha = 0.5f)))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(3.dp))
            .background(if (mode == ThemeMode.SYSTEM) Color(0xFF777777) else ink.copy(alpha = 0.12f)))
    }
}
