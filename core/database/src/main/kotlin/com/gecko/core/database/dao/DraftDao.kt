package com.gecko.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gecko.core.database.entity.DraftEntity

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE conversationId = :id")
    suspend fun get(id: String): DraftEntity?
    @Upsert suspend fun save(draft: DraftEntity)
    @Query("DELETE FROM drafts WHERE conversationId = :id")
    suspend fun delete(id: String)
    @Query("DELETE FROM drafts")
    suspend fun deleteAll()
}
