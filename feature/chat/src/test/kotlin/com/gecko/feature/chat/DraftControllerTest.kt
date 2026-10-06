package com.gecko.feature.chat

import com.gecko.core.model.chat.ChatDraft
import com.gecko.core.model.chat.DocumentAttachment
import com.gecko.core.testing.fake.FakeConversationRepository
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DraftControllerTest {
    @Test fun savesBeforeSwitchAndRestoresAttachmentsOnRecreation() = runTest {
        val repository = FakeConversationRepository()
        val controller = DraftController(repository, backgroundScope)
        runCurrent()
        val draft = ChatDraft("Explain this", document = DocumentAttachment("notes.pdf", "[Page 1]\nHello", 1))
        controller.save(null, draft)
        controller.load("other-chat")
        runCurrent()
        assertEquals(ChatDraft(), controller.state.value.draft)
        controller.save("other-chat", ChatDraft("Image question", "image"))
        controller.load(null)
        runCurrent()
        assertEquals(draft, controller.state.value.draft)
        val restored = DraftController(repository, backgroundScope)
        runCurrent()
        assertEquals(draft, restored.state.value.draft)
        restored.load("other-chat")
        runCurrent()
        assertEquals("image", restored.state.value.draft.imageBase64)
    }

    @Test fun rapidSwitchCannotDisplayTheWrongDraft() = runTest {
        val repository = FakeConversationRepository()
        repository.saveDraft("a", ChatDraft("A"))
        repository.saveDraft("b", ChatDraft("B"))
        val controller = DraftController(repository, backgroundScope)
        controller.load("a")
        controller.load("b")
        runCurrent()
        assertEquals("b", controller.state.value.conversationId)
        assertEquals("B", controller.state.value.draft.text)
        controller.save("b", ChatDraft())
        controller.load("b")
        runCurrent()
        assertTrue(controller.state.value.draft.isEmpty)
    }
}
