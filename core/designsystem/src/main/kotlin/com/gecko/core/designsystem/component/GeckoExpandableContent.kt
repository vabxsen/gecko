package com.gecko.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.gecko.core.designsystem.theme.GeckoMotion
import com.gecko.core.designsystem.theme.LocalGeckoMotionEnabled

/** Consistent top-anchored disclosure motion for optional settings and diagnostic details. */
@Composable
fun GeckoExpandableContent(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motion = LocalGeckoMotionEnabled.current
    AnimatedVisibility(visible, modifier,
        enter = if (motion) expandVertically(tween(240, easing = GeckoMotion.EasingEmphasized),
            expandFrom = Alignment.Top) + fadeIn(tween(180)) else EnterTransition.None,
        exit = if (motion) shrinkVertically(tween(180), shrinkTowards = Alignment.Top) +
            fadeOut(tween(120)) else ExitTransition.None,
        content = content)
}
