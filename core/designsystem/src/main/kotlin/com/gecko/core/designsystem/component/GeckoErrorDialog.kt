package com.gecko.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Explains one failure and offers the single thing that fixes it.
 *
 * This replaces a four-second snackbar that showed the provider's own error text and then deleted
 * it — no action, no way to read it twice, and nothing to do about it. Takes plain strings rather
 * than a domain type so the design system stays free of dependencies on it; callers translate
 * their own error and keep the decision about what the fix button *does*.
 *
 * [technicalDetail] is the provider's raw wording. It stays collapsed: it's occasionally the thing
 * that solves the problem and usually noise, and it's never the explanation.
 */
@Composable
fun GeckoErrorDialog(
    title: String,
    explanation: String,
    fixLabel: String?,
    technicalDetail: String?,
    onFix: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailShown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column {
                Text(explanation, style = MaterialTheme.typography.bodyMedium)
                if (technicalDetail != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { detailShown = !detailShown }) {
                        Text(if (detailShown) "Hide details" else "Details")
                    }
                    AnimatedVisibility(visible = detailShown) {
                        Text(
                            text = technicalDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (fixLabel != null) {
                TextButton(onClick = onFix) { Text(fixLabel) }
            } else {
                // Nothing the app can do from here — adding credit, rephrasing — so the only
                // honest button is the one that closes it.
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (fixLabel != null) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        },
    )
}
