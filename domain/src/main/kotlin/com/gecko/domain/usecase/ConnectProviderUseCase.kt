package com.gecko.domain.usecase

import com.gecko.core.model.chat.ChatEvent
import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.MessageRole
import com.gecko.core.model.chat.MessageStatus
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.error.GeckoException
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.domain.model.connectionCandidates
import com.gecko.domain.repository.ChatCompletionRepository
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.UserPreferencesRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout

/** Connect only when a model has actually replied, then make that exact model ready for chat. */
class ConnectProviderUseCase @Inject constructor(
    private val chat: ChatCompletionRepository,
    private val providers: ProviderConfigRepository,
    private val preferences: UserPreferencesRepository,
) {
    suspend operator fun invoke(configId: String): Result<ModelInfo> = try {
        withTimeout(90_000) {
            providers.setConnectionStatus(configId, ConnectionStatus.Testing)
            val config = providers.observe(configId).first()
                ?: throw GeckoException(GeckoError(ErrorKind.KeyRemoved))
            val models = chat.fetchModels(configId).getOrThrow()
            providers.saveModels(configId, models)
            val candidates = (listOfNotNull(models.find { it.modelId == config.verifiedModelId }) +
                models.connectionCandidates(config.providerId, config.baseUrlOverride)).distinctBy { it.modelId }.take(5)
            var lastError = GeckoError(ErrorKind.ModelUnavailable)
            for (model in candidates) {
                var hasContent = false
                val terminal = withTimeoutOrNull(20_000) {
                    chat.sendMessage(configId, model.modelId, listOf(PROBE_MESSAGE), stream = model.supportsStreaming)
                        .firstOrNull { event ->
                            if (event is ChatEvent.ContentDelta && event.text.isNotBlank()) hasContent = true
                            event is ChatEvent.Error || event is ChatEvent.Completed
                        }
                }
                if (terminal is ChatEvent.Completed && hasContent) {
                    // Keep Testing until both preferences and availability have been saved, so
                    // chat's legacy auto-selection cannot replace the model verified above.
                    providers.setVerifiedModel(configId, model.modelId)
                    providers.setEnabled(configId, true)
                    preferences.setDefaultSelection(configId, model.modelId)
                    providers.setConnectionStatus(configId, ConnectionStatus.Success)
                    return@withTimeout Result.success(model)
                }
                if (terminal == null) {
                    lastError = GeckoError(ErrorKind.Offline, "The model did not finish responding in time.")
                    continue
                }
                lastError = (terminal as? ChatEvent.Error)?.error ?: GeckoError(ErrorKind.EmptyResponse)
                if (lastError.kind !in MODEL_SPECIFIC_FAILURES) break
            }
            throw GeckoException(lastError)
        }
    } catch (timeout: TimeoutCancellationException) {
        failure(configId, GeckoError(ErrorKind.Offline, "The provider took too long to respond. Try again."))
    } catch (cancelled: CancellationException) {
        withContext(NonCancellable) {
            providers.setConnectionStatus(configId, ConnectionStatus.Untested)
        }
        throw cancelled
    } catch (error: Exception) {
        failure(configId, (error as? GeckoException)?.error ?: GeckoError(ErrorKind.Unknown, error.message))
    }

    private suspend fun failure(configId: String, error: GeckoError): Result<ModelInfo> {
        val contextual = error.copy(configId = configId)
        providers.setConnectionStatus(configId, ConnectionStatus.Failure(contextual))
        return Result.failure(GeckoException(contextual))
    }

    private companion object {
        val MODEL_SPECIFIC_FAILURES = setOf(
            ErrorKind.ModelUnavailable, ErrorKind.PermissionDenied, ErrorKind.QuotaExhausted,
            ErrorKind.BadRequest, ErrorKind.EmptyResponse,
        )
        val PROBE_MESSAGE = ChatMessage(
            id = "setup-probe", conversationId = "setup-probe", role = MessageRole.USER,
            content = "Reply with just OK.", createdAt = Instant.EPOCH, status = MessageStatus.COMPLETE,
        )
    }
}
