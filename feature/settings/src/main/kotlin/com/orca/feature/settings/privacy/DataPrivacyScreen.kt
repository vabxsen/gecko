package com.orca.feature.settings.privacy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orca.feature.settings.component.SettingsRow
import com.orca.feature.settings.component.SettingsSectionHeader
import com.orca.feature.settings.component.SettingsTopBar
import java.io.OutputStreamWriter

@Composable
fun DataPrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataPrivacyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val exportedMarkdown by viewModel.exportedMarkdown.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteConversationsDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val markdown = exportedMarkdown
        if (uri != null && markdown != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream).use { it.write(markdown) }
                }
            }
        }
        viewModel.consumeExportedMarkdown()
    }

    LaunchedEffect(exportedMarkdown) {
        if (exportedMarkdown != null) {
            exportLauncher.launch("orca-conversations.md")
        }
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissActionMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "Data & Privacy", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(innerPadding)) {
            SettingsSectionHeader("Export")
            SettingsRow(
                title = "Export conversations",
                subtitle = "Save every conversation as a Markdown file",
                leading = { Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = viewModel::prepareExport,
            )

            SettingsSectionHeader("Danger zone")
            SettingsRow(
                title = "Delete all conversations",
                subtitle = "Keeps your provider settings and API keys",
                leading = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { showDeleteConversationsDialog = true },
            )
            SettingsRow(
                title = "Clear local data",
                subtitle = "Deletes conversations, API keys, and preferences",
                leading = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { showClearAllDialog = true },
            )
        }
    }

    if (showDeleteConversationsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConversationsDialog = false },
            title = { Text("Delete all conversations?") },
            text = { Text("This permanently deletes every conversation on this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllConversations()
                    showDeleteConversationsDialog = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConversationsDialog = false }) { Text("Cancel") } },
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear local data?") },
            text = { Text("This permanently deletes every conversation, API key, and preference on this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllLocalData()
                    showClearAllDialog = false
                }) { Text("Clear everything") }
            },
            dismissButton = { TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") } },
        )
    }
}
