package com.gecko.feature.chat.component

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.theme.GeckoMotion
import kotlinx.coroutines.launch

@Composable
fun MessageComposer(
    isGenerating: Boolean,
    sendOnEnter: Boolean,
    onSend: (text: String, attachmentBase64: String?) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var attachmentBase64 by remember { mutableStateOf<String?>(null) }
    var isEncodingAttachment by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isEncodingAttachment = true
            scope.launch {
                attachmentBase64 = encodeImageAttachment(context, uri)
                isEncodingAttachment = false
            }
        }
    }

    fun send() {
        if (text.isBlank() && attachmentBase64 == null) return
        onSend(text, attachmentBase64)
        text = ""
        attachmentBase64 = null
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(50),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            AnimatedVisibility(
                visible = attachmentBase64 != null,
                enter = fadeIn(tween(GeckoMotion.DURATION_STANDARD)) + expandVertically(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingEmphasized)),
                exit = fadeOut(tween(GeckoMotion.DURATION_QUICK)) + shrinkVertically(tween(GeckoMotion.DURATION_QUICK)),
            ) {
                attachmentBase64?.let { base64 ->
                    AttachmentPreviewChip(base64 = base64, onRemove = { attachmentBase64 = null })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !isEncodingAttachment,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isEncodingAttachment) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add attachment",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("What's up…") },
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                if (isGenerating) {
                    ComposerActionButton(
                        icon = Icons.Filled.Stop,
                        contentDescription = "Stop generating",
                        enabled = true,
                        onClick = onStop,
                    )
                } else {
                    IconButton(onClick = { /* voice input not implemented yet */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Voice input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val canSend = text.isNotBlank() || attachmentBase64 != null
                    ComposerActionButton(
                        icon = Icons.Filled.ArrowUpward,
                        contentDescription = "Send message",
                        enabled = canSend,
                        onClick = ::send,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colorAnimSpec = tween<androidx.compose.ui.graphics.Color>(GeckoMotion.DURATION_QUICK, easing = GeckoMotion.EasingStandard)
    val containerColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        },
        animationSpec = colorAnimSpec,
        label = "composerActionContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = colorAnimSpec,
        label = "composerActionContentColor",
    )
    IconButton(onClick = onClick, enabled = enabled) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(containerColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AttachmentPreviewChip(base64: String, onRemove: () -> Unit) {
    val bitmap = remember(base64) {
        runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Attached image",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
