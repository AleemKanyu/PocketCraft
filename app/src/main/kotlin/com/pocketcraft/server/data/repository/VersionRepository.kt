package com.pocketcraft.server.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketcraft.server.data.api.MojangApiService
import com.pocketcraft.server.data.db.VersionDao
import com.pocketcraft.server.data.model.MCVersion
import com.pocketcraft.server.service.ServerFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
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
        ServerFileManager.getServerJarFile(context, versionId, com.pocketcraft.server.data.model.ServerType.PAPER)

    fun isServerJarDownloaded(versionId: String): Boolean =
        ServerFileManager.isServerJarReady(context, versionId, com.pocketcraft.server.data.model.ServerType.PAPER)

    fun getDownloadedSizeBytes(versionId: String): Long =
        getServerJarPath(versionId).let { if (it.exists()) it.length() else 0L }

    fun deleteServerJar(versionId: String): Boolean =
        ServerFileManager.getServerDir(context, versionId).deleteRecursively()


    /**
     * Runtime server JAR downloads are disabled for Google Play policy compliance.
     * Import server JARs through the SAF file picker instead.
     */
    suspend fun downloadServerJar(version: MCVersion, onProgress: suspend (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            onProgress(0)
            if (isServerJarDownloaded(version.id)) {
                onProgress(100)
                return@withContext
            }
            throw IllegalStateException("Server JAR downloads are disabled. Select ${version.id} in the version picker and import the downloaded JAR from device storage.")
        }
}
