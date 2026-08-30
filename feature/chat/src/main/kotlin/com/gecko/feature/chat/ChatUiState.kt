package com.gecko.feature.chat

import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.conversation.Conversation
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val searchQuery: String = "",
    val providerConfigs: List<ProviderConfig> = emptyList(),
    val selectedConfigId: String? = null,
    val selectedModelId: String? = null,
    val availableModels: List<ModelInfo> = emptyList(),
    val editingMessageId: String? = null,
    val errorMessage: String? = null,
    val sendOnEnter: Boolean = true,
    val streamingEnabled: Boolean = true,
) {
    val currentConversation: Conversation?
        get() = conversations.find { it.id == currentConversationId }

    val enabledProviders: List<ProviderConfig>
        get() = providerConfigs.filter { it.enabled && it.hasApiKey }

    val canSend: Boolean
        get() = !isGenerating && selectedConfigId != null && selectedModelId != null
}
