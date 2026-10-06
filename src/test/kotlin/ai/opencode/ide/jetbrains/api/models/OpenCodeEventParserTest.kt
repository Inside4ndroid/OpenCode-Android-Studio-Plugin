package ai.opencode.ide.jetbrains.api.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeEventParserTest {
    @Test
    fun `parses SSE diff events with null file content`() {
        val event = OpenCodeEventParser.parse(
            """{"type":"session.diff","properties":{"sessionID":"ses-1","diff":[{"file":"src/Main.kt","before":null,"after":null,"additions":null,"deletions":null}]}}"""
        )

        assertTrue(event is SessionDiffEvent)
        val diff = (event as SessionDiffEvent).properties.diff.single()
        assertEquals("src/Main.kt", diff.file)
        assertEquals("", diff.before)
        assertEquals("", diff.after)
        assertEquals(0, diff.additions)
        assertEquals(0, diff.deletions)
    }
}
