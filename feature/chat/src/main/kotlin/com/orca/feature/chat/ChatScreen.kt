package com.orca.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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

/** Screens at least this wide get a permanent side rail instead of a swipe-away drawer. */
private const val WIDE_SCREEN_BREAKPOINT_DP = 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_BREAKPOINT_DP

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    val drawerContent: @Composable () -> Unit = {
        ConversationDrawerContent(
            conversations = uiState.conversations,
            currentConversationId = uiState.currentConversationId,
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onNewChat = viewModel::startNewConversation,
            onSelectConversation = viewModel::selectConversation,
            onRenameConversation = viewModel::renameConversation,
            onDeleteConversation = viewModel::deleteConversation,
            onTogglePinned = viewModel::setPinned,
            onOpenSettings = onOpenSettings,
        )
    }

    if (isWideScreen) {
        Row(modifier = modifier.fillMaxSize()) {
            Surface(modifier = Modifier.width(320.dp).fillMaxHeight()) { drawerContent() }
            VerticalDivider()
            ChatContent(
                uiState = uiState,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                showMenuButton = false,
                onOpenDrawer = {},
                modifier = Modifier.fillMaxHeight(),
            )
        }
    } else {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
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
            ChatContent(
                uiState = uiState,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                showMenuButton = true,
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopBar(
                title = uiState.currentConversation?.title ?: "New chat",
                showMenuButton = showMenuButton,
                onOpenDrawer = onOpenDrawer,
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
