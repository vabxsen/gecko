package com.gecko.core.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Builds the shared OkHttp client used by every [com.gecko.core.provider.api.AiProvider].
 * [callTimeout] is disabled since streaming responses can legitimately stay open for a long
 * time and cancellation is the caller's responsibility (e.g. "stop generation"). [readTimeout]
 * is a *per-read* idle timeout, not a total-call cap — it resets on every byte received, so it
 * bounds "connection stalled with no data at all" without limiting how long a healthy stream
 * can run.
 */
object OkHttpClientProvider {

    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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
