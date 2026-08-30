package com.orca.core.model.provider

sealed interface ConnectionStatus {
    data object Untested : ConnectionStatus
    data object Testing : ConnectionStatus
    data object Success : ConnectionStatus
    data class Failure(val message: String) : ConnectionStatus
}
