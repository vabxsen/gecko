package com.gecko.feature.settings.about

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.gecko.core.designsystem.component.GeckoBrandTile
import com.gecko.feature.settings.component.SettingsPage
import androidx.compose.foundation.layout.Arrangement
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
        SettingsPage(innerPadding) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GeckoBrandTile(size = 88.dp)
                Text(
                    text = "Gecko",
                    style = MaterialTheme.typography.displayLarge,
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
                subtitle = "Connect your own AI provider. Gecko sends your messages directly to the service you choose, without a Gecko backend.",
            )
            SettingsRow(
                title = "Stored on your device",
                subtitle = "Your conversation history and preferences are stored locally. API keys are encrypted using Android Keystore. Messages you send are processed by your chosen AI provider.",
            )
        }
    }
}
