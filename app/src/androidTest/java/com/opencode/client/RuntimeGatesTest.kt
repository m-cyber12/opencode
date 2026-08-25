package com.opencode.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.opencode.client.core.appJson
import com.opencode.client.core.network.AuthInterceptor
import com.opencode.client.core.network.Http
import com.opencode.client.core.native.NativeArtifacts
import com.opencode.client.opencode.dto.HealthDto
import com.opencode.client.runtime.EmbeddedRuntime
import com.opencode.client.runtime.RootfsBootstrap
import com.opencode.client.runtime.RuntimeEnv
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * PHASE A EMPIRICAL GATES.
 *
 * Nothing below assumes prior-art claims hold: every gate executes the real component chain on
 * this emulator/device at the real target API level. A FAIL here is a STOP for the project until
 * that exact failure is investigated - no mocks, no remote fallbacks (per directive).
 *
 * Output contract consumed by CI:
 *   GATE <id> name="<name>" result=PASS|FAIL detail="<...>"
 */
@RunWith(AndroidJUnit4::class)
class RuntimeGatesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private data class GateResult(val id: String, val name: String, val pass: Boolean, val detail: String)
    private val results = CopyOnWriteArrayList<GateResult>()

    private lateinit var runtime: EmbeddedRuntime
    private var baseUrl = ""
    private var sessionId = ""

    // -------------------------------------------------------------------------- driver

    @Test
    fun allGates() {
        if (NativeArtifacts.resolve(context) !is NativeArtifacts.Status.Ready) {
            println("GATE SKIP name=\"runtime bundle not placed\" result=FAIL detail=\"CI must place jniLibs+assets before build\"")
            throw AssertionError("Runtime bundle missing - did the CI placement step run?")
        }

        runGate("G1", "Android -> proot -> Alpine shell") { g1_alpineShell() }
        runGate("G2", "Android -> proot -> Bun") { g2_bun() }
        runGate("G3", "Android -> proot -> OpenCode server") { g3_server() }
        runGate("G4", "OpenCode -> Bash/Git/filesystem") { g4_tools() }
        runGate("G5", "OpenCode -> MCP stdio child process") { g5_mcp() }
        runGate("G6", "streaming/SSE") { g6_sse() }
        runGate("G7", "crash/restart") { g7_crashRestart() }
        runGate("G8", "app restart/session recovery") { g8_sessionRecovery() }

        printSummary()
        val failed = results.filterNot { it.pass }
        assertTrue(
            "GATES FAILED: ${failed.joinToString("; ") { "${it.id} ${it.detail}" }}",
            failed.isEmpty()
        )
    }

    // -------------------------------------------------------------------------- gates

    private fun g1_alpineShell() {
        runtime = EmbeddedRuntime(context, autoRestart = true)
        runtime.bootstrapNow()
        val out = runGuest("/bin/sh -lc 'echo ALPINE_OK && uname -m && id -u'")
        assertTrue("stdout missing ALPINE_OK: $out", out.stdout.contains("ALPINE_OK"))
        assertTrue("guest arch not aarch64: $out", out.stdout.contains("aarch64"))
    }

    private fun g2_bun() {
        val out = runGuest("/bin/sh -lc 'bun --version && bun -e \"console.log(40+2)\"'")
        assertTrue("bun version missing: $out", Regex("""\d+\.\d+\.\d+""").containsMatchIn(out.stdout))
        assertTrue("bun eval/JIT broken: $out", out.stdout.contains("42"))
    }

    private fun g3_server() {
        runBlocking {
            withTimeout(300_000) { baseUrl = runtime.start() } // start() asserts /global/health internally
        }
        val health = appJson.decodeFromString(
            HealthDto.serializer(),
            getRaw("$baseUrl/global/health")
        )
        assertTrue("health not healthy", health.healthy)
        assertTrue("version missing", health.version.isNotBlank())
    }

    private fun g4_tools() {
        sessionId = createSession(title = "phase-a-gates")
        val cmd = listOf(
            "echo GATE4_OK",
            "uname -m",
            "git --version",
            "echo hello-fs > /home/opencode/project/g4.txt",
            "cat /home/opencode/project/g4.txt"
        ).joinToString(" && ")
        val output = shellCommand(sessionId, cmd)
        assertTrue("bash echo missing: $output", output.contains("GATE4_OK"))
        assertTrue("uname mismatch: $output", output.contains("aarch64"))
        assertTrue("real git missing: $output", Regex("""git version \d+""").containsMatchIn(output))
        assertTrue("filesystem write/read missing: $output", output.contains("hello-fs"))
    }

    private fun g5_mcp() {
        // The image's opencode.json registers MCP server "gate5" via `node mcp-server.mjs`.
        // PASS requires OpenCode to spawn that node CHILD PROCESS and complete a stdio
        // handshake - i.e. child-process creation works inside the guest.
        val deadline = System.currentTimeMillis() + 90_000
        var lastBody = ""
        while (System.currentTimeMillis() < deadline) {
            lastBody = getRaw("$baseUrl/mcp")
            if (lastBody.contains("\"connected\"")) break
            Thread.sleep(1500)
        }
        assertTrue(
            "MCP stdio server 'gate5' never connected; last=$lastBody",
            lastBody.contains("gate5") && lastBody.contains("\"connected\"")
        )
    }

    private fun g6_sse() {
        val sseClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // bounds each line-read; events fire immediately below
            .build()

        var sawEvent: String? = null
        sseClient.newCall(
            Request.Builder().url("$baseUrl/global/event")
                .header("Accept", "text/event-stream").build()
        ).execute().use { response ->
            assertTrue("SSE endpoint status ${response.code}", response.isSuccessful)
            val trigger = Thread { runCatching { createSession("sse-trigger-${System.currentTimeMillis()}") } }
            trigger.start()

            val source = response.body!!.source()
            val deadline = System.currentTimeMillis() + 55_000
            while (System.currentTimeMillis() < deadline && sawEvent == null) {
                val line = try {
                    source.readUtf8Line() ?: break
                } catch (_: java.io.IOException) {
                    break // read timeout with no event = failure surfaced by sawEvent==null
                }
                if (line.startsWith("data:") && line.contains("session.created")) {
                    sawEvent = line.take(200)
                }
            }
        }
        assertTrue("no session.created observed on /global/event", sawEvent != null)
    }

    private fun g7_crashRestart() {
        runtime.crashForTesting() // SIGKILL semantics via watchdog-visible path

        // Worst case: restart backoff (~30s) + cold health timeout (240s) → allow 320s.
        val deadline = System.currentTimeMillis() + 320_000
        while (System.currentTimeMillis() < deadline) {
            if (runtime.state.value is EmbeddedRuntime.State.Healthy) break
            Thread.sleep(500)
        }
        val finalState = runtime.state.value
        assertTrue("runtime not healthy after crash: $finalState", finalState is EmbeddedRuntime.State.Healthy)
        baseUrl = runtime.baseUrl!!

        // State lives in the guest filesystem, so it must survive the crash.
        val list = getRaw("$baseUrl/session")
        assertTrue("sessions lost after crash: ${list.take(140)}", list.contains(sessionId))
    }

    private fun g8_sessionRecovery() {
        // Clean stop + brand-new runtime object == behavior after full app process death.
        runBlocking { runtime.stop() }

        val fresh = EmbeddedRuntime(context, autoRestart = false)
        runBlocking { withTimeout(300_000) { baseUrl = fresh.start() } }
        runtime = fresh

        val list = getRaw("$baseUrl/session")
        assertTrue(
            "session $sessionId not recovered after restart: ${list.take(180)}",
            list.contains(sessionId)
        )
    }

    // -------------------------------------------------------------------------- helpers

    private fun runGate(id: String, name: String, block: () -> Unit) {
        val started = System.currentTimeMillis()
        val result = try {
            block()
            GateResult(id, name, true, "ok in ${System.currentTimeMillis() - started}ms")
        } catch (t: Throwable) {
            GateResult(id, name, false, (t.message ?: t.javaClass.simpleName).replace("\n", " ").take(400))
        }
        results += result
        println("GATE $id name=\"$name\" result=${if (result.pass) "PASS" else "FAIL"} detail=\"${result.detail}\"")
    }

    private fun printSummary() {
        println("---- RUNTIME GATE SUMMARY ----")
        results.forEach {
            println("GATE ${it.id} name=\"${it.name}\" result=${if (it.pass) "PASS" else "FAIL"} detail=\"${it.detail}\"")
        }
        println("------------------------------")
    }

    /** One-shot guest command through proot using the production argv/env builder. */
    private fun runGuest(guestCommand: String): ProcessOutput {
        val artifacts = (NativeArtifacts.resolve(context) as NativeArtifacts.Status.Ready).artifacts
        val layout = RootfsBootstrap(context).layout()
        val cmd = RuntimeEnv.build(
            proot = artifacts.proot,
            prootLoader = artifacts.prootLoader,
            rootfsDir = layout.rootfsDir,
            guestWorkdir = "/home/opencode/project",
            hostLogDir = layout.logDir,
            port = RuntimeEnv.pickFreePort(), // unused by one-shot commands
            guestCommand = guestCommand
        )
        val proc = ProcessBuilder(cmd.argv)
            .apply { environment().putAll(cmd.env); redirectErrorStream(true) }
            .start()
        val finished = proc.waitFor(240, TimeUnit.SECONDS)
        check(finished) { "guest command timed out" }
        return ProcessOutput(proc.inputStream.bufferedReader().readText(), proc.exitValue())
    }

    private data class ProcessOutput(val stdout: String, val exitCode: Int)

    private val client: OkHttpClient = Http.client(AuthInterceptor())

    private fun getRaw(url: String): String =
        Http.execute(client, Http.get(url)) { it.string() }.let { res ->
            when (res) {
                is com.opencode.client.core.Outcome.Ok -> res.value
                is com.opencode.client.core.Outcome.Err ->
                    error("${res.error.userMessage} :: ${res.error.technical}")
            }
        }

    private fun postJson(url: String, body: String): JsonObject =
        Http.execute(client, Http.post(url, body)) { it.string() }.let { res ->
            when (res) {
                is com.opencode.client.core.Outcome.Ok ->
                    appJson.parseToJsonElement(res.value).jsonObject
                is com.opencode.client.core.Outcome.Err ->
                    error("${res.error.userMessage} :: ${res.error.technical}")
            }
        }

    private fun createSession(title: String): String {
        val created = postJson("$baseUrl/session", buildJsonObject {
            put("title", title)
        }.toString())
        return created["id"]?.jsonPrimitive?.content ?: error("no session id in $created")
    }

    private fun shellCommand(targetSession: String, command: String): String {
        val body = postJson("$baseUrl/session/$targetSession/shell", buildJsonObject {
            put("agent", "build")
            put("command", command)
        }.toString())
        val parts = body["parts"] as? JsonArray ?: error("shell returned no parts: $body")
        return buildString {
            for (p in parts) {
                val obj = p.jsonObject
                obj["state"]?.jsonObject?.get("output")?.jsonPrimitive?.content?.let { append(it).append('\n') }
                obj["text"]?.jsonPrimitive?.content?.let { append(it).append('\n') }
            }
        }
    }
}
