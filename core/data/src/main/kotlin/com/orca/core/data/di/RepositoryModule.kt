package com.orca.core.data.di

import com.orca.core.data.repository.ChatCompletionRepositoryImpl
import com.orca.core.data.repository.ConversationRepositoryImpl
import com.orca.core.data.repository.ProviderConfigRepositoryImpl
import com.orca.core.data.repository.SecureKeyRepositoryImpl
import com.orca.core.data.repository.UserPreferencesRepositoryImpl
import com.orca.domain.repository.ChatCompletionRepository
import com.orca.domain.repository.ConversationRepository
import com.orca.domain.repository.ProviderConfigRepository
import com.orca.domain.repository.SecureKeyRepository
import com.orca.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindProviderConfigRepository(impl: ProviderConfigRepositoryImpl): ProviderConfigRepository

    @Binds
    @Singleton
    abstract fun bindSecureKeyRepository(impl: SecureKeyRepositoryImpl): SecureKeyRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindChatCompletionRepository(impl: ChatCompletionRepositoryImpl): ChatCompletionRepository
}
