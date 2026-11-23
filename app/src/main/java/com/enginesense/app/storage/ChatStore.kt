package com.enginesense.app.storage

import android.content.Context
import com.enginesense.app.vehicle.ai.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Each car's AI Mechanic conversation, one JSON file per car under filesDir/chat — same approach as [OwnerDataStore]. */
class ChatStore(context: Context) {

    private val dir = File(context.filesDir, "chat").apply { mkdirs() }

    private fun fileFor(carId: String) = File(dir, "$carId.json")

    /** Never throws — a missing or corrupt file is an empty conversation. */
    @Synchronized
    fun load(carId: String): List<ChatMessage> = try {
        val file = fileFor(carId)
        if (!file.exists()) emptyList() else fromJson(file.readText())
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun save(carId: String, messages: List<ChatMessage>) {
        fileFor(carId).writeText(toJson(messages))
    }

    @Synchronized
    fun delete(carId: String) {
        fileFor(carId).delete()
    }

    companion object {
        fun toJson(messages: List<ChatMessage>): String = JSONArray(messages.map { m ->
            JSONObject().apply { put("fromUser", m.fromUser); put("text", m.text) }
        }).toString()

        fun fromJson(json: String): List<ChatMessage> = JSONArray(json).let { a ->
            (0 until a.length()).map { a.getJSONObject(it).let { o -> ChatMessage(o.getBoolean("fromUser"), o.getString("text")) } }
        }
    }
}
