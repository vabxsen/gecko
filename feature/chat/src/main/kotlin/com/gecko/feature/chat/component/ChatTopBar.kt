package com.gecko.feature.chat.component

import com.gecko.core.designsystem.component.GeckoIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

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
                    GeckoIconButton(onClick = onOpenDrawer,
                        modifier = Modifier.padding(start = 4.dp).background(MaterialTheme.colorScheme.surface, CircleShape)) {
                        Icon(imageVector = Icons.Outlined.Menu, contentDescription = "Open conversations")
                    }
                }
            },
            title = {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, letterSpacing = (-0.5).sp),
                    fontWeight = FontWeight.SemiBold)
            },
            actions = { GeckoIconButton(onClick = onNewChat, enabled = newChatEnabled,
                modifier = Modifier.padding(end = 8.dp).background(MaterialTheme.colorScheme.surface, CircleShape)) {
                Icon(Icons.Outlined.Edit, contentDescription = "New chat")
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (showModelSelector) Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) { modelSelector() }
    }
}
