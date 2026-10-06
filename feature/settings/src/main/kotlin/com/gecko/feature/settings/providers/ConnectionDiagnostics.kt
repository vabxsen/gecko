package com.gecko.feature.settings.providers

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.gecko.core.designsystem.component.GeckoTextButton
import com.gecko.core.model.error.ErrorFix
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.domain.error.copyForUser
import com.gecko.feature.settings.component.SettingsPanel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ConnectionDiagnostics(state: ProviderDetailUiState, onCheck: () -> Unit, onEditKey: () -> Unit) {
    val status = state.connectionStatus
    val failure = (status as? ConnectionStatus.Failure)?.error
    val error = if (!state.hasApiKey) GeckoError(ErrorKind.NoApiKey) else failure
    val copy = error?.copy(providerLabel = state.label.ifBlank { state.providerId?.displayName })?.copyForUser()
    SettingsPanel {
        Text("Connection check", style = MaterialTheme.typography.titleMedium)
        when {
            state.isSavingKey || status == ConnectionStatus.Testing -> {
                Text("Checking access and a real reply…", style = MaterialTheme.typography.bodyMedium)
                Text("Gecko will choose a working model automatically.", style = MaterialTheme.typography.bodySmall)
            }
            copy != null -> {
                Text(copy.title, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                Text(copy.explanation, style = MaterialTheme.typography.bodyMedium)
                if (copy.fix == ErrorFix.OpenProviderKey) {
                    GeckoTextButton(onClick = onEditKey) { Text("Edit API key") }
                }
            }
            status == ConnectionStatus.Success -> {
                Text("A model returned a reply on the last check.", style = MaterialTheme.typography.bodyMedium)
                Text("Availability and account limits can change. Check again if messages fail.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Text("Run a check to verify this key and find a working model.", style = MaterialTheme.typography.bodyMedium)
        }
        if (!state.enabled) Text("This connection is disabled. Turn on Use this connection to chat with it.",
            style = MaterialTheme.typography.bodySmall)
        state.lastCheckedAt?.let { time ->
            val formatted = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss").withZone(ZoneId.systemDefault()).format(time)
            Text("Last check: $formatted · ${state.checkDurationMs ?: 0} ms",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.hasApiKey) {
            GeckoTextButton(onClick = onCheck, enabled = !state.isSavingKey && status != ConnectionStatus.Testing) {
                Text("Check connection")
            }
            Text("Sends a short test message; provider usage limits apply.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
