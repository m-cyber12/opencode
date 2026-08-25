package com.opencode.client.opencode.dto

import kotlinx.serialization.Serializable

/*
 * Wire types mirroring the official OpenCode OpenAPI schema (packages/sdk/js/src/gen/types.gen.ts).
 * Fields that are optional on the wire are nullable/defaulted here so older or newer servers
 * never crash the decoder. Unknown JSON fields are ignored (see appJson).
 */

@Serializable
data class HealthDto(
    val healthy: Boolean = false,
    val version: String = "unknown"
)

@Serializable
data class ProjectDto(
    val id: String = "",
    val worktree: String = "",
    val vcsDir: String? = null,
    val vcs: String? = null,
    val time: ProjectTimeDto = ProjectTimeDto()
)

@Serializable
data class ProjectTimeDto(
    val created: Long = 0,
    val initialized: Long? = null
)

@Serializable
data class PathDto(
    val state: String = "",
    val config: String = "",
    val worktree: String = "",
    val directory: String = ""
)

@Serializable
data class VcsDto(
    val branch: String? = null
)

@Serializable
data class SessionDto(
    val id: String,
    val projectID: String = "",
    val directory: String = "",
    val parentID: String? = null,
    val title: String = "New session",
    val version: String = "",
    val share: ShareDto? = null,
    val summary: SessionSummaryDto? = null,
    val revert: JsonObjectLike? = null,
    val time: SessionTimeDto = SessionTimeDto()
)

@Serializable
data class SessionTimeDto(
    val created: Long = 0,
    val updated: Long = 0,
    val compacting: Long? = null
)

@Serializable
data class SessionSummaryDto(
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: Int = 0,
    val diffs: List<FileDiffDto> = emptyList()
)

@Serializable
data class ShareDto(
    val url: String = ""
)

@Serializable
data class FileDiffDto(
    val file: String = "",
    val before: String = "",
    val after: String = "",
    val additions: Int = 0,
    val deletions: Int = 0
)

@Serializable
data class ModelRefDto(
    val providerID: String = "",
    val modelID: String = ""
)

@Serializable
data class UserMessageDto(
    val id: String,
    val sessionID: String = "",
    val role: String = "user",
    val time: CreatedTimeDto = CreatedTimeDto(),
    val agent: String = "",
    val model: ModelRefDto = ModelRefDto(),
    val system: String? = null
)

@Serializable
data class CreatedTimeDto(
    val created: Long = 0
)

@Serializable
data class TokensDto(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cache: CacheTokensDto = CacheTokensDto()
)

@Serializable
data class CacheTokensDto(
    val read: Long = 0,
    val write: Long = 0
)

@Serializable
data class AssistantMessageDto(
    val id: String,
    val sessionID: String = "",
    val role: String = "assistant",
    val time: AssistantTimeDto = AssistantTimeDto(),
    val parentID: String = "",
    val modelID: String = "",
    val providerID: String = "",
    val mode: String = "",
    val agent: String? = null,
    val cost: Double = 0.0,
    val tokens: TokensDto = TokensDto(),
    val summary: Boolean? = null,
    val finish: String? = null,
    // Error union flattened to its common denominator; parsed loosely.
    val error: JsonObjectLike? = null
)

/** Loose object holder for unions we only inspect opportunistically (e.g. message errors). */
typealias JsonObjectLike = kotlinx.serialization.json.JsonObject

@Serializable
data class AssistantTimeDto(
    val created: Long = 0,
    val completed: Long? = null
)

// ---- Parts -----------------------------------------------------------------

@Serializable
data class TextPartDto(
    val id: String,
    val sessionID: String = "",
    val messageID: String = "",
    val type: String = "text",
    val text: String = "",
    val synthetic: Boolean? = null,
    val ignored: Boolean? = null,
    val time: PartTimeDto? = null
)

@Serializable
data class ReasoningPartDto(
    val id: String,
    val sessionID: String = "",
    val messageID: String = "",
    val type: String = "reasoning",
    val text: String = "",
    val time: PartTimeDto? = null
)

@Serializable
data class PartTimeDto(
    val start: Long = 0,
    val end: Long? = null
)

@Serializable
data class ToolPartDto(
    val id: String,
    val sessionID: String = "",
    val messageID: String = "",
    val type: String = "tool",
    val callID: String = "",
    val tool: String = "",
    // Tool state is a union; decode into the loose ToolStateDto below.
    val state: ToolStateDto = ToolStateDto(),
    val metadata: JsonObjectLike? = null
)

@Serializable
data class ToolStateDto(
    // pending | running | completed | error
    val status: String = "pending",
    val input: JsonObjectLike? = null,
    val output: String? = null,
    val title: String? = null,
    val error: String? = null,
    val metadata: JsonObjectLike? = null,
    val raw: String? = null,
    val time: ToolTimeDto? = null
)

@Serializable
data class ToolTimeDto(
    val start: Long = 0,
    val end: Long? = null,
    val compacted: Long? = null
)

@Serializable
data class StepStartPartDto(
    val id: String,
    val type: String = "step-start"
)

@Serializable
data class StepFinishPartDto(
    val id: String,
    val type: String = "step-finish",
    val reason: String = "",
    val cost: Double = 0.0,
    val tokens: TokensDto = TokensDto()
)

@Serializable
data class PatchPartDto(
    val id: String,
    val sessionID: String = "",
    val messageID: String = "",
    val type: String = "patch",
    val hash: String = "",
    val files: List<String> = emptyList()
)

@Serializable
data class SubtaskPartDto(
    val id: String,
    val sessionID: String = "",
    val messageID: String = "",
    val type: String = "subtask",
    val prompt: String = "",
    val description: String = "",
    val agent: String = ""
)

@Serializable
data class AgentPartDto(
    val id: String,
    val sessionID: String = "",
    val messageID: String = "",
    val type: String = "agent",
    val name: String = ""
)

@Serializable
data class RetryPartDto(
    val id: String,
    val sessionID: String = "",
    val messageID: String = "",
    val type: String = "retry",
    val attempt: Int = 0,
    val error: JsonObjectLike? = null,
    val time: CreatedTimeDto = CreatedTimeDto()
)

@Serializable
data class CompactionPartDto(
    val id: String,
    val sessionID: String = "",
    val messageID: String = "",
    val type: String = "compaction",
    val auto: Boolean = false
)

@Serializable
data class FilePartDto(
    val id: String,
    val sessionID: String = "",
    val messageID: String = "",
    val type: String = "file",
    val mime: String = "",
    val filename: String? = null,
    val url: String = ""
)

/**
 * A generic part envelope used for decoding arbitrary parts from lists and events.
 * `kind` is the discriminator ("text" | "tool" | ...); the raw object is retained so the mapper
 * can extract whichever concrete part it needs without a fragile polymorphic setup.
 */
@Serializable
data class RawPartDto(
    val id: String = "",
    val sessionID: String = "",
    val messageID: String = "",
    val type: String = "unknown",
    // Common optional payloads, decoded leniently:
    val text: String? = null,
    val synthetic: Boolean? = null,
    val tool: String? = null,
    val callID: String? = null,
    val state: ToolStateDto? = null,
    val metadata: JsonObjectLike? = null,
    val hash: String? = null,
    val files: List<String>? = null,
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val prompt: String? = null,
    val description: String? = null,
    val agent: String? = null,
    val name: String? = null,
    val reason: String? = null,
    val attempt: Int? = null,
    val error: JsonObjectLike? = null,
    val auto: Boolean? = null,
    val time: JsonObjectLike? = null
)

// ---- Message bundles ---------------------------------------------------------

@Serializable
data class MessageBundleDto(
    val info: JsonObjectLike,
    val parts: List<RawPartDto> = emptyList()
)

// ---- Requests ----------------------------------------------------------------

@Serializable
data class PromptBodyDto(
    val messageID: String? = null,
    val model: ModelRefDto? = null,
    val agent: String? = null,
    val noReply: Boolean? = null,
    val system: String? = null,
    val tools: Map<String, Boolean>? = null,
    val parts: List<JsonObjectLike>
)

// ---- Providers / models / agents / commands -----------------------------------

@Serializable
data class ProvidersResponseDto(
    val providers: List<ProviderDto> = emptyList(),
    val default: Map<String, String> = emptyMap()
)

@Serializable
data class ProviderDto(
    val id: String = "",
    val name: String = "",
    val env: List<String> = emptyList(),
    val models: Map<String, ModelDto> = emptyMap()
)

@Serializable
data class ModelDto(
    val id: String = "",
    val name: String = "",
    val status: String? = null,
    val limit: LimitDto? = null,
    val cost: CostDto? = null
)

@Serializable
data class LimitDto(
    val context: Long = 0,
    val output: Long = 0
)

@Serializable
data class CostDto(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cache: CacheCostDto? = null
)

@Serializable
data class CacheCostDto(
    val read: Double = 0.0,
    val write: Double = 0.0
)

@Serializable
data class ProviderListResponseDto(
    val all: List<ProviderDto> = emptyList(),
    val default: Map<String, String> = emptyMap(),
    val connected: List<String> = emptyList()
)

@Serializable
data class AgentDto(
    val name: String = "",
    val description: String? = null,
    val mode: String = "all",
    val builtIn: Boolean = true,
    val color: String? = null,
    val model: ModelRefDto? = null
)

@Serializable
data class CommandDto(
    val name: String = "",
    val description: String? = null,
    val template: String = "",
    val agent: String? = null,
    val model: String? = null,
    val subtask: Boolean? = null
)

// ---- Files / search -------------------------------------------------------------

@Serializable
data class FileNodeDto(
    val name: String = "",
    val path: String = "",
    val absolute: String = "",
    val type: String = "file",
    val ignored: Boolean = false
)

@Serializable
data class FileContentDto(
    val type: String = "text",
    val content: String = "",
    val diff: String? = null,
    val encoding: String? = null,
    val mimeType: String? = null
)

@Serializable
data class TextMatchDto(
    val path: PathTextDto = PathTextDto(),
    val lines: LinesDto = LinesDto(),
    val line_number: Int = 0,
    val absolute_offset: Long = 0
)

@Serializable
data class PathTextDto(val text: String = "")

@Serializable
data class LinesDto(val text: String = "")

@Serializable
data class GitFileDto(
    val path: String = "",
    val added: Int = 0,
    val removed: Int = 0,
    val status: String = "modified"
)

// ---- Todos / statuses --------------------------------------------------------

@Serializable
data class TodoDto(
    val id: String = "",
    val content: String = "",
    val status: String = "pending",
    val priority: String = "medium"
)

/** Loose status entry from GET /session/status (idle | busy | retry{attempt,message,next}). */
@Serializable
data class SessionStatusEntryDto(
    val type: String = "idle",
    val attempt: Int? = null,
    val message: String? = null,
    val next: Long? = null
)

// ---- MCP / LSP / formatters -----------------------------------------------------

@Serializable
data class McpStatusDto(
    val status: String = "unknown",
    val error: String? = null
)

@Serializable
data class LspStatusDto(
    val id: String = "",
    val name: String = "",
    val root: String = "",
    val status: String = ""
)

@Serializable
data class FormatterStatusDto(
    val name: String = "",
    val extensions: List<String> = emptyList(),
    val enabled: Boolean = true
)

// ---- Permissions -------------------------------------------------------------

/** Emitted through permission.updated; answered via POST /session/:id/permissions/:permissionID. */
@Serializable
data class PermissionRequestDto(
    val id: String,
    val type: String = "",
    val pattern: List<String>? = null,
    val sessionID: String = "",
    val messageID: String = "",
    val callID: String? = null,
    val title: String = "",
    val metadata: JsonObjectLike? = null,
    val time: CreatedTimeDto = CreatedTimeDto()
)
