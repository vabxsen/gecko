package com.gecko.feature.settings.providers

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.gecko.core.model.provider.ProviderId
import org.junit.Rule
import org.junit.Test

class AddProviderScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pasteButtonFillsTheKeyWithoutSubmittingIt() {
        val state = mutableStateOf(AddProviderUiState())
        var connects = 0
        compose.setContent {
            MaterialTheme {
                AddProviderContent(state.value, {}, { state.value = state.value.copy(apiKey = it) }, {}, {}, {}, { connects++ })
            }
        }
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        compose.runOnIdle {
            compose.activity
                .getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("test key", "synthetic-test-key"))
        }
        compose.onNodeWithContentDescription("Paste API key").performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals("synthetic-test-key", state.value.apiKey)
            org.junit.Assert.assertEquals(0, connects)
        }
    }

    @Test fun freshSetupShowsOneKeyFieldAndHidesAdvancedChoices() {
        compose.setContent {
            MaterialTheme { AddProviderContent(AddProviderUiState(), {}, {}, {}, {}, {}, {}) }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("Connect & start chatting").assertIsNotEnabled()
        compose.onNodeWithText("OpenAI").assertDoesNotExist()
        compose.onNodeWithText("Custom base URL").assertDoesNotExist()
        compose.onNodeWithText("Name (optional)").assertDoesNotExist()
    }

    @Test fun recognizedProviderNeedsNoModelSelection() {
        compose.setContent {
            MaterialTheme {
                AddProviderContent(AddProviderUiState(apiKey = "synthetic-key", selectedProviderId = ProviderId.GOOGLE,
                    providerLabel = "Google Gemini"), {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Google Gemini").assertIsDisplayed()
        compose.onNodeWithText("Connect & start chatting").assertIsEnabled()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test fun ambiguousKeyRequiresOnlyAProviderChoice() {
        val state = mutableStateOf(AddProviderUiState(apiKey = "synthetic-key"))
        compose.setContent {
            MaterialTheme {
                AddProviderContent(state.value, {}, {}, { option ->
                    state.value = state.value.copy(selectedProviderId = option.providerId, providerLabel = option.label)
                }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Choose").performClick()
        compose.onNodeWithText("DeepSeek").performClick()
        compose.onNodeWithText("Connect & start chatting").assertIsEnabled()
        compose.onNodeWithText("Custom base URL").assertDoesNotExist()
    }

    @Test fun connectStaysVisibleWithLargeTextAndKeyboardSizedAvailableSpace() {
        var connects = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    Box(Modifier.fillMaxWidth().height(340.dp).testTag("available-space")) {
                        AddProviderContent(AddProviderUiState(apiKey = "synthetic-key", selectedProviderId = ProviderId.OPENROUTER,
                            providerLabel = "OpenRouter"), {}, {}, {}, {}, {}, { connects++ })
                    }
                }
            }
        }
        val button = compose.onNodeWithText("Connect & start chatting")
        button.assertIsDisplayed().assertIsEnabled()
        val available = compose.onNodeWithTag("available-space").getUnclippedBoundsInRoot()
        val bounds = button.getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("Connect must fit above the keyboard", bounds.top >= available.top && bounds.bottom <= available.bottom)
        button.performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, connects) }
    }
}
