package com.pocketcraft.server.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketcraft.server.data.api.MojangApiService
import com.pocketcraft.server.data.db.VersionDao
import com.pocketcraft.server.data.model.DownloadState
import com.pocketcraft.server.data.model.MCVersion
import com.pocketcraft.server.data.model.ServerDownload
import com.pocketcraft.server.data.model.VersionDetail
import com.pocketcraft.server.service.ServerFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val Context.versionDataStore by preferencesDataStore(name = "version_prefs")

@Singleton
class VersionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mojangApiService: MojangApiService,
    private val versionDao: VersionDao,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "VersionRepository"
        private val KEY_SELECTED_VERSION = stringPreferencesKey("selected_version_id")
        // Fallback versions shown when offline and Room cache is empty
        private val FALLBACK_VERSIONS = listOf(
            MCVersion("1.21.4", "release", "", "2024-12-03T10:12:57+00:00"),
            MCVersion("1.21.3", "release", "", "2024-11-13T11:29:23+00:00"),
            MCVersion("1.21.1", "release", "", "2024-08-08T12:21:14+00:00"),
            MCVersion("1.20.4", "release", "", "2023-12-07T09:04:55+00:00"),
            MCVersion("1.20.1", "release", "", "2023-06-12T13:08:22+00:00"),
            MCVersion("1.19.4", "release", "", "2023-03-14T12:07:45+00:00"),
        )
    }

    // ---------- Version list ----------

    fun getCachedVersions(): Flow<List<MCVersion>> = versionDao.getAllVersions()

    /** Fetch from Mojang API and refresh the Room cache. Throws on error. */
    suspend fun fetchAndCacheVersions() = withContext(Dispatchers.IO) {
        val manifest = mojangApiService.getVersionManifest()
        val versions = manifest.versions.map { it.toMCVersion() }
        versionDao.deleteAll()
        versionDao.insertAll(versions)
        Log.d(TAG, "Cached ${versions.size} versions from Mojang API")
    }

    /** Returns fallback list if Room & API are unavailable. */
    suspend fun getFallbackVersions(): List<MCVersion> = FALLBACK_VERSIONS

    // ---------- Selected version (DataStore) ----------

    fun getSelectedVersionIdFlow(): Flow<String> =
        context.versionDataStore.data.map { prefs ->
            prefs[KEY_SELECTED_VERSION] ?: "1.20.4"
        }

    suspend fun saveSelectedVersionId(id: String) {
        context.versionDataStore.edit { prefs ->
            prefs[KEY_SELECTED_VERSION] = id
        }
    }

    // ---------- JAR management ----------

    fun getServerJarPath(versionId: String): File =
        ServerFileManager.getServerJarFile(context, versionId)

    fun isServerJarDownloaded(versionId: String): Boolean =
        ServerFileManager.isServerJarReady(context, versionId)

    fun getDownloadedSizeBytes(versionId: String): Long =
        getServerJarPath(versionId).let { if (it.exists()) it.length() else 0L }

    fun deleteServerJar(versionId: String): Boolean =
        ServerFileManager.getServerDir(context, versionId).deleteRecursively()


    /**
     * Download the server JAR for [version].
     * Emits progress 0–100 via [onProgress].
     * Skips download if the JAR already exists and is valid.
     */
    suspend fun downloadServerJar(version: MCVersion, onProgress: suspend (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            onProgress(0)

            // 1. Quick cache check
            if (isServerJarDownloaded(version.id)) {
                onProgress(100)
                return@withContext
            }

            // 2. Fetch the version detail JSON to get the server download URL
            onProgress(5)
            if (version.url.isBlank()) {
                throw Exception("No download URL for version ${version.id} (offline fallback)")
            }
            val detail = fetchVersionDetail(version.url)
            val downloadUrl = detail.downloads.server.url

            // 3. Prepare target dir + file
            val dir = ServerFileManager.getServerDir(context, version.id)
            val targetFile = File(dir, "paper-${version.id}.jar.tmp")
            if (targetFile.exists()) targetFile.delete()

            // 4. Download with progress
            try {
                onProgress(10)
                downloadFile(downloadUrl, targetFile) { percent ->
                    val scaled = 10 + (percent * 0.88).toInt()
                    onProgress(scaled)
                }
                // Rename to final name
                val finalFile = ServerFileManager.getServerJarFile(context, version.id)
                if (finalFile.exists()) finalFile.delete()
                targetFile.renameTo(finalFile)
            } catch (e: Exception) {
                targetFile.delete()
                throw Exception("Download failed for ${version.id}: ${e.message}", e)
            }

            if (!isServerJarDownloaded(version.id)) {
                throw Exception("Downloaded JAR for ${version.id} appears invalid")
            }
            onProgress(100)
        }

    /** Fetch and parse a Mojang version-detail JSON to get the server download info. */
    private suspend fun fetchVersionDetail(url: String): VersionDetail = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(req).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} fetching version detail")
            val body = resp.body?.string() ?: throw Exception("Empty version detail response")
            val json = JSONObject(body)
            val downloads = json.getJSONObject("downloads")
            val server = downloads.getJSONObject("server")
            VersionDetail(
                id = json.getString("id"),
                downloads = com.pocketcraft.server.data.model.Downloads(
                    server = ServerDownload(
                        url = server.getString("url"),
                        size = server.getLong("size")
                    )
                )
            )
        }
    }

    private suspend fun downloadFile(url: String, target: File, onProgress: suspend (Int) -> Unit) {
        val req = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(req).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} downloading JAR")
            val contentLength = resp.body?.contentLength() ?: -1L
            val input = resp.body?.byteStream() ?: throw Exception("No download stream")
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(16384)
                var downloaded = 0L
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    downloaded += bytes
                    if (contentLength > 0) {
                        onProgress(((downloaded * 100) / contentLength).toInt().coerceIn(0, 100))
                    }
                }
                output.flush()
            }
        }
    }
}
