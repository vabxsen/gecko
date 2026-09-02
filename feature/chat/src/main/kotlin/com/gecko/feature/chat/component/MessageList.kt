package com.gecko.feature.chat.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import kotlinx.coroutines.launch

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    editingMessageId: String?,
    onBeginEdit: (String) -> Unit,
    onSubmitEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onShowError: (GeckoError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lastAssistantId = remember(messages) { messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id }

    val lastMessage = messages.lastOrNull()
    val lastMessageId = lastMessage?.id

    // The reply is revealed a word at a time rather than in the slabs the network and the
    // database-write throttle deliver it in. `key` ties the reveal's progress to the message it
    // belongs to, so a new reply starts from nothing instead of inheriting the previous one's
    // position.
    val lastVisibleContent = key(lastMessageId) {
        rememberTypewriterText(
            fullText = lastMessage?.content.orEmpty(),
            isStreaming = lastMessage?.status == MessageStatus.STREAMING,
        )
    }

    // `reverseLayout` is what keeps a reply that's being written glued to the bottom of the
    // screen, and it does it structurally rather than by chasing the text with scroll calls.
    //
    // A normal LazyColumn anchors to the *top*: it holds the first visible item still, so a
    // growing message extends downwards, out of sight, and every new word has to be answered
    // with another scroll — which is a fight the list wins, because it re-anchors on every
    // re-measure. Reversed, the anchor is the bottom edge: the newest message sits against it
    // and grows upward, so the words being written stay exactly where the reader is looking and
    // no scrolling happens at all. Scrolling away still works normally, and nothing yanks the
    // view back, because there is no auto-scroll left to fight.
    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
        ) {
            // Emitted first, so under `reverseLayout` it lands at the very bottom: breathing room
            // between the newest message and the composer.
            item { Spacer(Modifier.padding(bottom = 68.dp)) }

            // Reversed to match the reversed layout, so the newest message is the one pinned to
            // the bottom. `asReversed()` is a view over the same list, not a copy.
            items(messages.asReversed(), key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    visibleContent = if (message.id == lastMessageId) lastVisibleContent else message.content,
                    isEditing = message.id == editingMessageId,
                    isLastAssistantMessage = message.id == lastAssistantId,
                    onBeginEdit = { onBeginEdit(message.id) },
                    onSubmitEdit = onSubmitEdit,
                    onCancelEdit = onCancelEdit,
                    onRegenerate = onRegenerate,
                    onShowError = onShowError,
                    // Deliberately no animateItem(): it animates every size change, so a bubble
                    // growing a word at a time spends the whole reply chasing its own last frame.
                    // Messages are only appended here, never reordered, so it earns nothing.
                )
            }
        }

        ScrollToBottomFab(
            visible = !isAtBottom && messages.isNotEmpty(),
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}
