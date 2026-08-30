package com.gecko.core.model.preferences

import com.gecko.core.model.provider.ProviderId

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val defaultProviderId: ProviderId? = null,
    val defaultModelId: String? = null,
    val sendOnEnter: Boolean = true,
    val streamingEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val lastOpenedConversationId: String? = null,
)
