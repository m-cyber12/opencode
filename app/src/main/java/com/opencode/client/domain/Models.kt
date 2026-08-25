package com.opencode.client.domain

import kotlinx.serialization.json.JsonObject

/*
 * Domain models. The UI renders ONLY these types - never raw API DTOs - so OpenCode schema
 * evolution is absorbed in the mapper/integration layer.
 */

// ---- Projects & server ---------------------------------------------------------

data class ProjectInfo(
    val id: String,
    val worktree: String,
    val name: String,
    val vcs: String?,
    val createdAt: Long
)

data class PathInfo(
    val worktree: String,
    val directory: String,
    val configFile: String
)

data class McpInfo(val name: String, val status: String, val error: String?)

data class LspInfo(val name: String, val status: String, val root: String)

data class FormatterInfo(val name: String, val extensions: String, val enabled: Boolean)

data class ServerHealth(
    val healthy: Boolean,
    val version: String
)

/** Feature flags detected at connect time; features degrade gracefully instead of breaking. */
data class Capabilities(
    val supportsProviders: Boolean = false,
    val supportsAgents: Boolean = false,
    val supportsCommands: Boolean = false,
    val supportsFiles: Boolean = false,
    val supportsMcp: Boolean = false,
    val supportsVcs: Boolean = false,
    val supportsDiff: Boolean = true,
    val supportsPermissions: Boolean = true
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState

    /** SSE dropped; automatic reconnection with backoff in progress. */
    data class Reconnecting(val attempt: Int, val nextInMs: Long) : ConnectionState

    data class Connected(
        val version: String,
        val directory: String?,
        val capabilities: Capabilities
    ) : ConnectionState

    data class Failed(val message: String, val technical: String?) : ConnectionState
}

// ---- Sessions -------------------------------------------------------------------

data class SessionInfo(
    val id: String,
    val projectId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val parentId: String? = null,
    val shareUrl: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changedFiles: Int = 0,
    val reverted: Boolean = false
) {
    val isNew: Boolean get() = updatedAt == 0L && createdAt == 0L
}

sealed interface RunStatus {
    data object Idle : RunStatus
    data object Busy : RunStatus

    data class Retrying(val attempt: Int, val message: String?, val nextAtMs: Long) : RunStatus
}

// ---- Messages / parts ---------------------------------------------------------------

enum class Role { USER, ASSISTANT }

data class ModelRef(val providerID: String, val modelID: String) {
    fun label(modelsByName: Map<String, String>): String =
        modelsByName["$providerID/$modelID"] ?: modelID
}

data class UiMessage(
    val id: String,
    val sessionId: String,
    val role: Role,
    val createdAt: Long,
    val completedAt: Long? = null,
    val model: ModelRef? = null,
    val agent: String? = null,
    val cost: Double? = null,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val errorName: String? = null,
    val errorMessage: String? = null,
    val parts: List<MsgPart> = emptyList()
) {
    val isStreaming: Boolean get() = role == Role.ASSISTANT && completedAt == null && errorMessage == null
}

sealed class MsgPart {
    abstract val id: String
    open val order: Int get() = 0
}

data class TextPartUi(
    override val id: String,
    val text: String,
    val synthetic: Boolean = false
) : MsgPart()

data class ReasoningPartUi(
    override val id: String,
    val text: String
) : MsgPart() {
    override val order: Int get() = -1 // render before text
}

enum class ToolStateKind { PENDING, RUNNING, COMPLETED, FAILED }

data class ToolPartUi(
    override val id: String,
    val callId: String,
    val toolName: String,
    val state: ToolStateKind,
    val inputJson: JsonObject?,
    val output: String?,
    val title: String?,
    val errorText: String?,
    val startedAt: Long?,
    val endedAt: Long?,
    val metadataJson: JsonObject?
) : MsgPart() {

    val durationMs: Long? get() = if (startedAt != null && endedAt != null) endedAt - startedAt else null

    /** Best-effort primary argument for the collapsed card line (path/command/pattern/url). */
    val headline: String?
        get() {
            val input = inputJson ?: return title
            val candidates = listOf("filePath", "file_path", "path", "command", "pattern", "query", "url", "description", "notebookPath")
            for (key in candidates) {
                (input[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { return it }
            }
            return title
        }
}

data class FilePartUi(
    override val id: String,
    val mime: String,
    val filename: String?,
    val url: String
) : MsgPart() {
    val isImage: Boolean get() = mime.startsWith("image/")
}

data class PatchPartUi(
    override val id: String,
    val hash: String,
    val files: List<String>
) : MsgPart()

data class SubtaskPartUi(
    override val id: String,
    val prompt: String,
    val description: String,
    val agent: String
) : MsgPart()

data class RetryPartUi(
    override val id: String,
    val attempt: Int,
    val errorText: String,
    val atMs: Long
) : MsgPart()

data class CompactionPartUi(
    override val id: String,
    val auto: Boolean
) : MsgPart()

object StepStartUi : MsgPart() {
    override val id: String = "step-start"
}

data class StepFinishUi(
    override val id: String,
    val reason: String
) : MsgPart()

data class UnknownPartUi(
    override val id: String,
    val kind: String
) : MsgPart()

// ---- Permissions / todos ---------------------------------------------------------------

data class PermissionRequest(
    val id: String,
    val type: String,
    val title: String,
    val patterns: List<String>,
    val sessionId: String,
    val messageId: String,
    val callId: String?,
    val metadata: JsonObject?,
    val createdAt: Long
) {
    /**
     * OpenCode has no dedicated "question" endpoint; interactive asks surface as permission
     * requests. When metadata carries option labels we render them as choices.
     */
    fun options(): List<String> {
        val md = metadata ?: return emptyList()
        val raw = md["options"] ?: md["choices"] ?: return emptyList()
        val arr = raw as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }
}

data class TodoItem(
    val id: String,
    val content: String,
    val status: String,
    val priority: String
) {
    val done: Boolean get() = status == "completed"
    val active: Boolean get() = status == "in_progress"
}

// ---- Models / agents / commands -----------------------------------------------------------

data class ModelInfo(
    val providerId: String,
    val modelId: String,
    val displayName: String
) {
    val key: String get() = "$providerId/$modelId"
}

data class ProviderInfo(
    val id: String,
    val name: String,
    val models: List<ModelInfo>
)

data class AgentInfo(
    val name: String,
    val description: String?,
    val mode: String
) {
    val isPrimary: Boolean get() = mode == "primary" || mode == "all"
}

data class CommandInfo(
    val name: String,
    val description: String?
)

// ---- Files / diffs -----------------------------------------------------------------------------

data class FileNodeInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val ignored: Boolean
)

data class FileContentInfo(
    val path: String,
    val binary: Boolean,
    val content: String,
    val diff: String?,
    val mimeType: String?,
    val base64: Boolean
)

data class TextSearchMatch(
    val path: String,
    val lineText: String,
    val lineNumber: Int
)

data class GitFileStatus(
    val path: String,
    val added: Int,
    val removed: Int,
    val status: String
)

data class DiffFileInfo(
    val file: String,
    val before: String,
    val after: String,
    val additions: Int,
    val deletions: Int
)

// Unified diff rendering model produced by DiffEngine.
data class DiffHunk(
    val oldStart: Int,
    val newStart: Int,
    val lines: List<DiffLine>
)

data class DiffLine(
    val kind: LineKind,
    val text: String,
    val oldNo: Int?,
    val newNo: Int?
)

enum class LineKind { CONTEXT, ADD, DEL, HUNK_HEADER }

data class RenderedFileDiff(
    val file: DiffFileInfo,
    val hunks: List<DiffHunk>
)
