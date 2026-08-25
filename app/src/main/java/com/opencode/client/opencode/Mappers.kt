package com.opencode.client.opencode

import com.opencode.client.domain.AgentInfo
import com.opencode.client.domain.CommandInfo
import com.opencode.client.domain.DiffFileInfo
import com.opencode.client.domain.FileContentInfo
import com.opencode.client.domain.FileNodeInfo
import com.opencode.client.domain.GitFileStatus
import com.opencode.client.domain.ModelInfo
import com.opencode.client.domain.MsgPart
import com.opencode.client.domain.PermissionRequest
import com.opencode.client.domain.ProjectInfo
import com.opencode.client.domain.ProviderInfo
import com.opencode.client.domain.Role
import com.opencode.client.domain.SessionInfo
import com.opencode.client.domain.TextPartUi
import com.opencode.client.domain.TextSearchMatch
import com.opencode.client.domain.TodoItem
import com.opencode.client.domain.ToolStateKind
import com.opencode.client.domain.ToolPartUi
import com.opencode.client.domain.UiMessage
import com.opencode.client.opencode.dto.AgentDto
import com.opencode.client.opencode.dto.AssistantMessageDto
import com.opencode.client.opencode.dto.CommandDto
import com.opencode.client.opencode.dto.FileContentDto
import com.opencode.client.opencode.dto.FileDiffDto
import com.opencode.client.opencode.dto.FileNodeDto
import com.opencode.client.opencode.dto.GitFileDto
import com.opencode.client.opencode.dto.MessageBundleDto
import com.opencode.client.opencode.dto.PermissionRequestDto
import com.opencode.client.opencode.dto.ProjectDto
import com.opencode.client.opencode.dto.ProviderDto
import com.opencode.client.opencode.dto.RawPartDto
import com.opencode.client.opencode.dto.SessionDto
import com.opencode.client.opencode.dto.TodoDto
import com.opencode.client.opencode.dto.UserMessageDto
import com.opencode.client.core.appJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * DTO -> domain mappers. All schema quirks are absorbed here.
 */

fun ProjectDto.toDomain(): ProjectInfo = ProjectInfo(
    id = id,
    worktree = worktree,
    name = worktree.trimEnd('/').substringAfterLast('/').ifBlank { worktree },
    vcs = vcs,
    createdAt = time.created
)

fun SessionDto.toDomain(): SessionInfo = SessionInfo(
    id = id,
    projectId = projectID,
    title = title.ifBlank { "New session" },
    createdAt = time.created,
    updatedAt = if (time.updated > 0) time.updated else time.created,
    parentId = parentID,
    shareUrl = share?.url,
    additions = summary?.additions ?: 0,
    deletions = summary?.deletions ?: 0,
    changedFiles = summary?.files ?: 0,
    reverted = revert != null
)

fun ProviderDto.toDomain(): ProviderInfo = ProviderInfo(
    id = id,
    name = name.ifBlank { id },
    models = models.map { (modelId, m) ->
        ModelInfo(
            providerId = id,
            modelId = modelId,
            displayName = (m.name ?: modelId).ifBlank { modelId }
        )
    }.sortedBy { it.displayName }
)

fun AgentDto.toDomain(): AgentInfo = AgentInfo(
    name = name,
    description = description,
    mode = mode
)

fun CommandDto.toDomain(): CommandInfo = CommandInfo(name, description)

fun TodoDto.toDomain(): TodoItem = TodoItem(id, content, status, priority)

fun FileDiffDto.toDomain(): DiffFileInfo =
    DiffFileInfo(file, before, after, additions, deletions)

fun FileNodeDto.toDomain(): FileNodeInfo =
    FileNodeInfo(name, path, type == "directory", ignored)

fun FileContentDto.toDomain(path: String): FileContentInfo = FileContentInfo(
    path = path,
    binary = type == "binary",
    content = content,
    diff = diff,
    mimeType = mimeType,
    base64 = encoding == "base64"
)

fun GitFileDto.toDomain(): GitFileStatus = GitFileStatus(path, added, removed, status)

fun PermissionRequestDto.toDomain(): PermissionRequest = PermissionRequest(
    id = id,
    type = type,
    title = title.ifBlank { type },
    patterns = pattern ?: emptyList(),
    sessionId = sessionID,
    messageId = messageID,
    callId = callID,
    metadata = metadata,
    createdAt = time.created
)

/**
 * Maps a full message bundle (info + parts) into a [UiMessage] with ordered parts.
 * The `info` object is decoded leniently by the caller-provided decoders so a partially-known
 * message shape still renders.
 */
fun MessageBundleDto.toDomain(
    decodeUser: (JsonObject) -> UserFields?,
    decodeAssistant: (JsonObject) -> AssistantFields?
): UiMessage? {
    val info = info
    val roleStr = info.primitive("role") ?: return null
    val id = info.primitive("id") ?: return null
    val sessionId = info.primitive("sessionID") ?: ""
    return when (roleStr) {
        "user" -> {
            val f = decodeUser(info) ?: UserFields(
                id = id,
                sessionId = sessionId,
                createdAt = info.longField("time", "created") ?: 0L
            )
            UiMessage(
                id = f.id,
                sessionId = f.sessionId,
                role = Role.USER,
                createdAt = f.createdAt,
                model = f.model,
                agent = f.agent,
                parts = parts.mapNotNull { it.toDomain() }
            )
        }
        "assistant" -> {
            val f = decodeAssistant(info) ?: AssistantFields(
                id = id,
                sessionId = sessionId,
                createdAt = info.longField("time", "created") ?: 0L
            )
            UiMessage(
                id = f.id,
                sessionId = f.sessionId,
                role = Role.ASSISTANT,
                createdAt = f.createdAt,
                completedAt = f.completedAt,
                model = f.model,
                agent = f.agentName,
                cost = f.cost,
                tokensIn = f.tokensIn,
                tokensOut = f.tokensOut,
                errorName = f.errorName,
                errorMessage = f.errorMessage,
                parts = parts.mapNotNull { it.toDomain() }
            )
        }
        else -> null
    }
}

data class UserFields(
    val id: String,
    val sessionId: String,
    val createdAt: Long,
    val model: ModelRef? = null,
    val agent: String? = null
)

data class AssistantFields(
    val id: String,
    val sessionId: String,
    val createdAt: Long,
    val completedAt: Long? = null,
    val model: ModelRef? = null,
    val agentName: String? = null,
    val cost: Double? = null,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val errorName: String? = null,
    val errorMessage: String? = null
)

/** Maps any raw part envelope into a domain part; unknown kinds become [UnknownPartUi]. */
fun RawPartDto.toDomain(): MsgPart? {
    return when (type) {
        "text" -> TextPartUi(id, text ?: "", synthetic == true)
        "reasoning" -> toReasoning()
        "tool" -> ToolPartUi(
            id = id,
            callId = callID ?: "",
            toolName = tool ?: "tool",
            state = state?.status.toToolState(),
            inputJson = state?.input,
            output = state?.output,
            title = state?.title,
            errorText = state?.error,
            startedAt = state?.time?.start?.takeIf { it > 0 },
            endedAt = state?.time?.end,
            metadataJson = state?.metadata ?: metadata
        )
        "file" -> toFilePart()
        "patch" -> toPatch()
        "subtask" -> toSubtask()
        "agent" -> toAgentPart()
        "retry" -> RetryPartUi(
            id = id,
            attempt = attempt ?: 0,
            errorText = extractErrorMessage(error),
            atMs = 0L
        )
        "compaction" -> toCompaction()
        "step-start" -> toStepStart()
        "step-finish" -> toStepFinish()
        // snapshot & other internal bookkeeping parts carry no user-visible content
        else -> UnknownPartUi(id, type)
    }
}

private fun RawPartDto.toStepStart(): MsgPart? = null // rendered implicitly

private fun RawPartDto.toStepFinish(): MsgPart? = null

private fun RawPartDto.toCompaction(): MsgPart? =
    com.opencode.client.domain.CompactionPartUi(id, auto ?: false)

private fun RawPartDto.toAgentPart(): MsgPart? =
    com.opencode.client.domain.SubtaskPartUi(id, prompt ?: "", description ?: "", name ?: agent ?: "")

private fun RawPartDto.toSubtask(): MsgPart? =
    com.opencode.client.domain.SubtaskPartUi(id, prompt ?: "", description ?: "", agent ?: "")

private fun RawPartDto.toPatch(): MsgPart? =
    PatchPartUi(id, hash ?: "", files ?: emptyList())

private fun RawPartDto.toFilePart(): MsgPart? =
    com.opencode.client.domain.FilePartUi(id, mime ?: "", filename, url ?: "")

private fun RawPartDto.toReasoning(): MsgPart? =
    com.opencode.client.domain.ReasoningPartUi(id, text ?: "")

private fun String?.toToolState(): ToolStateKind = when (this) {
    "completed" -> ToolStateKind.COMPLETED
    "error" -> ToolStateKind.FAILED
    "running" -> ToolStateKind.RUNNING
    else -> ToolStateKind.PENDING
}

internal fun extractErrorMessage(obj: JsonObject?): String {
    obj ?: return "unknown error"
    val direct = obj.primitive("message")
    if (direct != null) return direct
    val data = obj["data"] as? JsonObject
    return data?.primitive("message")
        ?: data?.primitive("name")
        ?: obj.primitive("name")
        ?: "unknown error"
}

// ---- tiny JsonObject helpers shared with the mapper layer -------------------------

fun JsonObject?.primitive(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.content

fun JsonObject?.longField(parentKey: String, key: String): Long? {
    val parent = this?.get(parentKey) as? JsonObject ?: return null
    val prim = parent[key] as? JsonPrimitive ?: return null
    return prim.content.toLongOrNull()
}

/** Default lenient decoders used by repositories when mapping message bundles. */
object FieldDecoders {

    val user: (JsonObject) -> UserFields? = { json ->
        runCatching { appJson.decodeFromJsonElement<UserMessageDto>(json) }.getOrNull()?.let { d ->
            UserFields(
                id = d.id,
                sessionId = d.sessionID,
                createdAt = d.time.created,
                model = ModelRef(d.model.providerID, d.model.modelID)
                    .takeIf { it.providerID.isNotBlank() || it.modelID.isNotBlank() },
                agent = d.agent.takeIf { it.isNotBlank() }
            )
        }
    }

    val assistant: (JsonObject) -> AssistantFields? = { json ->
        runCatching { appJson.decodeFromJsonElement<AssistantMessageDto>(json) }.getOrNull()?.let { d ->
            AssistantFields(
                id = d.id,
                sessionId = d.sessionID,
                createdAt = d.time.created,
                completedAt = d.time.completed,
                model = ModelRef(d.providerID, d.modelID)
                    .takeIf { it.providerID.isNotBlank() || it.modelID.isNotBlank() },
                agentName = d.agent?.takeIf { it.isNotBlank() },
                cost = d.cost.takeIf { it > 0.0 },
                tokensIn = d.tokens.input,
                tokensOut = d.tokens.output,
                errorName = (d.error?.get("name") as? JsonPrimitive)?.content,
                errorMessage = if (d.error != null) extractErrorMessage(d.error) else null
            )
        }
    }
}
