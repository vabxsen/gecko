package com.gecko.feature.settings.navigation

import kotlinx.serialization.Serializable

@Serializable
object SettingsRoute

@Serializable
object AppearanceRoute

@Serializable
object ChatPreferencesRoute

@Serializable
object AiProvidersRoute

@Serializable
object AddProviderRoute

@Serializable
data class ProviderDetailRoute(val configId: String)

/** Restored back stacks from older versions open connection details instead of a model catalog. */
@Serializable
data class ModelSelectionRoute(val configId: String)

@Serializable
object DataPrivacyRoute

@Serializable
object AboutRoute
