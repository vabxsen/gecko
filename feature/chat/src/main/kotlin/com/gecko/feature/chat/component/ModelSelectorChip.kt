package com.gecko.feature.chat.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.gecko.core.designsystem.theme.geckoPress
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.icon.ProviderLogo
import com.gecko.core.model.provider.ProviderConfig

/** Switch connections while Gecko manages the model for each key. */
@Composable
fun ModelSelectorChip(
    selectedProvider: ProviderConfig?,
    selectedModelLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    connecting: Boolean = false,
) {
    val press = remember { MutableInteractionSource() }
    val hasSelection = selectedProvider != null && selectedModelLabel != null
    val connectionName = selectedProvider?.label?.ifBlank { selectedProvider.providerId.displayName }
    val title = when {
        connecting -> "Connecting…"
        hasSelection -> connectionName.orEmpty()
        connectionName != null -> "Connect $connectionName"
        else -> "Connect AI"
    }

    Surface(
        onClick = onClick,
        interactionSource = press,
        enabled = enabled && !connecting,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = if (hasSelection) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        modifier = modifier
            .geckoPress(press, enabled && !connecting)
            .padding(end = 4.dp)
            .widthIn(max = 360.dp).heightIn(min = 48.dp)
            .semantics {
                contentDescription = if (hasSelection) {
                    "Connection: $title. Automatic model. Tap to change connection."
                } else {
                    "Connect AI"
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedProvider != null) {
                ProviderLogo(selectedProvider.providerId, selectedProvider.baseUrlOverride, size = 24.dp)
            } else {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (hasSelection) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = if (hasSelection) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.padding(start = 2.dp).size(18.dp),
            )
        }
    }
}
