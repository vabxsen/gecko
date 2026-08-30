package com.orca.domain.usecase

import com.orca.core.model.chat.MessageRole
import com.orca.domain.repository.ConversationRepository
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/** Renders every conversation as a single Markdown transcript, newest first. */
class ExportConversationsUseCase(
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(): String {
        val conversations = conversationRepository.observeConversations().first()
        if (conversations.isEmpty()) return "# Orca conversation export\n\nNo conversations yet.\n"

        val formatter = DateTimeFormatter.ISO_INSTANT
        return buildString {
            appendLine("# Orca conversation export")
            appendLine()
            conversations.forEach { conversation ->
                appendLine("## ${conversation.title}")
                appendLine()
                appendLine("_${formatter.format(conversation.updatedAt)}_")
                appendLine()
                val messages = conversationRepository.observeMessages(conversation.id).first()
                messages.forEach { message ->
                    val speaker = when (message.role) {
                        MessageRole.USER -> "**You**"
                        MessageRole.ASSISTANT -> "**Assistant**"
                        MessageRole.SYSTEM -> "**System**"
                    }
                    appendLine("$speaker:")
                    appendLine()
                    appendLine(message.content)
                    appendLine()
                }
                appendLine("---")
                appendLine()
            }
        }
    }
}
