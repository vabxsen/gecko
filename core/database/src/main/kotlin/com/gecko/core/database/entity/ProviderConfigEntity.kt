package com.gecko.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val label: String,
    val enabled: Boolean,
    /**
     * Vestigial. No longer read or written — the app-wide model lives in user preferences, which
     * is the one chat actually reads. Kept in the schema only so the v3→v4 migration can stay
     * additive; see GeckoDatabaseMigrations.
     */
    val selectedModelId: String?,
    val baseUrlOverride: String?,
    val connectionStatus: String,
    val connectionErrorMessage: String?,
    /** `ErrorKind.wireName` for a failed connection test, alongside the provider's own wording. */
    val connectionErrorKind: String?,
    val createdAt: Long,
)
