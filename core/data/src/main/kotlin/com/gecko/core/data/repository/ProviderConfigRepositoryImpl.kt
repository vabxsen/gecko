package com.gecko.core.data.repository

import com.gecko.core.common.dispatchers.DispatcherProvider
import com.gecko.core.common.util.newId
import com.gecko.core.data.mapper.toConnectionStatus
import com.gecko.core.data.mapper.toDomain
import com.gecko.core.data.mapper.toEntity
import com.gecko.core.data.mapper.toWireString
import com.gecko.core.database.dao.ModelCatalogDao
import com.gecko.core.database.dao.ProviderConfigDao
import com.gecko.core.database.entity.ProviderConfigEntity
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.MAX_PROVIDER_CONFIGS
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.SecureKeyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ProviderConfigRepositoryImpl @Inject constructor(
    private val providerConfigDao: ProviderConfigDao,
    private val modelCatalogDao: ModelCatalogDao,
    private val secureKeyRepository: SecureKeyRepository,
    private val dispatchers: DispatcherProvider,
) : ProviderConfigRepository {

    override fun observeAll(): Flow<List<ProviderConfig>> =
        providerConfigDao.observeAll().map { entities -> entities.mapNotNull { it.toDomainOrNull() } }

    override fun observe(id: String): Flow<ProviderConfig?> =
        providerConfigDao.observeById(id).map { it?.toDomainOrNull() }

    override suspend fun addProvider(providerId: ProviderId, label: String): Result<String> = withContext(dispatchers.io) {
        if (providerConfigDao.count() >= MAX_PROVIDER_CONFIGS) {
            return@withContext Result.failure(IllegalStateException("You can save up to $MAX_PROVIDER_CONFIGS API keys"))
        }
        val id = newId()
        providerConfigDao.upsert(
            ProviderConfigEntity(
                id = id,
                providerId = providerId.slug,
                label = label,
                enabled = true,
                selectedModelId = null,
                baseUrlOverride = null,
                connectionStatus = "UNTESTED",
                connectionErrorMessage = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
        Result.success(id)
    }

    override suspend fun removeProvider(id: String) = withContext(dispatchers.io) {
        providerConfigDao.deleteById(id)
        modelCatalogDao.deleteForConfig(id)
        secureKeyRepository.clearApiKey(id)
    }

    override suspend fun setLabel(id: String, label: String) = withContext(dispatchers.io) {
        updateConfig(id) { it.copy(label = label) }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = withContext(dispatchers.io) {
        updateConfig(id) { it.copy(enabled = enabled) }
    }

    override suspend fun setSelectedModel(id: String, modelId: String?) = withContext(dispatchers.io) {
        updateConfig(id) { it.copy(selectedModelId = modelId) }
    }

    override suspend fun setBaseUrlOverride(id: String, baseUrl: String?) = withContext(dispatchers.io) {
        updateConfig(id) { it.copy(baseUrlOverride = baseUrl) }
    }

    override suspend fun setConnectionStatus(id: String, status: ConnectionStatus) = withContext(dispatchers.io) {
        updateConfig(id) {
            it.copy(
                connectionStatus = status.toWireString(),
                connectionErrorMessage = (status as? ConnectionStatus.Failure)?.message,
            )
        }
    }

    override fun observeModels(id: String): Flow<List<ModelInfo>> =
        modelCatalogDao.observeForConfig(id).map { entities -> entities.map { it.toDomain() } }

    override suspend fun saveModels(id: String, models: List<ModelInfo>) = withContext(dispatchers.io) {
        modelCatalogDao.deleteForConfig(id)
        modelCatalogDao.upsertAll(models.map { it.toEntity(id) })
    }

    override suspend fun clearAll() = withContext(dispatchers.io) {
        providerConfigDao.observeAll().first().forEach { secureKeyRepository.clearApiKey(it.id) }
        modelCatalogDao.deleteAll()
        providerConfigDao.deleteAll()
    }

    private suspend fun updateConfig(id: String, transform: (ProviderConfigEntity) -> ProviderConfigEntity) {
        val current = providerConfigDao.getById(id) ?: return
        providerConfigDao.upsert(transform(current))
    }

    private suspend fun ProviderConfigEntity.toDomainOrNull(): ProviderConfig? {
        val resolvedProviderId = ProviderId.fromSlug(providerId) ?: return null
        return ProviderConfig(
            id = id,
            providerId = resolvedProviderId,
            label = label,
            enabled = enabled,
            selectedModelId = selectedModelId,
            baseUrlOverride = baseUrlOverride,
            connectionStatus = toConnectionStatus(),
            hasApiKey = secureKeyRepository.hasApiKey(id),
        )
    }
}
