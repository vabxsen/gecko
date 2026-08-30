package com.gecko.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gecko.core.database.dao.ConversationDao
import com.gecko.core.database.dao.MessageDao
import com.gecko.core.database.dao.ModelCatalogDao
import com.gecko.core.database.dao.ProviderConfigDao
import com.gecko.core.database.entity.ConversationEntity
import com.gecko.core.database.entity.MessageEntity
import com.gecko.core.database.entity.ModelCatalogEntity
import com.gecko.core.database.entity.ProviderConfigEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ProviderConfigEntity::class,
        ModelCatalogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class GeckoDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun providerConfigDao(): ProviderConfigDao
    abstract fun modelCatalogDao(): ModelCatalogDao

    companion object {
        const val DATABASE_NAME = "gecko.db"
    }
}
