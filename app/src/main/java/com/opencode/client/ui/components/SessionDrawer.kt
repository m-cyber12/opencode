package com.opencode.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opencode.client.core.util.DayBucket
import com.opencode.client.core.util.Time
import com.opencode.client.data.repo.SessionRepository
import com.opencode.client.domain.SessionInfo
import com.opencode.client.opencode.event.OpenCodeEvent
import com.opencode.client.ui.theme.AppTheme

/**
 * ChatGPT-style session drawer: grouped by day, live status dots, rename/delete per row.
 * OpenCode sessions remain the single source of truth - this list re-reads from the repository
 * and refreshes incrementally on session events.
 */
@Composable
fun SessionDrawerContent(
    sessionRepo: SessionRepository,
    events: kotlinx.coroutines.flow.Flow<OpenCodeEvent>,
    busySessions: Set<String>,
    activeSessionId: String,
    onNewChat: () -> Unit,
    onPickSession: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
) {
    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var loadTick by remember { mutableStateOf(0) }

    LaunchedEffect(loadTick) {
        when (val res = sessionRepo.sessions()) {
            is com.opencode.client.core.Outcome.Ok -> sessions = res.value.filter { it.parentId == null }
            else -> Unit // drawer degrades to "new chat only" when the server is unreachable
        }
    }

    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                is OpenCodeEvent.SessionCreated,
                is OpenCodeEvent.SessionUpdated,
                is OpenCodeEvent.SessionDeleted,
                is OpenCodeEvent.SessionIdle,
                is OpenCodeEvent.SessionStatus -> loadTick++
                else -> Unit
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "OpenCode",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        ListItem(
            headlineContent = { Text("New chat", color = MaterialTheme.colorScheme.primary) },
            leadingContent = {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.clickable(onClick = onNewChat)
        )

        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = AppTheme.extended.border.copy(alpha = 0.5f))

        val grouped = remember(sessions) { groupByDay(sessions) }

        LazyColumn(modifier = Modifier.weight(1f)) {
            grouped.forEach { (bucket, list) ->
                item(key = "bucket-${bucket.name}") {
                    Text(
                        bucket.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTheme.extended.textFaint,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                items(list, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        active = session.id == activeSessionId,
                        running = busySessions.contains(session.id),
                        onClick = { onPickSession(session.id) },
                        onLongPressRename = { onRename(session.id) },
                        onDelete = { onDelete(session.id) }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionInfo,
    active: Boolean,
    running: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    val bg = if (active) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surface

    Column(Modifier.fillMaxWidth().background(bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { showActions = !showActions })
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Chat,
                contentDescription = null,
                tint = AppTheme.extended.textFaint,
                modifier = Modifier.padding(start = 12.dp).size(18.dp)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(running = running)
                    Text(
                        relativeDayLabel(session.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.extended.textFaint
                    )
                }
            }
            IconButton(onClick = { showActions = !showActions }) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "Session actions")
            }
        }

        AnimatedVisibility(visible = showActions) {
            Row(Modifier.padding(start = 52.dp, bottom = 6.dp)) {                IconButton(onClick = { showActions = false; onRename() }) {
                    Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Rename")
                }
                IconButton(onClick = { showActions = false; onDelete() }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = AppTheme.extended.error)
                }
            }
        }
    }
}

private fun groupByDay(sessions: List<SessionInfo>): List<Pair<DayBucket, List<SessionInfo>>> {
    val buckets = linkedMapOf<DayBucket, MutableList<SessionInfo>>()
    sessions.forEach { s ->
        buckets.getOrPut(Time.dayBucket(s.updatedAt)) { mutableListOf() }.add(s)
    }
    return buckets.map { it.key to it.value.toList() }
}
