package com.pocketcraft.server.update

import android.content.Context
import com.pocketcraft.server.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object GitHubUpdateChecker {

    data class ReleaseInfo(
        val tagName: String,
        val releaseName: String,
        val htmlUrl: String
    )

    private val client = OkHttpClient()

    suspend fun checkForUpdate(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val owner = BuildConfig.GITHUB_REPO_OWNER.trim()
            val repo = BuildConfig.GITHUB_REPO_NAME.trim()
            if (owner.isBlank() || repo.isBlank()) return@runCatching null

            val currentVersion = BuildConfig.VERSION_NAME
            val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PocketCraft/${BuildConfig.VERSION_NAME}")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null

                val release = JSONObject(body)
                val tagName = release.optString("tag_name").trim()
                val htmlUrl = release.optString("html_url").trim()
                val releaseName = release.optString("name").ifBlank { tagName }
                if (tagName.isBlank() || htmlUrl.isBlank()) return@use null

                val hasNewVersion = compareVersions(tagName, currentVersion) > 0
                if (!hasNewVersion) return@use null

                ReleaseInfo(
                    tagName = tagName,
                    releaseName = releaseName,
                    htmlUrl = htmlUrl
                )
            }
        }.getOrNull()
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = normalizeVersion(a)
        val right = normalizeVersion(b)

        val max = maxOf(left.size, right.size)
        for (i in 0 until max) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun normalizeVersion(raw: String): List<Int> {
        val withoutPrefix = raw.trim().removePrefix("v").removePrefix("V")
        val core = withoutPrefix.substringBefore('-').substringBefore('+')
        return core
            .split('.')
            .map { part -> part.filter { it.isDigit() } }
            .filter { it.isNotBlank() }
            .mapNotNull { digits -> digits.toIntOrNull() }
            .ifEmpty { listOf(0) }
    }
}
