package com.gecko.feature.settings.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gecko.domain.repository.ConversationRepository
import com.gecko.domain.usecase.ClearAllLocalDataUseCase
import com.gecko.domain.usecase.ExportConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DataPrivacyViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val exportConversationsUseCase: ExportConversationsUseCase,
    private val clearAllLocalDataUseCase: ClearAllLocalDataUseCase,
) : ViewModel() {

    private val _exportedMarkdown = MutableStateFlow<String?>(null)
    val exportedMarkdown: StateFlow<String?> = _exportedMarkdown.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun prepareExport() {
        viewModelScope.launch { _exportedMarkdown.value = exportConversationsUseCase() }
    }

    fun consumeExportedMarkdown() {
        _exportedMarkdown.value = null
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            conversationRepository.deleteAllConversations()
            _actionMessage.value = "All conversations deleted"
        }
    }

    fun clearAllLocalData() {
        viewModelScope.launch {
            clearAllLocalDataUseCase()
            _actionMessage.value = "All local data cleared"
        }
    }

    fun dismissActionMessage() {
        _actionMessage.value = null
    }
}
