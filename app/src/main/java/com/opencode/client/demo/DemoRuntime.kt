package com.opencode.client.demo

import kotlinx.coroutines.CompletableDeferredimport kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The demo runtime simulates an OpenCode server end-to-end.
 *
 * It emits REAL SSE JSON frames consumed by the production event parser, so the entire app
 * pipeline (transport -> parser -> reducer -> UI) is exercised without a live server.
 */
object DemoRuntime {

    val DEMO_VERSION = "v1.0.0-demo"
    const val DEMO_PROJECT = "/home/dev/opencode-gui"

    private val clock = AtomicLong(System.currentTimeMillis())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    internal val emitter = MutableSharedFlow<String>(extraBufferCapacity = 1024)

    data class DemoSession(
        val id: String,
        val title: String,
        val createdAt: Long,
        val updatedAt: Long
    )

    private val sessions = LinkedHashMap<String, DemoSession>()
    /** sessionId -> messageId -> {"info":..., "parts":[...]} JSON snapshots. */
    private val transcripts = ConcurrentHashMap<String, LinkedHashMap<String, JsonObject>>()
    /** sessionId -> diffs produced during the demo. */
    private val diffs = ConcurrentHashMap<String, JsonArray>()
    private val busy = ConcurrentHashMap.newKeySet<String>()

    private var seeded = false

    @Synchronized
    fun seedInitialSessions() {
        if (seeded) return
        seeded = true
        val now = System.currentTimeMillis()
        addSessionInternal("ses-seeded-1", "Fix authentication flow", now - 86_400_000L * 2, now - 86_400_000L * 2 + 600_000)
        addSessionInternal("ses-seeded-2", "Refactor API client", now - 86_400_000L, now - 86_400_000L + 300_000)

        // Seed transcript for session 1 so resuming shows history.
        val s1 = "ses-seeded-1"
        recordMessage(s1, buildJsonObject {
            put("id", "msg-s1-u1"); put("sessionID", s1); put("role", "user")
            put("time", timeJson(now - 86_400_000L * 2, null))
            put("agent", "build")
            put("model", modelJson("anthropic", "claude-sonnet-4"))
        })
        appendPart(s1, "msg-s1-u1", buildTextPart("$s1-p0", "The login button does nothing on mobile Safari. Can you investigate?", false))
        recordMessage(s1, buildJsonObject {
            put("id", "msg-s1-a1"); put("sessionID", s1); put("role", "assistant")
            put("time", timeJson(now - 86_400_000L * 2, now - 86_400_000L * 2 + 90_000))
            put("parentID", "msg-s1-u1"); put("modelID", "claude-sonnet-4"); put("providerID", "anthropic")
            put("mode", "build"); put("cost", 0.021)
            put("tokens", tokensJson(4210, 512))
        })
        appendPart(s1, "msg-s1-a1", buildTextPart(
            "$s1-p1",
            "Found it. The login handler was bound to `onClick` without a touch fallback.\n\nI patched `LoginForm.tsx` to use **pointer events** and added a regression test:",
            false
        ))
        appendPart(s1, "msg-s1-a1", buildToolPartCompleted(
            "$s1-p2", "call-1", "edit",
            buildJsonObject { put("filePath", "src/ui/LoginForm.tsx") },
            "Modified src/ui/LoginForm.tsx",
            "patched onClick -> onPointerDown; added touch-action CSS",
            buildJsonObject {
                put("additions", 6); put("deletions", 2); put("exitCode", 0)
            }
        ))
    }

    // ------------------------------------------------------------------ public triggers

    fun handleCreateSession(id: String, title: String?): DemoSession {
        val s = addSessionInternal(id, title ?: "New session", now(), now())
        frame("session.created", buildJsonObject { put("info", sessionJson(s)) })
        return s
    }

    fun handleDeleteSession(id: String) {
        synchronized(sessions) { sessions.remove(id) }
        frame("session.deleted", buildJsonObject {
            put("info", buildJsonObject {
                put("id", id); put("projectID", "p"); put("directory", DEMO_PROJECT)
                put("title", ""); put("version", "")
                put("time", buildJsonObject { put("created", 0); put("updated", 0) })
            })
        })
    }

    fun handleRenameSession(id: String, title: String) {
        synchronized(sessions) {
            sessions[id]?.let {
                sessions[id] = it.copy(title = title, updatedAt = now())
                frame("session.updated", buildJsonObject { put("info", sessionJson(it.copy(title = title))) })
            }
        }
    }

    fun handlePrompt(sessionId: String, text: String) {
        val counter = promptsInSession.getOrPut(sessionId) { AtomicInteger(0) }
        val n = counter.incrementAndGet()
        scope.launch {
            when {
                text.trim().startsWith("/boom") -> playErrorScenario(sessionId, text)
                n == 1 -> playAgenticScenario(sessionId, text)
                else -> playStreamingScenario(sessionId, text, long = n % 3 == 0)
            }
        }
    }

    private val promptsInSession = ConcurrentHashMap<String, AtomicInteger>()

    // ------------------------------------------------------------------ scenarios

    private suspend fun playAgenticScenario(sessionId: String, prompt: String) {
        val uid = "msg-$sessionId-u${promptsInSession[sessionId]!!.get()}"
        val aid = "msg-$sessionId-a${promptsInSession[sessionId]!!.get()}"

        setStatusBusy(sessionId)
        emitUserMessage(sessionId, uid, prompt)

        emitAssistantStart(sessionId, aid, uid)
        appendPartLive(sessionId, aid, buildStepStart("$aid-ps"))

        // Reasoning flicker
        val reasonId = "$aid-pr"
        appendPartLive(sessionId, aid, buildReasoning(reasonId, ""))
        updatePartLive(sessionId, aid, buildReasoning(reasonId, "The user reports an issue. Let me inspect "))
        updatePartLive(sessionId, aid, buildReasoning(reasonId, "The user reports an issue. Let me inspect the relevant source files first."))

        // Intro text
        val t1 = "$aid-pt1"
        streamTextIntoPart(sessionId, aid, t1, listOf("Looking into ", "this now. Let me check the ", "**auth module** first."))

        // Read tool
        val readTool = "$aid-tool-read"
        appendPartLive(sessionId, aid, buildToolPartRunning(readTool, "call-r1", "read",
            buildJsonObject { put("filePath", "src/auth/LoginService.kt") }, "Reading src/auth/LoginService.kt"))
        delay(900)
        appendPartLive(sessionId, aid, buildToolPartCompleted(readTool, "call-r1", "read",
            buildJsonObject { put("filePath", "src/auth/LoginService.kt") },
            "Read src/auth/LoginService.kt",
            "class LoginService {\n  fun login(user: String, pass: String): Token {\n    // TODO: rate limiting missing\n    ...\n  }\n}",
            buildJsonObject { put("additions", 0); put("deletions", 0); put("lines", 42) }
        ))

        // Bash tool
        val bashTool = "$aid-tool-bash"
        appendPartLive(sessionId, aid, buildToolPartRunning(bashTool, "call-b1", "bash",
            buildJsonObject { put("command", "./gradlew :auth:test --tests LoginServiceTest") }, "Running tests"))
        delay(1400)
        appendPartLive(sessionId, aid, buildToolPartCompleted(bashTool, "call-b1", "bash",
            buildJsonObject { put("command", "./gradlew :auth:test --tests LoginServiceTest") },
            "Ran ./gradlew :auth:test",
            "> Task :auth:test\n\nLoginServiceTest > rejectsEmptyPassword PASSED\nLoginServiceTest > locksAfterFiveAttempts FAILED\n\n1 test failed",
            buildJsonObject { put("exitCode", 1) }
        ))

        // Permission gate - waits for the user's real answer.
        val permId = "perm-demo-1"
        val answered = CompletableDeferred<String>()
        pendingPermissions[permId] = answered
        frame("permission.updated", buildJsonObject {
            put("permission", buildJsonObject {
                put("id", permId); put("type", "bash")
                put("sessionID", sessionId); put("messageID", aid); put("callID", "call-b2")
                put("title", "Run destructive command")
                put("pattern", JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("git reset --hard HEAD"))))
                put("metadata", buildJsonObject {
                    put("command", "git reset --hard HEAD")
                    put("options", JsonArray(listOf(
                        kotlinx.serialization.json.JsonPrimitive("Allow"),
                        kotlinx.serialization.json.JsonPrimitive("Reject")
                    )))
                })
                put("time", buildJsonObject { put("created", now()) })
            })
        })

        val response = try {
            kotlinx.coroutines.withTimeoutOrNull(120_000) { answered.await() } ?: "reject"
        } finally {
            pendingPermissions.remove(permId)
        }
        frame("permission.replied", buildJsonObject {
            put("sessionID", sessionId); put("permissionID", permId); put("response", response)
        })

        if (response == "reject") {
            streamTextIntoPart(sessionId, aid, "$aid-pt2", listOf(
                "Understood - I will **not** run `git reset --hard`. ",
                "Instead, let me fix the failing test directly."
            ))
        } else {
            appendPartLive(sessionId, aid, buildToolPartCompleted("$aid-tool-git", "call-b2", "bash",
                buildJsonObject { put("command", "git reset --hard HEAD") },
                "Ran git reset --hard HEAD",
                "HEAD is now at 9f2c1ab previous commit",
                buildJsonObject { put("exitCode", 0) }
            ))
            streamTextIntoPart(sessionId, aid, "$aid-pt2", listOf("Reset applied. Now re-applying the safe fix."))
        }

        // Edit file + diff + todos
        val editTool = "$aid-tool-edit"
        appendPartLive(sessionId, aid, buildToolPartRunning(editTool, "call-e1", "edit",
            buildJsonObject { put("filePath", "src/auth/LoginService.kt") }, "Editing src/auth/LoginService.kt"))
        delay(1100)
        appendPartLive(sessionId, aid, buildToolPartCompleted(editTool, "call-e1", "edit",
            buildJsonObject { put("filePath", "src/auth/LoginService.kt") },
            "Modified src/auth/LoginService.kt",
            "Added lockout counter + rate limit",
            buildJsonObject { put("additions", 18); put("deletions", 7); put("exitCode", 0) }
        ))
        emitFileEdited(sessionId)
        emitSessionDiff(sessionId)

        frame("todo.updated", buildJsonObject {
            put("sessionID", sessionId); put("todos", buildJsonArray {
                add(todoJson("t1", "Reproduce failing test", "completed", "high"))
                add(todoJson("t2", "Add lockout counter", "completed", "high"))
                add(todoJson("t3", "Write regression test", "in_progress", "medium"))
                add(todoJson("t4", "Update AGENTS.md notes", "pending", "low"))
            })
        })

        // Wrap-up text
        streamTextIntoPart(sessionId, aid, "$aid-pt3", listOf(
            "\n\nDone. Summary:\n\n",
            "- Added lockout after **5 failed attempts**\n- Fixed `locksAfterFiveAttempts`\n- Tests: `8 passed`\n\n",
            "```kotlin\nfun lockIfExceeded(key: String) {\n  if (attempts[key]!! >= MAX_ATTEMPTS) {\n    lockUntil[key] = now + LOCK_WINDOW\n  }\n}\n```\n\n",
            "Want me to open a PR?"
        ))

        emitAssistantComplete(sessionId, aid)
        setStatusIdle(sessionId)
    }

    private suspend fun playStreamingScenario(sessionId: String, prompt: String, long: Boolean) {
        val n = promptsInSession[sessionId]!!.get()
        val uid = "msg-$sessionId-u$n"
        val aid = "msg-$sessionId-a$n"

        setStatusBusy(sessionId)
        emitUserMessage(sessionId, uid, prompt)
        emitAssistantStart(sessionId, aid, uid)

        val body = if (long) {
            """
            Here's a longer walkthrough of how this area works:

            ## Architecture overview

            The client never talks to model providers directly. Every capability flows through
            the OpenCode **server**:

            ```
            Android UI -> HTTP/SSE -> OpenCode server -> agent loop -> tools
            ```

            ### Why this matters

            | Layer | Responsibility |
            |-------|----------------|
            | UI | presentation only |
            | Server | sessions, permissions, tools |
            | Agent | planning, tool use |

            1. Sessions are authoritative server-side
            2. Events stream over `/global/event`
            3. Permissions pause the agent until answered

            ```python
            def summarize(items):
                # trivial example
                return ", ".join(sorted(items))
            ```

            If you want, I can go deeper on any of these layers next.
            """.trimIndent()
        } else {
            "Quick take: yes - ship it behind a flag, watch the metrics for two days, then remove the flag. Small reversible steps beat big rewrites."
        }

        val pid = "$aid-pt"
        streamTextIntoPart(sessionId, aid, pid, body.chunked(28))

        emitAssistantComplete(sessionId, aid)
        setStatusIdle(sessionId)
    }

    private suspend fun playErrorScenario(sessionId: String, prompt: String) {
        val n = promptsInSession[sessionId]!!.get()
        val uid = "msg-$sessionId-u$n"
        val aid = "msg-$sessionId-a$n"
        setStatusBusy(sessionId)
        emitUserMessage(sessionId, uid, prompt)
        emitAssistantStart(sessionId, aid, uid)
        delay(700)
        frame("session.error", buildJsonObject {
            put("sessionID", sessionId)
            put("error", buildJsonObject {
                put("name", "ProviderAuthError")
                put("data", buildJsonObject {
                    put("providerID", "anthropic")
                    put("message", "Provider authentication expired. Re-authorize anthropic to continue.")
                })
            })
        })
        emitAssistantComplete(sessionId, aid)
        setStatusIdle(sessionId)
    }

    /** Simulates a network drop + recovery to exercise reconnection UX (driven by ServerController). */
    fun simulateConnectionBlip() {
        scope.launch {
            connectionBlipListener?.invoke()
            frame("server.connected", buildJsonObject {})
        }
    }

    @Volatile
    var connectionBlipListener: (() -> Unit)? = null

    // ------------------------------------------------------------------ permission plumbing

    private val pendingPermissions = ConcurrentHashMap<String, CompletableDeferred<String>>()

    fun answerPermission(permissionId: String, response: String) {
        pendingPermissions[permissionId]?.complete(response)
    }

    // ------------------------------------------------------------------ snapshot accessors (for DemoApi)

    @Synchronized
    fun allSessions(): List<DemoSession> = sessions.values.sortedByDescending { it.updatedAt }

    fun getSession(id: String): DemoSession? = synchronized(sessions) { sessions[id] }

    fun messagesFor(sessionId: String): List<Pair<JsonObject, List<JsonObject>>> =
        transcripts[sessionId]?.values?.map { bundle ->
            val info = bundle["info"] as JsonObject
            val parts = (bundle["parts"] as? JsonArray)?.map { it as JsonObject } ?: emptyList()
            info to parts
        } ?: emptyList()

    fun statusOf(sessionId: String): String = if (busy.contains(sessionId)) "busy" else "idle"

    fun diffsFor(sessionId: String): JsonArray = diffs[sessionId] ?: buildJsonArray {}

    // ------------------------------------------------------------------ emit helpers

    private fun addSessionInternal(id: String, title: String, created: Long, updated: Long): DemoSession {
        val s = DemoSession(id, title, created, updated)
        synchronized(sessions) { sessions[id] = s }
        return s
    }

    private fun recordMessage(sessionId: String, info: JsonObject) {
        val map = transcripts.getOrPut(sessionId) { LinkedHashMap() }
        synchronized(map) { map[info.requireId()] = buildJsonObject {
            put("info", info); put("parts", buildJsonArray {})
        } }
    }

    private fun appendPart(sessionId: String, messageId: String, part: JsonObject) {
        val map = transcripts.getOrPut(sessionId) { LinkedHashMap() }
        synchronized(map) {
            val existing = map[messageId] ?: return
            val parts = existing["parts"] as? JsonArray ?: buildJsonArray {}
            map[messageId] = buildJsonObject {
                put("info", existing["info"] as JsonObject)
                put("parts", buildJsonArray {
                    parts.forEach { add(it) }; add(part)
                })
            }
        }
    }

    private fun JsonObject.requireId(): String =
        (this["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""

    private fun frame(type: String, properties: JsonObject) {
        val json = buildJsonObject {
            put("type", type)
            put("properties", properties)
        }
        emitter.tryEmit(json.toString())
    }

    private fun now(): Long = clock.updateAndGet { it + 17 }

    private fun sessionJson(s: DemoSession) = buildJsonObject {
        put("id", s.id); put("projectID", "proj-demo"); put("directory", DEMO_PROJECT)
        put("title", s.title); put("version", DEMO_VERSION)
        put("time", buildJsonObject { put("created", s.createdAt); put("updated", s.updatedAt) })
    }

    private fun setStatusBusy(sessionId: String) {
        busy.add(sessionId)
        frame("session.status", buildJsonObject {
            put("sessionID", sessionId)
            put("status", buildJsonObject { put("type", "busy") })
        })
    }

    private fun setStatusIdle(sessionId: String) {
        busy.remove(sessionId)
        frame("session.idle", buildJsonObject { put("sessionID", sessionId) })
    }

    private fun emitUserMessage(sessionId: String, id: String, text: String) {
        val info = buildJsonObject {
            put("id", id); put("sessionID", sessionId); put("role", "user")
            put("time", timeJson(now(), null))
            put("agent", "build")
            put("model", modelJson("anthropic", "claude-sonnet-4"))
        }
        recordMessage(sessionId, info)
        frame("message.updated", buildJsonObject { put("info", info) })
        val part = buildTextPart("$id-p0", text, false)
        frame("message.part.updated", buildJsonObject { put("part", part) })
        appendPart(sessionId, id, part)
    }

    private fun emitAssistantStart(sessionId: String, id: String, parentId: String) {
        val info = buildJsonObject {
            put("id", id); put("sessionID", sessionId); put("role", "assistant")
            put("time", timeJson(now(), null))
            put("parentID", parentId); put("modelID", "claude-sonnet-4"); put("providerID", "anthropic")
            put("mode", "build"); put("cost", 0.004)
            put("tokens", tokensJson(1200, 10))
        }
        recordMessage(sessionId, info)
        frame("message.updated", buildJsonObject { put("info", info) })
    }

    private fun emitAssistantComplete(sessionId: String, id: String) {
        val info = buildJsonObject {
            put("id", id); put("sessionID", sessionId); put("role", "assistant")
            put("time", timeJson(clock.get(), now()))
            put("parentID", ""); put("modelID", "claude-sonnet-4"); put("providerID", "anthropic")
            put("mode", "build"); put("cost", 0.032)
            put("tokens", tokensJson(5200, 640))
        }
        frame("message.updated", buildJsonObject { put("info", info) })
        // Update stored snapshot completion time.
        val map = transcripts[sessionId]
        synchronized(map ?: return) {
            map[id]?.let { bundle ->
                map[id] = buildJsonObject {
                    put("info", info); put("parts", bundle["parts"] as? JsonArray ?: buildJsonArray {})
                }
            }
        }
    }

    private fun appendPartLive(sessionId: String, messageId: String, part: JsonObject) {
        frame("message.part.updated", buildJsonObject { put("part", part) })
        appendPart(sessionId, messageId, part)
    }

    private fun updatePartLive(sessionId: String, messageId: String, part: JsonObject) {
        frame("message.part.updated", buildJsonObject { put("part", part) })
        replacePart(sessionId, messageId, part)
    }

    private fun replacePart(sessionId: String, messageId: String, part: JsonObject) {
        val map = transcripts.getOrPut(sessionId) { LinkedHashMap() }
        synchronized(map) {
            val existing = map[messageId] ?: return
            val parts = (existing["parts"] as? JsonArray)?.toMutableList() ?: mutableListOf()
            val idx = parts.indexOfFirst { (it as JsonObject).requireId() == part.requireId() }
            if (idx >= 0) parts[idx] = part else parts.add(part)
            map[messageId] = buildJsonObject {
                put("info", existing["info"] as JsonObject)
                put("parts", buildJsonArray { parts.forEach { add(it) } })
            }
        }
    }

    private suspend fun streamTextIntoPart(sessionId: String, messageId: String, partId: String, chunks: List<String>) {
        var cumulative = ""
        chunks.forEachIndexed { i, chunk ->
            cumulative += chunk
            val part = buildTextPart(partId, cumulative, false)
            frameWithDelta(part, chunk)
            delay(if (chunk.length > 60) 90L else 55L)
            if (i % 5 == 4 || i == chunks.lastIndex) replacePart(sessionId, messageId, part)
        }
        replacePart(sessionId, messageId, buildTextPart(partId, cumulative, false))
    }

    private fun frameWithDelta(part: JsonObject, delta: String) {
        val json = buildJsonObject {
            put("type", "message.part.updated")
            put("properties", buildJsonObject { put("part", part); put("delta", delta) })
        }
        emitter.tryEmit(json.toString())
    }

    private fun emitFileEdited(sessionId: String) {
        frame("file.edited", buildJsonObject { put("file", "src/auth/LoginService.kt") })
    }

    private fun emitSessionDiff(sessionId: String) {
        val arr = buildJsonArray {
            add(fileDiffJson(
                "src/auth/LoginService.kt",
                BEFORE_LOGIN,
                AFTER_LOGIN,
                18, 7
            ))
        }
        diffs[sessionId] = arr
        frame("session.diff", buildJsonObject { put("sessionID", sessionId); put("diff", arr) })
    }

    // ------------------------------------------------------------------ JSON part factories

    private fun timeJson(created: Long, completed: Long?): JsonObject = buildJsonObject {
        put("created", created)
        completed?.let { put("completed", it) }
    }

    private fun modelJson(provider: String, model: String): JsonObject = buildJsonObject {
        put("providerID", provider); put("modelID", model)
    }

    private fun tokensJson(input: Int, output: Int): JsonObject = buildJsonObject {
        put("input", input); put("output", output); put("reasoning", 0)
        put("cache", buildJsonObject { put("read", 0); put("write", 0) })
    }

    private fun buildTextPart(id: String, text: String, synthetic: Boolean): JsonObject = buildJsonObject {
        put("id", id); put("type", "text"); put("text", text)
        if (synthetic) put("synthetic", true)
    }

    private fun buildReasoning(id: String, text: String): JsonObject = buildJsonObject {
        put("id", id); put("type", "reasoning"); put("text", text)
    }

    private fun buildStepStart(id: String): JsonObject = buildJsonObject {
        put("id", id); put("type", "step-start")
    }

    private fun buildToolPartRunning(id: String, callId: String, tool: String, input: JsonObject, title: String): JsonObject =
        buildJsonObject {
            put("id", id); put("type", "tool"); put("tool", tool); put("callID", callId)
            put("state", buildJsonObject {
                put("status", "running"); put("input", input); put("title", title)
                put("time", buildJsonObject { put("start", now()) })
            })
        }

    private fun buildToolPartCompleted(
        id: String, callId: String, tool: String, input: JsonObject,
        title: String, output: String, metadata: JsonObject?
    ): JsonObject = buildJsonObject {
        put("id", id); put("type", "tool"); put("tool", tool); put("callID", callId)
        put("state", buildJsonObject {
            put("status", "completed"); put("input", input); put("title", title); put("output", output)
            metadata?.let { put("metadata", it) }
            put("time", buildJsonObject { put("start", now()); put("end", now()) })
        })
    }

    private fun todoJson(id: String, content: String, status: String, priority: String): JsonObject =
        buildJsonObject {
            put("id", id); put("content", content); put("status", status); put("priority", priority)
        }

    private fun fileDiffJson(file: String, before: String, after: String, add: Int, del: Int): JsonObject =
        buildJsonObject {
            put("file", file); put("before", before); put("after", after)
            put("additions", add); put("deletions", del)
        }

    private const val BEFORE_LOGIN = """class LoginService {
  fun login(user: String, pass: String): Token {
    // TODO: rate limiting missing
    val token = api.authenticate(user, pass)
    audit.log("login", user)
    return token
  }
}"""

    private const val AFTER_LOGIN = """class LoginService {

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
}"""
}
