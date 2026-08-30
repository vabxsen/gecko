package com.orca.feature.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orca.core.model.preferences.ThemeMode
import com.orca.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppearanceUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<AppearanceUiState> = userPreferencesRepository.userPreferences
        .map { AppearanceUiState(it.themeMode, it.dynamicColorEnabled) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setDynamicColorEnabled(enabled) }
    }
}
