package ai.opencode.ide.jetbrains.api.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

internal object OpenCodeEventParser {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(OpenCodeEvent::class.java, OpenCodeEventDeserializer())
        .registerTypeAdapter(FileDiff::class.java, FileDiffDeserializer())
        .create()

    fun parse(json: String): OpenCodeEvent? {
        val element = JsonParser.parseString(json)
        if (element.isJsonObject) {
            val normalized = element.asJsonObject.deepCopy()
            if (normalized.has("data") && !normalized.has("properties")) {
                normalized.add("properties", normalized.remove("data"))
                return gson.fromJson(normalized, OpenCodeEvent::class.java)
            }
        }
        return gson.fromJson(element, OpenCodeEvent::class.java)
    }
}
