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

    @Test
    fun `parses v2 SSE diff events with data payload`() {
        val event = OpenCodeEventParser.parse(
            """{"id":"evt_test","type":"session.diff","location":{"directory":"C:/project"},"data":{"sessionID":"ses-1","diff":[{"file":"src/Main.kt","before":"old","after":"new","additions":1,"deletions":1}]}}"""
        )

        assertTrue(event is SessionDiffEvent)
        val diff = (event as SessionDiffEvent).properties.diff.single()
        assertEquals("src/Main.kt", diff.file)
        assertEquals("old", diff.before)
        assertEquals("new", diff.after)
    }

    @Test
    fun `parses v2 file edited events`() {
        val event = OpenCodeEventParser.parse(
            """{"id":"evt_test","type":"file.edited","location":{"directory":"C:/project"},"data":{"file":"src/Main.kt"}}"""
        )

        assertTrue(event is FileEditedEvent)
        assertEquals("src/Main.kt", (event as FileEditedEvent).properties.file)
    }

    @Test
    fun `parses v2 session status events`() {
        val event = OpenCodeEventParser.parse(
            """{"id":"evt_test","type":"session.status","data":{"sessionID":"ses-1","status":{"type":"idle"}}}"""
        )

        assertTrue(event is SessionStatusEvent)
        assertEquals("ses-1", (event as SessionStatusEvent).properties.sessionID)
        assertEquals(true, event.properties.status.isIdle())
    }

    @Test
    fun `parses v2 session execution lifecycle events`() {
        val started = OpenCodeEventParser.parse(
            """{"id":"evt_start","type":"session.execution.started","data":{"sessionID":"ses-1","executionID":"exec-1"}}"""
        )
        val succeeded = OpenCodeEventParser.parse(
            """{"id":"evt_end","type":"session.execution.succeeded","data":{"sessionID":"ses-1","executionID":"exec-1"}}"""
        )

        assertTrue(started is SessionExecutionEvent)
        assertEquals("ses-1", (started as SessionExecutionEvent).sessionID)
        assertTrue(succeeded is SessionExecutionEvent)
        assertEquals("session.execution.succeeded", (succeeded as SessionExecutionEvent).type)
        assertEquals("ses-1", succeeded.sessionID)
    }
}
