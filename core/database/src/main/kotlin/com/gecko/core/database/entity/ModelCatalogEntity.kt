package com.gecko.core.database.entity

import androidx.room.Entity

@Entity(tableName = "model_catalog", primaryKeys = ["configId", "modelId"])
data class ModelCatalogEntity(
    val configId: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val contextWindowTokens: Int,
    val supportsStreaming: Boolean,
    val supportsImages: Boolean,
    val fetchedAt: Long,
)
