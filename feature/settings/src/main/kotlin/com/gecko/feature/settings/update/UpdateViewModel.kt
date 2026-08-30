package com.gecko.feature.settings.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gecko.core.model.update.AppUpdate
import com.gecko.domain.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Available(val update: AppUpdate) : UpdateCheckState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateCheckState
    data class NeedsInstallPermission(val fileUri: Uri) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    fun checkForUpdate() {
        if (_state.value is UpdateCheckState.Checking || _state.value is UpdateCheckState.Downloading) return
        _state.value = UpdateCheckState.Checking
        viewModelScope.launch {
            val currentVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "0"

            updateRepository.getLatestRelease()
                .onSuccess { update ->
                    _state.value = if (isNewerVersion(update.versionName, currentVersion)) {
                        UpdateCheckState.Available(update)
                    } else {
                        UpdateCheckState.UpToDate
                    }
                }
                .onFailure { e ->
                    _state.value = UpdateCheckState.Error(e.message ?: "Couldn't check for updates")
                }
        }
    }

    fun downloadAndInstall(update: AppUpdate) {
        _state.value = UpdateCheckState.Downloading(update, 0f)
        viewModelScope.launch {
            updateRepository.downloadApk(update) { progress ->
                _state.value = UpdateCheckState.Downloading(update, progress)
            }.onSuccess { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                if (context.packageManager.canRequestPackageInstalls()) {
                    launchInstallIntent(uri)
                    _state.value = UpdateCheckState.Idle
                } else {
                    _state.value = UpdateCheckState.NeedsInstallPermission(uri)
                }
            }.onFailure { e ->
                _state.value = UpdateCheckState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun dismiss() {
        _state.value = UpdateCheckState.Idle
    }

    private fun launchInstallIntent(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remote = remoteVersion.removePrefix("v").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val current = currentVersion.removePrefix("v").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remote.size, current.size)) {
            val r = remote.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
