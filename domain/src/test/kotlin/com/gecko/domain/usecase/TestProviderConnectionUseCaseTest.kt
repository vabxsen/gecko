package com.gecko.domain.usecase

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
        val chatRepo = FakeChatCompletionRepository(testConnectionResult = Result.success(Unit))
        val useCase = TestProviderConnectionUseCase(chatRepo, configRepo)

        val result = useCase(ProviderId.OPENAI)

        assertTrue(result.isSuccess)
        assertEquals(ConnectionStatus.Success, configRepo.currentStatus(ProviderId.OPENAI))
    }

    @Test
    fun failureUpdatesStatusToFailureWithMessage() = runTest {
        val configRepo = FakeProviderConfigRepository()
        val chatRepo = FakeChatCompletionRepository(testConnectionResult = Result.failure(IllegalStateException("Invalid key")))
        val useCase = TestProviderConnectionUseCase(chatRepo, configRepo)

        val result = useCase(ProviderId.ANTHROPIC)

        assertTrue(result.isFailure)
        assertEquals(ConnectionStatus.Failure("Invalid key"), configRepo.currentStatus(ProviderId.ANTHROPIC))
    }
}
