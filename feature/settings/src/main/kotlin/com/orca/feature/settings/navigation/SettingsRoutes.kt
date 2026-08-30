package com.orca.feature.settings.navigation

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
data class ProviderDetailRoute(val providerSlug: String)

@Serializable
object ModelPreferencesRoute

@Serializable
object DataPrivacyRoute

@Serializable
object AboutRoute
