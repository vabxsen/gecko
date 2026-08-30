package com.gecko.core.data.di

import com.gecko.core.data.repository.ChatCompletionRepositoryImpl
import com.gecko.core.data.repository.ConversationRepositoryImpl
import com.gecko.core.data.repository.ProviderConfigRepositoryImpl
import com.gecko.core.data.repository.SecureKeyRepositoryImpl
import com.gecko.core.data.repository.UpdateRepositoryImpl
import com.gecko.core.data.repository.UserPreferencesRepositoryImpl
import com.gecko.domain.repository.ChatCompletionRepository
import com.gecko.domain.repository.ConversationRepository
import com.gecko.domain.repository.ProviderConfigRepository
import com.gecko.domain.repository.SecureKeyRepository
import com.gecko.domain.repository.UpdateRepository
import com.gecko.domain.repository.UserPreferencesRepository
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

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(impl: UpdateRepositoryImpl): UpdateRepository
}
