package com.opencode.client.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.opencode.client.domain.FilePartUi
import com.opencode.client.domain.MsgPart
import com.opencode.client.domain.PatchPartUi
import com.opencode.client.domain.ReasoningPartUi
import com.opencode.client.domain.RetryPartUi
import com.opencode.client.domain.Role
import com.opencode.client.domain.SubtaskPartUi
import com.opencode.client.domain.TextPartUi
import com.opencode.client.domain.ToolPartUi
import com.opencode.client.domain.UiMessage
import com.opencode.client.domain.UnknownPartUi
import com.opencode.client.ui.theme.AppMonospace
import com.opencode.client.ui.theme.AppTheme

/**
 * Renders one conversation turn. User messages are compact right-aligned bubbles; assistant
 * turns render their part stream in order - text via markdown, tools as activity cards,
 * reasoning as a collapsed-by-default strip.
 */
@Composable
fun MessageItem(
    message: UiMessage,
    showReasoning: Boolean,
    onRetryLast: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when (message.role) {
        Role.USER -> UserBubble(message, modifier)
        Role.ASSISTANT -> AssistantBlock(message, showReasoning, onRetryLast, modifier)
    }
}

@Composable
private fun UserBubble(message: UiMessage, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 6.dp),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                message.parts.forEach { part ->
                    when (part) {
                        is TextPartUi -> Text(
                            part.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        is FilePartUi -> FileAttachmentChip(part)
                        else -> Unit
                    }
                }
            }
        }
        val label = buildString {
            message.agent?.let { append(it); append(" · ") }
            append(relativeDayLabel(message.createdAt))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.extended.textFaint,
            modifier = Modifier.padding(top = 4.dp, end = 4.dp)
        )
    }
}

@Composable
private fun AssistantBlock(
    message: UiMessage,
    showReasoning: Boolean,
    onRetryLast: (() -> Unit)?,
    modifier: Modifier,
) {
    val ext = AppTheme.extended
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Reasoning strips first (they precede the answer conceptually).
        if (showReasoning) {
            message.parts.filterIsInstance<ReasoningPartUi>().forEach { ReasoningStrip(it) }
        }

        message.parts.forEach { part ->
            when (part) {
                is TextPartUi -> {
                    if (!part.synthetic && part.text.isNotBlank()) {
                        MarkdownText(part.text)
                    }
                }

                is ToolPartUi -> ToolCard(part)

                is PatchPartUi -> ChangedFilesCard(part)

                is SubtaskPartUi -> SubtaskCard(part)

                is RetryPartUi -> RetryNotice(part)

                is FilePartUi -> FileAttachmentChip(part)

                is UnknownPartUi -> if (part.kind !in setOf("snapshot", "step-start", "step-finish")) {
                    Text(
                        "· ${part.kind}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.textFaint
                    )
                }

                else -> Unit // reasoning handled above; steps are structural noise
            }
        }

        message.errorMessage?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            message.errorName ?: "The agent hit an error",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(err, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        onRetryLast?.let {
                            OutlinedButton(onClick = it, modifier = Modifier.padding(top = 6.dp)) {
                                Text("Try again")
                            }
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StreamingCursor(visible = message.isStreaming)
            val meta = buildString {
                message.agent?.let { append(it).append(" · ") }
                append(relativeDayLabel(message.completedAt ?: message.createdAt))
                if (message.tokensOut > 0) {
                    append(" · ${formatTokens(message.tokensOut)} out")
                }
                message.cost?.takeIf { it > 0 }?.let { append(" · $${"%.3f".format(it)}") }
            }
            Text(meta, style = MaterialTheme.typography.labelSmall, color = ext.textFaint)
        }
    }
}

@Composable
private fun ReasoningStrip(part: ReasoningPartUi) {
    var expanded by rememberSaveable(part.id) { mutableStateOf(false) }
    val preview = part.text.trim().take(120)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Thinking",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.extended.textFaint
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (expanded) "hide" else "show",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!expanded && preview.isNotBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.extended.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (expanded) {
                MarkdownText(part.text, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun ChangedFilesCard(part: PatchPartUi) {
    val ext = AppTheme.extended
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(10.dp)) {
            Text("Changed files", style = MaterialTheme.typography.labelMedium, color = ext.textFaint)
            part.files.forEach { f ->
                Text(
                    f.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppMonospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SubtaskCard(part: SubtaskPartUi) {
    val ext = AppTheme.extended
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("↳", color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "Delegated to ${part.agent}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    part.description.ifBlank { part.prompt },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RetryNotice(part: RetryPartUi) {
    Text(
        "Retrying (attempt ${part.attempt}): ${part.errorText}",
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.extended.warning
    )
}

@Composable
private fun FileAttachmentChip(part: FilePartUi) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (part.isImage) {
                AsyncImage(
                    model = part.url,
                    contentDescription = part.filename ?: "attached image",
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp))
                )
            } else {
                Text(
                    part.filename ?: part.mime,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Pulsing caret shown while an assistant message is still receiving tokens. */
@Composable
fun StreamingCursor(visible: Boolean) {
    if (!visible) return
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(600),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Box(
        Modifier
            .size(width = 10.dp, height = 16.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), RoundedCornerShape(2.dp))
    )
}
