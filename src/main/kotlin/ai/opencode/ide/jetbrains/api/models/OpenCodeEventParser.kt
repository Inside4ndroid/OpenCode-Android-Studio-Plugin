package ai.opencode.ide.jetbrains.api.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder

internal object OpenCodeEventParser {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(OpenCodeEvent::class.java, OpenCodeEventDeserializer())
        .registerTypeAdapter(FileDiff::class.java, FileDiffDeserializer())
        .create()

    fun parse(json: String): OpenCodeEvent? = gson.fromJson(json, OpenCodeEvent::class.java)
}
