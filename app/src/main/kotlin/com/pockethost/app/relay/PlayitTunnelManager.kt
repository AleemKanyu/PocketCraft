package com.pockethost.app.relay

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import org.json.JSONObject

/**
 * Runs PocketCraft's playit.gg tunnel agent (native/pocket-tunnel, shipped as
 * libpockettunnel.so) as a child process and turns its JSON status lines into [Event]s.
 *
 * The agent links the user's own playit.gg account through a claim link, then carries
 * Java (TCP) and Bedrock (UDP) players to the local server over playit's network.
 */
class PlayitTunnelManager(private val context: Context) {

    sealed interface Event {
        /** First run: the user has to open this link and approve the agent. */
        data class Claim(val url: String) : Event
        /** New agent key to save, so later starts need no approval. */
        data class Secret(val key: String) : Event
        data class Status(val message: String) : Event
        data class Tunnels(val javaAddress: String?, val bedrockAddress: String?) : Event
        /** The account has no Java tunnel and the agent could not create one. */
        data class NeedsTunnel(val url: String) : Event
        data class Warning(val code: String, val message: String) : Event
        data class Failed(val code: String, val message: String) : Event
    }

    @Volatile private var process: Process? = null

    /**
     * Starts the agent and blocks, delivering events until it exits or [stop] is called.
     * Run it on an IO thread.
     */
    fun run(
        secretKey: String?,
        javaPort: Int,
        bedrockPort: Int,
        linkOnly: Boolean = false,
        onEvent: (Event) -> Unit
    ) {
        val binary = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)
        if (!binary.exists()) {
            throw IOException("Tunnel agent is missing from this build ($BINARY_NAME)")
        }

        val started = ProcessBuilder(binary.absolutePath).apply {
            environment()["PT_SECRET"] = secretKey.orEmpty()
            environment()["PT_JAVA_PORT"] = javaPort.toString()
            environment()["PT_BEDROCK_PORT"] = bedrockPort.toString()
            // Link the account and exit: lets the user finish setup before the server starts.
            if (linkOnly) environment()["PT_LINK_ONLY"] = "1"
            redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
        }.start()
        process = started

        try {
            started.inputStream.bufferedReader().forEachLine { line ->
                parse(line)?.let(onEvent)
            }
        } finally {
            // The agent exits when its stdin closes, so it cannot outlive the service.
            started.destroy()
            if (process === started) process = null
        }
    }

    fun stop() {
        process?.destroy()
        process = null
    }

    private fun parse(line: String): Event? {
        val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
        // Never log the "secret" line: it carries the account's agent key.
        if (json.optString("event") != "secret") Log.i(TAG, line)
        return when (json.optString("event")) {
            "claim" -> Event.Claim(json.optString("url"))
            "secret" -> Event.Secret(json.optString("key"))
            "status" -> Event.Status(json.optString("message"))
            "tunnels" -> Event.Tunnels(
                javaAddress = json.optStringOrNull("java"),
                bedrockAddress = json.optStringOrNull("bedrock")
            )
            "needs_tunnel" -> Event.NeedsTunnel(json.optString("url"))
            "warning" -> Event.Warning(json.optString("code"), json.optString("message"))
            "error" -> Event.Failed(json.optString("code"), json.optString("message"))
            else -> null
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    companion object {
        private const val TAG = "PlayitTunnelManager"
        private const val BINARY_NAME = "libpockettunnel.so"
        const val SECRET_INVALID = "secret_invalid"
        const val AGENT_OVER_LIMIT = "agent_over_limit"
        const val AGENTS_URL = "https://playit.gg/account/agents"
    }
}
