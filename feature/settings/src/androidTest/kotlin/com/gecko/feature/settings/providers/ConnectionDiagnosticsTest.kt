package com.gecko.feature.settings.providers

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gecko.core.model.error.*
import com.gecko.core.model.provider.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConnectionDiagnosticsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun failureExplainsCauseAndOffersTheRightFix() {
        val config = ProviderConfig("c", ProviderId.OPENROUTER, "OpenRouter", true, null,
            ConnectionStatus.Failure(GeckoError(ErrorKind.InvalidApiKey)), true)
        val state = mutableStateOf(ProviderDetailUiState("c", config = config))
        var edits = 0
        var checks = 0
        compose.setContent { MaterialTheme {
            ConnectionDiagnostics(state.value, { checks++ }, { edits++ })
        } }
        compose.onNodeWithText("That API key was rejected").assertExists()
        compose.onNodeWithText("Edit API key").performClick()
        compose.runOnIdle { assertEquals(1, edits)
            state.value = state.value.copy(config = config.copy(connectionStatus = ConnectionStatus.Failure(GeckoError(ErrorKind.QuotaExhausted)))) }
        compose.onNodeWithText("You're out of credit").assertExists()
        compose.onNodeWithText("Edit API key").assertDoesNotExist()
        compose.onNodeWithText("Check connection").performClick()
        compose.runOnIdle { assertEquals(1, checks) }
    }
}
