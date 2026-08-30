package com.orca.core.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Builds the shared OkHttp client used by every [com.orca.core.provider.api.AiProvider].
 * Read/call timeouts are disabled since streaming responses can legitimately stay open for
 * a long time; cancellation is the caller's responsibility (e.g. "stop generation").
 */
object OkHttpClientProvider {

    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .addInterceptor(redactingLoggingInterceptor())
        .build()

    private fun redactingLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("x-api-key")
            redactHeader("x-goog-api-key")
        }
}
