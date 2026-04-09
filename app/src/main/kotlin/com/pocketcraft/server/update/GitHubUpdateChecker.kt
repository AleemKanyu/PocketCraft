package com.pocketcraft.server.update

import android.content.Context
import com.pocketcraft.server.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant

object GitHubUpdateChecker {

    private const val FORCE_UPDATE_GRACE_PERIOD_MS = 3L * 24L * 60L * 60L * 1000L

    data class ReleaseInfo(
        val tagName: String,
        val releaseName: String,
        val body: String,
        val htmlUrl: String,
        val downloadUrl: String?,
        val publishedAtMillis: Long?
    ) {
        fun isForceUpdateDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
            val publishedAt = publishedAtMillis ?: return false
            return nowMillis - publishedAt >= FORCE_UPDATE_GRACE_PERIOD_MS
        }
    }

    private val client = OkHttpClient()

    suspend fun checkForUpdate(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        fetchLatestRelease(context)?.takeIf { compareVersions(it.tagName, BuildConfig.VERSION_NAME) > 0 }
    }

    suspend fun fetchLatestRelease(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
            val hostedRelease = manifestUrl.takeIf { it.isNotBlank() }?.let(::fetchHostedManifest)

            val owner = BuildConfig.GITHUB_REPO_OWNER.trim()
            val repo = BuildConfig.GITHUB_REPO_NAME.trim()
            val githubRelease = if (owner.isBlank() || repo.isBlank()) {
                null
            } else {
                fetchGitHubRelease(owner = owner, repo = repo)
            }

            pickNewestRelease(hostedRelease, githubRelease)
        }.getOrNull()
    }

    private fun fetchGitHubRelease(owner: String, repo: String): ReleaseInfo? {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "PocketCraft/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null

            val release = JSONObject(body)
            val tagName = release.optString("tag_name").trim()
            val htmlUrl = release.optString("html_url").trim()
            val releaseName = release.optString("name").ifBlank { tagName }
            val publishedAtMillis = release.optString("published_at")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            val downloadUrl = release.optJSONArray("assets")
                ?.let { assets ->
                    (0 until assets.length())
                        .asSequence()
                        .mapNotNull { index -> assets.optJSONObject(index) }
                        .firstOrNull { asset ->
                            val name = asset.optString("name").trim().lowercase()
                            name.endsWith(".apk")
                        }
                        ?.let { asset ->
                            asset.optString("browser_download_url")
                                .trim()
                                .takeIf { it.isNotBlank() }
                                ?: asset.optString("url")
                                    .trim()
                                    .takeIf { it.isNotBlank() }
                        }
                }
            if (tagName.isBlank() || htmlUrl.isBlank()) return null

            return ReleaseInfo(
                tagName = tagName,
                releaseName = releaseName,
                body = release.optString("body").trim(),
                htmlUrl = htmlUrl,
                downloadUrl = downloadUrl,
                publishedAtMillis = publishedAtMillis
            )
        }
    }

    private fun pickNewestRelease(first: ReleaseInfo?, second: ReleaseInfo?): ReleaseInfo? {
        if (first == null) return second
        if (second == null) return first

        return when {
            compareVersions(first.tagName, second.tagName) > 0 -> first
            compareVersions(first.tagName, second.tagName) < 0 -> second
            else -> ReleaseInfo(
                tagName = second.tagName.ifBlank { first.tagName },
                releaseName = second.releaseName.ifBlank { first.releaseName },
                body = second.body.ifBlank { first.body },
                htmlUrl = second.htmlUrl.ifBlank { first.htmlUrl },
                downloadUrl = second.downloadUrl ?: first.downloadUrl,
                publishedAtMillis = maxOf(
                    first.publishedAtMillis ?: Long.MIN_VALUE,
                    second.publishedAtMillis ?: Long.MIN_VALUE
                ).takeIf { it != Long.MIN_VALUE }
            )
        }
    }

    private fun fetchHostedManifest(manifestUrl: String): ReleaseInfo? {
        val request = Request.Builder()
            .url(manifestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "PocketCraft/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null

            val json = JSONObject(body)
            val tagName = json.optString("version").ifBlank { json.optString("tag_name") }.trim()
            val releaseName = json.optString("releaseName").ifBlank {
                json.optString("release_name").ifBlank { tagName }
            }
            val htmlUrl = json.optString("htmlUrl").ifBlank {
                json.optString("html_url").ifBlank { manifestUrl }
            }
            val downloadUrl = json.optString("downloadUrl").ifBlank {
                json.optString("download_url")
            }.trim().ifBlank { null }
            val publishedAtMillis = json.optString("publishedAt")
                .ifBlank { json.optString("published_at") }
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

            if (tagName.isBlank()) return null
            return ReleaseInfo(
                tagName = tagName,
                releaseName = releaseName,
                body = json.optString("body").trim(),
                htmlUrl = htmlUrl,
                downloadUrl = downloadUrl,
                publishedAtMillis = publishedAtMillis
            )
        }
    }

    fun compareVersions(a: String, b: String): Int {
        val left = parseVersion(a)
        val right = parseVersion(b)

        val max = maxOf(left.coreParts.size, right.coreParts.size)
        for (i in 0 until max) {
            val l = left.coreParts.getOrElse(i) { 0 }
            val r = right.coreParts.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        if (left.suffix.isBlank() && right.suffix.isNotBlank()) return 1
        if (left.suffix.isNotBlank() && right.suffix.isBlank()) return -1
        return left.suffix.compareTo(right.suffix, ignoreCase = true)
    }

    private data class ParsedVersion(
        val coreParts: List<Int>,
        val suffix: String
    )

    private fun parseVersion(raw: String): ParsedVersion {
        val withoutPrefix = raw.trim().removePrefix("v").removePrefix("V")
        val core = withoutPrefix.substringBefore('-').substringBefore('+')
        val suffix = withoutPrefix
            .substringAfter('-', missingDelimiterValue = "")
            .substringBefore('+')
            .trim()

        return ParsedVersion(
            coreParts = core
            .split('.')
            .map { part -> part.filter { it.isDigit() } }
            .filter { it.isNotBlank() }
            .mapNotNull { digits -> digits.toIntOrNull() }
            .ifEmpty { listOf(0) },
            suffix = suffix
        )
    }
}
