package com.gecko.feature.chat.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.gecko.core.designsystem.component.GeckoErrorDialog
import com.gecko.core.model.chat.*
import com.gecko.core.model.provider.*
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun editCancelCopyAndResubmitWork() {
        val editing = mutableStateOf(false)
        var submitted = ""
        val message = ChatMessage("user", "chat", MessageRole.USER, "Original draft", Instant.EPOCH, MessageStatus.COMPLETE)
        compose.setContent { MaterialTheme {
            MessageBubble(message, message.content, editing.value, false,
                { editing.value = true }, { submitted = it; editing.value = false }, { editing.value = false }, {}, {})
        } }
        compose.onNodeWithContentDescription("Copy message").performClick()
        compose.onNodeWithText("Copied").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit message").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Changed draft")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Original draft").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit message").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("Revised draft")
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle { assertEquals("Revised draft", submitted) }
    }

    @Test fun regenerateAndStopDispatchTheirActions() {
        val generating = mutableStateOf(false)
        var stopped = false
        val message = ChatMessage("assistant", "chat", MessageRole.ASSISTANT, "A reply", Instant.EPOCH, MessageStatus.COMPLETE)
        compose.setContent { MaterialTheme {
            if (!generating.value) MessageBubble(message, message.content, false, true, {}, {}, {}, { generating.value = true }, {})
            else MessageComposer(true, true, { _, _ -> false }, { stopped = true })
        } }
        compose.onNodeWithContentDescription("Regenerate response").performClick()
        compose.onNodeWithContentDescription("Stop generating").performClick()
        compose.runOnIdle { assertEquals(true, stopped) }
    }

    @Test fun errorDetailsCanExpandCollapseDismissAndFix() {
        var fixed = false
        var dismissed = false
        compose.setContent { MaterialTheme {
            GeckoErrorDialog("Connection failed", "Try again", "Retry", "Diagnostic detail",
                { fixed = true }, { dismissed = true })
        } }
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithText("Diagnostic detail").assertIsDisplayed()
        compose.onNodeWithText("Hide details").performClick()
        compose.onNodeWithText("Diagnostic detail").assertDoesNotExist()
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(true, fixed); assertEquals(true, dismissed) }
    }

    @Test fun modelSearchClearAndSelectionReachTheChosenModel() {
        val provider = ProviderConfig("key", ProviderId.OPENAI, "Test connection", true, null, ConnectionStatus.Success, true)
        val models = (1..12).map { ModelInfo(ProviderId.OPENAI, "test-$it", "Test model $it", 8192, true, false) }
        var selection = ""
        compose.setContent { MaterialTheme {
            ModelPickerSheet(listOf(provider), mapOf("key" to models), emptySet(), "key", null,
                { key, model -> selection = "$key/$model" }, {}, {}, {})
        } }
        compose.onNode(hasSetTextAction()).performTextInput("impossible-match")
        compose.onNodeWithText("No models match", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("test-12")
        compose.onNodeWithText("Test model 12").performClick()
        compose.runOnIdle { assertEquals("key/test-12", selection) }
    }
    @Test fun editIsDisabledDuringGenerationButCopyStillWorks() {
        val message = ChatMessage("user", "chat", MessageRole.USER, "Original draft", Instant.EPOCH, MessageStatus.COMPLETE)
        compose.setContent { MaterialTheme {
            MessageBubble(message, message.content, false, false, {}, {}, {}, {}, {}, actionsEnabled = false)
        } }
        compose.onNodeWithContentDescription("Edit message").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Copy message").performClick()
        compose.onNodeWithText("Copied").assertIsDisplayed()
    }

    @Test fun scrollToBottomReturnsToTheLatestMessage() {
        val messages = (1..30).map {
            ChatMessage("message-$it", "chat", MessageRole.USER, "Message $it", Instant.EPOCH.plusSeconds(it.toLong()), MessageStatus.COMPLETE)
        }
        compose.setContent { MaterialTheme { MessageList(messages, null, {}, {}, {}, {}, {}) } }
        compose.onNode(hasScrollAction()).performScrollToIndex(20)
        compose.onNodeWithContentDescription("Scroll to bottom").performClick()
        compose.onNodeWithText("Message 30").assertIsDisplayed()
    }

}
