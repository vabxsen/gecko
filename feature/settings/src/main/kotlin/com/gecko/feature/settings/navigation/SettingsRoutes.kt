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

@Serializable
object ModelPreferencesRoute

@Serializable
object DataPrivacyRoute

@Serializable
object AboutRoute
