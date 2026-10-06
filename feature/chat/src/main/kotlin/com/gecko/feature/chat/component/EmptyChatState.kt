package com.gecko.feature.chat.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gecko.core.designsystem.component.GeckoWelcomeMark
import com.gecko.core.designsystem.theme.geckoPress
import com.gecko.core.designsystem.theme.geckoReveal

@Composable
fun EmptyChatState(
    needsConnection: Boolean = false,
    onConnect: () -> Unit = {},
    modifier: Modifier = Modifier,
    onPromptSelected: (String) -> Unit = {},
    hasSavedConnection: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 420.dp
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = if (compact) 12.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!compact) {
                GeckoWelcomeMark(Modifier.geckoReveal())
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "A little space\nfor big ideas.",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = if (compact) 26.sp else 32.sp,
                    lineHeight = if (compact) 32.sp else 38.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.8).sp,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.geckoReveal(60).semantics { heading() },
            )
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Text(
                if (needsConnection && hasSavedConnection) "Your key is saved. Let's get connected."
                else if (needsConnection) "Your favorite AI. Your own key." else "What would you like to explore?",
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.geckoReveal(110),
            )
            Spacer(Modifier.height(if (compact) 16.dp else 32.dp))
            if (needsConnection) {
                val press = remember { MutableInteractionSource() }
                Button(onClick = onConnect, interactionSource = press,
                    modifier = Modifier.geckoReveal(160).geckoPress(press).fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (hasSavedConnection) "Connect automatically" else "Connect your AI", Modifier.padding(vertical = 8.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 12.dp).size(18.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(if (hasSavedConnection) "Gecko will check a working model for this key." else "Paste a key. We'll take care of the setup.", style = MaterialTheme.typography.bodySmall,
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
    BoxWithConstraints(Modifier.fillMaxWidth().geckoReveal(160)) {
        // Preserve readable labels and touch targets when accessibility text is enlarged.
        if (maxWidth >= 330.dp && fontScale <= 1.15f) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PromptChip(Icons.Outlined.Lightbulb, "Explain something", "Make it click.", explain, Modifier.weight(1.1f))
                PromptChip(Icons.Outlined.EditNote, "Shape an idea", "Find a fresh angle.", brainstorm, Modifier.weight(1f), emphasized = true)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PromptChip(Icons.Outlined.Lightbulb, "Explain something", "Make it click.", explain, Modifier.fillMaxWidth())
                PromptChip(Icons.Outlined.EditNote, "Shape an idea", "Find a fresh angle.", brainstorm, Modifier.fillMaxWidth(), emphasized = true)
            }
        }
    }
}

@Composable
private fun PromptChip(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit,
    modifier: Modifier, emphasized: Boolean = false) {
    val press = remember { MutableInteractionSource() }
    val foreground = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val secondary = if (emphasized) foreground.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        interactionSource = press,
        modifier = modifier.geckoPress(press).heightIn(min = 120.dp),
        shape = MaterialTheme.shapes.large,
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = foreground,
        border = if (emphasized) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small,
                    color = if (emphasized) foreground.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = foreground) {
                    Icon(icon, null, Modifier.padding(7.dp).size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp),
                    tint = secondary)
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondary)
        }
    }
}
