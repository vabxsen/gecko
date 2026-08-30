package com.orca.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orca.feature.chat.component.ChatTopBar
import com.orca.feature.chat.component.ConversationDrawerContent
import com.orca.feature.chat.component.EmptyChatState
import com.orca.feature.chat.component.MessageComposer
import com.orca.feature.chat.component.MessageList
import com.orca.feature.chat.component.ModelSelectorDropdown
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawerContent(
                conversations = uiState.conversations,
                currentConversationId = uiState.currentConversationId,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onNewChat = {
                    viewModel.startNewConversation()
                    scope.launch { drawerState.close() }
                },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onRenameConversation = viewModel::renameConversation,
                onDeleteConversation = viewModel::deleteConversation,
                onTogglePinned = viewModel::setPinned,
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
        modifier = modifier,
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    title = uiState.currentConversation?.title ?: "New chat",
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    modelSelector = {
                        ModelSelectorDropdown(
                            enabledProviders = uiState.enabledProviders,
                            selectedProviderId = uiState.selectedProviderId,
                            selectedModelId = uiState.selectedModelId,
                            modelsForSelectedProvider = uiState.availableModels,
                            onSelectProvider = viewModel::selectProvider,
                            onSelectModel = viewModel::selectModel,
                        )
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                MessageComposer(
                    isGenerating = uiState.isGenerating,
                    sendOnEnter = uiState.sendOnEnter,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::stopGeneration,
                    modifier = Modifier
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        ) { innerPadding ->
            if (uiState.messages.isEmpty()) {
                EmptyChatState(modifier = Modifier.padding(innerPadding))
            } else {
                MessageList(
                    messages = uiState.messages,
                    editingMessageId = uiState.editingMessageId,
                    isGenerating = uiState.isGenerating,
                    onBeginEdit = viewModel::beginEdit,
                    onSubmitEdit = viewModel::submitEdit,
                    onCancelEdit = viewModel::cancelEdit,
                    onRegenerate = viewModel::regenerate,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
