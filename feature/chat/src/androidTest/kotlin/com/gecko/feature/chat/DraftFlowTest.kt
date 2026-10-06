package com.gecko.feature.chat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.gecko.core.designsystem.theme.GeckoTheme
import com.gecko.core.model.chat.*
import com.gecko.core.model.preferences.UserPreferences
import com.gecko.core.model.provider.*
import com.gecko.core.testing.fake.*
import com.gecko.domain.usecase.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DraftFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun documentDraftSurvivesSwitchingChatsAndSendingClearsIt() = runBlocking {
        val conversations = FakeConversationRepository()
        val providers = FakeProviderConfigRepository()
        val id = providers.addProvider(ProviderId.OPENROUTER, "OpenRouter").getOrThrow()
        providers.setHasApiKey(id, true)
        providers.setVerifiedModel(id, "openrouter/free")
        providers.setConnectionStatus(id, ConnectionStatus.Success)
        val prefs = FakeUserPreferencesRepository(UserPreferences(defaultProviderConfigId = id, defaultModelId = "openrouter/free"))
        val completions = FakeChatCompletionRepository()
        val source = DocumentAttachment("Study notes.pdf", "[Page 1]\nPlants use sunlight to make food.\n\n[Page 2]\nRoots take in water.", 2)
        conversations.saveDraft(null, ChatDraft("Explain these notes", document = source))
        val other = conversations.createConversation(null, null)
        conversations.renameConversation(other.id, "Other chat")
        val send = SendChatMessageUseCase(conversations, completions)
        val vm = ChatViewModel(conversations, providers, prefs, send,
            RegenerateResponseUseCase(conversations, send), EditAndResendMessageUseCase(conversations, send),
            ConnectProviderUseCase(completions, providers, prefs))
        val store = ViewModelStore().apply { put("chat", vm) }
        try {
            compose.setContent { GeckoTheme { ChatScreen({}, {}, viewModel = vm) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Study notes.pdf").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasSetTextAction()).assertTextContains("Explain these notes")
            compose.onNode(hasSetTextAction()).performTextReplacement("What do roots do?")
            compose.runOnIdle { vm.selectConversation(other.id) }
            compose.waitUntil { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasSetTextAction()).assertTextContains("")
            compose.onNode(hasSetTextAction()).performTextReplacement("Other draft")
            compose.runOnIdle { vm.startNewConversation() }
            compose.waitUntil { compose.onAllNodesWithText("Study notes.pdf").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasSetTextAction()).assertTextContains("What do roots do?")
            capture("document-draft.png")
            compose.onNodeWithContentDescription("Send message").performClick()
            compose.waitUntil(10_000) { vm.uiState.value.messages.any { it.document != null } }
            compose.waitUntil(10_000) { !vm.uiState.value.isGenerating }
            assertEquals(source, vm.uiState.value.messages.first { it.role == MessageRole.USER }.document)
            assertEquals(ChatDraft(), conversations.getDraft(null))
            compose.onNodeWithText("Study notes.pdf · View source").performClick()
            compose.onNodeWithText(source.text).assertExists()
            capture("document-source.png")
        } finally { compose.runOnIdle { store.clear() } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
