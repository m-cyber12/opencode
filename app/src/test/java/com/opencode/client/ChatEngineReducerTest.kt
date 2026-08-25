package com.opencode.client

import com.opencode.client.core.Outcome
import com.opencode.client.data.repo.PromptAttachment
import com.opencode.client.data.repo.SessionsGateway
import com.opencode.client.domain.DiffFileInfo
import com.opencode.client.domain.ModelRef
import com.opencode.client.domain.PermissionRequest
import com.opencode.client.domain.Role
import com.opencode.client.domain.RunStatus
import com.opencode.client.domain.TodoItem
import com.opencode.client.domain.TextPartUi
import com.opencode.client.domain.ToolPartUi
import com.opencode.client.domain.ToolStateKind
import com.opencode.client.domain.UiMessage
import com.opencode.client.engine.ChatEngine
import com.opencode.client.opencode.dto.PermissionRequestDto
import com.opencode.client.opencode.event.OpenCodeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reducer contract tests for the event-driven chat state machine. Events are injected exactly as
 * the SSE pipeline would deliver them (including duplicates and out-of-order arrivals).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatEngineReducerTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeSessions : SessionsGateway {
        var sendCalls = AtomicInteger(0)

        override suspend fun messages(sessionId: String) =
            Outcome.Ok(emptyList<UiMessage>())

        override suspend fun todo(sessionId: String) =
            Outcome.Ok(emptyList<TodoItem>())

        override suspend fun statuses() =
            Outcome.Ok(emptyMap<String, RunStatus>())

        override suspend fun sessionDiff(sessionId: String) =
            Outcome.Ok(emptyList<DiffFileInfo>())

        override suspend fun sendPrompt(
            sessionId: String,
            text: String,
            attachments: List<PromptAttachment>,
            model: ModelRef?,
            agent: String?
        ): Outcome<Unit> {
            sendCalls.incrementAndGet()
            return Outcome.Ok(Unit)
        }

        override suspend fun respondToPermission(request: PermissionRequest, response: String) =
            Outcome.Ok(true)

        override suspend fun abortSession(sessionId: String) = Outcome.Ok(true)
    }

    private lateinit var fakeSessions: FakeSessions

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fakeSessions = FakeSessions()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newEngine(sessionId: String = "ses-1"): ChatEngine = ChatEngine(
        sessionId = sessionId,
        sessions = fakeSessions,
        externalEvents = emptyFlow(),
        resyncTick = emptyFlow(),
        scope = CoroutineScope(dispatcher)
    )

    @Test
    fun `streaming text parts accumulate into one assistant message`() = runTest(dispatcher) {
        val engine = newEngine()
        engine.onEvent(messageUpdated("m1", role = "assistant"))
        engine.onEvent(partText("p1", "m1", "Hello "))
        engine.onEvent(partText("p1", "m1", "Hello world"))
        engine.onEvent(OpenCodeEvent.SessionIdle("ses-1"))

        val state = engine.state.value
        assertEquals(1, state.messages.size)
        assertEquals(
            "Hello world",
            (state.messages[0].parts.single() as TextPartUi).text
        )
        assertFalse(state.messages[0].isStreaming)
        assertEquals(RunStatus.Idle, state.runStatus)
    }

    @Test
    fun `tool part transitions pending running completed`() = runTest(dispatcher) {
        val engine = newEngine()
        engine.onEvent(partTool("pt", "m1", "bash", "pending"))
        engine.onEvent(partTool("pt", "m1", "bash", "running"))
        engine.onEvent(partTool("pt", "m1", "bash", "completed", output = "done"))

        val tool = engine.state.value.messages
            .single().parts.filterIsInstance<ToolPartUi>().single()
        assertEquals(ToolStateKind.COMPLETED, tool.state)
        assertEquals("done", tool.output)
    }

    @Test
    fun `duplicate delivery is idempotent`() = runTest(dispatcher) {
        val engine = newEngine()
        repeat(3) {
            engine.onEvent(messageUpdated("m1", role = "assistant"))
            engine.onEvent(partText("p1", "m1", "same text"))
        }

        val state = engine.state.value
        assertEquals(1, state.messages.size)
        assertEquals(1, state.messages[0].parts.size)
        assertEquals("same text", (state.messages[0].parts.single() as TextPartUi).text)
    }

    @Test
    fun `optimistic local prompt replaced by server confirmation`() = runTest(dispatcher) {
        val engine = newEngine()
        val accepted = kotlinx.coroutines.withContext(dispatcher) {
            engine.send("fix the bug", emptyList(), null, null)
        }
        assertTrue(accepted)
        assertEquals(1, fakeSessions.sendCalls.get())
        assertTrue(engine.state.value.pendingLocalId != null)
        assertEquals(Role.USER, engine.state.value.messages.last().role)

        // Server confirms the user message with a real ID.
        engine.onEvent(messageUpdated("srv-u1", role = "user"))
        engine.onEvent(partText("srv-p1", "srv-u1", "fix the bug"))

        val after = engine.state.value
        assertNull(after.pendingLocalId)
        assertTrue(after.messages.none { it.id.startsWith("local-") })
        assertEquals("fix the bug", (after.messages.last().parts.first() as TextPartUi).text)
    }

    @Test
    fun `permission gate opens and closes via replied event`() = runTest(dispatcher) {
        val engine = newEngine()
        engine.onEvent(permissionUpdated("perm-1", "ses-1"))
        assertEquals("perm-1", engine.state.value.pendingPermission?.id)

        engine.onEvent(OpenCodeEvent.PermissionReplied("ses-1", "perm-1", "once"))
        assertNull(engine.state.value.pendingPermission)
    }

    @Test
    fun `foreign-session events are ignored entirely`() = runTest(dispatcher) {
        val engine = newEngine(sessionId = "ses-mine")
        engine.onEvent(partText("px", "om", "foreign", sessionId = "ses-other"))
        engine.onEvent(OpenCodeEvent.SessionIdle("ses-other"))

        assertTrue(engine.state.value.messages.isEmpty())
        assertEquals(RunStatus.Idle, engine.state.value.runStatus)
    }

    @Test
    fun `unknown events never disturb state`() = runTest(dispatcher) {
        val engine = newEngine()
        engine.onEvent(OpenCodeEvent.Unknown("future.thing.v99", null))
        engine.onEvent(OpenCodeEvent.Ignored("tui.toast.show"))

        assertTrue(engine.state.value.messages.isEmpty())
        assertNull(engine.state.value.error)
    }

    @Test
    fun `abort error is informational not an error banner`() = runTest(dispatcher) {
        val engine = newEngine()
        engine.onEvent(messageUpdated("m1", role = "assistant"))
        engine.onEvent(partText("p1", "m1", "partial"))
        engine.onEvent(
            OpenCodeEvent.SessionError(
                sessionID = "ses-1",
                errorName = "MessageAbortedError",
                errorMessage = "aborted by user",
                raw = null
            )
        )

        assertNull(engine.state.value.error)
        assertEquals(RunStatus.Idle, engine.state.value.runStatus)
        assertFalse(engine.state.value.messages.single().isStreaming)
    }

    @Test
    fun `todo list replaces wholesale on each update`() = runTest(dispatcher) {
        val engine = newEngine()
        engine.onEvent(todos(listOf(TodoItem("t1", "one", "pending", "high"))))
        engine.onEvent(todos(listOf(
            TodoItem("t1", "one", "completed", "high"),
            TodoItem("t2", "two", "in_progress", "low")
        )))

        val todosNow = engine.state.value.todos
        assertEquals(2, todosNow.size)
        assertTrue(todosNow.first { it.id == "t1" }.done)
    }

    // ---- event factories -------------------------------------------------------

    private fun messageUpdated(id: String, role: String): OpenCodeEvent.MessageUpdated {
        val info = kotlinx.serialization.json.buildJsonObject {
            put("id", id)
            put("sessionID", "ses-1")
            put("role", role)
            put("time", kotlinx.serialization.json.buildJsonObject { put("created", 1L) })
        }
        return OpenCodeEvent.MessageUpdated(info)
    }

    private fun partText(
        id: String,
        messageId: String,
        text: String,
        sessionId: String = "ses-1"
    ): OpenCodeEvent.MessagePartUpdated = OpenCodeEvent.MessagePartUpdated(
        part = raw(id, messageId, sessionId, "text").copy(text = text),
        delta = null
    )

    private fun partTool(
        id: String,
        messageId: String,
        tool: String,
        status: String,
        output: String? = null,
        sessionId: String = "ses-1"
    ): OpenCodeEvent.MessagePartUpdated = OpenCodeEvent.MessagePartUpdated(
        part = raw(id, messageId, sessionId, "tool").copy(
            tool = tool,
            state = com.opencode.client.opencode.dto.ToolStateDto(
                status = status,
                input = kotlinx.serialization.json.buildJsonObject { },
                output = output
            )
        ),
        delta = null
    )

    private fun raw(id: String, messageId: String, sessionId: String, type: String) =
        com.opencode.client.opencode.dto.RawPartDto(
            id = id, sessionID = sessionId, messageID = messageId, type = type
        )

    private fun permissionUpdated(id: String, sessionId: String): OpenCodeEvent.PermissionUpdated =
        OpenCodeEvent.PermissionUpdated(
            PermissionRequestDto(
                id = id,
                type = "bash",
                title = "run thing",
                pattern = listOf("*"),
                sessionID = sessionId,
                messageID = "m",
                metadata = null,
                time = com.opencode.client.opencode.dto.CreatedTimeDto(1L)
            )
        )

    private fun todos(items: List<TodoItem>): OpenCodeEvent.TodoUpdated =
        OpenCodeEvent.TodoUpdated(
            "ses-1",
            items.map {
                com.opencode.client.opencode.dto.TodoDto(it.id, it.content, it.status, it.priority)
            }
        )
}
