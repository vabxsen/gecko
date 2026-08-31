package com.gecko.feature.chat.component

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.theme.GeckoMotion
import com.gecko.core.markdown.GeckoMarkdown
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus

@Composable
fun MessageBubble(
    message: ChatMessage,
    isEditing: Boolean,
    isLastAssistantMessage: Boolean,
    onBeginEdit: () -> Unit,
    onSubmitEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (message.role) {
        MessageRole.USER -> UserMessage(message, isEditing, onBeginEdit, onSubmitEdit, onCancelEdit, modifier)
        MessageRole.ASSISTANT -> AssistantMessage(message, isLastAssistantMessage, onRegenerate, modifier)
        MessageRole.SYSTEM -> Unit
    }
}

@Composable
private fun UserMessage(
    message: ChatMessage,
    isEditing: Boolean,
    onBeginEdit: () -> Unit,
    onSubmitEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (isEditing) {
            EditingBubble(initialText = message.content, onSubmit = onSubmitEdit, onCancel = onCancelEdit)
            return@Column
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                message.attachmentImageBase64?.let { AttachedImage(it) }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        MessageActionsRow(alignEnd = true) {
            ActionIcon(icon = Icons.Outlined.Edit, contentDescription = "Edit message", onClick = onBeginEdit)
            CopyActionIcon(text = message.content)
        }
    }
}

@Composable
private fun EditingBubble(initialText: String, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    Column(modifier = Modifier.widthIn(max = 320.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { if (text.isNotBlank()) onSubmit(text) }, enabled = text.isNotBlank()) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    isLastAssistantMessage: Boolean,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            message.status == MessageStatus.ERROR -> {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.padding(start = 4.dp))
                    Text(
                        text = message.errorMessage ?: "Something went wrong.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            message.content.isEmpty() && message.generatedImageBase64 == null && message.status == MessageStatus.STREAMING -> {
                ThinkingIndicator()
            }
            else -> {
                message.generatedImageBase64?.let { AttachedImage(it) }
                GeckoMarkdown(content = message.content)
                if (message.status == MessageStatus.STOPPED) {
                    Text(
                        text = "Stopped",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (message.status != MessageStatus.STREAMING) {
            MessageActionsRow(alignEnd = false) {
                CopyActionIcon(text = message.content)
                if (isLastAssistantMessage) {
                    ActionIcon(icon = Icons.Outlined.Refresh, contentDescription = "Regenerate response", onClick = onRegenerate)
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinkingPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(GeckoMotion.DURATION_EMPHASIZED * 2, easing = GeckoMotion.EasingStandard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinkingPulseAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.padding(end = 8.dp).size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Thinking…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
    }
}

@Composable
private fun MessageActionsRow(alignEnd: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        content = content,
    )
}

@Composable
private fun ActionIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CopyActionIcon(text: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(text))
            copied = true
        },
    ) {
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "Copy message",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
    AnimatedVisibility(visible = copied, enter = fadeIn(tween(150))) {
        Text(
            text = "Copied",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
internal fun AttachedImage(base64: String, modifier: Modifier = Modifier) {
    val bitmap = remember(base64) {
        runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Box(
            modifier = modifier
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Attached image",
                modifier = Modifier.widthIn(max = 240.dp),
            )
        }
    }
}
