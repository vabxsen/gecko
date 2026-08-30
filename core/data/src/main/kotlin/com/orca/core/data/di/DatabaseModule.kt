package com.orca.core.data.di

import android.content.Context
import androidx.room.Room
import com.orca.core.database.OrcaDatabase
import com.orca.core.database.dao.ConversationDao
import com.orca.core.database.dao.MessageDao
import com.orca.core.database.dao.ModelCatalogDao
import com.orca.core.database.dao.ProviderConfigDao
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
    fun provideOrcaDatabase(@ApplicationContext context: Context): OrcaDatabase =
        Room.databaseBuilder(context, OrcaDatabase::class.java, OrcaDatabase.DATABASE_NAME).build()

    @Provides
    fun provideConversationDao(database: OrcaDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: OrcaDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideProviderConfigDao(database: OrcaDatabase): ProviderConfigDao = database.providerConfigDao()

    @Provides
    fun provideModelCatalogDao(database: OrcaDatabase): ModelCatalogDao = database.modelCatalogDao()
}
