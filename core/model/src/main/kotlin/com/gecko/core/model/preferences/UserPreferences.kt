package com.gecko.core.model.preferences

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val defaultProviderConfigId: String? = null,
    val defaultModelId: String? = null,
    val sendOnEnter: Boolean = true,
    val streamingEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val lastOpenedConversationId: String? = null,
)
