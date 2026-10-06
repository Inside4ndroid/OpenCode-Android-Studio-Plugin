package ai.opencode.ide.jetbrains.util

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCodeCliCompatibilityTest {
    @Test
    fun `detects legacy and v2 versions`() {
        assertEquals(OpenCodeCliCompatibility.Mode.LEGACY, OpenCodeCliCompatibility.modeFromVersionOutput("opencode 1.2.3"))
        assertEquals(OpenCodeCliCompatibility.Mode.V2, OpenCodeCliCompatibility.modeFromVersionOutput("opencode 2.0.0"))
        assertEquals(null, OpenCodeCliCompatibility.modeFromVersionOutput("OpenCode development build"))
    }

    @Test
    fun `requires a password only for v2`() {
        assertEquals(false, OpenCodeCliCompatibility.requiresPassword(OpenCodeCliCompatibility.Mode.LEGACY))
        assertEquals(true, OpenCodeCliCompatibility.requiresPassword(OpenCodeCliCompatibility.Mode.V2))
    }

    @Test
    fun `falls back to detecting legacy flags from help`() {
        assertEquals(
            OpenCodeCliCompatibility.Mode.LEGACY,
            OpenCodeCliCompatibility.modeFromHelpOutput("FLAGS\n  --hostname string\n  --port number")
        )
        assertEquals(OpenCodeCliCompatibility.Mode.V2, OpenCodeCliCompatibility.modeFromHelpOutput("SUBCOMMANDS\n  serve"))
    }

    @Test
    fun `builds Windows command for PATH-installed CLI without nested quotes`() {
        assertEquals(
            listOf("cmd.exe", "/d", "/c", "call opencode --help"),
            OpenCodeCliCompatibility.windowsScriptArguments("opencode", "--help")
        )
    }

    @Test
    fun `quotes Windows shim paths with spaces`() {
        assertEquals(
            listOf("cmd.exe", "/d", "/c", "call \"C:\\Program Files\\OpenCode\\opencode.cmd\" serve --port 4096"),
            OpenCodeCliCompatibility.windowsScriptArguments(
                "C:\\Program Files\\OpenCode\\opencode.cmd",
                "serve",
                "--port",
                "4096"
            )
        )
    }

    @Test
    fun `sets both server and client password environment variables`() {
        val encodedCommand = OpenCodeCliCompatibility.buildPasswordTerminalCommand(
            "opencode",
            "127.0.0.1",
            4096,
            true,
            OpenCodeCliCompatibility.Mode.V2,
            "secret",
            true
        )
        val script = String(
            java.util.Base64.getDecoder().decode(encodedCommand.substringAfterLast(' ')),
            Charsets.UTF_16LE
        )
        assertEquals(
            "powershell.exe -NoProfile -EncodedCommand",
            encodedCommand.substringBeforeLast(' ')
        )
        assertEquals(
            "\$env:OPENCODE_PASSWORD='secret'; \$env:OPENCODE_SERVER_PASSWORD='secret'; " +
                "& 'opencode' '--server' 'http://127.0.0.1:4096' '--continue'",
            script
        )
        assertEquals(
            "OPENCODE_PASSWORD='a'\\''b' OPENCODE_SERVER_PASSWORD='a'\\''b' " +
                "opencode --server http://127.0.0.1:4096 --continue",
            OpenCodeCliCompatibility.buildPasswordTerminalCommand(
                "opencode",
                "127.0.0.1",
                4096,
                true,
                OpenCodeCliCompatibility.Mode.V2,
                "a'b",
                false
            )
        )
    }

    @Test
    fun `quotes apostrophes in Windows credentials and executable paths`() {
        val encodedCommand = OpenCodeCliCompatibility.buildPasswordTerminalCommand(
            "C:\\Program Files\\OpenCode\\opencode.cmd",
            "127.0.0.1",
            4096,
            false,
            OpenCodeCliCompatibility.Mode.V2,
            "pass'word",
            true
        )
        val script = String(
            java.util.Base64.getDecoder().decode(encodedCommand.substringAfterLast(' ')),
            Charsets.UTF_16LE
        )
        assertEquals(
            "\$env:OPENCODE_PASSWORD='pass''word'; \$env:OPENCODE_SERVER_PASSWORD='pass''word'; " +
                "& 'C:\\Program Files\\OpenCode\\opencode.cmd' '--server' 'http://127.0.0.1:4096'",
            script
        )
    }

    @Test
    fun `builds legacy terminal command`() {
        assertEquals(
            """opencode --hostname 127.0.0.1 --port 4096 --continue""",
            OpenCodeCliCompatibility.buildTerminalCommand("opencode", "127.0.0.1", 4096, true, OpenCodeCliCompatibility.Mode.LEGACY)
        )
    }

    @Test
    fun `builds v2 terminal command using server connection`() {
        assertEquals(
            """opencode --server http://127.0.0.1:4096 --continue""",
            OpenCodeCliCompatibility.buildTerminalCommand("opencode", "0.0.0.0", 4096, true, OpenCodeCliCompatibility.Mode.V2)
        )
    }
}
