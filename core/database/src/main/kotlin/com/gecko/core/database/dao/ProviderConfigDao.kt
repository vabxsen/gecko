package com.gecko.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gecko.core.database.entity.ProviderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs WHERE id = :id")
    fun observeById(id: String): Flow<ProviderConfigEntity?>

    @Query("SELECT * FROM provider_configs WHERE id = :id")
    suspend fun getById(id: String): ProviderConfigEntity?

    @Query("SELECT COUNT(*) FROM provider_configs")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(config: ProviderConfigEntity)

    @Query("DELETE FROM provider_configs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM provider_configs")
    suspend fun deleteAll()
}
