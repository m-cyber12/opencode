package com.opencode.client.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ShieldOutlined
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.client.domain.ConnectionState
import com.opencode.client.domain.PermissionRequest
import com.opencode.client.domain.TodoItem
import com.opencode.client.ui.theme.AppMonospace
import com.opencode.client.ui.theme.AppTheme

// --------------------------------------------------------------------------- connection state

@Composable
fun ConnectionBadge(state: ConnectionState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val ext = AppTheme.extended
    val (color, label) = when (state) {
        is ConnectionState.Connected -> ext.success to (state.version.ifBlank { "connected" })
        is ConnectionState.Connecting -> MaterialTheme.colorScheme.primary to "connecting…"
        is ConnectionState.Reconnecting -> ext.warning to "reconnecting (${state.attempt})"
        is ConnectionState.Disconnected -> ext.textFaint to "offline"
        is ConnectionState.Failed -> ext.error to "offline"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusDot(running: Boolean, modifier: Modifier = Modifier) {
    val color = if (running) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(modifier.size(7.dp).clip(CircleShape).background(color))
}

// --------------------------------------------------------------------------- banners / states

@Composable
fun ErrorBanner(
    message: String,
    technical: String? = null,
    onDismiss: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ext = AppTheme.extended
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            technical?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppMonospace),
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    maxLines = 4
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (actionLabel != null && onAction != null) {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                }
                if (onDismiss != null) {
                    Text(
                        "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String?, icon: @Composable (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon?.invoke()
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.extended.textFaint,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

// --------------------------------------------------------------------------- permissions

/**
 * First-class permission approval surface. Rendered as a pinned card above the composer so the
 * agent visibly pauses until answered. Destructive operations are visually flagged.
 */
@Composable
fun PermissionCard(
    request: PermissionRequest,
    onRespond: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = AppTheme.extended
    val destructive = listOf("reset", "rm ", "delete", "force", "--hard", "drop")
        .any { request.title.lowercase().contains(it) } ||
        request.patterns.any { p -> destructive.any { p.lowercase().contains(it) } }

    Surface(
        color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (destructive) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else ext.border
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.ShieldOutlined,
                    contentDescription = null,
                    tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    if (destructive) "Sensitive action needs approval" else "Approval needed",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(request.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (request.patterns.isNotEmpty()) {
                CodeBlock(code = request.patterns.joinToString("\n"), title = "scope", maxVisibleLines = 6)
            }

            val options = request.options()
            if (options.isNotEmpty()) {
                // Metadata-driven choices rendered as explicit buttons.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { opt ->
                        val allow = !opt.equals("reject", true) && !opt.equals("deny", true) && !opt.equals("no", true)
                        if (allow) Button(onClick = { onRespond(mapOption(opt)) }) { Text(opt) }
                        else OutlinedButton(onClick = { onRespond("reject") }) { Text(opt) }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onRespond("reject") }, modifier = Modifier.weight(1f)) {
                        Text("Reject")
                    }
                    OutlinedButton(onClick = { onRespond("always") }, modifier = Modifier.weight(1f)) {
                        Text("Always allow")
                    }
                    Button(onClick = { onRespond("once") }, modifier = Modifier.weight(1f)) {
                        Text("Allow once")
                    }
                }
            }
        }
    }
}

private fun mapOption(opt: String): String = when (opt.lowercase()) {
    "always" -> "always"
    "once", "allow", "yes" -> "once"
    else -> "once"
}

// --------------------------------------------------------------------------- todos

@Composable
fun TodoPanel(todos: List<TodoItem>, modifier: Modifier = Modifier) {
    if (todos.isEmpty()) return
    val ext = AppTheme.extended
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = ext.info, modifier = Modifier.size(16.dp))
                Text("Plan", style = MaterialTheme.typography.labelMedium, color = ext.textFaint)
            }
            todos.forEach { todo ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            todo.done -> "✓"
                            todo.active -> "›"
                            else -> "○"
                        },
                        color = when {
                            todo.done -> ext.success
                            todo.active -> MaterialTheme.colorScheme.primary
                            else -> ext.textFaint
                        }
                    )
                    Text(
                        todo.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (todo.done) ext.textFaint else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (todo.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- misc

@Composable
fun OfflineNotice(connection: ConnectionState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (connection) {
        is ConnectionState.Reconnecting -> Row(
            modifier
                .fillMaxWidth()
                .background(AppTheme.extended.warning.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = AppTheme.extended.warning, modifier = Modifier.size(15.dp))
            Text(
                "Connection lost - resyncing automatically…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        is ConnectionState.Failed -> ErrorBanner(
            message = connection.message,
            technical = connection.technical,
            actionLabel = "Retry",
            onAction = onRetry,
            modifier = modifier
        )
        else -> Unit
    }
}
