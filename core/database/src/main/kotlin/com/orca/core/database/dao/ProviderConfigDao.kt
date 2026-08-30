package com.orca.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.orca.core.database.entity.ProviderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs")
    fun observeAll(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs WHERE providerId = :providerId")
    fun observeById(providerId: String): Flow<ProviderConfigEntity?>

    @Query("SELECT * FROM provider_configs WHERE providerId = :providerId")
    suspend fun getById(providerId: String): ProviderConfigEntity?

    @Upsert
    suspend fun upsert(config: ProviderConfigEntity)
}
