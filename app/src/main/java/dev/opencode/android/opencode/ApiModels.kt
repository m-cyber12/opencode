package dev.opencode.android.opencode

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Schema-tolerant DTOs for the local OpenCode server (v1.18.x).
 *
 * Volatile payloads (message parts) are carried as [JsonElement] so upstream
 * additions never break the app; stable identity fields are typed.
 */
object Api {
    data class Health(val healthy: Boolean, val version: String?)

    data class SessionInfo(
        val id: String,
        val title: String?,
        val directory: String?,
        val projectID: String?,
        val parentID: String?,
        val raw: JsonObject,
    )

    data class MessageWithParts(
        val info: JsonObject,
        val parts: List<JsonElement>,
    ) {
        val messageID: String get() = info.str("id") ?: ""
    }

    data class PermissionRequest(
        val id: String,
        val sessionID: String,
        val permission: String,
        val patterns: List<String>,
        val metadata: JsonObject,
    )

    fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.contentOrNull
}
