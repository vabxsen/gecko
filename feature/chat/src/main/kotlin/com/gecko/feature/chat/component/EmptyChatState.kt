package com.gecko.feature.chat.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gecko.core.designsystem.component.GeckoBrandTile

@Composable
fun EmptyChatState(
    needsConnection: Boolean = false,
    onConnect: () -> Unit = {},
    modifier: Modifier = Modifier,
    onPromptSelected: (String) -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 420.dp
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = if (compact) 12.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!compact) {
                GeckoBrandTile(size = 72.dp)
                Spacer(Modifier.height(24.dp))
            }
            Text(
                "A little space\nfor big ideas.",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = if (compact) 24.sp else 28.sp,
                    lineHeight = if (compact) 30.sp else 36.sp,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Text(
                if (needsConnection) "Your favorite AI. Your own key." else "What would you like to explore?",
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (compact) 16.dp else 32.dp))
            if (needsConnection) {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Connect your AI", Modifier.padding(vertical = 8.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 12.dp).size(18.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Paste a key. We'll take care of the setup.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            } else {
                PromptSuggestions(onPromptSelected)
            }
        }
    }
}

@Composable
private fun PromptSuggestions(onPromptSelected: (String) -> Unit) {
    val explain = { onPromptSelected("Help me understand a topic. Start by asking what I want to learn.") }
    val brainstorm = { onPromptSelected("Help me brainstorm. Ask me about the idea I want to explore.") }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Preserve readable labels and touch targets when accessibility text is enlarged.
        if (maxWidth >= 330.dp && fontScale <= 1.15f) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PromptChip(Icons.Outlined.Lightbulb, "Explain something", explain, Modifier.weight(1.1f))
                PromptChip(Icons.Outlined.EditNote, "Shape an idea", brainstorm, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PromptChip(Icons.Outlined.Lightbulb, "Explain something", explain, Modifier.fillMaxWidth())
                PromptChip(Icons.Outlined.EditNote, "Shape an idea", brainstorm, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PromptChip(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
        }
    }
}
