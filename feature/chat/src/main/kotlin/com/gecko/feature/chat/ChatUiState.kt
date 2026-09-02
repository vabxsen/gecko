package com.gecko.feature.chat

import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.conversation.Conversation
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.domain.model.friendlyName

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val searchQuery: String = "",
    val providerConfigs: List<ProviderConfig> = emptyList(),
    val selectedConfigId: String? = null,
    val selectedModelId: String? = null,
    /**
     * Every saved key's cached model catalog, keyed by provider-config id — not just the selected
     * one. The model picker shows all providers at once, so it needs the whole map up front;
     * loading one provider at a time is what made the old picker's "Tap to load models…" rows
     * necessary.
     */
    val modelCatalog: Map<String, List<ModelInfo>> = emptyMap(),
    /** Configs whose catalog is being fetched right now, so their picker rows can show a spinner. */
    val loadingModelConfigIds: Set<String> = emptySet(),
    val editingMessageId: String? = null,
    val errorMessage: String? = null,
    val sendOnEnter: Boolean = true,
    val streamingEnabled: Boolean = true,
) {
    val currentConversation: Conversation?
        get() = conversations.find { it.id == currentConversationId }

    val enabledProviders: List<ProviderConfig>
        get() = providerConfigs.filter { it.enabled && it.hasApiKey }

    val selectedProvider: ProviderConfig?
        get() = providerConfigs.find { it.id == selectedConfigId }

    val selectedModel: ModelInfo?
        get() = modelCatalog[selectedConfigId].orEmpty().find { it.modelId == selectedModelId }

    /**
     * What to call the current model in the top bar. Prefers the friendly display name, but a
     * selection restored from preferences before its catalog has loaded has no [ModelInfo] yet —
     * the raw id is still better than implying nothing is selected.
     */
    val selectedModelLabel: String?
        get() = selectedModel?.friendlyName ?: selectedModelId

    val canSend: Boolean
        get() = !isGenerating && selectedConfigId != null && selectedModelId != null
}
