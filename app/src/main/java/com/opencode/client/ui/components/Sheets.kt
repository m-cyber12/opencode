package com.opencode.client.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.client.domain.AgentInfo
import com.opencode.client.domain.CommandInfo
import com.opencode.client.domain.ModelInfo
import com.opencode.client.domain.ProviderInfo
import com.opencode.client.ui.theme.AppMonospace
import com.opencode.client.ui.theme.AppTheme

/** Bottom sheet listing providers -> models from the live /config/providers payload. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<ProviderInfo>,
    selected: ModelInfo?,
    onSelect: (ModelInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                "Model",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                providers.forEach { provider ->
                    item(key = "prov-${provider.id}") {
                        Text(
                            provider.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.extended.textFaint,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    provider.models.forEach { model ->
                        item(key = model.key) {
                            val isSelected = selected?.key == model.key
                            ListItem(
                                headlineContent = { Text(model.displayName) },
                                supportingContent = { Text(model.key, style = MaterialTheme.typography.labelSmall) },
                                leadingContent = {
                                    Icon(
                                        Icons.Outlined.Bolt,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else AppTheme.extended.textFaint
                                    )
                                },
                                modifier = Modifier.clickable { onSelect(model) }
                            )
                        }
                    }
                }
                if (providers.isEmpty()) {
                    item {
                        Text(
                            "No providers are configured on this server yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.extended.textFaint,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPickerSheet(
    agents: List<AgentInfo>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Agent",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            agents.forEach { agent ->
                ListItem(
                    headlineContent = { Text(agent.name.replaceFirstChar { it.uppercase() }) },
                    supportingContent = { agent.description?.let { Text(it, maxLines = 2) } },
                    trailingContent = if (selected == agent.name) {
                        { Text("Active", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                    } else null,
                    modifier = Modifier.clickable { onSelect(agent.name) }
                )
            }
        }
    }
}

/**
 * Slash-command palette. Filtered by the composer as the user types; selecting executes through
 * POST /session/:id/command - never fake local behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPaletteSheet(
    commands: List<CommandInfo>,
    filter: String,
    onPick: (CommandInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val filtered = commands.filter {
        filter.isBlank() || it.name.contains(filter.removePrefix("/"), ignoreCase = true)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Commands",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider(color = AppTheme.extended.border.copy(alpha = 0.5f))
            LazyColumn {
                items(filtered.size, key = { filtered[it].name }) { idx ->
                    val cmd = filtered[idx]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(cmd) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "/${cmd.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = AppMonospace,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(14.dp))
                        cmd.description?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.extended.textFaint,
                                modifier = Modifier.weight(1f)
                            )
                        } ?: Spacer(Modifier.weight(1f))
                    }
                    HorizontalDivider(color = AppTheme.extended.border.copy(alpha = 0.25f))
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No matching commands.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.extended.textFaint,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
