package com.gecko.feature.chat.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MessageComposerTest {
    @get:Rule
    val compose = createComposeRule()

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
