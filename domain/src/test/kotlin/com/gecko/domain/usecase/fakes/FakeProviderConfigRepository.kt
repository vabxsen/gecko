package com.gecko.domain.usecase.fakes

import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderConfig
import com.gecko.core.model.provider.ProviderId
import com.gecko.domain.repository.MAX_PROVIDER_CONFIGS
import com.gecko.domain.repository.ProviderConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeProviderConfigRepository : ProviderConfigRepository {
    private val configs = MutableStateFlow<Map<String, ProviderConfig>>(emptyMap())
    private val models = mutableMapOf<String, MutableStateFlow<List<ModelInfo>>>()
    private var nextId = 0

    var clearAllCalled: Boolean = false
        private set

    override fun observeAll(): Flow<List<ProviderConfig>> = configs.map { it.values.toList() }

    override fun observe(id: String): Flow<ProviderConfig?> = configs.map { it[id] }

    override suspend fun addProvider(providerId: ProviderId, label: String): Result<String> {
        if (configs.value.size >= MAX_PROVIDER_CONFIGS) {
            return Result.failure(IllegalStateException("You can save up to $MAX_PROVIDER_CONFIGS API keys"))
        }
        val id = "fake-${nextId++}"
        val config = ProviderConfig(
            id = id,
            providerId = providerId,
            label = label,
            enabled = true,
            baseUrlOverride = null,
            connectionStatus = ConnectionStatus.Untested,
            hasApiKey = false,
        )
        configs.update { it + (id to config) }
        return Result.success(id)
    }

    override suspend fun removeProvider(id: String) {
        configs.update { it - id }
        models.remove(id)
    }

    override suspend fun setLabel(id: String, label: String) {
        updateConfig(id) { it.copy(label = label) }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        updateConfig(id) { it.copy(enabled = enabled) }
    }

    override suspend fun setBaseUrlOverride(id: String, baseUrl: String?) {
        updateConfig(id) { it.copy(baseUrlOverride = baseUrl) }
    }

    override suspend fun setConnectionStatus(id: String, status: ConnectionStatus) {
        updateConfig(id) { it.copy(connectionStatus = status) }
    }

    override fun observeModels(id: String): Flow<List<ModelInfo>> = modelsFlow(id)

    override suspend fun saveModels(id: String, models: List<ModelInfo>) {
        modelsFlow(id).value = models
    }

    override suspend fun clearAll() {
        clearAllCalled = true
        configs.value = emptyMap()
        models.clear()
    }

    /** Test helper: read the current state of a saved config synchronously. */
    fun currentConfig(id: String): ProviderConfig? = configs.value[id]

    /** Test helper: read the current connection status of a saved config synchronously. */
    fun currentStatus(id: String): ConnectionStatus = configs.value.getValue(id).connectionStatus

    /** Test helper: mark a config as having a key saved, without going through [addProvider]. */
    fun setHasApiKey(id: String, hasKey: Boolean) {
        updateConfig(id) { it.copy(hasApiKey = hasKey) }
    }

    private fun modelsFlow(id: String) = models.getOrPut(id) { MutableStateFlow(emptyList()) }

    private fun updateConfig(id: String, transform: (ProviderConfig) -> ProviderConfig) {
        configs.update { current -> current[id]?.let { current + (id to transform(it)) } ?: current }
    }
}
