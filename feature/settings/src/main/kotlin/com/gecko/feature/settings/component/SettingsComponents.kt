package com.gecko.feature.settings.component

import com.gecko.core.designsystem.component.GeckoIconButton
import com.gecko.core.designsystem.component.GeckoTextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import com.gecko.core.designsystem.theme.LocalGeckoMotionEnabled
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.gecko.core.designsystem.theme.geckoPress
import com.gecko.core.designsystem.theme.geckoReveal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        modifier = modifier,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium) },
        navigationIcon = { GeckoIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Keeps every settings page readable on a phone, a large font, or a tablet. */
@Composable
fun SettingsPageFrame(padding: PaddingValues, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding).geckoReveal(40), contentAlignment = Alignment.TopCenter, content = content)
}

@Composable
fun SettingsPage(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    SettingsPageFrame(padding) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(SettingsContentPadding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun SettingsPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp)
            .semantics { heading() })
}

/** One quiet surface for related destinations, with full-width accessible row targets. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun SettingsGroupDivider() {
    HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    grouped: Boolean = false,
) {
    val press = remember { MutableInteractionSource() }
    Surface(modifier.fillMaxWidth().geckoPress(press, onClick != null),
        shape = if (grouped) RectangleShape else MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = if (grouped) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))) {
        Row(Modifier.then(if (onClick != null) Modifier.clickable(interactionSource = press, indication = ripple(), role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 76.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (leading != null) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, subtitle: String? = null) {
    val press = remember { MutableInteractionSource() }
    SettingsRow(title = title, subtitle = subtitle,
        modifier = modifier.geckoPress(press).toggleable(value = checked, interactionSource = press,
            indication = ripple(), role = Role.Switch, onValueChange = onCheckedChange),
        trailing = { Switch(checked = checked, onCheckedChange = null) })
}

val SettingsContentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp)

@Composable
fun AdvancedSettingsButton(expanded: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val motion = LocalGeckoMotionEnabled.current
    val rotation = animateFloatAsState(if (expanded) 180f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 550f), label = "advancedChevron")
    GeckoTextButton(onClick = onClick, enabled = enabled) {
        Text(if (expanded) "Hide advanced settings" else "Advanced settings")
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp).graphicsLayer {
            rotationZ = if (motion) rotation.value else if (expanded) 180f else 0f
        })
    }
}
