package com.gecko.feature.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gecko.feature.settings.component.SettingsRow
import com.gecko.feature.settings.component.SettingsSectionHeader
import com.gecko.feature.settings.component.SettingsTopBar

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = "About", onBack = onBack) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Gecko",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SettingsSectionHeader("About")
            SettingsRow(
                title = "Bring your own keys",
                subtitle = "Gecko talks directly to OpenAI, Anthropic, Google, and OpenRouter using API keys you provide. There is no Gecko backend, and your conversations never pass through a third-party server.",
            )
            SettingsRow(
                title = "Your data stays on this device",
                subtitle = "Conversations and settings are stored locally in an encrypted, on-device database. API keys are encrypted with a hardware-backed Android Keystore key.",
            )
        }
    }
}
