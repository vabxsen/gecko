package com.gecko.feature.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.gecko.core.designsystem.theme.LocalGeckoMotionEnabled
import com.gecko.core.designsystem.theme.geckoPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ScrollToBottomFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalGeckoMotionEnabled.current
    val press = remember { MutableInteractionSource() }
    AnimatedVisibility(visible = visible,
        enter = if (motion) scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.8f) + fadeIn(tween(160)) else EnterTransition.None,
        exit = if (motion) scaleOut(tween(140), targetScale = 0.8f) + fadeOut(tween(120)) else ExitTransition.None,
        modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            interactionSource = press,
            modifier = Modifier.geckoPress(press),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(imageVector = Icons.Outlined.KeyboardArrowDown, contentDescription = "Scroll to bottom")
        }
    }
}
