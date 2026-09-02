package com.gecko.domain.repository

import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.model.provider.ProviderId
import kotlinx.coroutines.flow.Flow

/** A user may save up to this many API key entries in total, across all provider types. */
const val MAX_PROVIDER_CONFIGS = 10

interface ProviderConfigRepository {
    fun observeAll(): Flow<List<ProviderConfig>>
    fun observe(id: String): Flow<ProviderConfig?>

    /** Creates a new saved key entry. Fails if [MAX_PROVIDER_CONFIGS] is already reached. */
    suspend fun addProvider(providerId: ProviderId, label: String): Result<String>
    suspend fun removeProvider(id: String)
    suspend fun setLabel(id: String, label: String)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun setBaseUrlOverride(id: String, baseUrl: String?)
    suspend fun setConnectionStatus(id: String, status: ConnectionStatus)

    fun observeModels(id: String): Flow<List<ModelInfo>>
    suspend fun saveModels(id: String, models: List<ModelInfo>)

    /** Wipes every saved key entry, its API key, and its cached model catalog. */
    suspend fun clearAll()
}
