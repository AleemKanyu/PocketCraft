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
                    val cleanVersion = CarpetModManager.cleanMcVersion(version)
                    val versionsToTry = listOfNotNull(
                        cleanVersion,
                        cleanVersion.substringBeforeLast('.', missingDelimiterValue = cleanVersion).takeIf { it != cleanVersion }
                    )

                    var loaderVersion: String? = null
                    for (v in versionsToTry) {
                        val loaderJson = runCatching {
                            URL("https://meta.fabricmc.net/v2/versions/loader/$v").openStream().bufferedReader().use { it.readText() }
                        }.getOrNull() ?: continue
                        val loaderArr = org.json.JSONArray(loaderJson)
                        for (i in 0 until loaderArr.length()) {
                            val obj = loaderArr.getJSONObject(i)
                            val lv = obj.optJSONObject("loader")?.optString("version") ?: continue
                            val stable = obj.optJSONObject("loader")?.optBoolean("stable", false) ?: false
                            if (loaderVersion == null) loaderVersion = lv
                            if (stable) { loaderVersion = lv; break }
                        }
                        if (loaderVersion != null) break
                    }

                    // 2. Fetch the latest stable installer version
                    val installerJson = URL("https://meta.fabricmc.net/v2/versions/installer").openStream().bufferedReader().use { it.readText() }
                    val installerArr = org.json.JSONArray(installerJson)
                    var installerVersion: String? = null
                    for (i in 0 until installerArr.length()) {
                        val obj = installerArr.getJSONObject(i)
                        val iv = obj.optString("version")
                        val stable = obj.optBoolean("stable", false)
                        if (installerVersion == null) installerVersion = iv
                        if (stable) { installerVersion = iv; break }
                    }

                    fabricServerJarUrl(cleanVersion, loaderVersion ?: "0.16.10", installerVersion ?: "1.0.1")
                }
            }
        } catch (e: Exception) {
            val cleanVersion = CarpetModManager.cleanMcVersion(version)
            when (serverType) {
                ServerType.PAPER   -> "https://papermc.io/downloads/paper"
                ServerType.PURPUR  -> "https://purpurmc.org/downloads"
                ServerType.FABRIC  -> fabricServerJarUrl(cleanVersion, "0.16.10", "1.0.1")
                ServerType.MODPACK -> "https://modrinth.com/modpacks"
            }
        }
    }

    private fun fabricServerJarUrl(
        mcVersion: String,
        loaderVersion: String?,
        installerVersion: String?
    ): String {
        val lv = loaderVersion ?: "0.16.10"
        val iv = installerVersion ?: "1.0.1"
        return "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$lv/$iv/server/jar"
    }
}
