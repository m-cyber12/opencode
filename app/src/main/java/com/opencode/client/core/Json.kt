package com.opencode.client.core

import kotlinx.serialization.json.Json

/**
 * Single JSON configuration for the whole app.
 * - ignoreUnknownKeys: future OpenCode versions may add fields; the app must not crash.
 * - isLenient: tolerate loosely-typed values in event payloads.
 * - coerceInputValues: nulls for non-null fields fall back to defaults instead of throwing.
 */
val appJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    prettyPrint = false
}
