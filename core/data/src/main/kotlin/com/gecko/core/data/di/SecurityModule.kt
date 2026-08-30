package com.gecko.core.data.di

import android.content.Context
import com.gecko.core.security.AndroidKeystoreSecureKeyStore
import com.gecko.core.security.SecureKeyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecureKeyStore(@ApplicationContext context: Context): SecureKeyStore =
        AndroidKeystoreSecureKeyStore(context)
}
