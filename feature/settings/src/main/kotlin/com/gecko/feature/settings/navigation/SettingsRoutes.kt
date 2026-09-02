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

/**
 * The one model picker. Replaces ProviderModelSelectionRoute and DefaultModelSelectionRoute, which
 * were separate destinations rendering the same list and writing to two different places — only
 * one of which chat ever read.
 */
@Serializable
data class ModelSelectionRoute(val configId: String)

@Serializable
object DataPrivacyRoute

@Serializable
object AboutRoute
