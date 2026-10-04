package com.gecko.feature.chat

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gecko.core.designsystem.theme.GeckoMotion
import com.gecko.core.designsystem.component.GeckoErrorDialog
import com.gecko.core.model.error.ErrorFix
import com.gecko.domain.error.copyForUser
import com.gecko.feature.chat.component.ChatTopBar
import com.gecko.feature.chat.component.ConversationDrawerContent
import com.gecko.feature.chat.component.EmptyChatState
import com.gecko.feature.chat.component.MessageComposer
import com.gecko.feature.chat.component.MessageList
import com.gecko.feature.chat.component.ModelPickerSheet
import com.gecko.feature.chat.component.ModelSelectorChip
import kotlinx.coroutines.launch

/** Screens at least this wide get a permanent side rail instead of a swipe-away drawer. */
private const val WIDE_SCREEN_BREAKPOINT_DP = 840

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onConnectProvider: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_BREAKPOINT_DP
    // Hoisted out of ChatContent so an error dialog's "Choose a model" action can open it.
    var modelPickerVisible by rememberSaveable { mutableStateOf(false) }
    var draftRevision by rememberSaveable { mutableStateOf(0) }
    val startNewChat: () -> Unit = {
        if (!uiState.isGenerating) {
            viewModel.startNewConversation()
            draftRevision++
        }
    }

    // Replaces a snackbar that showed the provider's raw error text for four seconds and then
    // destroyed it. A failure now stays on screen until it's read, and offers the fix.
    uiState.error?.let { error ->
        val copy = error.copyForUser()
        GeckoErrorDialog(
            title = copy.title,
            explanation = copy.explanation,
            fixLabel = copy.fixLabel,
            technicalDetail = error.technicalDetail,
            onDismiss = viewModel::dismissError,
            onFix = {
                viewModel.dismissError()
                when (copy.fix) {
                    ErrorFix.Retry -> viewModel.regenerate()
                    ErrorFix.OpenProviderKey -> onOpenSettings()
                    ErrorFix.PickAnotherModel -> modelPickerVisible = true
                    ErrorFix.StartNewChat -> startNewChat()
                    ErrorFix.None -> Unit
                }
            },
        )
    }

    val drawerContent: @Composable () -> Unit = {
        ConversationDrawerContent(
            conversations = uiState.conversations,
            navigationEnabled = !uiState.isGenerating,
            currentConversationId = uiState.currentConversationId,
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onNewChat = startNewChat,
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
                draftRevision = draftRevision,
                onNewChat = startNewChat,
                viewModel = viewModel,
                modelPickerVisible = modelPickerVisible,
                onShowModelPicker = { modelPickerVisible = true },
                onHideModelPicker = { modelPickerVisible = false },
                showMenuButton = false,
                onOpenDrawer = {},
                onOpenSettings = onOpenSettings,
                onConnectProvider = onConnectProvider,
                modifier = Modifier.weight(1f).fillMaxHeight(),
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
                    navigationEnabled = !uiState.isGenerating,
                    currentConversationId = uiState.currentConversationId,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onNewChat = {
                        startNewChat()
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
                        scope.launch {
                            drawerState.close()
                            onOpenSettings()
                        }
                    },
                )
            },
            modifier = modifier,
        ) {
            ChatContent(
                uiState = uiState,
                draftRevision = draftRevision,
                onNewChat = startNewChat,
                viewModel = viewModel,
                modelPickerVisible = modelPickerVisible,
                onShowModelPicker = { modelPickerVisible = true },
                onHideModelPicker = { modelPickerVisible = false },
                showMenuButton = true,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenSettings = onOpenSettings,
                onConnectProvider = onConnectProvider,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatContent(
    uiState: ChatUiState,
    draftRevision: Int,
    onNewChat: () -> Unit,
    viewModel: ChatViewModel,
    modelPickerVisible: Boolean,
    onShowModelPicker: () -> Unit,
    onHideModelPicker: () -> Unit,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onConnectProvider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 16dp of bottom margin is for comfortable thumb reach above the gesture/nav bar when the
    // keyboard is closed. That full margin isn't needed once the keyboard is up, but the composer
    // still needs a little breathing room above the keyboard rather than sitting flush on it.
    var suggestedPrompt by remember { mutableStateOf<String?>(null) }
    val imeVisible = WindowInsets.isImeVisible
    val composerBottomPadding by animateDpAsState(
        targetValue = if (imeVisible) 8.dp else 16.dp,
        label = "composerBottomPadding",
    )
    if (modelPickerVisible) {
        ModelPickerSheet(
            providers = uiState.enabledProviders,
            modelCatalog = uiState.modelCatalog,
            loadingConfigIds = uiState.loadingModelConfigIds,
            selectedConfigId = uiState.selectedConfigId,
            selectedModelId = uiState.selectedModelId,
            onSelect = viewModel::selectModel,
            onLoadModels = { configId -> viewModel.loadModels(configId) },
            onOpenSettings = onOpenSettings,
            onDismiss = onHideModelPicker,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopBar(
                title = uiState.currentConversation?.title ?: "Gecko",
                showMenuButton = showMenuButton,
                onOpenDrawer = onOpenDrawer,
                onNewChat = onNewChat,
                newChatEnabled = !uiState.isGenerating,
                showModelSelector = uiState.enabledProviders.isNotEmpty() && !imeVisible,
                modelSelector = {
                    if (uiState.enabledProviders.isNotEmpty()) {
                        ModelSelectorChip(
                            selectedProvider = uiState.selectedProvider,
                            selectedModelLabel = uiState.selectedModelLabel,
                            onClick = onShowModelPicker,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (uiState.enabledProviders.isNotEmpty()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    key(draftRevision, uiState.currentConversationId) {
                        MessageComposer(
                            isGenerating = uiState.isGenerating,
                            sendOnEnter = uiState.sendOnEnter,
                            onSend = viewModel::sendMessage,
                            onStop = viewModel::stopGeneration,
                            suggestedPrompt = suggestedPrompt,
                            onSuggestionConsumed = { suggestedPrompt = null },
                            modifier = Modifier
                                .navigationBarsPadding()
                                .imePadding()
                                .widthIn(max = 800.dp)
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp, bottom = composerBottomPadding),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.messages.isEmpty(),
            animationSpec = tween(GeckoMotion.DURATION_EMPHASIZED, easing = GeckoMotion.EasingStandard),
            label = "chatContentCrossfade",
        ) { isEmpty ->
            if (isEmpty) {
                if (!imeVisible) {
                    EmptyChatState(
                        needsConnection = uiState.enabledProviders.isEmpty(),
                        onConnect = onConnectProvider,
                        onPromptSelected = { suggestedPrompt = it },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            } else {
                key(uiState.currentConversationId) {
                    MessageList(
                        messages = uiState.messages,
                        isGenerating = uiState.isGenerating,
                        editingMessageId = uiState.editingMessageId,
                        onBeginEdit = viewModel::beginEdit,
                        onSubmitEdit = viewModel::submitEdit,
                        onCancelEdit = viewModel::cancelEdit,
                        onRegenerate = viewModel::regenerate,
                        onShowError = viewModel::showError,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
