package ai.opencode.ide.jetbrains.api.models

import com.google.gson.*
import java.lang.reflect.Type

data class FileDiff(
    val file: String,
    val before: String,
    val after: String,
    val additions: Int,
    val deletions: Int
)

/**
 * Custom deserializer for Go `%q`-formatted strings returned by the server.
 * 
 * When serializing strings containing non-ASCII characters, the server may use Go's `%q` format,
 * resulting in JSON values such as `"\"\\344\\270\\255\\346\\226\\207.md\""`.
 * 
 * This deserializer:
 * 1. Removes the extra outer quotes.
 * 2. Decodes octal escape sequences (`\xxx`) into bytes.
 * 3. Converts the bytes back to text using UTF-8.
 */
class FileDiffDeserializer : JsonDeserializer<FileDiff> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FileDiff {
        val obj = json.asJsonObject
        fun stringValue(name: String): String =
            obj.get(name)?.takeUnless(JsonElement::isJsonNull)?.asString ?: ""

        fun intValue(name: String): Int =
            obj.get(name)?.takeUnless(JsonElement::isJsonNull)?.asInt ?: 0
        
        return FileDiff(
            file = decodeGoQuotedString(stringValue("file")),
            // The before and after fields are regular JSON strings; decoding them would remove
            // surrounding quotes or alter backslashes.
            before = stringValue("before"),
            after = stringValue("after"),
            additions = intValue("additions"),
            deletions = intValue("deletions")
        )
    }
    
    companion object {
        /**
         * Decodes a string formatted using Go's `%q` representation.
         * 
         * For example:
         * - `"\\143\\141\\146\\303\\251.md"` -> `"café.md"`
         * - `"normal.txt"` -> `"normal.txt"` (unchanged)
         */
        fun decodeGoQuotedString(input: String): String {
            var s = input
            
            // Return directly when there are no octal escapes.
            if (!s.contains("\\")) {
                // The value may still have outer quotes.
                if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
                    return s.substring(1, s.length - 1)
                }
                return s
            }
            
            // Remove the outer quotes added by Go's `%q` format.
            if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
                s = s.substring(1, s.length - 1)
            }
            
            // Process escape sequences.
            val bytes = mutableListOf<Byte>()
            var i = 0
            while (i < s.length) {
                if (s[i] == '\\' && i + 1 < s.length) {
                    when {
                        // Octal escape: \xxx (three octal digits).
                        i + 3 < s.length && s[i + 1].isDigit() -> {
                            val octal = s.substring(i + 1, i + 4)
                            try {
                                bytes.add(octal.toInt(8).toByte())
                                i += 4
                                continue
                            } catch (_: NumberFormatException) {
                                // Not a valid octal value; process it as ordinary characters.
                            }
                        }
                        // Common escape characters.
                        s[i + 1] == 'n' -> { bytes.add('\n'.code.toByte()); i += 2; continue }
                        s[i + 1] == 'r' -> { bytes.add('\r'.code.toByte()); i += 2; continue }
                        s[i + 1] == 't' -> { bytes.add('\t'.code.toByte()); i += 2; continue }
                        s[i + 1] == '\\' -> { bytes.add('\\'.code.toByte()); i += 2; continue }
                        s[i + 1] == '"' -> { bytes.add('"'.code.toByte()); i += 2; continue }
                    }
                }
                
                // Preserve non-ASCII characters as UTF-8 instead of truncating them to one byte.
                val c = s[i]
                if (c.code < 128) {
                    bytes.add(c.code.toByte())
                } else {
                    val charBytes = c.toString().toByteArray(Charsets.UTF_8)
                    for (b in charBytes) {
                        bytes.add(b)
                    }
                }
                i++
            }
            
            return bytes.toByteArray().toString(Charsets.UTF_8)
        }
    }
}
