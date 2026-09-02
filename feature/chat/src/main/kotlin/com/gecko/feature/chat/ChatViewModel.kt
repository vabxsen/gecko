package com.gecko.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gecko.core.common.util.newId
import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.model.curatedForSelection
import com.gecko.domain.repository.ConversationRepository
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.UserPreferencesRepository
import com.gecko.domain.usecase.EditAndResendMessageUseCase
import com.gecko.domain.usecase.RefreshProviderModelsUseCase
import com.gecko.domain.usecase.RegenerateResponseUseCase
import com.gecko.domain.usecase.SendChatMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
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
    private val refreshProviderModelsUseCase: RefreshProviderModelsUseCase,
) : ViewModel() {

    private val currentConversationId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val selectedConfigId = MutableStateFlow<String?>(null)
    private val selectedModelId = MutableStateFlow<String?>(null)
    private val editingMessageId = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val isGenerating = MutableStateFlow(false)
    private val loadingModelConfigIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Configs whose catalog has already been auto-fetched once this session. Without it, a key
     * that legitimately returns an empty catalog (or is simply offline) would re-trigger the
     * background fetch on every emission, since its catalog stays empty either way.
     */
    private val autoLoadAttempted = mutableSetOf<String>()

    private var generationJob: Job? = null

    private val conversations = searchQuery
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { query ->
            if (query.isBlank()) conversationRepository.observeConversations() else conversationRepository.searchConversations(query)
        }

    private val messages = currentConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else conversationRepository.observeMessages(id)
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
        val error: String?,
    )

    private val chatSection = combine(currentConversationId, messages, isGenerating, ::ChatSection)
    private val providerSection =
        combine(providerConfigs, selectedConfigId, selectedModelId, modelCatalog, loadingModelConfigIds, ::ProviderSection)
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
            selectedConfigId = provider.configId,
            selectedModelId = provider.modelId,
            modelCatalog = provider.catalog,
            loadingModelConfigIds = provider.loadingModels,
            editingMessageId = misc.editingId,
            errorMessage = misc.error,
            sendOnEnter = prefs.sendOnEnter,
            streamingEnabled = prefs.streamingEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collectLatest { prefs ->
                selectedConfigId.value = prefs.defaultProviderConfigId
                selectedModelId.value = prefs.defaultModelId
            }
        }
        viewModelScope.launch {
            combine(providerConfigs, selectedConfigId, selectedModelId, modelCatalog) { configs, configId, modelId, catalog ->
                val selected = configs.find { it.id == configId }
                // Nothing chosen yet — a fresh install, or the chosen key was deleted. Adopt the
                // first usable key rather than leaving the composer unsendable until the user
                // finds their way to Settings.
                val config = selected ?: configs.firstOrNull { it.enabled && it.hasApiKey } ?: return@combine null
                val models = catalog[config.id].orEmpty()
                // Otherwise only step in when the selection is unusable — no model at all, or one
                // this provider no longer offers (a retired or renamed id). Anything the catalog
                // still lists is a deliberate choice, including a model picked from behind "Show
                // all models", and must not be quietly swapped back to the curated default.
                if (selected != null && modelId != null && models.any { it.modelId == modelId }) return@combine null
                val curated = models.curatedForSelection(config.providerId, config.baseUrlOverride)
                val preferredModel = curated.primary.firstOrNull() ?: return@combine null
                config.id to preferredModel.modelId
            }.collectLatest { replacement ->
                replacement ?: return@collectLatest
                val (configId, modelId) = replacement
                selectedConfigId.value = configId
                selectedModelId.value = modelId
                userPreferencesRepository.setDefaultProviderConfig(configId)
                userPreferencesRepository.setDefaultModel(modelId)
            }
        }
        // A key whose catalog was never cached (its fetch failed when the key was saved, or the
        // app was offline then) would otherwise show up in the picker as an empty section. Fetch
        // it once per session in the background; the picker's per-provider "Load models" button
        // is the retry path if this doesn't land.
        viewModelScope.launch {
            combine(providerConfigs, modelCatalog) { configs, catalog ->
                configs.filter { it.enabled && it.hasApiKey && catalog[it.id].isNullOrEmpty() }.map { it.id }
            }.collect { missing ->
                missing.filter(autoLoadAttempted::add).forEach { loadModels(it, silent = true) }
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

    fun sendMessage(text: String, attachmentImageBase64: String? = null) {
        if (isGenerating.value) return
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachmentImageBase64 == null) return
        val configId = selectedConfigId.value
        val modelId = selectedModelId.value
        if (configId == null || modelId == null) {
            errorMessage.value = "Pick an AI provider and model first — tap the model name above, or go to Settings → Model preferences."
            return
        }

        viewModelScope.launch {
            val providerId = resolveProviderId(configId) ?: return@launch
            val conversationId = currentConversationId.value
                ?: conversationRepository.createConversation(providerId, modelId).id.also { currentConversationId.value = it }

            // uiState.messages is already this same reactive query's latest result for an
            // existing, already-open conversation — reuse it instead of re-querying Room a
            // second time (this history can carry large base64 image blobs on long chats). A
            // brand-new conversation's id hasn't propagated through that reactive chain yet at
            // this point, so it still needs a direct fetch (trivially cheap: empty history).
            val history = if (uiState.value.currentConversationId == conversationId) {
                uiState.value.messages
            } else {
                conversationRepository.observeMessages(conversationId).first()
            }
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

            runGeneration {
                sendChatMessageUseCase(conversationId, configId, providerId, modelId, history + userMessage, streaming = uiState.value.streamingEnabled)
            }
        }
    }

    fun regenerate() {
        if (isGenerating.value) return
        val conversationId = currentConversationId.value ?: return
        val configId = selectedConfigId.value ?: return
        val modelId = selectedModelId.value ?: return
        runGeneration {
            val providerId = resolveProviderId(configId) ?: return@runGeneration flowOf(unresolvedProviderError())
            regenerateResponseUseCase(conversationId, configId, providerId, modelId, streaming = uiState.value.streamingEnabled)
        }
    }

    private suspend fun resolveProviderId(configId: String): ProviderId? =
        providerConfigs.first().find { it.id == configId }?.providerId

    private fun unresolvedProviderError() =
        ChatEvent.Error(message = "This API key was removed. Pick another one.", cause = null, isRetryable = false)

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
        val configId = selectedConfigId.value ?: return
        val modelId = selectedModelId.value ?: return
        editingMessageId.value = null
        runGeneration {
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

    /**
     * Provider and model move together in one call. The old picker set them separately, which
     * meant selecting a provider first blanked the model and left the chat unsendable until a
     * second tap landed — there is no useful intermediate state to expose.
     */
    fun selectModel(configId: String, modelId: String) {
        selectedConfigId.value = configId
        selectedModelId.value = modelId
        viewModelScope.launch {
            userPreferencesRepository.setDefaultProviderConfig(configId)
            userPreferencesRepository.setDefaultModel(modelId)
        }
    }

    /**
     * Fetches and caches one key's model catalog. [silent] is for the unprompted background fetch
     * — a failure there leaves the picker's "Load models" button in place rather than throwing a
     * snackbar at a user who never asked for anything. An explicit tap does report why it failed.
     */
    fun loadModels(configId: String, silent: Boolean = false) {
        if (configId in loadingModelConfigIds.value) return
        viewModelScope.launch {
            loadingModelConfigIds.update { it + configId }
            try {
                refreshProviderModelsUseCase(configId).onFailure { error ->
                    if (!silent) {
                        errorMessage.value = error.message ?: "Couldn't load this provider's models."
                    }
                }
            } finally {
                loadingModelConfigIds.update { it - configId }
            }
        }
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

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
