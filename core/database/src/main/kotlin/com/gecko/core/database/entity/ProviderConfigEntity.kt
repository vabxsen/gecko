package com.gecko.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val providerId: String,
    val enabled: Boolean,
    val selectedModelId: String?,
    val baseUrlOverride: String?,
    val connectionStatus: String,
    val connectionErrorMessage: String?,
)
