package com.orca.core.data.di

import com.orca.core.network.OkHttpClientProvider
import com.orca.core.provider.api.ProviderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClientProvider.create()

    @Provides
    @Singleton
    fun provideProviderFactory(httpClient: OkHttpClient): ProviderFactory = ProviderFactory(httpClient)
}
