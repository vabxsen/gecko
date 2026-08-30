package com.orca.core.provider

import com.orca.core.model.chat.ChatMessage
import com.orca.core.model.chat.MessageRole
import com.orca.core.model.chat.MessageStatus
import java.time.Instant

internal fun testMessage(role: MessageRole, content: String, id: String = "m-${content.hashCode()}"): ChatMessage = ChatMessage(
    id = id,
    conversationId = "c1",
    role = role,
    content = content,
    createdAt = Instant.EPOCH,
    status = MessageStatus.COMPLETE,
)

internal fun userMessage(content: String): ChatMessage = testMessage(MessageRole.USER, content)
internal fun assistantMessage(content: String): ChatMessage = testMessage(MessageRole.ASSISTANT, content)
internal fun systemMessage(content: String): ChatMessage = testMessage(MessageRole.SYSTEM, content)
