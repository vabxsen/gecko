package com.gecko.core.data.mapper

import com.gecko.core.database.entity.ConversationEntity
import com.gecko.core.database.entity.MessageEntity
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import com.gecko.core.model.chat.TokenUsage
import com.gecko.core.model.conversation.Conversation
import com.gecko.core.model.provider.ProviderId
import java.time.Instant

internal fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    pinned = pinned,
    providerId = providerId?.let(ProviderId::fromSlug),
    modelId = modelId,
)

internal fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    pinned = pinned,
    providerId = providerId?.slug,
    modelId = modelId,
)

internal fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = MessageRole.valueOf(role),
    content = content,
    createdAt = Instant.ofEpochMilli(createdAt),
    status = MessageStatus.valueOf(status),
    providerId = providerId?.let(ProviderId::fromSlug),
    modelId = modelId,
    tokenUsage = run {
        val prompt = promptTokens
        val completion = completionTokens
        val total = totalTokens
        if (prompt != null && completion != null && total != null) TokenUsage(prompt, completion, total) else null
    },
    errorMessage = errorMessage,
    attachmentImageBase64 = attachmentImageBase64,
    generatedImageBase64 = generatedImageBase64,
)

internal fun ChatMessage.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    content = content,
    createdAt = createdAt.toEpochMilli(),
    status = status.name,
    providerId = providerId?.slug,
    modelId = modelId,
    promptTokens = tokenUsage?.promptTokens,
    completionTokens = tokenUsage?.completionTokens,
    totalTokens = tokenUsage?.totalTokens,
    errorMessage = errorMessage,
    attachmentImageBase64 = attachmentImageBase64,
    generatedImageBase64 = generatedImageBase64,
)
