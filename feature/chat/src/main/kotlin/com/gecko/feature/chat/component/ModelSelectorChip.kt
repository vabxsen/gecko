package com.gecko.feature.chat.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.icon.providerLogoRes
import com.gecko.core.model.provider.ProviderConfig

/**
 * The always-visible "what am I talking to right now" control in the chat top bar, and the way
 * into [ModelPickerSheet].
 *
 * It shows the model's *display name* rather than its raw id — the id a provider returns
 * ("nvidia/nemotron-3-super-120b-a12b") is unreadable at chip size and means nothing to someone
 * who hasn't memorised that vendor's catalog. With nothing chosen yet it deliberately reads as an
 * unfinished setup step ("Choose a model") in the primary colour, because on a fresh install that
 * is the one tap standing between the user and a working chat.
 */
@Composable
fun ModelSelectorChip(
    selectedProvider: ProviderConfig?,
    selectedModelLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSelection = selectedProvider != null && selectedModelLabel != null
    val title = selectedModelLabel ?: "Choose a model"
    val subtitle = selectedProvider?.let { it.label.ifBlank { it.providerId.displayName } }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (hasSelection) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        modifier = modifier
            .padding(end = 4.dp)
            .widthIn(max = 220.dp)
            .semantics {
                contentDescription = if (hasSelection) {
                    "Model: $title from $subtitle. Tap to change."
                } else {
                    "Choose a model"
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedProvider != null) {
                Image(
                    painter = painterResource(
                        id = providerLogoRes(selectedProvider.providerId, selectedProvider.baseUrlOverride),
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = title,
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
                // The provider name is the disambiguator when two saved keys expose
                // similarly-named models, but it's secondary — hidden until there's a selection
                // so the empty state stays a single clear call to action.
                if (hasSelection && subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
