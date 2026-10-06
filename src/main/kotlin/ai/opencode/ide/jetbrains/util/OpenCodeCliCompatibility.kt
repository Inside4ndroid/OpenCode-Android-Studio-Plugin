package ai.opencode.ide.jetbrains.util

internal object OpenCodeCliCompatibility {
    enum class Mode { LEGACY, V2 }

    fun requiresPassword(mode: Mode): Boolean = mode == Mode.V2

    fun modeFromVersionOutput(output: String): Mode? {
        val version = Regex("""(?:^|\s)v?(\d+)\.\d+\.\d+""").find(output)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        return if (version >= 2) Mode.V2 else Mode.LEGACY
    }

    fun modeFromHelpOutput(output: String): Mode {
        val hasLegacyServerFlags = Regex("""(?m)^\s*--hostname\b""").containsMatchIn(output) &&
            Regex("""(?m)^\s*--port\b""").containsMatchIn(output)
        return if (hasLegacyServerFlags) Mode.LEGACY else Mode.V2
    }

    fun windowsScriptArguments(command: String, vararg arguments: String): List<String> {
        val commandLine = (listOf("call", command) + arguments)
            .joinToString(" ") { argument ->
                if (argument.any { it.isWhitespace() }) "\"${argument.replace("\"", "\"\"")}\"" else argument
            }
        return listOf("cmd.exe", "/d", "/c", commandLine)
    }

    private fun withPosixPasswordEnvironment(command: String, password: String): String {
        val quotedPassword = "'${password.replace("'", "'\\''")}'"
        return "OPENCODE_PASSWORD=$quotedPassword OPENCODE_SERVER_PASSWORD=$quotedPassword $command"
    }

    fun buildPasswordTerminalCommand(
        command: String,
        host: String,
        port: Int,
        continueSession: Boolean,
        mode: Mode,
        password: String,
        isWindows: Boolean
    ): String {
        if (!isWindows) {
            return withPosixPasswordEnvironment(
                buildTerminalCommand(command, host, port, continueSession, mode),
                password
            )
        }

        val serverHost = if (host == "0.0.0.0") "127.0.0.1" else host
        val arguments = when (mode) {
            Mode.LEGACY -> listOf("--hostname", host, "--port", port.toString())
            Mode.V2 -> listOf("--server", "http://$serverHost:$port")
        } + if (continueSession) listOf("--continue") else emptyList()
        val quotedPassword = quotePowerShell(password)
        val invocation = (listOf(command) + arguments).joinToString(" ") { quotePowerShell(it) }
        val script = "\$env:OPENCODE_PASSWORD=$quotedPassword; " +
            "\$env:OPENCODE_SERVER_PASSWORD=$quotedPassword; & $invocation"
        val encodedScript = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return "powershell.exe -NoProfile -EncodedCommand $encodedScript"
    }

    private fun quotePowerShell(value: String): String = "'${value.replace("'", "''")}'"

    fun buildTerminalCommand(
        command: String,
        host: String,
        port: Int,
        continueSession: Boolean,
        mode: Mode
    ): String {
        val commandArgument = if (command.contains(" ")) "\"$command\"" else command
        return when (mode) {
            Mode.LEGACY ->
                "$commandArgument --hostname $host --port $port${if (continueSession) " --continue" else ""}"
            Mode.V2 -> {
                val serverHost = if (host == "0.0.0.0") "127.0.0.1" else host
                "$commandArgument --server http://$serverHost:$port${if (continueSession) " --continue" else ""}"
            }
        }
    }
}
