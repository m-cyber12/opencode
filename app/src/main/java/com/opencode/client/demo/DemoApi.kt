package com.opencode.client.demo

import com.opencode.client.core.Outcome
import com.opencode.client.opencode.OpenCodeApi
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

/**
 * In-memory OpenCodeApi implementation backed by [DemoRuntime].
 * Responses mirror real server payloads so the production mappers run unchanged.
 */
class DemoApi : OpenCodeApi {

    override suspend fun health(): Outcome<HealthDto> =
        Outcome.Ok(HealthDto(healthy = true, version = DemoRuntime.DEMO_VERSION))

    override suspend fun projects(directory: String?): Outcome<List<ProjectDto>> = Outcome.Ok(
        listOf(
            ProjectDto(
                id = "proj-demo",
                worktree = DemoRuntime.DEMO_PROJECT,
                vcs = "git",
                time = com.opencode.client.opencode.dto.ProjectTimeDto(created = 1_700_000_000_000)
            )
        )
    )

    override suspend fun currentProject(directory: String?): Outcome<ProjectDto> =
        projects().map { it.first() }

    override suspend fun path(directory: String?): Outcome<PathDto> = Outcome.Ok(
        PathDto(state = "/home/dev/.local/share/opencode", config = "/home/dev/.config/opencode/opencode.json", worktree = DemoRuntime.DEMO_PROJECT, directory = directory ?: DemoRuntime.DEMO_PROJECT)
    )

    override suspend fun vcs(directory: String?): Outcome<VcsDto> = Outcome.Ok(VcsDto(branch = "feat/mobile-client"))

    override suspend fun providers(directory: String?): Outcome<ProvidersResponseDto> {
        val anthropicModels = linkedMapOf(
            "claude-opus-4" to model("Claude Opus 4"),
            "claude-sonnet-4" to model("Claude Sonnet 4"),
            "claude-haiku-4" to model("Claude Haiku 4")
        )
        val openaiModels = linkedMapOf(
            "gpt-5.2" to model("GPT-5.2"),
            "o4-mini" to model("o4-mini")
        )
        val localModels = linkedMapOf("llama3:70b" to model("Llama 3 70B"))
        return Outcome.Ok(
            ProvidersResponseDto(
                providers = listOf(
                    ProviderDto(id = "anthropic", name = "Anthropic", models = anthropicModels),
                    ProviderDto(id = "openai", name = "OpenAI", models = openaiModels),
                    ProviderDto(id = "ollama", name = "Ollama (local)", models = localModels)
                ),
                default = mapOf("anthropic" to "claude-sonnet-4")
            )
        )
    }

    private fun model(name: String) = com.opencode.client.opencode.dto.ModelDto(id = name, name = name)

    override suspend fun agents(directory: String?): Outcome<List<AgentDto>> = Outcome.Ok(
        listOf(
            AgentDto(name = "build", description = "Write features and fix bugs", mode = "primary", builtIn = true),
            AgentDto(name = "plan", description = "Architect solutions before coding", mode = "primary", builtIn = true),
            AgentDto(name = "explore", description = "Fast codebase reconnaissance", mode = "subagent", builtIn = true)
        )
    )

    override suspend fun commands(): Outcome<List<CommandDto>> = Outcome.Ok(
        listOf(
            CommandDto(name = "init", description = "Analyze the project and create AGENTS.md", template = ""),
            CommandDto(name = "compact", description = "Summarize and compress this session", template = ""),
            CommandDto(name = "review", description = "Review pending changes like a staff engineer", template = "Review the uncommitted changes with a focus on correctness.")
        )
    )

    @Synchronized
    override suspend fun sessions(directory: String?): Outcome<List<SessionDto>> = Outcome.Ok(
        DemoRuntime.allSessions().map { s ->
            SessionDto(
                id = s.id,
                projectID = "proj-demo",
                directory = DemoRuntime.DEMO_PROJECT,
                title = s.title,
                version = DemoRuntime.DEMO_VERSION,
                time = com.opencode.client.opencode.dto.SessionTimeDto(created = s.createdAt, updated = s.updatedAt)
            )
        }
    )

    override suspend fun createSession(title: String?, directory: String?): Outcome<SessionDto> {
        val id = "ses-${System.nanoTime()}"
        val s = DemoRuntime.handleCreateSession(id, title)
        return Outcome.Ok(
            SessionDto(
                id = s.id, projectID = "proj-demo", directory = DemoRuntime.DEMO_PROJECT,
                title = s.title, version = DemoRuntime.DEMO_VERSION,
                time = com.opencode.client.opencode.dto.SessionTimeDto(created = s.createdAt, updated = s.updatedAt)
            )
        )
    }

    override suspend fun deleteSession(id: String, directory: String?): Outcome<Boolean> {
        DemoRuntime.handleDeleteSession(id)
        return Outcome.Ok(true)
    }

    override suspend fun renameSession(id: String, title: String, directory: String?): Outcome<SessionDto> {
        DemoRuntime.handleRenameSession(id, title)
        return sessions().map { list -> list.first { it.id == id } }
    }

    override suspend fun sessionStatuses(directory: String?): Outcome<Map<String, SessionStatusEntryDto>> {
        val result = mutableMapOf<String, SessionStatusEntryDto>()
        DemoRuntime.allSessions().forEach { s ->
            if (DemoRuntime.statusOf(s.id) == "busy") {
                result[s.id] = SessionStatusEntryDto(type = "busy")
            }
        }
        return Outcome.Ok(result)
    }

    override suspend fun sessionTodo(id: String, directory: String?): Outcome<List<TodoDto>> = Outcome.Ok(emptyList())

    override suspend fun abortSession(id: String, directory: String?): Outcome<Boolean> = Outcome.Ok(true)

    override suspend fun shareSession(id: String, directory: String?): Outcome<SessionDto> =
        sessions().map { it.first() }

    override suspend fun unshareSession(id: String, directory: String?): Outcome<SessionDto> =
        sessions().map { it.first() }

    override suspend fun sessionDiff(id: String, messageID: String?, directory: String?): Outcome<List<FileDiffDto>> {
        val arr = DemoRuntime.diffsFor(id)
        val diffs = arr.mapNotNull { el ->
            runCatching {
                com.opencode.client.core.appJson.decodeFromJsonElement<FileDiffDto>(el as JsonObject)
            }.getOrNull()
        }
        return Outcome.Ok(diffs)
    }

    override suspend fun summarizeSession(id: String, model: ModelRefDto, directory: String?): Outcome<Boolean> =
        Outcome.Ok(true)

    override suspend fun revertMessage(id: String, messageID: String, partID: String?, directory: String?): Outcome<SessionDto> =
        sessions().map { it.firstOrNull() ?: SessionDto(id = id) }

    override suspend fun unrevert(id: String, directory: String?): Outcome<SessionDto> =
        sessions().map { it.firstOrNull() ?: SessionDto(id = id) }

    override suspend fun respondToPermission(id: String, permissionID: String, response: String, directory: String?): Outcome<Boolean> {
        DemoRuntime.answerPermission(permissionID, response)
        return Outcome.Ok(true)
    }

    override suspend fun messages(id: String, limit: Int?, directory: String?): Outcome<List<MessageBundleDto>> {
        val bundles = DemoRuntime.messagesFor(id).takeLast(limit ?: 200).map { (info, parts) ->
            MessageBundleDto(info = info, parts = parts.map { rawPart(it) })
        }
        return Outcome.Ok(bundles)
    }

    private fun rawPart(json: JsonObject): com.opencode.client.opencode.dto.RawPartDto =
        com.opencode.client.core.appJson.decodeFromJsonElement(json)

    override suspend fun message(id: String, messageID: String, directory: String?): Outcome<MessageBundleDto> =
        messages(id).map { list -> list.first { (it.info["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content == messageID } }

    override suspend fun promptAsync(id: String, body: PromptBodyDto, directory: String?): Outcome<Unit> {
        val textPart = body.parts.firstOrNull()
        val text = textPart?.get("text")?.let {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
        } ?: ""
        DemoRuntime.handlePrompt(id, text)
        return Outcome.Ok(Unit)
    }

    override suspend fun runCommand(id: String, command: String, arguments: String, agent: String?, directory: String?): Outcome<MessageBundleDto> {
        DemoRuntime.handlePrompt(id, "/$command $arguments")
        // The response arrives via events; provide a placeholder bundle.
        return Outcome.Ok(placeholderBundle(id))
    }

    private fun placeholderBundle(sessionId: String): MessageBundleDto = MessageBundleDto(
        info = kotlinx.serialization.json.buildJsonObject {
            put("id", "cmd-$sessionId"); put("sessionID", sessionId); put("role", "user")
        },
        parts = emptyList()
    )

    override suspend fun runShell(id: String, agent: String, command: String, directory: String?): Outcome<MessageBundleDto> {
        val out = when {
            command.startsWith("ls") -> "src\nbuild.gradle.kts\nREADME.md"
            command.startsWith("pwd") -> DemoRuntime.DEMO_PROJECT
            command.startsWith("echo") -> command.removePrefix("echo ").trim('"', '\'')
            else -> "demo shell: '$command' executed"
        }
        return Outcome.Ok(
            MessageBundleDto(
                info = kotlinx.serialization.json.buildJsonObject {
                    put("id", "sh-${System.nanoTime()}"); put("sessionID", id); put("role", "assistant")
                },
                parts = listOf(
                    com.opencode.client.opencode.dto.RawPartDto(
                        id = "shp-${System.nanoTime()}",
                        type = "text",
                        text = out
                    )
                )
            )
        )
    }

    override suspend fun findText(pattern: String, directory: String?): Outcome<List<TextMatchDto>> = Outcome.Ok(
        listOf(
            TextMatchDto(
                path = com.opencode.client.opencode.dto.PathTextDto("src/auth/LoginService.kt"),
                lines = com.opencode.client.opencode.dto.LinesDto("  fun lockIfExceeded(key: String) {"),
                line_number = 14, absolute_offset = 380
            ),
            TextMatchDto(
                path = com.opencode.client.opencode.dto.PathTextDto("src/ui/LoginForm.tsx"),
                lines = com.opencode.client.opencode.dto.LinesDto("<button onPointerDown={submit}>Sign in</button>"),
                line_number = 57, absolute_offset = 1902
            )
        )
    )

    override suspend fun findFiles(query: String, limit: Int, directory: String?): Outcome<List<String>> = Outcome.Ok(
        listOf(
            "src/auth/LoginService.kt",
            "src/auth/LoginServiceTest.kt",
            "src/ui/LoginForm.tsx",
            "src/main.rs",
            "gradle/libs.versions.toml"
        ).filter { it.contains(query, ignoreCase = true) || query.isBlank() }.take(limit)
    )

    override suspend fun listFiles(path: String, directory: String?): Outcome<List<FileNodeDto>> {
        val p = path.trimEnd('/')
        val tree: Map<String, List<FileNodeDto>> = mapOf(
            "." to listOf(dir("src"), dir("gradle"), file("build.gradle.kts"), file("README.md"), ignored("node_modules")),
            "src" to listOf(dir("src/auth"), dir("src/ui"), dir("src/tools")),
            "src/auth" to listOf(file("src/auth/LoginService.kt"), file("src/auth/LoginServiceTest.kt")),
            "src/ui" to listOf(file("src/ui/LoginForm.tsx"), file("src/ui/theme.css")),
            "src/tools" to listOf(file("src/tools/index.js")),
            "gradle" to listOf(file("gradle/libs.versions.toml"))
        )
        val listing = tree[p] ?: tree["."]!!
        return Outcome.Ok(listing)
    }

    private fun dir(path: String) = FileNodeDto(name = path.substringAfterLast('/'), path = path, type = "directory")
    private fun file(path: String) = FileNodeDto(name = path.substringAfterLast('/'), path = path, type = "file")
    private fun ignored(path: String) = FileNodeDto(name = path.substringAfterLast('/'), path = path, type = "directory", ignored = true)

    override suspend fun readFile(path: String, directory: String?): Outcome<FileContentDto> {
        if (path.endsWith(".kt")) {
            return Outcome.Ok(
                FileContentDto(
                    type = "text",
                    content = BEFORE_LOGIN_CONTENT,
                    diff = SAMPLE_PATCH.takeIf { path.endsWith("LoginService.kt") }
                )
            )
        }
        return Outcome.Ok(FileContentDto(type = "text", content = "// demo content for $path\n"))
    }

    override suspend fun fileStatus(directory: String?): Outcome<List<GitFileDto>> = Outcome.Ok(
        listOf(GitFileDto("src/auth/LoginService.kt", added = 18, removed = 7, status = "modified"))
    )

    override suspend fun mcpStatus(directory: String?): Outcome<Map<String, McpStatusDto>> = Outcome.Ok(
        mapOf(
            "filesystem" to McpStatusDto(status = "connected"),
            "github-tools" to McpStatusDto(status = "connected"),
            "flaky-server" to McpStatusDto(status = "failed", error = "connect ECONNREFUSED 127.0.0.1:9228")
        )
    )

    override suspend fun lspStatus(directory: String?): Outcome<List<LspStatusDto>> = Outcome.Ok(
        listOf(LspStatusDto(id = "typescript-language-server", name = "typescript-language-server", root = DemoRuntime.DEMO_PROJECT, status = "connected"))
    )

    override suspend fun formatterStatus(directory: String?): Outcome<List<FormatterStatusDto>> = Outcome.Ok(
        listOf(FormatterStatusDto(name = "prettier", extensions = listOf(".ts", ".tsx"), enabled = true))
    )

    override fun decodeUserMessage(json: JsonObject): UserMessageDto? = null
    override fun decodeAssistantMessage(json: JsonObject): AssistantMessageDto? = null

    private const val BEFORE_LOGIN_CONTENT = """package auth

class LoginService {

  private val attempts = ConcurrentHashMap<String, Int>()

  fun login(user: String, pass: String): Token {
    lockIfExceeded(user)
    val token = api.authenticate(user, pass)
    attempts[user] = 0
    audit.log("login", user)
    return token
  }

  fun lockIfExceeded(key: String) {
    if (attempts.merge(key, 0, Int::plus) >= MAX_ATTEMPTS) {
      throw AccountLockedException(key)
    }
  }

  companion object {
    private const val MAX_ATTEMPTS = 5
  }
}
"""

    private const val SAMPLE_PATCH = """@@ -1,7 +1,12 @@
 class LoginService {
+
+  private val attempts = ConcurrentHashMap<String, Int>()
 
   fun login(user: String, pass: String): Token {
+    lockIfExceeded(user)
     val token = api.authenticate(user, pass)
+    attempts[user] = 0
     audit.log("login", user)
     return token
   }
"""
}
