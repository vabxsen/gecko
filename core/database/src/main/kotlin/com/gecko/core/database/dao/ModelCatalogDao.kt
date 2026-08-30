package com.gecko.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gecko.core.database.entity.ModelCatalogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelCatalogDao {
    @Query("SELECT * FROM model_catalog WHERE configId = :configId ORDER BY displayName ASC")
    fun observeForConfig(configId: String): Flow<List<ModelCatalogEntity>>

    @Upsert
    suspend fun upsertAll(models: List<ModelCatalogEntity>)

    @Query("DELETE FROM model_catalog WHERE configId = :configId")
    suspend fun deleteForConfig(configId: String)

    @Query("DELETE FROM model_catalog")
    suspend fun deleteAll()
}
