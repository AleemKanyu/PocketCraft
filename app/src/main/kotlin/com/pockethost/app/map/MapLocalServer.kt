package com.pockethost.app.map

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Lightweight loopback HTTP server for cached map assets.
 *
 * Deliberately NOT on 8100: BlueMap's own webserver claims that port from the `:server`
 * process whenever the Minecraft server is running, and two binds would race. This one
 * serves the same web root statically so the rendered map is still viewable offline.
 *
 * Implemented with pure java.net.ServerSocket with zero dependency on com.sun.net.httpserver.
 */
class MapLocalServer(
    private val context: Context,
    private val port: Int = STATIC_PORT
) {
    companion object {
        private const val TAG = "MapLocalServer"

        /** BlueMap's plugin webserver owns 8100; the offline static server sits beside it. */
        const val STATIC_PORT = 8101
    }

    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isRunning: Boolean = false
    private var executor: ExecutorService? = null
    private var activeWorldName: String = "world"
    private var getOnlinePlayersCallback: (() -> List<Map<String, Any>>)? = null

    fun start(worldName: String, getOnlinePlayers: () -> List<Map<String, Any>>) {
        this.activeWorldName = worldName
        this.getOnlinePlayersCallback = getOnlinePlayers

        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
            return
        }

        try {
            stop()
            val ss = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = ss
            isRunning = true
            val pool = Executors.newCachedThreadPool()
            executor = pool

            pool.execute {
                while (isRunning && !ss.isClosed) {
                    try {
                        val client = ss.accept()
                        pool.execute {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
            Log.i(TAG, "Map local server started on http://127.0.0.1:$port for world '$worldName'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local map server on port $port: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null
        try {
            executor?.shutdownNow()
        } catch (e: Exception) {
            // ignore
        }
        executor = null
        Log.i(TAG, "Map local server stopped.")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 10000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val output = BufferedOutputStream(s.getOutputStream())

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val rawPath = parts[1]
                val path = rawPath.substringBefore("?")

                // Drain all request headers until blank line
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                if (method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)) {
                    if (path == "/api/players") {
                        handlePlayersApi(output, method.equals("HEAD", ignoreCase = true))
                    } else {
                        handleStaticFiles(output, path, method.equals("HEAD", ignoreCase = true))
                    }
                } else {
                    sendResponse(output, 405, "Method Not Allowed", "text/plain", "Method Not Allowed".toByteArray(), method.equals("HEAD", ignoreCase = true))
                }
            }
        } catch (e: Exception) {
            // Client disconnect or socket error
        }
    }

    private fun handlePlayersApi(output: BufferedOutputStream, headOnly: Boolean) {
        val players = getOnlinePlayersCallback?.invoke() ?: emptyList()
        val jsonArr = JSONArray()
        players.forEach { p ->
            val obj = JSONObject()
            obj.put("name", p["name"] ?: "Player")
            obj.put("uuid", p["uuid"] ?: "")
            obj.put("x", p["x"] ?: 0.0)
            obj.put("y", p["y"] ?: 64.0)
            obj.put("z", p["z"] ?: 0.0)
            obj.put("dimension", p["dimension"] ?: "minecraft:overworld")
            jsonArr.put(obj)
        }

        val responseBytes = jsonArr.toString().toByteArray(Charsets.UTF_8)
        sendResponse(output, 200, "OK", "application/json; charset=utf-8", responseBytes, headOnly)
    }

    private fun handleStaticFiles(output: BufferedOutputStream, requestedPath: String, headOnly: Boolean) {
        // BlueMap tile and asset names are percent-encoded on the wire; without decoding, any
        // path containing a space or other escaped character resolves to a non-existent file
        // and is served as a 404.
        var path = runCatching {
            // URLDecoder also maps '+' to a space, which is form encoding, not path encoding --
            // escape it first so a literal '+' in a filename survives.
            java.net.URLDecoder.decode(requestedPath.replace("+", "%2B"), "UTF-8")
        }.getOrDefault(requestedPath)
        if (path == "/" || path.isBlank()) path = "/index.html"

        val mapDir = MapCacheManager.getMapDir(context, activeWorldName)
        val webRoot = File(mapDir, "web")
        val requestedFile = File(webRoot, path.removePrefix("/")).canonicalFile

        // Security check: path traversal prevention
        if (!isInside(requestedFile, webRoot)) {
            sendResponse(output, 403, "Forbidden", "text/plain", "Forbidden".toByteArray(), headOnly)
            return
        }

        if (requestedFile.exists() && requestedFile.isFile) {
            val isGzipped = requestedFile.name.endsWith(".gz", ignoreCase = true)
            // For "foo.json.gz" the media type is that of the *inner* file.
            val effectiveExtension = if (isGzipped) {
                // isGzipped was matched case-insensitively, so drop the extension by length.
                requestedFile.name.dropLast(3).substringAfterLast('.', "")
            } else {
                requestedFile.extension
            }
            val mime = getMimeType(effectiveExtension)
            val encoding = if (isGzipped) "gzip" else null
            val header = buildResponseHeader(200, "OK", mime, requestedFile.length(), encoding)
            output.write(header.toByteArray(Charsets.UTF_8))
            if (!headOnly) {
                FileInputStream(requestedFile).use { input ->
                    input.copyTo(output)
                }
            }
            output.flush()
        } else {
            // If index.html doesn't exist yet, serve beautiful built-in loading/placeholder viewer
            if (path.endsWith("index.html") || path == "/") {
                val fallbackHtml = generateFallbackViewerHtml(activeWorldName)
                val bytes = fallbackHtml.toByteArray(Charsets.UTF_8)
                sendResponse(output, 200, "OK", "text/html; charset=utf-8", bytes, headOnly)
            } else {
                sendResponse(output, 404, "Not Found", "text/plain", "File Not Found".toByteArray(), headOnly)
            }
        }
    }

    /**
     * True when [candidate] is [root] itself or sits underneath it.
     *
     * A bare `startsWith` on the canonical paths also accepts siblings whose names merely begin
     * with the root's name (`.../webassets` passes a check against `.../web`), so the separator
     * has to be part of the comparison.
     */
    private fun isInside(candidate: File, root: File): Boolean {
        val rootPath = root.canonicalPath
        val candidatePath = candidate.canonicalPath
        return candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
    }

    private fun sendResponse(
        output: BufferedOutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray,
        headOnly: Boolean
    ) {
        val header = buildResponseHeader(statusCode, statusText, contentType, body.size.toLong())
        output.write(header.toByteArray(Charsets.UTF_8))
        if (!headOnly) {
            output.write(body)
        }
        output.flush()
    }

    private fun buildResponseHeader(
        statusCode: Int,
        statusText: String,
        contentType: String,
        length: Long,
        contentEncoding: String? = null
    ): String {
        val encodingHeader = contentEncoding?.let { "Content-Encoding: $it\r\n" } ?: ""
        return "HTTP/1.1 $statusCode $statusText\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: $length\r\n" +
            encodingHeader +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
    }

    private fun getMimeType(ext: String): String = when (ext.lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "svg" -> "image/svg+xml"
        "gltf" -> "model/gltf+json"
        "glb", "bin" -> "application/octet-stream"
        // BlueMap's packed hi-res tile format
        "prbm" -> "application/octet-stream"
        else -> "application/octet-stream"
    }

    private fun generateFallbackViewerHtml(worldName: String): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <title>PocketHost World Map</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; }
        body {
            background-color: #0d1217;
            color: #e2e8f0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            text-align: center;
            padding: 24px;
        }
        .card {
            background: #161f28;
            border: 1px solid #233140;
            border-bottom: 4px solid #10161d;
            border-radius: 20px;
            padding: 32px 24px;
            max-width: 380px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.5);
        }
        .icon {
            font-size: 48px;
            margin-bottom: 16px;
            animation: float 3s ease-in-out infinite;
        }
        @keyframes float {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-8px); }
        }
        h1 {
            font-size: 20px;
            font-weight: 700;
            color: #4ade80;
            margin-bottom: 8px;
            letter-spacing: -0.5px;
        }
        p {
            font-size: 13px;
            line-height: 1.6;
            color: #94a3b8;
            margin-bottom: 20px;
        }
        .badge {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: #0f2317;
            color: #4ade80;
            border: 1px solid #16532d;
            border-radius: 999px;
            padding: 6px 14px;
            font-size: 11px;
            font-weight: 600;
        }
        .pulse {
            width: 8px;
            height: 8px;
            background: #4ade80;
            border-radius: 50%;
            animation: pulse 1.5s infinite;
        }
        @keyframes pulse {
            0% { opacity: 0.4; }
            50% { opacity: 1; }
            100% { opacity: 0.4; }
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">🌍</div>
        <h1>Preparing 3D World Map</h1>
        <p>PocketHost only renders terrain that already exists in <b>$worldName</b>.<br>Unexplored chunks will never be generated.</p>
        <div class="badge">
            <div class="pulse"></div>
            <span>Safe Progressive Rendering Active</span>
        </div>
    </div>
</body>
</html>
""".trimIndent()
}
