package com.gecko.feature.chat.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gecko.core.model.chat.*
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReadAloudControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun togglesReadAndStopForTheAnswer() {
        var readText = ""
        val player = object : ReadAloudPlayback {
            override var speakingId by mutableStateOf<String?>(null)
            override fun toggle(id: String, text: String) {
                speakingId = if (speakingId == id) null else id
                readText = text
            }
        }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalReadAloud provides player) {
                    MessageBubble(ChatMessage("m", "c", MessageRole.ASSISTANT, "Hello there", Instant.EPOCH, MessageStatus.COMPLETE),
                        "Hello there", false, true, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Read aloud").performClick()
        compose.onNodeWithContentDescription("Stop reading").assertExists().performClick()
        compose.onNodeWithContentDescription("Read aloud").assertExists()
        compose.runOnIdle { assertEquals("Hello there", readText); assertEquals(null, player.speakingId) }
    }
}
