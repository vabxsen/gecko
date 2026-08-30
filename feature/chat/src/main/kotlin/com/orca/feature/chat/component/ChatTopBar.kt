package com.orca.feature.chat.component

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
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    modelSelector: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    showMenuButton: Boolean = true,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            if (showMenuButton) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(imageVector = Icons.Outlined.Menu, contentDescription = "Open conversations")
                }
            }
        },
        title = {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        },
        actions = { modelSelector() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}
