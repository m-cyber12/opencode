package com.opencode.client.ui.components

import com.opencode.client.core.appJson
import com.opencode.client.core.util.DayBucket
import com.opencode.client.core.util.Time
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Shared low-level helpers used across chat, tool cards, files and diffs.
 */

/** Pretty-prints raw JSON text; returns null when the input is not valid JSON. */
internal fun prettyJsonOrNull(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val el: JsonElement = appJson.parseToJsonElement(raw)
        appJson.encodeToString(JsonElement.serializer(), el)
    }.getOrNull()
}

/** First non-blank string value among [keys]; used for tool card headlines. */
internal fun summarizeInput(obj: JsonObject?, keys: List<String>, maxLen: Int = 140): String? {
    obj ?: return null
    for (key in keys) {
        val prim = obj[key] as? JsonPrimitive
        if (prim != null && prim.content.isNotBlank()) {
            return prim.content.take(maxLen)
        }
    }
    return null
}

internal fun metadataInt(metadata: JsonObject?, key: String): Int? =
    (metadata?.get(key) as? JsonPrimitive)?.content?.toIntOrNull()

internal fun metadataString(metadata: JsonObject?, key: String): String? =
    (metadata?.get(key) as? JsonPrimitive)?.content

/** "Today · 14:32" / "Yesterday · ..." style compact labels for session rows and messages. */
internal fun relativeDayLabel(epochMs: Long): String =
    when (Time.dayBucket(epochMs)) {
        DayBucket.TODAY -> Time.clock(epochMs)
        else -> "${Time.dayBucket(epochMs).label} ${Time.shortDateTime(epochMs)}"
    }

internal fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "%.1fk".format(n / 1_000f)
    else -> n.toString()
}
