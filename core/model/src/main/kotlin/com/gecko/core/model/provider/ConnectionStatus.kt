package com.gecko.core.model.provider

import com.gecko.core.model.error.GeckoError

sealed interface ConnectionStatus {
    data object Untested : ConnectionStatus
    data object Testing : ConnectionStatus
    data object Success : ConnectionStatus
    data class Failure(val error: GeckoError) : ConnectionStatus
}
