package com.gecko.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migrations preserve chats and provider metadata across shipped database versions.
 * API keys are stored separately in Android Keystore-backed preferences, so retaining the
 * provider config id during the v1 migration also retains its key association.
 */
object GeckoDatabaseMigrations {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_configs_new` (
                    `id` TEXT NOT NULL,
                    `providerId` TEXT NOT NULL,
                    `label` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `selectedModelId` TEXT,
                    `baseUrlOverride` TEXT,
                    `connectionStatus` TEXT NOT NULL,
                    `connectionErrorMessage` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO `provider_configs_new`
                    (`id`, `providerId`, `label`, `enabled`, `selectedModelId`, `baseUrlOverride`, `connectionStatus`, `connectionErrorMessage`, `createdAt`)
                SELECT `providerId`, `providerId`, `providerId`, `enabled`, `selectedModelId`, `baseUrlOverride`, `connectionStatus`, `connectionErrorMessage`, 0
                FROM `provider_configs`
                """.trimIndent(),
            )
            database.execSQL("DROP TABLE `provider_configs`")
            database.execSQL("ALTER TABLE `provider_configs_new` RENAME TO `provider_configs`")

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `model_catalog_new` (
                    `configId` TEXT NOT NULL,
                    `providerId` TEXT NOT NULL,
                    `modelId` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `contextWindowTokens` INTEGER NOT NULL,
                    `supportsStreaming` INTEGER NOT NULL,
                    `supportsImages` INTEGER NOT NULL,
                    `fetchedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`configId`, `modelId`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO `model_catalog_new`
                    (`configId`, `providerId`, `modelId`, `displayName`, `contextWindowTokens`, `supportsStreaming`, `supportsImages`, `fetchedAt`)
                SELECT `providerId`, `providerId`, `modelId`, `displayName`, `contextWindowTokens`, `supportsStreaming`, `supportsImages`, `fetchedAt`
                FROM `model_catalog`
                """.trimIndent(),
            )
            database.execSQL("DROP TABLE `model_catalog`")
            database.execSQL("ALTER TABLE `model_catalog_new` RENAME TO `model_catalog`")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `messages` ADD COLUMN `generatedImageBase64` TEXT")
        }
    }

    /**
     * Deliberately additive only. SQLite on this app's minimum API level has no `DROP COLUMN`, so
     * retiring a column means rebuilding the table — and since API keys are keyed by
     * `provider_configs.id`, one mis-ordered column in that copy would orphan every saved key on
     * every existing install. `provider_configs.selectedModelId` is no longer read or written, but
     * it stays in the schema rather than being worth that risk.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `messages` ADD COLUMN `errorKind` TEXT")
            database.execSQL("ALTER TABLE `provider_configs` ADD COLUMN `connectionErrorKind` TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
