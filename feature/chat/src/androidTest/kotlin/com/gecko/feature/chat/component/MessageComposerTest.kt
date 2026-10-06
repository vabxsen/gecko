package com.gecko.feature.chat.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MessageComposerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun restoresDocumentAndKeepsItWhenSendIsRejected() {
        val source = com.gecko.core.model.chat.DocumentAttachment("notes.pdf", "[Page 1] Source", 1)
        var saved = com.gecko.core.model.chat.ChatDraft()
        var sent: com.gecko.core.model.chat.DocumentAttachment? = null
        compose.setContent {
            MaterialTheme {
                MessageComposer(false, true, onSend = { _, _ -> false }, onStop = {},
                    initialDraft = com.gecko.core.model.chat.ChatDraft("Question", document = source),
                    onDraftChange = { saved = it },
                    onSendDocument = { _, _, doc -> sent = doc; false })
            }
        }
        compose.onNode(hasSetTextAction()).assertTextContains("Question")
        compose.onNodeWithText("notes.pdf").assertExists()
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.runOnIdle { assertEquals(source, sent); assertEquals(source, saved.document) }
        compose.onNodeWithContentDescription("Remove document").performClick()
        compose.runOnIdle { assertEquals(null, saved.document) }
    }

    @Test fun attachmentMenuOffersPhotoAndDocumentWithoutSending() {
        compose.setContent {
            MaterialTheme {
                MessageComposer(false, true, onSend = { _, _ -> false }, onStop = {},
                    onSendDocument = { _, _, _ -> false })
            }
        }
        compose.onNodeWithContentDescription("Add attachment").performClick()
        compose.onNodeWithText("Photo").assertExists()
        compose.onNodeWithText("Document · PDF, text, Markdown").assertExists()
    }

    @Test
    fun starterFillsTheDraftWithoutSending() {
        val suggestion = mutableStateOf<String?>(null)
        var sends = 0
        compose.setContent {
            MaterialTheme {
                Column {
                    EmptyChatState(modifier = Modifier.weight(1f), onPromptSelected = { suggestion.value = it })
                MessageComposer(false, true, onSend = { _, _ -> sends++; true }, onStop = {},
                    suggestedPrompt = suggestion.value, onSuggestionConsumed = { suggestion.value = null })
                }
            }
        }
        compose.onNodeWithText("Shape an idea").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Help me brainstorm. Ask me about the idea I want to explore.")
        compose.runOnIdle { assertEquals(0, sends) }
    }

    @Test
    fun rejectedSendKeepsTheDraft() {
        compose.setContent {
            MaterialTheme {
                MessageComposer(false, true, onSend = { _, _ -> false }, onStop = {})
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("Keep this draft")
        compose.onNodeWithContentDescription("Send message").performClick()

        compose.onNode(hasSetTextAction()).assertTextContains("Keep this draft")
    }

    @Test
    fun keyboardSendWhileGeneratingKeepsTheDraft() {
        var sends = 0
        compose.setContent {
            MaterialTheme {
                MessageComposer(true, true, onSend = { _, _ -> sends++; true }, onStop = {})
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("Next question")
        compose.onNode(hasSetTextAction()).performImeAction()

        compose.onNode(hasSetTextAction()).assertTextContains("Next question")
        compose.runOnIdle { assertEquals(0, sends) }
    }

    @Test
    fun acceptedSendClearsTheDraft() {
        var sent: String? = null
        compose.setContent {
            MaterialTheme {
                MessageComposer(false, true, onSend = { text, _ -> sent = text; true }, onStop = {})
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("Hello")
        compose.onNodeWithContentDescription("Send message").performClick()

        compose.onNode(hasSetTextAction()).assertTextContains("")
        compose.runOnIdle { assertEquals("Hello", sent) }
    }
}
