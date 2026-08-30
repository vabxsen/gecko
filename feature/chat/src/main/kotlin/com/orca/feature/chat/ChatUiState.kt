package com.orca.feature.chat

import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.conversation.Conversation
import com.orca.core.model.provider.ModelInfo
import com.orca.core.model.provider.ProviderConfig
import com.orca.core.model.provider.ProviderId

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val searchQuery: String = "",
    val providerConfigs: List<ProviderConfig> = emptyList(),
    val selectedProviderId: ProviderId? = null,
    val selectedModelId: String? = null,
    val availableModels: List<ModelInfo> = emptyList(),
    val editingMessageId: String? = null,
    val errorMessage: String? = null,
    val sendOnEnter: Boolean = true,
) {
    val currentConversation: Conversation?
        get() = conversations.find { it.id == currentConversationId }

    val enabledProviders: List<ProviderConfig>
        get() = providerConfigs.filter { it.enabled && it.hasApiKey }

    val canSend: Boolean
        get() = !isGenerating && selectedProviderId != null && selectedModelId != null
}
