package com.gecko.feature.chat

import com.gecko.core.model.chat.ChatDraft
import com.gecko.domain.repository.ConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DraftEditorState(val conversationId: String? = null, val loaded: Boolean = false,
    val draft: ChatDraft = ChatDraft(), val error: String? = null)

/** One ordered queue prevents a chat switch from loading before the preceding save finishes. */
class DraftController(private val repository: ConversationRepository, scope: CoroutineScope) {
    private sealed interface Command {
        data class Load(val id: String?) : Command
        data class Save(val id: String?, val draft: ChatDraft) : Command
    }
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(DraftEditorState())
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            for (command in commands) {
                try {
                    when (command) {
                        is Command.Load -> {
                            val draft = repository.getDraft(command.id)
                            if (mutableState.value.conversationId == command.id && !mutableState.value.loaded)
                                mutableState.value = DraftEditorState(command.id, true, draft)
                        }
                        is Command.Save -> {
                            repository.saveDraft(command.id, command.draft)
                            if (mutableState.value.conversationId == command.id)
                                mutableState.value = mutableState.value.copy(error = null)
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    val id = when (command) { is Command.Load -> command.id; is Command.Save -> command.id }
                    if (mutableState.value.conversationId == id) mutableState.value = mutableState.value.copy(
                        error = "Couldn't save or restore this draft. Keep Gecko open and try again.")
                }
            }
        }
        load(null)
    }

    fun load(id: String?) {
        mutableState.value = DraftEditorState(conversationId = id)
        commands.trySend(Command.Load(id))
    }

    fun save(id: String?, draft: ChatDraft) {
        if (mutableState.value.conversationId == id)
            mutableState.value = mutableState.value.copy(draft = draft)
        commands.trySend(Command.Save(id, draft))
    }
}
