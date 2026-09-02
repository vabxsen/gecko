package com.gecko.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
private const val WIDE_SCREEN_BREAKPOINT_DP = 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_BREAKPOINT_DP
    // Hoisted out of ChatContent so an error dialog's "Choose a model" action can open it.
    var modelPickerVisible by rememberSaveable { mutableStateOf(false) }

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
                    ErrorFix.StartNewChat -> viewModel.startNewConversation()
                    ErrorFix.None -> Unit
                }
            },
        )
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
                modelPickerVisible = modelPickerVisible,
                onShowModelPicker = { modelPickerVisible = true },
                onHideModelPicker = { modelPickerVisible = false },
                showMenuButton = false,
                onOpenDrawer = {},
                onOpenSettings = onOpenSettings,
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
                modelPickerVisible = modelPickerVisible,
                onShowModelPicker = { modelPickerVisible = true },
                onHideModelPicker = { modelPickerVisible = false },
                showMenuButton = true,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatContent(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    modelPickerVisible: Boolean,
    onShowModelPicker: () -> Unit,
    onHideModelPicker: () -> Unit,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 32dp of bottom margin is for comfortable thumb reach above the gesture/nav bar when the
    // keyboard is closed. That full margin isn't needed once the keyboard is up, but the composer
    // still needs a little breathing room above the keyboard rather than sitting flush on it.
    val imeVisible = WindowInsets.isImeVisible
    val composerBottomPadding by animateDpAsState(
        targetValue = if (imeVisible) 12.dp else 32.dp,
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
                title = uiState.currentConversation?.title ?: "New chat",
                showMenuButton = showMenuButton,
                onOpenDrawer = onOpenDrawer,
                modelSelector = {
                    ModelSelectorChip(
                        selectedProvider = uiState.selectedProvider,
                        selectedModelLabel = uiState.selectedModelLabel,
                        onClick = onShowModelPicker,
                    )
                },
            )
        },
        bottomBar = {
            MessageComposer(
                isGenerating = uiState.isGenerating,
                sendOnEnter = uiState.sendOnEnter,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration,
                modifier = Modifier
                    .imePadding()
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp, bottom = composerBottomPadding),
            )
        },
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.messages.isEmpty(),
            animationSpec = tween(GeckoMotion.DURATION_EMPHASIZED, easing = GeckoMotion.EasingStandard),
            label = "chatContentCrossfade",
        ) { isEmpty ->
            if (isEmpty) {
                EmptyChatState(modifier = Modifier.padding(innerPadding))
            } else {
                MessageList(
                    messages = uiState.messages,
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
