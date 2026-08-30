package com.orca.core.data.repository

import com.orca.core.common.dispatchers.DispatcherProvider
import com.orca.core.data.mapper.toConnectionStatus
import com.orca.core.data.mapper.toDomain
import com.orca.core.data.mapper.toEntity
import com.orca.core.data.mapper.toWireString
import com.orca.core.database.dao.ModelCatalogDao
import com.orca.core.database.dao.ProviderConfigDao
import com.orca.core.database.entity.ProviderConfigEntity
import com.orca.core.model.provider.ConnectionStatus
import com.orca.core.model.provider.ModelInfo
import com.orca.core.model.provider.ProviderConfig
import com.orca.core.model.provider.ProviderId
import com.orca.domain.repository.ProviderConfigRepository
import com.orca.domain.repository.SecureKeyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ProviderConfigRepositoryImpl @Inject constructor(
    private val providerConfigDao: ProviderConfigDao,
    private val modelCatalogDao: ModelCatalogDao,
    private val secureKeyRepository: SecureKeyRepository,
    private val dispatchers: DispatcherProvider,
) : ProviderConfigRepository {

    override fun observeAll(): Flow<List<ProviderConfig>> =
        combine(ProviderId.entries.map { observe(it) }) { it.toList() }

    override fun observe(providerId: ProviderId): Flow<ProviderConfig> =
        providerConfigDao.observeById(providerId.slug).map { entity ->
            val hasKey = secureKeyRepository.hasApiKey(providerId)
            ProviderConfig(
                providerId = providerId,
                enabled = entity?.enabled ?: false,
                selectedModelId = entity?.selectedModelId,
                baseUrlOverride = entity?.baseUrlOverride,
                connectionStatus = entity?.toConnectionStatus() ?: ConnectionStatus.Untested,
                hasApiKey = hasKey,
            )
        }

    override suspend fun setEnabled(providerId: ProviderId, enabled: Boolean) = withContext(dispatchers.io) {
        updateConfig(providerId) { it.copy(enabled = enabled) }
    }

    override suspend fun setSelectedModel(providerId: ProviderId, modelId: String?) = withContext(dispatchers.io) {
        updateConfig(providerId) { it.copy(selectedModelId = modelId) }
    }

    override suspend fun setBaseUrlOverride(providerId: ProviderId, baseUrl: String?) = withContext(dispatchers.io) {
        updateConfig(providerId) { it.copy(baseUrlOverride = baseUrl) }
    }

    override suspend fun setConnectionStatus(providerId: ProviderId, status: ConnectionStatus) = withContext(dispatchers.io) {
        updateConfig(providerId) {
            it.copy(
                connectionStatus = status.toWireString(),
                connectionErrorMessage = (status as? ConnectionStatus.Failure)?.message,
            )
        }
    }

    override fun observeModels(providerId: ProviderId): Flow<List<ModelInfo>> =
        modelCatalogDao.observeForProvider(providerId.slug).map { entities -> entities.map { it.toDomain() } }

    override suspend fun saveModels(providerId: ProviderId, models: List<ModelInfo>) = withContext(dispatchers.io) {
        modelCatalogDao.deleteForProvider(providerId.slug)
        modelCatalogDao.upsertAll(models.map { it.toEntity() })
    }

    private suspend fun updateConfig(providerId: ProviderId, transform: (ProviderConfigEntity) -> ProviderConfigEntity) {
        val current = providerConfigDao.getById(providerId.slug) ?: ProviderConfigEntity(
            providerId = providerId.slug,
            enabled = false,
            selectedModelId = null,
            baseUrlOverride = null,
            connectionStatus = "UNTESTED",
            connectionErrorMessage = null,
        )
        providerConfigDao.upsert(transform(current))
    }
}
