package com.orca.domain.repository

import com.orca.core.model.provider.ConnectionStatus
import com.orca.core.model.provider.ModelInfo
import com.orca.core.model.provider.ProviderConfig
import com.orca.core.model.provider.ProviderId
import kotlinx.coroutines.flow.Flow

interface ProviderConfigRepository {
    fun observeAll(): Flow<List<ProviderConfig>>
    fun observe(providerId: ProviderId): Flow<ProviderConfig>
    suspend fun setEnabled(providerId: ProviderId, enabled: Boolean)
    suspend fun setSelectedModel(providerId: ProviderId, modelId: String?)
    suspend fun setBaseUrlOverride(providerId: ProviderId, baseUrl: String?)
    suspend fun setConnectionStatus(providerId: ProviderId, status: ConnectionStatus)

    fun observeModels(providerId: ProviderId): Flow<List<ModelInfo>>
    suspend fun saveModels(providerId: ProviderId, models: List<ModelInfo>)
}
