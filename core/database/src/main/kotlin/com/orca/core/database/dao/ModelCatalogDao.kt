package com.orca.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.orca.core.database.entity.ModelCatalogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelCatalogDao {
    @Query("SELECT * FROM model_catalog WHERE providerId = :providerId ORDER BY displayName ASC")
    fun observeForProvider(providerId: String): Flow<List<ModelCatalogEntity>>

    @Upsert
    suspend fun upsertAll(models: List<ModelCatalogEntity>)

    @Query("DELETE FROM model_catalog WHERE providerId = :providerId")
    suspend fun deleteForProvider(providerId: String)
}
