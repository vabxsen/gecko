package com.gecko.feature.chat.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import com.gecko.core.designsystem.theme.GeckoTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.theme.LocalGeckoMotionEnabled
import com.gecko.core.designsystem.theme.geckoReveal
import com.gecko.core.designsystem.theme.geckoPopIn
import com.gecko.core.designsystem.component.GeckoButton
import com.gecko.core.designsystem.component.GeckoTextButton
import com.gecko.core.designsystem.component.GeckoIconButton
import com.gecko.core.model.chat.*
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MotionBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun sharedAnimatedButtonsRespectDisabledCancelledAndReleasedPresses() {
        val enabled = mutableStateOf(false)
        val clicks = IntArray(3)
        compose.setContent { MaterialTheme { Column {
            GeckoButton({ clicks[0]++ }, Modifier.testTag("filled"), enabled.value) { Text("Filled") }
            GeckoTextButton({ clicks[1]++ }, Modifier.testTag("text"), enabled.value) { Text("Text") }
            GeckoIconButton({ clicks[2]++ }, Modifier.testTag("icon"), enabled.value) { Text("Icon") }
        } } }
        listOf("filled", "text", "icon").forEach { tag ->
            compose.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(listOf(0, 0, 0), clicks.toList()); enabled.value = true }
        listOf("filled", "text", "icon").forEach { tag ->
            compose.onNodeWithTag(tag).performTouchInput { down(center); advanceEventTime(80); cancel() }
        }
        compose.runOnIdle { assertEquals(listOf(0, 0, 0), clicks.toList()) }
        listOf("filled", "text", "icon").forEach { tag ->
            compose.onNodeWithTag(tag).performTouchInput { down(center); advanceEventTime(80); up() }
        }
        compose.runOnIdle { assertEquals(listOf(1, 1, 1), clicks.toList()) }
    }

    @Test fun reducedMotionShowsTransientSurfacesWithoutWaitingForTheSpring() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CompositionLocalProvider(LocalGeckoMotionEnabled provides false) {
            Box(Modifier.size(80.dp).testTag("surface").geckoPopIn().background(Color.Black))
        } }
        val pixels = compose.onNodeWithTag("surface").captureToImage().toPixelMap()
        assertEquals(Color.Black.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
    }

    @Test fun systemMotionChangesAreObservedWithoutRestarting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = Settings.Global.getFloat(instrumentation.targetContext.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        fun setScale(value: Float) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "settings put global animator_duration_scale $value")).use { it.readBytes() }
        }
        try {
            compose.setContent { GeckoTheme { Text(if (LocalGeckoMotionEnabled.current) "motion on" else "motion off") } }
            setScale(0f)
            compose.waitUntil(5_000) { compose.onAllNodesWithText("motion off").fetchSemanticsNodes().isNotEmpty() }
            setScale(1f)
            compose.waitUntil(5_000) { compose.onAllNodesWithText("motion on").fetchSemanticsNodes().isNotEmpty() }
        } finally { setScale(original) }
    }

    @Test fun reducedMotionShowsEntranceContentImmediatelyEvenWithADelay() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalGeckoMotionEnabled provides false) {
                Box(Modifier.size(80.dp).testTag("visible").geckoReveal(1000).background(Color.Black))
            }
        }
        val pixels = compose.onNodeWithTag("visible").captureToImage().toPixelMap()
        assertEquals(Color.Black.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
    }

    @Test fun releasingAPressedSendRunsOnceAndCancellingDoesNotSend() {
        var sends = 0
        compose.setContent { MaterialTheme { MessageComposer(false, true, { _, _ -> sends++; false }, {}) } }
        compose.onNode(hasSetTextAction()).performTextInput("Keep this draft")
        compose.onNodeWithContentDescription("Send message").performTouchInput { down(center); cancel() }
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithContentDescription("Send message").performTouchInput { down(center); advanceEventTime(100); up() }
        compose.runOnIdle { assertEquals(1, sends) }
        compose.onNode(hasSetTextAction()).assertTextContains("Keep this draft")
    }

    @Test fun streamedTextUpdatesDoNotWaitForAnAnimationClock() {
        // An unfinished code block is rendered directly; this isolates stream timing from
        // Markdown's asynchronous parser, which has its own completion schedule.
        val message = mutableStateOf(ChatMessage("reply", "chat", MessageRole.ASSISTANT, "```text\nFirst words",
            Instant.EPOCH, MessageStatus.STREAMING))
        compose.setContent { MaterialTheme { MessageList(listOf(message.value), null, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("First words", substring = true).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { message.value = message.value.copy(content = "```text\nThe next chunk arrives immediately.") }
        repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithText("The next chunk arrives immediately.", substring = true).assertIsDisplayed()
    }
}
