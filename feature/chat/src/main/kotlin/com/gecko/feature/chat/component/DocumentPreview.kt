package com.gecko.feature.chat.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.component.GeckoTextButton
import com.gecko.core.model.chat.DocumentAttachment

@Composable
internal fun DocumentPreview(document: DocumentAttachment) {
    var open by remember { mutableStateOf(false) }
    GeckoTextButton(onClick = { open = true }) {
        Text("${document.name} · View source", style = MaterialTheme.typography.labelLarge)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(document.name) },
        text = {
            SelectionContainer {
                Text(document.text, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { GeckoTextButton(onClick = { open = false }) { Text("Done") } },
    )
}
