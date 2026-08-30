package com.orca.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orca.core.common.util.newId
import com.orca.core.model.chat.ChatEvent
import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.chat.MessageRole
import com.orca.core.model.chat.MessageStatus
import com.orca.core.model.provider.ProviderId
import com.orca.domain.repository.ConversationRepository
import com.orca.domain.repository.ProviderConfigRepository
import com.orca.domain.repository.UserPreferencesRepository
import com.orca.domain.usecase.EditAndResendMessageUseCase
import com.orca.domain.usecase.RegenerateResponseUseCase
import com.orca.domain.usecase.SendChatMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val providerConfigRepository: ProviderConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val regenerateResponseUseCase: RegenerateResponseUseCase,
    private val editAndResendMessageUseCase: EditAndResendMessageUseCase,
) : ViewModel() {

    private val currentConversationId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val selectedProviderId = MutableStateFlow<ProviderId?>(null)
    private val selectedModelId = MutableStateFlow<String?>(null)
    private val editingMessageId = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val isGenerating = MutableStateFlow(false)

    private var generationJob: Job? = null

    private val conversations = searchQuery.flatMapLatest { query ->
        if (query.isBlank()) conversationRepository.observeConversations() else conversationRepository.searchConversations(query)
    }

    private val messages = currentConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else conversationRepository.observeMessages(id)
    }

    private val providerConfigs = providerConfigRepository.observeAll()

    private val availableModels = selectedProviderId.flatMapLatest { providerId ->
        if (providerId == null) flowOf(emptyList()) else providerConfigRepository.observeModels(providerId)
    }

    private data class ChatSection(val conversationId: String?, val messages: List<ChatMessage>, val generating: Boolean)
    private data class ProviderSection(
        val configs: List<com.orca.core.model.provider.ProviderConfig>,
        val providerId: ProviderId?,
        val modelId: String?,
        val models: List<com.orca.core.model.provider.ModelInfo>,
    )
    private data class MiscSection(
        val conversations: List<com.orca.core.model.conversation.Conversation>,
        val editingId: String?,
        val error: String?,
    )

    private val chatSection = combine(currentConversationId, messages, isGenerating, ::ChatSection)
    private val providerSection = combine(providerConfigs, selectedProviderId, selectedModelId, availableModels, ::ProviderSection)
    private val miscSection = combine(conversations, editingMessageId, errorMessage, ::MiscSection)

    val uiState: StateFlow<ChatUiState> = combine(
        chatSection,
        providerSection,
        miscSection,
        userPreferencesRepository.userPreferences,
    ) { chat, provider, misc, prefs ->
        ChatUiState(
            conversations = misc.conversations,
            currentConversationId = chat.conversationId,
            messages = chat.messages,
            isGenerating = chat.generating,
            searchQuery = searchQuery.value,
            providerConfigs = provider.configs,
            selectedProviderId = provider.providerId,
            selectedModelId = provider.modelId,
            availableModels = provider.models,
            editingMessageId = misc.editingId,
            errorMessage = misc.error,
            sendOnEnter = prefs.sendOnEnter,
            streamingEnabled = prefs.streamingEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.first()
            selectedProviderId.value = prefs.defaultProviderId
            selectedModelId.value = prefs.defaultModelId
        }
    }

    fun selectConversation(conversationId: String) {
        if (isGenerating.value) return
        currentConversationId.value = conversationId
        editingMessageId.value = null
    }

    fun startNewConversation() {
        if (isGenerating.value) return
        currentConversationId.value = null
        editingMessageId.value = null
    }

    fun sendMessage(text: String, attachmentImageBase64: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val providerId = selectedProviderId.value ?: return
        val modelId = selectedModelId.value ?: return

        viewModelScope.launch {
            val conversationId = currentConversationId.value
                ?: conversationRepository.createConversation(providerId, modelId).id.also { currentConversationId.value = it }

            val history = conversationRepository.observeMessages(conversationId).first()
            val userMessage = ChatMessage(
                id = newId(),
                conversationId = conversationId,
                role = MessageRole.USER,
                content = trimmed,
                createdAt = Instant.now(),
                status = MessageStatus.COMPLETE,
                attachmentImageBase64 = attachmentImageBase64,
            )
            conversationRepository.saveMessage(userMessage)
            maybeAutoTitle(conversationId, history, trimmed)

            runGeneration {
                sendChatMessageUseCase(conversationId, providerId, modelId, history + userMessage, streaming = uiState.value.streamingEnabled)
            }
        }
    }

    fun regenerate() {
        val conversationId = currentConversationId.value ?: return
        val providerId = selectedProviderId.value ?: return
        val modelId = selectedModelId.value ?: return
        runGeneration { regenerateResponseUseCase(conversationId, providerId, modelId, streaming = uiState.value.streamingEnabled) }
    }

    fun stopGeneration() {
        generationJob?.cancel()
    }

    fun beginEdit(messageId: String) {
        if (isGenerating.value) return
        editingMessageId.value = messageId
    }

    fun cancelEdit() {
        editingMessageId.value = null
    }

    fun submitEdit(newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return
        val messageId = editingMessageId.value ?: return
        val conversationId = currentConversationId.value ?: return
        val providerId = selectedProviderId.value ?: return
        val modelId = selectedModelId.value ?: return
        editingMessageId.value = null
        runGeneration { editAndResendMessageUseCase(conversationId, messageId, trimmed, providerId, modelId, streaming = uiState.value.streamingEnabled) }
    }

    fun renameConversation(conversationId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { conversationRepository.renameConversation(conversationId, trimmed) }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
            if (currentConversationId.value == conversationId) currentConversationId.value = null
        }
    }

    fun setPinned(conversationId: String, pinned: Boolean) {
        viewModelScope.launch { conversationRepository.setPinned(conversationId, pinned) }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectProvider(providerId: ProviderId) {
        selectedProviderId.value = providerId
        selectedModelId.value = null
    }

    fun selectModel(modelId: String) {
        selectedModelId.value = modelId
    }

    fun dismissError() {
        errorMessage.value = null
    }

    private fun runGeneration(flowProvider: suspend () -> Flow<ChatEvent>) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            isGenerating.value = true
            try {
                flowProvider().collect { event ->
                    if (event is ChatEvent.Error) errorMessage.value = event.message
                }
            } finally {
                isGenerating.value = false
            }
        }
    }

    private suspend fun maybeAutoTitle(conversationId: String, priorHistory: List<ChatMessage>, firstUserText: String) {
        if (priorHistory.isNotEmpty()) return
        val title = firstUserText.lineSequence().first().take(60).ifBlank { "New chat" }
        conversationRepository.renameConversation(conversationId, title)
    }
}
