package com.opencode.client.data.repo

import com.opencode.client.core.AppError
import com.opencode.client.core.Outcome
import com.opencode.client.domain.AgentInfo
import com.opencode.client.domain.CommandInfo
import com.opencode.client.domain.DiffFileInfo
import com.opencode.client.domain.FileContentInfo
import com.opencode.client.domain.FileNodeInfo
import com.opencode.client.domain.GitFileStatus
import com.opencode.client.domain.LspInfo
import com.opencode.client.domain.FormatterInfo
import com.opencode.client.domain.McpInfo
import com.opencode.client.domain.ModelRef
import com.opencode.client.domain.PathInfo
import com.opencode.client.domain.PermissionRequest
import com.opencode.client.domain.ProjectInfo
import com.opencode.client.domain.ProviderInfo
import com.opencode.client.domain.RunStatus
import com.opencode.client.domain.SessionInfo
import com.opencode.client.domain.TextSearchMatch
import com.opencode.client.domain.TodoItem
import com.opencode.client.domain.UiMessage
import com.opencode.client.engine.ServerController
import com.opencode.client.opencode.FieldDecoders
import com.opencode.client.opencode.dto.ModelRefDto
import com.opencode.client.opencode.dto.PromptBodyDto
import com.opencode.client.opencode.toDomain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

/**
 * Thin, focused facades over [OpenCodeApi]. They resolve the active server + project directory
 * from the [ServerController] and map DTOs to domain types. UI layers depend only on these.
 */

/**
 * The slice of session operations the chat state machine needs. Keeping this as an interface
 * makes the event-driven reducer unit-testable without any transport.
 */
interface SessionsGateway {
    suspend fun messages(sessionId: String): Outcome<List<UiMessage>>
    suspend fun todo(sessionId: String): Outcome<List<TodoItem>>
    suspend fun statuses(): Outcome<Map<String, RunStatus>>

    suspend fun sendPrompt(
        sessionId: String,
        text: String,
        attachments: List<PromptAttachment>,
        model: ModelRef?,
        agent: String?
    ): Outcome<Unit>

    suspend fun respondToPermission(request: PermissionRequest, response: String): Outcome<Boolean>
    suspend fun abortSession(sessionId: String): Outcome<Boolean>
    suspend fun sessionDiff(sessionId: String): Outcome<List<DiffFileInfo>>
}

class SessionRepository(private val controller: ServerController) : SessionsGateway {

    private suspend fun <T> call(block: suspend (com.opencode.client.opencode.OpenCodeApi, String?) -> Outcome<T>): Outcome<T> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return block(api, controller.activeDirectory)
    }

    suspend fun sessions(): Outcome<List<SessionInfo>> = call { api, dir ->
        api.sessions(dir).map { list -> list.map { it.toDomain() }.sortedByDescending { it.updatedAt } }
    }

    suspend fun createSession(title: String? = null): Outcome<SessionInfo> = call { api, dir ->
        api.createSession(title, dir).map { it.toDomain() }
    }

    suspend fun deleteSession(id: String): Outcome<Boolean> = call { api, dir ->
        api.deleteSession(id, dir)
    }

    suspend fun renameSession(id: String, title: String): Outcome<SessionInfo> = call { api, dir ->
        api.renameSession(id, title, dir).map { it.toDomain() }
    }

    override suspend fun abortSession(id: String): Outcome<Boolean> = call { api, dir ->
        api.abortSession(id, dir)
    }

    suspend fun shareSession(id: String, share: Boolean): Outcome<String?> = call { api, dir ->
        val result = if (share) api.shareSession(id, dir) else api.unshareSession(id, dir)
        result.map { it.share?.url }
    }

    override suspend fun statuses(): Outcome<Map<String, RunStatus>> = call { api, dir ->
        api.sessionStatuses(dir).map { raw ->
            raw.mapValues { (_, entry) ->
                when (entry.type) {
                    "busy" -> RunStatus.Busy
                    "retry" -> RunStatus.Retrying(
                        entry.attempt ?: 0,
                        entry.message,
                        entry.next ?: 0L
                    )
                    else -> RunStatus.Idle
                }
            }
        }
    }

    override suspend fun todo(sessionId: String): Outcome<List<TodoItem>> = call { api, dir ->
        api.sessionTodo(sessionId, dir).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun messages(sessionId: String): Outcome<List<UiMessage>> = call { api, dir ->
        api.messages(sessionId, limit = 200, directory = dir).map { bundles ->
            bundles.mapNotNull { bundle ->
                bundle.toDomain(FieldDecoders.user, FieldDecoders.assistant)
            }
        }
    }

    override suspend fun sendPrompt(
        sessionId: String,
        text: String,
        attachments: List<PromptAttachment>,
        model: ModelRef?,
        agent: String?
    ): Outcome<Unit> = call { api, dir ->
        val parts = buildList {
            add(
                kotlinx.serialization.json.buildJsonObject {
                    put("type", "text")
                    put("text", text)
                }
            )
            attachments.forEach { att ->
                add(
                    kotlinx.serialization.json.buildJsonObject {
                        put("type", "file")
                        put("mime", att.mime)
                        att.url?.let { put("url", it) }
                        att.filename?.let { put("filename", it) }
                    }
                )
            }
        }
        api.promptAsync(
            sessionId,
            PromptBodyDto(model = model?.let { ModelRefDto(it.providerID, it.modelID) }, agent = agent, parts = parts),
            directory = dir
        )
    }

    suspend fun runCommand(sessionId: String, command: CommandInfo, arguments: String): Outcome<Unit> =
        call { api, dir ->
            api.runCommand(sessionId, command.name, arguments, null, dir).map { }
        }

    suspend fun runShell(sessionId: String, agent: String?, command: String): Outcome<UiMessage?> =
        call { api, dir ->
            val effectiveAgent = agent ?: "build"
            api.runShell(sessionId, effectiveAgent, command, dir).map { bundle ->
                bundle.toDomain(FieldDecoders.user, FieldDecoders.assistant)
            }
        }

    override suspend fun respondToPermission(request: PermissionRequest, response: String): Outcome<Boolean> =
        call { api, dir ->
            api.respondToPermission(request.sessionId, request.id, response, dir)
        }

    override suspend fun sessionDiff(sessionId: String): Outcome<List<DiffFileInfo>> = call { api, dir ->
        api.sessionDiff(sessionId, null, dir).map { list -> list.map { it.toDomain() } }
    }

    suspend fun revertMessage(sessionId: String, messageID: String): Outcome<Unit> = call { api, dir ->
        api.revertMessage(sessionId, messageID, null, dir).map { }
    }

    suspend fun unrevert(sessionId: String): Outcome<Unit> = call { api, dir ->
        api.unrevert(sessionId, dir).map { }
    }
}

data class PromptAttachment(val mime: String, val url: String, val filename: String?)

class ProjectRepository(private val controller: ServerController) {

    suspend fun projects(): Outcome<List<ProjectInfo>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.projects().map { list -> list.map { it.toDomain() } }
    }

    suspend fun pathInfo(): Outcome<PathInfo?> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.path(controller.activeDirectory).map { dto ->
            PathInfo(dto.worktree, dto.directory, dto.config)
        }
    }

    suspend fun vcsBranch(): Outcome<String?> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.vcs(controller.activeDirectory).map { it.branch }
    }
}

class FileRepository(private val controller: ServerController) {

    suspend fun list(path: String): Outcome<List<FileNodeInfo>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.listFiles(path, controller.activeDirectory).map { list ->
            list.map { it.toDomain() }.sortedWith(
                compareByDescending<FileNodeInfo> { it.isDirectory }.thenBy { it.name.lowercase() }
            )
        }
    }

    suspend fun searchFiles(query: String): Outcome<List<String>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.findFiles(query, 50, controller.activeDirectory)
    }

    suspend fun readFile(path: String): Outcome<FileContentInfo> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.readFile(path, controller.activeDirectory).map { it.toDomain(path) }
    }

    suspend fun searchText(pattern: String): Outcome<List<TextSearchMatch>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.findText(pattern, controller.activeDirectory).map { list ->
            list.take(200).map { TextSearchMatch(it.path.text, it.lines.text.trimEnd('\n'), it.line_number) }
        }
    }

    suspend fun gitStatus(): Outcome<List<GitFileStatus>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.fileStatus(controller.activeDirectory).map { list -> list.map { it.toDomain() } }
    }
}

class ConfigRepository(private val controller: ServerController) {

    suspend fun providers(): Outcome<List<ProviderInfo>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.providers(controller.activeDirectory).map { resp ->
            resp.providers.map { it.toDomain() }.sortedBy { it.name }
        }
    }

    suspend fun agents(): Outcome<List<AgentInfo>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.agents(controller.activeDirectory).map { list ->
            list.filter { it.mode != "subagent" }.map { it.toDomain() }
        }
    }

    suspend fun commands(): Outcome<List<CommandInfo>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.commands().map { list -> list.map { it.toDomain() } }
    }

    suspend fun mcpServers(): Outcome<List<McpInfo>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.mcpStatus(controller.activeDirectory).map { map ->
            map.map { (name, status) -> McpInfo(name, status.status, status.error) }
                .sortedBy { it.name }
        }
    }

    suspend fun lspServers(): Outcome<List<LspInfo>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.lspStatus(controller.activeDirectory).map { list ->
            list.map { LspInfo(it.name, it.status, it.root) }
        }
    }

    suspend fun formatters(): Outcome<List<FormatterInfo>> {
        val api = controller.api ?: return Outcome.Err(AppError.NotConnected())
        return api.formatterStatus(controller.activeDirectory).map { list ->
            list.map { FormatterInfo(it.name, it.extensions.joinToString(", "), it.enabled) }
        }
    }
}
