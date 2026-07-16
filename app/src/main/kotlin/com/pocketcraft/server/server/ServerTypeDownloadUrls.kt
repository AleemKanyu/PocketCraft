package com.pocketcraft.server.server

import com.pocketcraft.server.data.model.ServerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

object ServerTypeDownloadUrls {
    fun getDownloadPageUrl(serverType: ServerType, version: String): String {
        return when (serverType) {
            ServerType.PAPER   -> "https://papermc.io/downloads/paper"
            ServerType.PURPUR  -> "https://purpurmc.org/downloads"
            ServerType.FABRIC  -> fabricServerJarUrl(version, loaderVersion = null, installerVersion = null)
            ServerType.MODPACK -> "https://modrinth.com/modpacks"
        }
    }

    suspend fun resolveDownloadUrl(
        serverType: ServerType,
        version: String,
        relayHost: String
    ): String = withContext(Dispatchers.IO) {
        try {
            when (serverType) {
                ServerType.PAPER   -> {
                    // 1. Fetch version metadata to find the latest build
                    val versionUrl = java.net.URL("https://fill.papermc.io/v3/projects/paper/versions/$version")
                    val conn = versionUrl.openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("User-Agent", "PocketCraft/1.0")
                    val responseJson = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(responseJson)
                    val builds = json.getJSONArray("builds")
                    var latestBuild = -1
                    for (i in 0 until builds.length()) {
                        val b = builds.getInt(i)
                        if (b > latestBuild) {
                            latestBuild = b
                        }
                    }
                    if (latestBuild == -1) throw Exception("No builds found")

                    // 2. Fetch build metadata to get the direct download URL
                    val buildUrl = java.net.URL("https://fill.papermc.io/v3/projects/paper/versions/$version/builds/$latestBuild")
                    val buildConn = buildUrl.openConnection() as java.net.HttpURLConnection
                    buildConn.setRequestProperty("User-Agent", "PocketCraft/1.0")
                    val buildJson = buildConn.inputStream.bufferedReader().use { it.readText() }
                    val buildObj = org.json.JSONObject(buildJson)
                    val downloads = buildObj.getJSONObject("downloads")
                    val serverDefault = downloads.optJSONObject("server:default") ?: downloads.getJSONObject("application")
                    serverDefault.getString("url")
                }
                ServerType.PURPUR  -> "https://api.purpurmc.org/v2/purpur/$version/latest/download"
                ServerType.MODPACK -> "https://modrinth.com/modpacks"

                ServerType.FABRIC -> {
                    // 1. Fetch the latest stable loader version for this MC version
                    val loaderJson = URL(
                        "https://meta.fabricmc.net/v2/versions/loader/$version"
                    ).openStream().bufferedReader().use { it.readText() }
                    val loaderArr = org.json.JSONArray(loaderJson)
                    // Prefer stable; fall back to first available
                    var loaderVersion: String? = null
                    for (i in 0 until loaderArr.length()) {
                        val obj = loaderArr.getJSONObject(i)
                        val lv = obj.optJSONObject("loader")?.optString("version") ?: continue
                        val stable = obj.optJSONObject("loader")?.optBoolean("stable", false) ?: false
                        if (loaderVersion == null) loaderVersion = lv   // first = latest
                        if (stable) { loaderVersion = lv; break }       // prefer stable
                    }

                    // 2. Fetch the latest stable installer version
                    val installerJson = URL(
                        "https://meta.fabricmc.net/v2/versions/installer"
                    ).openStream().bufferedReader().use { it.readText() }
                    val installerArr = org.json.JSONArray(installerJson)
                    var installerVersion: String? = null
                    for (i in 0 until installerArr.length()) {
                        val obj = installerArr.getJSONObject(i)
                        val iv = obj.optString("version")
                        val stable = obj.optBoolean("stable", false)
                        if (installerVersion == null) installerVersion = iv
                        if (stable) { installerVersion = iv; break }
                    }

                    // 3. Build direct server JAR URL — browser triggers an immediate download
                    fabricServerJarUrl(version, loaderVersion, installerVersion)
                }


            }
        } catch (e: Exception) {
            // Safe generic fallbacks
            when (serverType) {
                ServerType.PAPER   -> "https://papermc.io/downloads/paper"
                ServerType.PURPUR  -> "https://purpurmc.org/downloads"
                ServerType.FABRIC  -> fabricServerJarUrl(version, loaderVersion = null, installerVersion = null)
                ServerType.MODPACK -> "https://modrinth.com/modpacks"
            }
        }
    }



    /**
     * Constructs the direct server JAR download URL from Fabric's meta API.
     *
     * Format:
     *   https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/{installer}/server/jar
     *
     * Opening this in a browser immediately downloads the ready-to-run server JAR —
     * no website navigation, no manual steps. Unlike Forge, this IS the server JAR itself
     * (not a desktop installer), so users can import it directly into PocketCraft.
     *
     * When loader/installer versions are null (quick-call fallback), redirects to the
     * Fabric installer page as a safe fallback.
     */
    private fun fabricServerJarUrl(
        mcVersion: String,
        loaderVersion: String?,
        installerVersion: String?
    ): String {
        if (loaderVersion == null || installerVersion == null) {
            return "https://fabricmc.net/use/server/"
        }
        return "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$loaderVersion/$installerVersion/server/jar"
    }
}
