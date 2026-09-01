package com.gecko.core.data.di

import android.content.Context
import androidx.room.Room
import com.gecko.core.database.GeckoDatabase
import com.gecko.core.database.GeckoDatabaseMigrations
import com.gecko.core.database.dao.ConversationDao
import com.gecko.core.database.dao.MessageDao
import com.gecko.core.database.dao.ModelCatalogDao
import com.gecko.core.database.dao.ProviderConfigDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGeckoDatabase(@ApplicationContext context: Context): GeckoDatabase =
        Room.databaseBuilder(context, GeckoDatabase::class.java, GeckoDatabase.DATABASE_NAME)
            .addMigrations(*GeckoDatabaseMigrations.ALL)
            .build()

    @Provides
    fun provideConversationDao(database: GeckoDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: GeckoDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideProviderConfigDao(database: GeckoDatabase): ProviderConfigDao = database.providerConfigDao()

    @Provides
    fun provideModelCatalogDao(database: GeckoDatabase): ModelCatalogDao = database.modelCatalogDao()
}
