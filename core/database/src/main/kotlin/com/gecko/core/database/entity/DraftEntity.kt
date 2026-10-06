package com.gecko.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val conversationId: String,
    val text: String,
    val imageBase64: String?,
    val documentName: String?,
    val documentText: String?,
    val documentPageCount: Int?,
)
