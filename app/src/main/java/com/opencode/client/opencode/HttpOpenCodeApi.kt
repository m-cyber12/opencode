package com.opencode.client.opencode

import com.opencode.client.core.AppError
import com.opencode.client.core.Outcome
import com.opencode.client.core.appJson
import com.opencode.client.core.network.Http
import com.opencode.client.core.network.q
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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.OkHttpClient

/**
 * HTTP implementation backed by OkHttp. Every call maps transport/HTTP failures to AppErrors;
 * decoding uses the lenient appJson so unknown fields are tolerated.
 */
class HttpOpenCodeApi(
    private val baseUrl: String,
    private val callFactory: Call.Factory
) : OpenCodeApi {

    private fun url(path: String, directory: String?, vararg query: Pair<String, String?>): String {
        val sb = StringBuilder(baseUrl.trimEnd('/')).append(path)
        val params = buildList {
            if (!directory.isNullOrBlank()) add("directory" to directory)
            query.forEach { (k, v) -> if (v != null) add(k to v) }
        }
        if (params.isNotEmpty()) {
            sb.append('?').append(params.joinToString("&") { "${it.first}=${q(it.second!!)}" })
        }
        return sb.toString()
    }

    private inline fun <reified T> decode(body: String): T {
        return appJson.decodeFromString<T>(body)
    }

    private inline fun <T> call(
        request: okhttp3.Request,
        crossinline parse: (String) -> T
    ): Outcome<T> = Http.execute(callFactory, request) { response ->
        val text = response.body?.string().orEmpty()
        parse(text)
    }

    // Global ------------------------------------------------------------------

    override suspend fun health(): Outcome<HealthDto> = runCatchingDecoded {
        call(Http.get(url("/global/health", null))) { decode<HealthDto>(it) }
    }

    // Projects / path ----------------------------------------------------------

    override suspend fun projects(directory: String?): Outcome<List<ProjectDto>> =
        call(Http.get(url("/project", directory))) { decode(it) }

    override suspend fun currentProject(directory: String?): Outcome<ProjectDto> =
        call(Http.get(url("/project/current", directory))) { decode(it) }

    override suspend fun path(directory: String?): Outcome<PathDto> =
        call(Http.get(url("/path", directory))) { decode(it) }

    override suspend fun vcs(directory: String?): Outcome<VcsDto> =
        call(Http.get(url("/vcs", directory))) { decode(it) }

    // Config / providers ---------------------------------------------------------

    override suspend fun providers(directory: String?): Outcome<ProvidersResponseDto> =
        call(Http.get(url("/config/providers", directory))) { decode(it) }

    // Agents / commands -------------------------------------------------------------

    override suspend fun agents(directory: String?): Outcome<List<AgentDto>> =
        call(Http.get(url("/agent", directory))) { decode(it) }

    override suspend fun commands(): Outcome<List<CommandDto>> =
        call(Http.get(url("/command", null))) { decode(it) }

    // Sessions ------------------------------------------------------------------------

    override suspend fun sessions(directory: String?): Outcome<List<SessionDto>> =
        call(Http.get(url("/session", directory))) { decode(it) }

    override suspend fun createSession(title: String?, directory: String?): Outcome<SessionDto> {
        val body = title?.let { appJson.encodeToString(mapOf("title" to it)) }
        return call(Http.post(url("/session", directory), body)) { decode(it) }
    }

    override suspend fun deleteSession(id: String, directory: String?): Outcome<Boolean> =
        call(Http.delete(url("/session/$id", directory))) { true }

    override suspend fun renameSession(id: String, title: String, directory: String?): Outcome<SessionDto> =
        call(
            Http.patch(
                url("/session/$id", directory),
                appJson.encodeToString(mapOf("title" to title))
            )
        ) { decode(it) }

    override suspend fun sessionStatuses(directory: String?): Outcome<Map<String, SessionStatusEntryDto>> =
        call(Http.get(url("/session/status", directory))) { decode(it) }

    override suspend fun sessionTodo(id: String, directory: String?): Outcome<List<TodoDto>> =
        call(Http.get(url("/session/$id/todo", directory))) { decode(it) }

    override suspend fun abortSession(id: String, directory: String?): Outcome<Boolean> =
        call(Http.post(url("/session/$id/abort", directory), null)) { true }

    override suspend fun shareSession(id: String, directory: String?): Outcome<SessionDto> =
        call(Http.post(url("/session/$id/share", directory), null)) { decode(it) }

    override suspend fun unshareSession(id: String, directory: String?): Outcome<SessionDto> =
        call(Http.delete(url("/session/$id/share", directory))) { decode(it) }

    override suspend fun sessionDiff(
        id: String,
        messageID: String?,
        directory: String?
    ): Outcome<List<FileDiffDto>> =
        call(Http.get(url("/session/$id/diff", directory, "messageID" to messageID))) { decode(it) }

    override suspend fun summarizeSession(
        id: String,
        model: ModelRefDto,
        directory: String?
    ): Outcome<Boolean> = call(
        Http.post(
            url("/session/$id/summarize", directory),
            appJson.encodeToString(
                mapOf("providerID" to model.providerID, "modelID" to model.modelID)
            )
        )
    ) { true }

    override suspend fun revertMessage(
        id: String,
        messageID: String,
        partID: String?,
        directory: String?
    ): Outcome<SessionDto> {
        val body = buildJsonObject {
            put("messageID", messageID)
            if (partID != null) put("partID", partID)
        }
        return call(
            Http.post(url("/session/$id/revert", directory), body.toString())
        ) { decode(it) }
    }

    override suspend fun unrevert(id: String, directory: String?): Outcome<SessionDto> =
        call(Http.post(url("/session/$id/unrevert", directory), null)) { decode(it) }

    override suspend fun respondToPermission(
        id: String,
        permissionID: String,
        response: String,
        directory: String?
    ): Outcome<Boolean> = call(
        Http.post(
            url("/session/$id/permissions/$permissionID", directory),
            appJson.encodeToString(mapOf("response" to response))
        )
    ) { true }

    // Messages ---------------------------------------------------------------------------

    override suspend fun messages(
        id: String,
        limit: Int?,
        directory: String?
    ): Outcome<List<MessageBundleDto>> =
        call(
            Http.get(url("/session/$id/message", directory, "limit" to limit?.toString()))
        ) { decode(it) }

    override suspend fun message(
        id: String,
        messageID: String,
        directory: String?
    ): Outcome<MessageBundleDto> =
        call(Http.get(url("/session/$id/message/$messageID", directory))) { decode(it) }

    override suspend fun promptAsync(
        id: String,
        body: PromptBodyDto,
        directory: String?
    ): Outcome<Unit> = call(
        Http.post(url("/session/$id/prompt_async", directory), appJson.encodeToString(body))
    ) { Unit }

    override suspend fun runCommand(
        id: String,
        command: String,
        arguments: String,
        agent: String?,
        directory: String?
    ): Outcome<MessageBundleDto> {
        val body = buildJsonObject {
            put("command", command)
            put("arguments", arguments)
            if (agent != null) put("agent", agent)
        }
        return call(
            Http.post(url("/session/$id/command", directory), body.toString())
        ) { decode(it) }
    }

    override suspend fun runShell(
        id: String,
        agent: String,
        command: String,
        directory: String?
    ): Outcome<MessageBundleDto> {
        val body = buildJsonObject {
            put("agent", agent)
            put("command", command)
        }
        return call(
            Http.post(url("/session/$id/shell", directory), body.toString())
        ) { decode(it) }
    }

    // Files ---------------------------------------------------------------------------------

    override suspend fun findText(pattern: String, directory: String?): Outcome<List<TextMatchDto>> =
        call(Http.get(url("/find", directory, "pattern" to pattern))) { decode(it) }

    override suspend fun findFiles(
        query: String,
        limit: Int,
        directory: String?
    ): Outcome<List<String>> =
        call(
            Http.get(
                url(
                    "/find/file",
                    directory,
                    "query" to query,
                    "limit" to limit.toString()
                )
            )
        ) { decode(it) }

    override suspend fun listFiles(path: String, directory: String?): Outcome<List<FileNodeDto>> =
        call(Http.get(url("/file", directory, "path" to path))) { decode(it) }

    override suspend fun readFile(path: String, directory: String?): Outcome<FileContentDto> =
        call(Http.get(url("/file/content", directory, "path" to path))) { decode(it) }

    override suspend fun fileStatus(directory: String?): Outcome<List<GitFileDto>> =
        call(Http.get(url("/file/status", directory))) { decode(it) }

    // MCP / LSP / formatters ---------------------------------------------------------------------

    override suspend fun mcpStatus(directory: String?): Outcome<Map<String, McpStatusDto>> =
        call(Http.get(url("/mcp", directory))) { decode(it) }

    override suspend fun lspStatus(directory: String?): Outcome<List<LspStatusDto>> =
        call(Http.get(url("/lsp", directory))) { decode(it) }

    override suspend fun formatterStatus(directory: String?): Outcome<List<FormatterStatusDto>> =
        call(Http.get(url("/formatter", directory))) { decode(it) }

    // Helpers ---------------------------------------------------------------------------------------

    override fun decodeUserMessage(json: JsonObject): UserMessageDto? =
        try {
            appJson.decodeFromJsonElement<UserMessageDto>(json)
        } catch (_: Exception) {
            null
        }

    override fun decodeAssistantMessage(json: JsonObject): AssistantMessageDto? =
        try {
            appJson.decodeFromJsonElement<AssistantMessageDto>(json)
        } catch (_: Exception) {
            null
        }

    /** Wraps a nested call so a failure of optional probes becomes null instead of an error. */
    private inline fun <T> runCatchingDecoded(block: () -> Outcome<T>): Outcome<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Outcome.Err(AppError.from(t))
    }

    companion object {
        /** Builds an API for [baseUrl] sharing [client] (auth interceptor already attached). */
        fun forServer(baseUrl: String, client: OkHttpClient): HttpOpenCodeApi =
            HttpOpenCodeApi(baseUrl, client)
    }
}
