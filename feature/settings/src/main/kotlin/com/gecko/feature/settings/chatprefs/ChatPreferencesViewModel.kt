package com.gecko.feature.settings.chatprefs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gecko.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatPreferencesUiState(
    val sendOnEnter: Boolean = true,
    val streamingEnabled: Boolean = true,
)

@HiltViewModel
class ChatPreferencesViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<ChatPreferencesUiState> = userPreferencesRepository.userPreferences
        .map { ChatPreferencesUiState(it.sendOnEnter, it.streamingEnabled) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatPreferencesUiState())

    fun setSendOnEnter(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setSendOnEnter(enabled) }
    }

    fun setStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setStreamingEnabled(enabled) }
    }
}
