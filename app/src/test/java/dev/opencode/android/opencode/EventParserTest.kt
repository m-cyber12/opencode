package dev.opencode.android.opencode

import dev.opencode.android.opencode.OpenCodeEventStream.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class EventParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesServerConnected() {
        val frame = """data: {"id":"evt_1","type":"server.connected","properties":{}}"""
        val parsed = parseFrame(frame)
        assertNotNull(parsed)
        assertEquals("server.connected", parsed!!.type)
    }

    @Test
    fun parsesPermissionAsked() {
        val frame = """data: {"id":"evt_2","type":"permission.asked","properties":{"id":"per_123","sessionID":"ses_1","permission":"bash","patterns":["*"],"metadata":{}}}"""
        val parsed = parseFrame(frame)
        assertNotNull(parsed)
        assertEquals("permission.asked", parsed!!.type)
        assertEquals("per_123", parsed.sessionID)
    }

    @Test
    fun parsesMessagePartUpdated() {
        val frame = """data: {"id":"evt_3","type":"message.part.updated","properties":{"sessionID":"ses_1","part":{"id":"part_1","messageID":"msg_1","type":"text","text":"hello"}}}"""
        val parsed = parseFrame(frame)
        assertNotNull(parsed)
        assertEquals("message.part.updated", parsed!!.type)
    }

    @Test
    fun ignoresHeartbeat() {
        val frame = """data: {"id":"evt_4","type":"server.heartbeat","properties":{}}"""
        val parsed = parseFrame(frame)
        assertNotNull(parsed)
        assertEquals("server.heartbeat", parsed!!.type)
    }

    private fun parseFrame(data: String): Event? {
        val o = json.parseToJsonElement(data) as? JsonObject ?: return null
        val type = (o["type"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return null
        val props = o["properties"] as? JsonObject ?: JsonObject(emptyMap())
        return Event.Generic(type, props, data)
    }
}