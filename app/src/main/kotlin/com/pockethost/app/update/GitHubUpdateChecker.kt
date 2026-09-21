package com.pockethost.app.update

import android.util.Log
import com.pockethost.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Metadata about a GitHub release, parsed from the GitHub Releases API.
 */
data class GitHubRelease(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val htmlUrl: String,
    val apkDownloadUrl: String,
    val publishedAt: String,
    val releaseNotes: String
)

/**
 * Checks the GitHub Releases API for new versions of the app.
 *
 * Uses [BuildConfig.GITHUB_REPO_OWNER] and [BuildConfig.GITHUB_REPO_NAME] to
 * construct the API URL. Compares the latest release tag against
 * [BuildConfig.VERSION_NAME] using semantic version comparison.
 */
object GitHubUpdateChecker {
    private const val TAG = "GitHubUpdateChecker"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Checks whether a newer release is available on GitHub.
     *
     * @return a [GitHubRelease] if a newer version exists, or `null` if the app
     *         is already up to date (or an error occurred).
     */
    suspend fun checkForUpdate(): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val release = fetchLatestRelease() ?: return@withContext null

            val comparison = compareVersionNames(BuildConfig.VERSION_NAME, release.versionName)
            if (comparison >= 0) {
                Log.d(TAG, "App is up to date (current=${BuildConfig.VERSION_NAME}, latest=${release.versionName})")
                return@withContext null
            }

            Log.d(TAG, "Update available: ${BuildConfig.VERSION_NAME} → ${release.versionName}")
            release
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for update: ${e.message}", e)
            null
        }
    }

    /**
     * Fetches the latest release from GitHub, regardless of whether it's newer.
     * Returns `null` if the API call fails or no APK asset is found.
     */
    suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val owner = BuildConfig.GITHUB_REPO_OWNER
            val repo = BuildConfig.GITHUB_REPO_NAME
            val url = "https://api.github.com/repos/$owner/$repo/releases/latest"

            Log.d(TAG, "Fetching latest release from: $url")

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PocketHost/${BuildConfig.VERSION_NAME} (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "GitHub API returned HTTP ${response.code}: ${response.message}")
                    return@withContext null
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Log.e(TAG, "GitHub API returned empty body")
                    return@withContext null
                }

                parseRelease(JSONObject(body))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching latest release: ${e.message}", e)
            null
        }
    }

    private fun parseRelease(json: JSONObject): GitHubRelease? {
        val tagName = json.optString("tag_name", "").trim()
        if (tagName.isBlank()) {
            Log.w(TAG, "Release has no tag_name")
            return null
        }

        val versionName = normalizeVersionName(tagName)

        val assets: JSONArray = json.optJSONArray("assets") ?: JSONArray()
        var preferredApkUrl: String? = null
        var fallbackApkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                if (name.contains("external", ignoreCase = true)) {
                    preferredApkUrl = asset.optString("browser_download_url", "")
                    break
                } else if (fallbackApkUrl == null) {
                    fallbackApkUrl = asset.optString("browser_download_url", "")
                }
            }
        }
        val apkUrl = preferredApkUrl ?: fallbackApkUrl

        if (apkUrl.isNullOrBlank()) {
            Log.w(TAG, "No APK asset found in release $tagName")
            return null
        }

        return GitHubRelease(
            tagName = tagName,
            versionName = versionName,
            releaseName = json.optString("name", tagName),
            htmlUrl = json.optString("html_url", ""),
            apkDownloadUrl = apkUrl,
            publishedAt = json.optString("published_at", ""),
            releaseNotes = json.optString("body", "").trim()
        )
    }

    // ── Version comparison (mirrors UpdateManager logic) ──

    private fun normalizeVersionName(versionName: String): String {
        return versionName.trim().removePrefix("v").removePrefix("V")
    }

    /**
     * Compares two semantic version strings.
     * Returns negative if [current] < [target], zero if equal, positive if [current] > [target].
     */
    internal fun compareVersionNames(current: String, target: String): Int {
        val currentParts = normalizeVersionName(current).split('.', '-', '_').filter { it.isNotBlank() }
        val targetParts = normalizeVersionName(target).split('.', '-', '_').filter { it.isNotBlank() }
        val maxParts = maxOf(currentParts.size, targetParts.size)

        for (index in 0 until maxParts) {
            val currentPart = currentParts.getOrNull(index).orEmpty()
            val targetPart = targetParts.getOrNull(index).orEmpty()
            val cmp = compareVersionPart(currentPart, targetPart)
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun compareVersionPart(currentPart: String, targetPart: String): Int {
        val currentNumber = currentPart.trim().toIntOrNull()
        val targetNumber = targetPart.trim().toIntOrNull()
        return when {
            currentNumber != null && targetNumber != null -> currentNumber.compareTo(targetNumber)
            else -> currentPart.compareTo(targetPart)
        }
    }
}
