package com.gecko.core.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object PreferencesKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
    val DEFAULT_PROVIDER_CONFIG_ID = stringPreferencesKey("default_provider_config_id")
    val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
    val SEND_ON_ENTER = booleanPreferencesKey("send_on_enter")
    val STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val LAST_OPENED_CONVERSATION_ID = stringPreferencesKey("last_opened_conversation_id")
}
