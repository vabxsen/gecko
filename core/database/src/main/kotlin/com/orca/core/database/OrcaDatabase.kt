package com.orca.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.orca.core.database.dao.ConversationDao
import com.orca.core.database.dao.MessageDao
import com.orca.core.database.dao.ModelCatalogDao
import com.orca.core.database.dao.ProviderConfigDao
import com.orca.core.database.entity.ConversationEntity
import com.orca.core.database.entity.MessageEntity
import com.orca.core.database.entity.ModelCatalogEntity
import com.orca.core.database.entity.ProviderConfigEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ProviderConfigEntity::class,
        ModelCatalogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OrcaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun providerConfigDao(): ProviderConfigDao
    abstract fun modelCatalogDao(): ModelCatalogDao

    companion object {
        const val DATABASE_NAME = "orca.db"
    }
}
