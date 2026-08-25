package com.opencode.client.opencode

import com.opencode.client.core.Outcome
import com.opencode.client.opencode.dto.AgentDto
import com.opencode.client.opencode.dto.AssistantMessageDto
import com.opencode.client.opencode.dto.CommandDto
import com.opencode.client.opencode.dto.FileContentDto
import com.opencode.client.opencode.dto.FileDiffDto
import com.opencode.client.opencode.dto.FileNodeDto
import com.opencode.client.opencode.dto.FormatterStatusDto
import com.opencode.client.opencode.dto.GitFileDto
import com.opencode.client.opencode.dto.HealthDto
import com.opencode.client.opencode.dto.LspStatusDto
import com.opencode.client.opencode.dto.McpStatusDto
import com.opencode.client.opencode.dto.MessageBundleDto
import com.opencode.client.opencode.dto.ModelRefDto
import com.opencode.client.opencode.dto.PromptBodyDto
import com.opencode.client.opencode.dto.ProjectDto
import com.opencode.client.opencode.dto.ProvidersResponseDto
import com.opencode.client.opencode.dto.PathDto
import com.opencode.client.opencode.dto.SessionDto
import com.opencode.client.opencode.dto.SessionStatusEntryDto
import com.opencode.client.opencode.dto.TextMatchDto
import com.opencode.client.opencode.dto.TodoDto
import com.opencode.client.opencode.dto.UserMessageDto
import com.opencode.client.opencode.dto.VcsDto

/**
 * Transport-agnostic OpenCode API surface.
 *
 * [directory] selects the project instance on the server (the `directory` query parameter).
 * Pass null to address the server's default instance.
 *
 * All methods are suspend and return typed Outcomes; no method throws for expected failures.
 */
interface OpenCodeApi {

    // Global ------------------------------------------------------------------
    suspend fun health(): Outcome<HealthDto>

    // Projects / path -----------------------------------------------------------
    suspend fun projects(directory: String? = null): Outcome<List<ProjectDto>>
    suspend fun currentProject(directory: String? = null): Outcome<ProjectDto>
    suspend fun path(directory: String? = null): Outcome<PathDto>
    suspend fun vcs(directory: String? = null): Outcome<VcsDto>

    // Config / providers ----------------------------------------------------------
    suspend fun providers(directory: String? = null): Outcome<ProvidersResponseDto>

    // Agents / commands -------------------------------------------------------------
    suspend fun agents(directory: String? = null): Outcome<List<AgentDto>>
    suspend fun commands(): Outcome<List<CommandDto>>

    // Sessions ------------------------------------------------------------------------
    suspend fun sessions(directory: String? = null): Outcome<List<SessionDto>>
    suspend fun createSession(title: String? = null, directory: String? = null): Outcome<SessionDto>
    suspend fun deleteSession(id: String, directory: String? = null): Outcome<Boolean>
    suspend fun renameSession(id: String, title: String, directory: String? = null): Outcome<SessionDto>
    suspend fun sessionStatuses(directory: String? = null): Outcome<Map<String, SessionStatusEntryDto>>
    suspend fun sessionTodo(id: String, directory: String? = null): Outcome<List<TodoDto>>
    suspend fun abortSession(id: String, directory: String? = null): Outcome<Boolean>
    suspend fun shareSession(id: String, directory: String? = null): Outcome<SessionDto>
    suspend fun unshareSession(id: String, directory: String? = null): Outcome<SessionDto>
    suspend fun sessionDiff(
        id: String,
        messageID: String? = null,
        directory: String? = null
    ): Outcome<List<FileDiffDto>>

    suspend fun summarizeSession(
        id: String,
        model: ModelRefDto,
        directory: String? = null
    ): Outcome<Boolean>

    suspend fun revertMessage(
        id: String,
        messageID: String,
        partID: String? = null,
        directory: String? = null
    ): Outcome<SessionDto>

    suspend fun unrevert(id: String, directory: String? = null): Outcome<SessionDto>

    /** response is one of "once" | "always" | "reject" (server-side enum). */
    suspend fun respondToPermission(
        id: String,
        permissionID: String,
        response: String,
        directory: String? = null
    ): Outcome<Boolean>

    // Messages ---------------------------------------------------------------------------
    suspend fun messages(id: String, limit: Int? = null, directory: String? = null): Outcome<List<MessageBundleDto>>
    suspend fun message(id: String, messageID: String, directory: String? = null): Outcome<MessageBundleDto>

    /**
     * Fire-and-forget prompt. Streaming output arrives through the event stream as
     * message.updated / message.part.updated events - the UI never polls.
     */
    suspend fun promptAsync(id: String, body: PromptBodyDto, directory: String? = null): Outcome<Unit>

    suspend fun runCommand(
        id: String,
        command: String,
        arguments: String,
        agent: String?,
        directory: String? = null
    ): Outcome<MessageBundleDto>

    suspend fun runShell(
        id: String,
        agent: String,
        command: String,
        directory: String? = null
    ): Outcome<MessageBundleDto>

    // Files ---------------------------------------------------------------------------------
    suspend fun findText(pattern: String, directory: String? = null): Outcome<List<TextMatchDto>>
    suspend fun findFiles(query: String, limit: Int = 50, directory: String? = null): Outcome<List<String>>
    suspend fun listFiles(path: String, directory: String? = null): Outcome<List<FileNodeDto>>
    suspend fun readFile(path: String, directory: String? = null): Outcome<FileContentDto>
    suspend fun fileStatus(directory: String? = null): Outcome<List<GitFileDto>>

    // MCP / LSP / formatters --------------------------------------------------------------------
    suspend fun mcpStatus(directory: String? = null): Outcome<Map<String, McpStatusDto>>
    suspend fun lspStatus(directory: String? = null): Outcome<List<LspStatusDto>>
    suspend fun formatterStatus(directory: String? = null): Outcome<List<FormatterStatusDto>>

    // Helpers used by higher layers ---------------------------------------------------------------
    /** Decodes a raw user-message JSON object into a typed DTO; lenient. */
    fun decodeUserMessage(json: kotlinx.serialization.json.JsonObject): UserMessageDto?
    fun decodeAssistantMessage(json: kotlinx.serialization.json.JsonObject): AssistantMessageDto?
}
