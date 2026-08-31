package com.gecko.feature.chat.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.MessageRole
import kotlinx.coroutines.launch

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    editingMessageId: String?,
    isGenerating: Boolean,
    onBeginEdit: (String) -> Unit,
    onSubmitEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lastAssistantId = remember(messages) { messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id }

    val isNearBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 2
        }
    }

    val lastMessageId = messages.lastOrNull()?.id
    val lastMessageContent = messages.lastOrNull()?.content

    // A genuinely new message appeared (sent, or a fresh assistant placeholder created) —
    // smooth one-time scroll, only if the user was already near the bottom.
    LaunchedEffect(lastMessageId) {
        if (messages.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // The same last message is growing (streaming). Re-pin with a plain, instant scrollToItem
    // rather than animateScrollToItem: an instant jump can't be interrupted the way a
    // multi-frame animation can, so restarting this on every streamed update causes no
    // fighting — it just keeps re-snapping to the bottom as the bubble grows.
    LaunchedEffect(lastMessageId, lastMessageContent) {
        if (lastMessageId != null && isNearBottom) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isEditing = message.id == editingMessageId,
                    isLastAssistantMessage = message.id == lastAssistantId,
                    onBeginEdit = { onBeginEdit(message.id) },
                    onSubmitEdit = onSubmitEdit,
                    onCancelEdit = onCancelEdit,
                    onRegenerate = onRegenerate,
                    modifier = Modifier.animateItem(),
                )
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 80.dp)) }
        }

        ScrollToBottomFab(
            visible = !isNearBottom && messages.isNotEmpty(),
            onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}
