package com.gecko.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.gecko.core.designsystem.theme.geckoPress

/** Keep Material's accessibility, ripple, and click handling with shared spring feedback. */
@Composable
fun GeckoIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, modifier = modifier.geckoPress(interaction, enabled),
        enabled = enabled, interactionSource = interaction, content = content)
}

@Composable
fun GeckoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(onClick = onClick, modifier = modifier.geckoPress(interaction, enabled),
        enabled = enabled, interactionSource = interaction, content = content)
}

@Composable
fun GeckoTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(onClick = onClick, modifier = modifier.geckoPress(interaction, enabled),
        enabled = enabled, interactionSource = interaction, content = content)
}
