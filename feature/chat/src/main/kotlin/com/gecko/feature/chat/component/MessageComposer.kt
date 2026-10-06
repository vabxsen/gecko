package com.gecko.feature.chat.component

import kotlinx.coroutines.CancellationException
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import com.gecko.core.model.chat.DocumentAttachment
import com.gecko.core.model.chat.ChatDraft
import com.gecko.core.designsystem.component.GeckoIconButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.gecko.core.designsystem.theme.geckoPress
import com.gecko.core.designsystem.theme.LocalGeckoMotionEnabled
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic as FilledMic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gecko.core.designsystem.theme.GeckoMotion
import kotlinx.coroutines.launch

@Composable
fun MessageComposer(
    isGenerating: Boolean,
    sendOnEnter: Boolean,
    onSend: (text: String, attachmentBase64: String?) -> Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    suggestedPrompt: String? = null,
    onSuggestionConsumed: () -> Unit = {},
    initialDraft: ChatDraft = ChatDraft(),
    onDraftChange: (ChatDraft) -> Unit = {},
    onSendDocument: ((String, String?, DocumentAttachment) -> Boolean)? = null,
    draftError: String? = null,
) {
    val context = LocalContext.current
    // Leave room for the keyboard and action row, including on short screens with large text.
    val maxInputHeight = (LocalConfiguration.current.screenHeightDp * 0.2f).coerceIn(56f, 144f).dp
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf(initialDraft.text) }
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusInteraction = remember { MutableInteractionSource() }
    val focused by focusInteraction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        tween(GeckoMotion.DURATION_STANDARD), label = "composerFocusBorder")
    val elevation by animateDpAsState(if (focused) 4.dp else 0.dp,
        tween(GeckoMotion.DURATION_STANDARD), label = "composerFocusElevation")
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(suggestedPrompt) {
        if (suggestedPrompt != null) {
            text = suggestedPrompt
            inputFocus.requestFocus()
            keyboard?.show()
            onSuggestionConsumed()
        }
    }
    var attachmentBase64 by remember { mutableStateOf(initialDraft.imageBase64) }
    var document by remember { mutableStateOf(initialDraft.document) }
    var attachmentMenu by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    // Read live state at disposal too: a successful first send may change the chat key before
    // this composer gets another recomposition. A cached snapshot would resurrect its old draft.
    val latestDraft = { ChatDraft(text, attachmentBase64, document) }
    val saveDraft by rememberUpdatedState(onDraftChange)
    LaunchedEffect(Unit) {
        snapshotFlow { latestDraft() }.collect { saveDraft(it) }
    }
    DisposableEffect(Unit) { onDispose { saveDraft(latestDraft()) } }
    var isEncodingAttachment by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isEncodingAttachment = true
            attachmentError = null
            scope.launch {
                try {
                    document = readDocument(context, uri)
                    attachmentBase64 = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    attachmentError = if (error is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException)
                        "This PDF is password protected. Choose an unlocked copy."
                    else if (error is java.nio.charset.CharacterCodingException) "Choose a UTF-8 text document."
                    else error.message ?: "Couldn't read this document. Try another file."
                } finally { isEncodingAttachment = false }
            }
        }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isEncodingAttachment = true
            scope.launch {
                try {
                    attachmentBase64 = encodeImageAttachment(context, uri)
                    document = null
                    attachmentError = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { attachmentError = "Couldn't read that image. Try another file." }
                finally {
                    isEncodingAttachment = false
                }
            }
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }
    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
            }
            override fun onResults(results: android.os.Bundle?) {
                isListening = false
                val spokenText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    text = if (text.isBlank()) spokenText else "$text $spokenText"
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        onDispose { speechRecognizer?.destroy() }
    }

    val requestAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            speechRecognizer?.startListening(createSpeechRecognitionIntent())
            isListening = true
        }
    }

    fun onMicClick() {
        val recognizer = speechRecognizer ?: return
        if (isListening) {
            recognizer.stopListening()
            isListening = false
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            recognizer.startListening(createSpeechRecognitionIntent())
            isListening = true
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun send() {
        if (isGenerating || isEncodingAttachment) return
        if (text.isBlank() && attachmentBase64 == null && document == null) return
        val source = document
        val accepted = if (source != null) onSendDocument?.invoke(text, attachmentBase64, source) == true
            else onSend(text, attachmentBase64)
        if (accepted) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            text = ""
            attachmentBase64 = null
            document = null
            onDraftChange(ChatDraft())
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = elevation,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            (attachmentError ?: draftError)?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp)) }
            document?.let { source ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge)
                        Text(source.pageCount?.let { "$it pages · Text ready" } ?: "Text ready",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    GeckoIconButton(onClick = { document = null }) {
                        Icon(Icons.Outlined.Close, "Remove document")
                    }
                }
                Text("Document text is sent with your message.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
            }
            AnimatedVisibility(
                visible = attachmentBase64 != null,
                enter = fadeIn(tween(GeckoMotion.DURATION_STANDARD)) + expandVertically(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingEmphasized)),
                exit = fadeOut(tween(GeckoMotion.DURATION_QUICK)) + shrinkVertically(tween(GeckoMotion.DURATION_QUICK)),
            ) {
                attachmentBase64?.let { base64 ->
                    AttachmentPreviewChip(base64 = base64, onRemove = { attachmentBase64 = null })
                }
            }
                TextField(
                    value = text,
                    interactionSource = focusInteraction,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(max = maxInputHeight).focusRequester(inputFocus),
                    placeholder = { Text("Message Gecko…") },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        .clickable(enabled = !isEncodingAttachment) {
                            attachmentMenu = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    DropdownMenu(expanded = attachmentMenu, onDismissRequest = { attachmentMenu = false }) {
                        DropdownMenuItem(text = { Text("Photo") }, onClick = {
                            attachmentMenu = false
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        if (onSendDocument != null) DropdownMenuItem(text = { Text("Document · PDF, text, Markdown") }, onClick = {
                            attachmentMenu = false
                            pickDocument.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "text/x-markdown"))
                        })
                    }
                    if (isEncodingAttachment) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add attachment",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (isGenerating) {
                    ComposerActionButton(
                        icon = Icons.Filled.Stop,
                        contentDescription = "Stop generating",
                        enabled = true,
                        onClick = onStop,
                    )
                } else {
                    GeckoIconButton(onClick = ::onMicClick, enabled = speechRecognizer != null) {
                        Icon(
                            imageVector = if (isListening) Icons.Filled.FilledMic else Icons.Outlined.Mic,
                            contentDescription = if (isListening) "Stop voice input" else "Voice input",
                            tint = if (isListening) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    val canSend = !isEncodingAttachment && (text.isNotBlank() || attachmentBase64 != null || document != null)
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
    val press = remember { MutableInteractionSource() }
    val motion = LocalGeckoMotionEnabled.current
    val rotation = animateFloatAsState(if (enabled) 0f else 45f,
        spring(dampingRatio = 0.8f, stiffness = 500f), label = "sendReadyRotation")
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
    Box(
        modifier = Modifier
            .padding(end = 2.dp)
            .size(48.dp)
            .geckoPress(press, enabled)
            .clip(CircleShape)
            .background(containerColor, CircleShape)
            .clickable(interactionSource = press, indication = ripple(), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (motion) rotation.value else 0f },
        )
    }
}

@Composable
private fun AttachmentPreviewChip(base64: String, onRemove: () -> Unit) {
    val maxDimensionPx = with(LocalDensity.current) { 96.dp.toPx() }.toInt()
    val bitmap = rememberDecodedBitmap(base64, maxDimensionPx)
    Row(
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Attached image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
            GeckoIconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove attachment",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

private fun createSpeechRecognitionIntent() =
    android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
