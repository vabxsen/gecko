package com.gecko.feature.chat.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.gecko.core.designsystem.theme.GeckoMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    showMenuButton: Boolean = true,
    newChatEnabled: Boolean = true,
    onNewChat: () -> Unit = {},
    showModelSelector: Boolean = true,
    modelSelector: @Composable () -> Unit = {},
) {
    Column(modifier = modifier) {
        TopAppBar(
            navigationIcon = {
                if (showMenuButton) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Outlined.Menu, contentDescription = "Open conversations")
                    }
                }
            },
            title = {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (slideInVertically(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingIncoming)) { it / 3 } + fadeIn(tween(GeckoMotion.DURATION_STANDARD)))
                            .togetherWith(slideOutVertically(tween(GeckoMotion.DURATION_QUICK, easing = GeckoMotion.EasingOutgoing)) { -it / 3 } + fadeOut(tween(GeckoMotion.DURATION_QUICK)))
                    },
                    label = "chatTopBarTitle",
                ) { animatedTitle ->
                    Text(
                        text = animatedTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            actions = { IconButton(onClick = onNewChat, enabled = newChatEnabled) {
                Icon(Icons.Outlined.Edit, contentDescription = "New chat")
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (showModelSelector) Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) { modelSelector() }
    }
}
