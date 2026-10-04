package com.gecko.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gecko.core.common.util.newId
import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.error.GeckoException
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.model.provider.ProviderId
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.domain.repository.ConversationRepository
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.UserPreferencesRepository
import com.gecko.domain.usecase.EditAndResendMessageUseCase
import com.gecko.domain.usecase.ConnectProviderUseCase
import com.gecko.domain.usecase.RegenerateResponseUseCase
import com.gecko.domain.usecase.SendChatMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val providerConfigRepository: ProviderConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val regenerateResponseUseCase: RegenerateResponseUseCase,
    private val editAndResendMessageUseCase: EditAndResendMessageUseCase,
    private val connectProviderUseCase: ConnectProviderUseCase,
) : ViewModel() {

    private val currentConversationId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private data class ModelSelection(val configId: String?, val modelId: String?)

    private val selection: StateFlow<ModelSelection?> = userPreferencesRepository.userPreferences
        .map { ModelSelection(it.defaultProviderConfigId, it.defaultModelId) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val editingMessageId = MutableStateFlow<String?>(null)
    /**
     * The failure currently worth interrupting someone about. Holds the whole [GeckoError] rather
     * than its message: the kind is what decides the dialog copy and which fix button appears, and
     * flattening it to a string here is exactly how that was lost before.
     */
    private val activeError = MutableStateFlow<GeckoError?>(null)
    private val isGenerating = MutableStateFlow(false)
    private val loadingModelConfigIds = MutableStateFlow<Set<String>>(emptySet())

    private var generationJob: Job? = null
    private var failedConnectionId: String? = null

    private val conversations = searchQuery
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { query ->
            if (query.isBlank()) conversationRepository.observeConversations() else conversationRepository.searchConversations(query)
        }

    private val chatSection = currentConversationId.flatMapLatest { id ->
        val messages = if (id == null) flowOf(emptyList()) else conversationRepository.observeMessages(id)
        combine(messages, isGenerating) { history, generating -> ChatSection(id, history, generating) }
    }

    private val providerConfigs = providerConfigRepository.observeAll()

    /**
     * Every saved config's cached catalog at once, so the model picker can list all providers
     * without a per-provider "tap to load" round trip. Re-subscribes only when the *set of config
     * ids* changes, not on every unrelated config edit (a renamed label, a connection-status
     * write), so an in-flight catalog observation isn't torn down and restarted needlessly.
     */
    private val modelCatalog: Flow<Map<String, List<ModelInfo>>> = providerConfigs
        .map { configs -> configs.map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(ids.map { id -> providerConfigRepository.observeModels(id).map { id to it } }) { it.toMap() }
            }
        }

    private data class ChatSection(val conversationId: String?, val messages: List<ChatMessage>, val generating: Boolean)
    private data class ProviderSection(
        val configs: List<ProviderConfig>,
        val configId: String?,
        val modelId: String?,
        val catalog: Map<String, List<ModelInfo>>,
        val loadingModels: Set<String>,
    )
    private data class MiscSection(
        val conversations: List<com.gecko.core.model.conversation.Conversation>,
        val editingId: String?,
        val error: GeckoError?,
    )

    private val providerSection =
        combine(providerConfigs, selection, modelCatalog, loadingModelConfigIds) { configs, selected, catalog, loading ->
            ProviderSection(configs, selected?.configId, selected?.modelId, catalog, loading)
        }
    private val miscSection = combine(conversations, editingMessageId, activeError, searchQuery) { chats, editing, error, _ ->
        MiscSection(chats, editing, error)
    }

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
            selectedConfigId = provider.configId,
            selectedModelId = provider.modelId,
            modelCatalog = provider.catalog,
            loadingModelConfigIds = provider.loadingModels,
            editingMessageId = misc.editingId,
            error = misc.error,
            sendOnEnter = prefs.sendOnEnter,
            streamingEnabled = prefs.streamingEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            combine(providerConfigs, selection) { configs, selected ->
                selected ?: return@combine null
                if (configs.any { it.connectionStatus == ConnectionStatus.Testing }) return@combine null
                val available = configs.filter { it.enabled && it.hasApiKey }
                val config = available.find { it.id == selected.configId }
                    ?: available.firstOrNull { it.verifiedModelId != null }
                    ?: available.firstOrNull()
                val replacement = ModelSelection(config?.id, config?.verifiedModelId)
                replacement.takeIf { it != selected }
            }.distinctUntilChanged().collect { replacement ->
                replacement ?: return@collect
                userPreferencesRepository.setDefaultSelection(replacement.configId, replacement.modelId)
            }
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

    fun sendMessage(text: String, attachmentImageBase64: String? = null): Boolean {
        if (isGenerating.value || loadingModelConfigIds.value.isNotEmpty()) return false
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachmentImageBase64 == null) return false
        val configId = selection.value?.configId
        val modelId = selection.value?.modelId
        if (configId == null || modelId == null || uiState.value.enabledProviders.none { it.id == configId }) {
            // Two different problems wear the same symptom here: no key saved at all, versus a key
            // whose catalog hasn't produced a model yet. They need opposite advice.
            activeError.value = if (uiState.value.enabledProviders.isEmpty()) {
                GeckoError(ErrorKind.NoApiKey)
            } else {
                GeckoError(ErrorKind.ModelUnavailable).withProviderContext()
            }
            return false
        }

        runGeneration(configId) {
            val providerId = resolveProviderId(configId) ?: return@runGeneration flowOf(unresolvedProviderError())
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
            maybeAutoTitle(conversationId, history, trimmed.ifBlank { "Image attachment" })

            sendChatMessageUseCase(conversationId, configId, providerId, modelId, history + userMessage, streaming = uiState.value.streamingEnabled)
        }
        return true
    }

    fun regenerate() {
        if (isGenerating.value) return
        val conversationId = currentConversationId.value ?: return
        val configId = selection.value?.configId ?: return
        val modelId = selection.value?.modelId ?: return
        runGeneration(configId) {
            val providerId = resolveProviderId(configId) ?: return@runGeneration flowOf(unresolvedProviderError())
            regenerateResponseUseCase(conversationId, configId, providerId, modelId, streaming = uiState.value.streamingEnabled)
        }
    }

    private suspend fun resolveProviderId(configId: String): ProviderId? =
        providerConfigs.first().find { it.id == configId && it.enabled && it.hasApiKey }?.providerId

    private fun unresolvedProviderError() = ChatEvent.Error(GeckoError(ErrorKind.KeyRemoved))

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
        if (isGenerating.value) return
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return
        val messageId = editingMessageId.value ?: return
        val conversationId = currentConversationId.value ?: return
        val configId = selection.value?.configId ?: return
        val modelId = selection.value?.modelId ?: return
        editingMessageId.value = null
        runGeneration(configId) {
            val providerId = resolveProviderId(configId) ?: return@runGeneration flowOf(unresolvedProviderError())
            editAndResendMessageUseCase(conversationId, messageId, trimmed, configId, providerId, modelId, streaming = uiState.value.streamingEnabled)
        }
    }

    fun renameConversation(conversationId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { conversationRepository.renameConversation(conversationId, trimmed) }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (currentConversationId.value == conversationId) {
                generationJob?.cancel()
                generationJob?.join()
            }
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

    /** One connection, one verified model. Catalog entries are never selectable here. */
    fun selectConnection(configId: String, reconnect: Boolean = false) {
        if (isGenerating.value || loadingModelConfigIds.value.isNotEmpty()) return
        val config = uiState.value.enabledProviders.find { it.id == configId } ?: return
        loadingModelConfigIds.update { it + configId }
        activeError.value = null
        failedConnectionId = null
        viewModelScope.launch {
            try {
                if (!reconnect && config.verifiedModelId != null && config.connectionStatus == ConnectionStatus.Success) {
                    userPreferencesRepository.setDefaultSelection(configId, config.verifiedModelId)
                } else {
                    connectProviderUseCase(configId).onFailure {
                        failedConnectionId = configId
                        activeError.value = it.asGeckoError(configId)
                    }
                }
            } finally {
                loadingModelConfigIds.update { it - configId }
            }
        }
    }

    fun dismissError() {
        activeError.value = null
    }

    fun retryAfterError() {
        val configId = failedConnectionId
        if (configId != null) selectConnection(configId, reconnect = true) else regenerate()
    }

    /**
     * Re-opens the explanation for a message that failed earlier. The dialog that first reported it
     * is long gone by the time someone scrolls back, so the transcript keeps enough to rebuild it.
     */
    fun showError(error: GeckoError) {
        failedConnectionId = null
        activeError.value = error.withProviderContext()
    }

    /**
     * Fills in which key and which provider a failure concerns when the layer that produced it
     * couldn't know — a provider deep in a stream has no idea which of several saved keys it was
     * built from, and that's exactly what the "Open key" button needs.
     */
    private fun GeckoError.withProviderContext(configId: String? = null): GeckoError {
        val id = this.configId ?: configId ?: selection.value?.configId
        val label = providerLabel ?: uiState.value.providerConfigs.find { it.id == id }?.displayLabel
        return copy(configId = id, providerLabel = label)
    }

    private fun Throwable.asGeckoError(configId: String): GeckoError =
        ((this as? GeckoException)?.error ?: GeckoError(ErrorKind.Unknown, technicalDetail = message))
            .withProviderContext(configId)

    private val ProviderConfig.displayLabel: String get() = label.ifBlank { providerId.displayName }

    private fun runGeneration(configId: String, flowProvider: suspend () -> Flow<ChatEvent>) {
        if (isGenerating.value) return
        failedConnectionId = null
        isGenerating.value = true
        generationJob = viewModelScope.launch {
            try {
                flowProvider().collect { event ->
                    if (event is ChatEvent.Error && event.error.deservesInterrupting) {
                        activeError.value = event.error.withProviderContext(configId)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                activeError.value = error.asGeckoError(configId)
            } finally {
                isGenerating.value = false
            }
        }
        generationJob?.invokeOnCompletion { isGenerating.value = false }
    }

    private suspend fun maybeAutoTitle(conversationId: String, priorHistory: List<ChatMessage>, firstUserText: String) {
        if (priorHistory.isNotEmpty()) return
        val title = firstUserText.lineSequence().first().take(60).ifBlank { "New chat" }
        conversationRepository.renameConversation(conversationId, title)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
