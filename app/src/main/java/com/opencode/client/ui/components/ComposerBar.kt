package com.opencode.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.opencode.client.ui.theme.AppTheme

/**
 * The message composer: multiline input, send/stop, agent + model chips, and slash-command
 * triggering via "/". Deliberately uncluttered - attachments live behind "+".
 */
@Composable
fun ComposerBar(
    text: String,
    onTextChange: (String) -> Unit,
    busy: Boolean,
    enabled: Boolean,
    agentLabel: String,
    modelLabel: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAgentClick: () -> Unit,
    onModelClick: () -> Unit,
    onAttachClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val ext = AppTheme.extended

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Chip(text = "agent · $agentLabel", onClick = onAgentClick)
                Chip(text = modelLabel, onClick = onModelClick)
                onAttachClick?.let { click ->
                    IconButton(onClick = click, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = "Attach file reference", tint = ext.textFaint)
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 160.dp),
                    placeholder = { Text("Message OpenCode…   / for commands") },
                    shape = MaterialTheme.shapes.large,
                    maxLines = 6,
                    enabled = enabled
                )
                Spacer(Modifier.width(8.dp))
                if (busy) {
                    StopButton(onStop = onStop, enabled = enabled)
                } else {
                    SendButton(onSend = onSend, canSend = text.isNotBlank() && enabled)
                }
            }
        }
    }
}

@Composable
internal fun Chip(text: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SendButton(onSend: () -> Unit, canSend: Boolean) {
    val container = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (canSend) MaterialTheme.colorScheme.onPrimary else AppTheme.extended.textFaint
    Surface(
        onClick = { if (canSend) onSend() },
        enabled = canSend,
        shape = CircleShape,
        color = container,
        modifier = Modifier.size(46.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Send message",
                tint = content,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StopButton(onStop: () -> Unit, enabled: Boolean) {
    Surface(
        onClick = { if (enabled) onStop() },
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.size(46.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Stop the agent",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
