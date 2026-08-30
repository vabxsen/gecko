package com.gecko.domain.usecase.fakes

import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.ProviderConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeProviderConfigRepository : ProviderConfigRepository {
    private val configs = ProviderId.entries.associateWith { providerId ->
        MutableStateFlow(
            ProviderConfig(
                providerId = providerId,
                enabled = false,
                selectedModelId = null,
                baseUrlOverride = null,
                connectionStatus = ConnectionStatus.Untested,
                hasApiKey = false,
            ),
        )
    }
    private val models = ProviderId.entries.associateWith { MutableStateFlow<List<ModelInfo>>(emptyList()) }

    override fun observeAll(): Flow<List<ProviderConfig>> = throw UnsupportedOperationException("Not needed by these tests")

    override fun observe(providerId: ProviderId): Flow<ProviderConfig> = configs.getValue(providerId)

    override suspend fun setEnabled(providerId: ProviderId, enabled: Boolean) {
        configs.getValue(providerId).update { it.copy(enabled = enabled) }
    }

    override suspend fun setSelectedModel(providerId: ProviderId, modelId: String?) {
        configs.getValue(providerId).update { it.copy(selectedModelId = modelId) }
    }

    override suspend fun setBaseUrlOverride(providerId: ProviderId, baseUrl: String?) {
        configs.getValue(providerId).update { it.copy(baseUrlOverride = baseUrl) }
    }

    override suspend fun setConnectionStatus(providerId: ProviderId, status: ConnectionStatus) {
        configs.getValue(providerId).update { it.copy(connectionStatus = status) }
    }

    override fun observeModels(providerId: ProviderId): Flow<List<ModelInfo>> = models.getValue(providerId)

    override suspend fun saveModels(providerId: ProviderId, models: List<ModelInfo>) {
        this.models.getValue(providerId).value = models
    }

    fun currentStatus(providerId: ProviderId): ConnectionStatus = configs.getValue(providerId).value.connectionStatus
}
