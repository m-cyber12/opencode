package com.opencode.client.opencode.event

import com.opencode.client.core.appJson
import com.opencode.client.opencode.dto.FileDiffDto
import com.opencode.client.opencode.dto.PermissionRequestDto
import com.opencode.client.opencode.dto.RawPartDto
import com.opencode.client.opencode.dto.SessionDto
import com.opencode.client.opencode.dto.SessionStatusEntryDto
import com.opencode.client.opencode.dto.TodoDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Internal, transport-independent representation of OpenCode server events.
 *
 * This is a *closed* but forgiving taxonomy: anything the server emits that we do not know about
 * is mapped to [Unknown] and safely ignored (or surfaced in the activity log). Adding support for
 * a new server event only requires a new case here - no UI rewrite.
 */
sealed interface OpenCodeEvent {

    val type: String

    data object ServerConnected : OpenCodeEvent {
        override val type = "server.connected"
    }

    data class SessionCreated(val session: SessionDto) : OpenCodeEvent {
        override val type = "session.created"
    }

    data class SessionUpdated(val session: SessionDto) : OpenCodeEvent {
        override val type = "session.updated"
    }

    data class SessionDeleted(val session: SessionDto) : OpenCodeEvent {
        override val type = "session.deleted"
    }

    data class SessionStatus(
        val sessionID: String,
        val status: SessionStatusEntryDto
    ) : OpenCodeEvent {
        override val type = "session.status"
    }

    data class SessionIdle(val sessionID: String) : OpenCodeEvent {
        override val type = "session.idle"
    }

    data class SessionCompacted(val sessionID: String) : OpenCodeEvent {
        override val type = "session.compacted"
    }

    data class SessionDiff(
        val sessionID: String,
        val diffs: List<FileDiffDto>
    ) : OpenCodeEvent {
        override val type = "session.diff"
    }

    /** Any message-level error (auth failure, API error, aborted, unknown...). */
    data class SessionError(
        val sessionID: String?,
        val errorName: String?,
        val errorMessage: String?,
        val raw: JsonObject?
    ) : OpenCodeEvent {
        override val type = "session.error"
    }

    data class MessageUpdated(val info: JsonObject) : OpenCodeEvent {
        override val type = "message.updated"
    }

    data class MessageRemoved(val sessionID: String, val messageID: String) : OpenCodeEvent {
        override val type = "message.removed"
    }

    data class MessagePartUpdated(
        val part: RawPartDto,
        val delta: String?
    ) : OpenCodeEvent {
        override val type = "message.part.updated"
    }

    data class MessagePartRemoved(
        val sessionID: String,
        val messageID: String,
        val partID: String
    ) : OpenCodeEvent {
        override val type = "message.part.removed"
    }

    data class PermissionUpdated(val permission: PermissionRequestDto) : OpenCodeEvent {
        override val type = "permission.updated"
    }

    data class PermissionReplied(
        val sessionID: String,
        val permissionID: String,
        val response: String
    ) : OpenCodeEvent {
        override val type = "permission.replied"
    }

    data class TodoUpdated(val sessionID: String, val todos: List<TodoDto>) : OpenCodeEvent {
        override val type = "todo.updated"
    }

    data class FileEdited(val file: String) : OpenCodeEvent {
        override val type = "file.edited"
    }

    data class VcsBranchUpdated(val branch: String?) : OpenCodeEvent {
        override val type = "vcs.branch.updated"
    }

    data class CommandExecuted(
        val name: String,
        val sessionID: String,
        val arguments: String,
        val messageID: String
    ) : OpenCodeEvent {
        override val type = "command.executed"
    }

    data object InstallationUpdated : OpenCodeEvent {
        override val type = "installation.updated"
    }

    data class PtyChanged(val ptyType: String, val id: String?) : OpenCodeEvent {
        override val type = "pty.changed"
    }

    /** Recognised envelope but not mapped to any UI behavior. */
    data class Ignored(override val type: String) : OpenCodeEvent

    /** Unknown future event. Must never crash; kept for the activity log. */
    data class Unknown(override val type: String, val payload: JsonObject?) : OpenCodeEvent

    companion object {

        /**
         * Parses one SSE `data:` frame.
         *
         * Two shapes exist:
         *  - instance stream `/event`: {"type": "...", "properties": {...}}
         *  - global stream `/global/event`: {"directory": "...", "payload": {instance shape}}
         *
         * Returns null for empty/unparseable frames (never throws).
         */
        fun parse(data: String, directoryHint: String? = null): ParsedFrame {
            if (data.isBlank()) return ParsedFrame.skipped()
            val root = try {
                appJson.parseToJsonElement(data).jsonObject
            } catch (_: Exception) {
                return ParsedFrame.skipped()
            }
            // Global envelope unwrapping.
            var directory = directoryHint
            var inner = root
            if (root.containsKey("payload")) {
                directory = root.str("directory") ?: directory
                inner = try {
                    root["payload"]?.jsonObject ?: return ParsedFrame.skipped()
                } catch (_: Exception) {
                    return ParsedFrame.skipped()
                }
            }
            val typeName = inner.str("type")
                ?: return ParsedFrame(directory, Unknown("<missing-type>", inner))
            val properties = try {
                inner["properties"]?.jsonObject
            } catch (_: Exception) {
                null
            }
            val event = map(typeName, properties)
            return ParsedFrame(directory, event)
        }

        private fun map(type: String, p: JsonObject?): OpenCodeEvent = when (type) {
            "server.connected" -> ServerConnected
            "installation.updated" -> InstallationUpdated
            "installation.update-available" -> Ignored(type)

            "session.created" -> decode(p, "info") { obj ->
                fromJson<SessionDto>(obj)?.let { SessionCreated(it) }
            } ?: fallback(type, p)

            "session.updated" -> decode(p, "info") { obj ->
                fromJson<SessionDto>(obj)?.let { SessionUpdated(it) }
            } ?: fallback(type, p)

            "session.deleted" -> decode(p, "info") { obj ->
                fromJson<SessionDto>(obj)?.let { SessionDeleted(it) }
            } ?: fallback(type, p)

            "session.status" -> {
                val sid = p.str("sessionID")
                val status = p["status"]?.let { el ->
                    try {
                        fromJson<SessionStatusEntryDto>(el.jsonObject)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (sid != null && status != null) SessionStatus(sid, status)
                else fallback(type, p)
            }

            "session.idle" -> p.str("sessionID")?.let { SessionIdle(it) } ?: fallback(type, p)
            "session.compacted" -> p.str("sessionID")?.let { SessionCompacted(it) } ?: fallback(type, p)

            "session.diff" -> {
                val sid = p.str("sessionID")
                val diffs = try {
                    fromList<FileDiffDto>(p["diff"])
                } catch (_: Exception) {
                    null
                }
                if (sid != null && diffs != null) SessionDiff(sid, diffs) else fallback(type, p)
            }

            "session.error" -> {
                val err = p?.get("error") as? JsonObject
                val name = err.str("name") ?: p.str("name")
                val msg = (err["data"] as? JsonObject).str("message")
                    ?: err.str("message")
                SessionError(
                    sessionID = p.str("sessionID"),
                    errorName = name,
                    errorMessage = msg,
                    raw = err
                )
            }

            "message.updated" -> p["info"]?.let { el ->
                try {
                    el as? JsonObject
                } catch (_: Exception) {
                    null
                }
            }?.let { MessageUpdated(it) } ?: fallback(type, p)

            "message.removed" -> {
                val s = p.str("sessionID")
                val m = p.str("messageID")
                if (s != null && m != null) MessageRemoved(s, m) else fallback(type, p)
            }

            "message.part.updated" -> {
                val partEl = p["part"] ?: return fallback(type, p)
                val part = try {
                    fromJson<RawPartDto>(partEl.jsonObject)
                } catch (_: Exception) {
                    null
                } ?: return fallback(type, p)
                MessagePartUpdated(part, p.str("delta"))
            }

            "message.part.removed" -> {
                val s = p.str("sessionID")
                val m = p.str("messageID")
                val part = p.str("partID")
                if (s != null && m != null && part != null) {
                    MessagePartRemoved(s, m, part)
                } else fallback(type, p)
            }

            "permission.updated" -> p["permission"]?.let { el ->
                try {
                    fromJson<PermissionRequestDto>(el.jsonObject)
                } catch (_: Exception) {
                    null
                }
            }?.let { PermissionUpdated(it) } ?: fallback(type, p)

            "permission.replied" -> {
                val s = p.str("sessionID")
                val perm = p.str("permissionID")
                val resp = p.str("response")
                if (s != null && perm != null) PermissionReplied(s, perm, resp ?: "") else fallback(type, p)
            }

            "todo.updated" -> {
                val s = p.str("sessionID")
                val todos = try {
                    fromList<TodoDto>(p["todos"])
                } catch (_: Exception) {
                    null
                }
                if (s != null && todos != null) TodoUpdated(s, todos) else fallback(type, p)
            }

            "file.edited" -> p.str("file")?.let { FileEdited(it) } ?: fallback(type, p)
            "vcs.branch.updated" -> VcsBranchUpdated(p.str("branch"))

            "command.executed" -> {
                val name = p.str("name")
                val s = p.str("sessionID")
                val mid = p.str("messageID")
                if (name != null && s != null && mid != null) {
                    CommandExecuted(name, s, p.str("arguments") ?: "", mid)
                } else fallback(type, p)
            }

            "lsp.updated", "file.watcher.updated", "lsp.client.diagnostics",
            "tui.prompt.append", "tui.command.execute", "tui.toast.show"
            -> Ignored(type)

            "pty.created", "pty.updated", "pty.exited", "pty.deleted" ->
                PtyChanged(type.substringAfterLast('.'), p.str("id"))

            else -> Unknown(type, p)
        }

        // ---- helpers ----

        private inline fun <reified T> fromJson(obj: JsonObject): T =
            appJson.decodeFromJsonElement(obj)

        private fun <T> fromList(el: kotlinx.serialization.json.JsonElement?): List<T> {
            val arr = el as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            return arr.mapNotNull { item ->
                runCatching { appJson.decodeFromJsonElement<T>(item.jsonObject) }.getOrNull()
            }
        }

        private fun JsonObject.str(key: String): String? =
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content

        private fun JsonObject?.str(key: String): String? = this?.let { (it[key] as? kotlinx.serialization.json.JsonPrimitive)?.content }

        @Suppress("UNUSED_PARAMETER")
        private fun decode(p: JsonObject?, key: String, transform: (JsonObject) -> OpenCodeEvent?): OpenCodeEvent? {
            if (p == null) return null
            val obj = p[key] as? JsonObject ?: return null
            return transform(obj)
        }

        private fun fallback(type: String, p: JsonObject?): OpenCodeEvent = Unknown(type, p)
    }
}

/** Result of parsing an SSE frame: the event plus the directory it belongs to (global stream). */
data class ParsedFrame(val directory: String?, val event: OpenCodeEvent) {
    companion object {
        fun skipped() = ParsedFrame(null, OpenCodeEvent.Ignored("<empty>"))
    }
}
