package com.orca.core.provider.internal

import kotlinx.serialization.json.Json

internal val ProviderJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}
