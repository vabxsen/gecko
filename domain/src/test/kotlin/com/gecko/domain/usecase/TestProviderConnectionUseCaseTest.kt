package com.gecko.domain.usecase

import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.error.GeckoException
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.usecase.fakes.FakeChatCompletionRepository
import com.gecko.domain.usecase.fakes.FakeProviderConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestProviderConnectionUseCaseTest {

    @Test
    fun successUpdatesStatusToSuccess() = runTest {
        val configRepo = FakeProviderConfigRepository()
        val id = configRepo.addProvider(ProviderId.OPENAI, "OpenAI").getOrThrow()
        val chatRepo = FakeChatCompletionRepository(testConnectionResult = Result.success(Unit))
        val useCase = TestProviderConnectionUseCase(chatRepo, configRepo)

        val result = useCase(id)

        assertTrue(result.isSuccess)
        assertEquals(ConnectionStatus.Success, configRepo.currentStatus(id))
    }

    @Test
    fun failureUpdatesStatusToFailureWithMessage() = runTest {
        val configRepo = FakeProviderConfigRepository()
        val id = configRepo.addProvider(ProviderId.ANTHROPIC, "Anthropic").getOrThrow()
        val chatRepo = FakeChatCompletionRepository(
            testConnectionResult = Result.failure(GeckoException(GeckoError(ErrorKind.InvalidApiKey, "Invalid key"))),
        )
        val useCase = TestProviderConnectionUseCase(chatRepo, configRepo)

        val result = useCase(id)

        assertTrue(result.isFailure)
        // The provider's classification survives into the persisted status, so Settings and chat
        // describe the same failure the same way.
        assertEquals(
            ConnectionStatus.Failure(GeckoError(ErrorKind.InvalidApiKey, "Invalid key")),
            configRepo.currentStatus(id),
        )
    }
}
