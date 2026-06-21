package com.pocketcraft.server.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.data.repository.ServerConfigRepository
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.service.ServerPropertiesHelper
import com.pocketcraft.server.service.VersionCatalog
import com.pocketcraft.server.server.ServerJarManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ServerTypeVersionViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _selectedType = MutableStateFlow(ServerType.PAPER)
    val selectedType: StateFlow<ServerType> = _selectedType.asStateFlow()

    private val _availableVersions = MutableStateFlow<List<String>>(emptyList())
    val availableVersions: StateFlow<List<String>> = _availableVersions.asStateFlow()

    private val _selectedVersion = MutableStateFlow<String?>(null)
    val selectedVersion: StateFlow<String?> = _selectedVersion.asStateFlow()

    private val _customJarPath = MutableStateFlow<String?>(null)
    val customJarPath: StateFlow<String?> = _customJarPath.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _showRcVersions = MutableStateFlow(false)
    val showRcVersions: StateFlow<Boolean> = _showRcVersions.asStateFlow()

    private val _downloadedVersions = MutableStateFlow<Set<String>>(emptySet())
    val downloadedVersions: StateFlow<Set<String>> = _downloadedVersions.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private var initializedSelectionKey: Triple<ServerType, String?, String?>? = null
    private val quickVersionFallbacks = listOf(
        "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
        "1.20.6", "1.20.4", "1.20.2", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.17.1", "1.16.5"
    )

    init {
        viewModelScope.launch {
            val savedVersion = loadSavedServerVersion()
            if (!savedVersion.isNullOrBlank()) {
                _selectedVersion.value = savedVersion
            }
            AppPreferencesStore.showRcVersionsFlow(getApplication()).collect { showRc ->
                _showRcVersions.value = showRc
                if (_selectedType.value.supportsVersionSelect) {
                    fetchVersionsForType(_selectedType.value, preferredVersion = _selectedVersion.value)
                }
            }
        }
        fetchVersionsForType(ServerType.PAPER, preferredVersion = null)
        refreshDownloadedVersions()
    }

    fun setServerType(type: ServerType) {
        _selectedType.value = type
        if (type.supportsVersionSelect) {
            _selectedVersion.value = null
            fetchVersionsForType(type)
        } else {
            _availableVersions.value = emptyList()
            _selectedVersion.value = null
            refreshDownloadedVersions()
        }
    }

    fun initializeSelection(
        serverType: ServerType,
        gameVersion: String?,
        customJarPath: String?
    ) {
        val key = Triple(serverType, gameVersion, customJarPath)
        if (initializedSelectionKey == key) return
        initializedSelectionKey = key
        _selectedType.value = serverType
        _customJarPath.value = customJarPath
        if (serverType.supportsVersionSelect) {
            _selectedVersion.value = gameVersion?.takeIf { it.isNotBlank() }
            fetchVersionsForType(serverType, preferredVersion = gameVersion)
        } else {
            _selectedVersion.value = null
            _availableVersions.value = emptyList()
            refreshDownloadedVersions()
        }
    }

    fun setSelectedVersion(version: String) {
        _selectedVersion.value = version
    }

    fun onServerJarImported(version: String) {
        _selectedVersion.value = version
        refreshDownloadedVersions()
    }

    fun setCustomJarPath(path: String) {
        _customJarPath.value = path
    }

    fun isVersionDownloaded(version: String): Boolean {
        return _downloadedVersions.value.contains(version)
    }

    fun deleteDownloadedVersion(version: String) {
        viewModelScope.launch {
            runCatching {
                val versionDir = File(getApplication<Application>().filesDir, "servers/binaries/$version")
                val file = File(versionDir, jarNameForVersion(_selectedType.value, version))
                if (file.exists()) {
                    file.delete()
                }
            }.onFailure {
                _error.value = it.message ?: "Could not delete downloaded version"
            }
            refreshDownloadedVersions()
        }
    }

    private fun refreshDownloadedVersions() {
        viewModelScope.launch {
            _downloadedVersions.value = listDownloadedVersionsForType(_selectedType.value).toSet()
        }
    }

    fun refresh() {
        fetchVersionsForType(_selectedType.value)
    }

    private fun fetchVersionsForType(type: ServerType, preferredVersion: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Step 1: Try cache first (Instant UI)
            val cached = withContext(Dispatchers.IO) {
                ServerJarManager.fetchAvailableVersions(getApplication(), type, forceRefresh = false)
            }
            if (cached.isNotEmpty()) {
                updateVersionList(cached, preferredVersion)
            }

            // Step 2: Network check
            val online = com.pocketcraft.server.util.NetworkUtils.isOnline(getApplication())
            _isOffline.value = !online
            if (!online) {
                if (_availableVersions.value.isEmpty()) {
                    val fallback = quickFallbackVersions(type)
                    if (fallback.isNotEmpty()) {
                        updateVersionList(fallback, preferredVersion)
                    } else {
                        _error.value = "No internet connection. Please check your network and try again."
                    }
                }
                _isLoading.value = false
                return@launch
            }

            // Step 3: Background refresh (Force network)
            try {
                val networkVersions = withContext(Dispatchers.IO) {
                    ServerJarManager.fetchAvailableVersions(getApplication(), type, forceRefresh = true)
                }
                
                if (networkVersions.isNotEmpty()) {
                    updateVersionList(networkVersions, preferredVersion)
                } else if (_availableVersions.value.isEmpty()) {
                    val fallback = withContext(Dispatchers.IO) { quickFallbackVersions(type) }
                    if (fallback.isNotEmpty()) {
                        updateVersionList(fallback, preferredVersion)
                    }
                }
            } catch (e: Exception) {
                if (_availableVersions.value.isEmpty()) {
                    val fallback = withContext(Dispatchers.IO) { quickFallbackVersions(type) }
                    if (fallback.isNotEmpty()) {
                        updateVersionList(fallback, preferredVersion)
                    } else {
                        _error.value = "No internet connection. Please check your network and try again."
                    }
                } else {
                    // Log but don't show error if we already have cached data
                    android.util.Log.w("ServerTypeVersionViewModel", "Background refresh failed: ${e.message}")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateVersionList(versions: List<String>, preferredVersion: String?) {
        val filteredVersions = if (_showRcVersions.value) {
            versions
        } else {
            versions.filterNot { isPreReleaseVersion(it) }
        }
        
        // If offline, only show downloaded versions
        val finalVersions = if (_isOffline.value) {
            val downloaded = listDownloadedVersionsForType(_selectedType.value)
            filteredVersions.filter { downloaded.contains(it) }
        } else {
            filteredVersions
        }

        _availableVersions.value = finalVersions
        
        val previousSelection = _selectedVersion.value
        _selectedVersion.value = when {
            preferredVersion != null && preferredVersion.isNotBlank() -> preferredVersion
            previousSelection != null && finalVersions.contains(previousSelection) -> previousSelection
            else -> null
        }
        refreshDownloadedVersions()
    }

    private suspend fun loadSavedServerVersion(): String? {
        val fromConfig = runCatching {
            ServerConfigRepository(getApplication()).loadConfig().gameVersion
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (fromConfig != null) return fromConfig

        return AppPreferencesStore.getSelectedVersionFlow(getApplication())
            .first()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun quickFallbackVersions(type: ServerType): List<String> {
        if (!type.supportsVersionSelect) return emptyList()
        val downloaded = listDownloadedVersionsForType(type)
        if (downloaded.isNotEmpty()) return downloaded

        val catalogVersions = runCatching { VersionCatalog.fetchStableVersions(limit = 24) }
            .getOrDefault(emptyList())
        return if (catalogVersions.isNotEmpty()) {
            catalogVersions
        } else {
            quickVersionFallbacks
        }
    }

    private fun isPreReleaseVersion(version: String): Boolean {
        val preReleasePattern = Regex(
            ".*(?:^|[.-])(rc|pre|beta|snapshot|alpha)(?:[.-]?\\d+)?(?:$|[.-]).*",
            RegexOption.IGNORE_CASE
        )
        return preReleasePattern.matches(version.lowercase())
    }

    private fun jarNameForVersion(type: ServerType, version: String): String {
        return "${type.name.lowercase()}-$version.jar"
    }

    private fun listDownloadedVersionsForType(type: ServerType): List<String> {
        if (type == ServerType.MODPACK) {
            return listDownloadedModpackIds()
        }

        val serversDir = File(getApplication<Application>().filesDir, "servers/binaries")
        if (!serversDir.exists()) return emptyList()
        
        val typeLower = type.name.lowercase()
        return serversDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { versionDir ->
            val version = versionDir.name
            val jarFile = File(versionDir, "$typeLower-$version.jar")
            val minBytes = if (typeLower == "fabric") 10_000L else 1_000_000L
            if (jarFile.exists() && jarFile.isFile && jarFile.length() > minBytes) version else null
        }?.distinct()?.sortedDescending() ?: emptyList()
    }

    private fun listDownloadedModpackIds(): List<String> {
        val worldsRoot = File(getApplication<Application>().filesDir, "servers/worlds")
        if (!worldsRoot.isDirectory) return emptyList()

        return worldsRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .mapNotNull { worldDir ->
                val props = ServerPropertiesHelper.readProperties(worldDir)
                if (ServerType.fromString(props.getProperty("pocketcraft-server-type")) != ServerType.MODPACK) {
                    return@mapNotNull null
                }
                val modpackId = props.getProperty("pocketcraft-modpack-id")
                    ?: props.getProperty("pocketcraft-custom-jar-path")
                    ?: return@mapNotNull null
                val launchTarget = ServerFileManager.readLaunchTarget(worldDir) ?: return@mapNotNull null
                if (modpackId.isNotBlank() && launchTarget.file.isFile && launchTarget.file.length() > 0L) {
                    modpackId
                } else {
                    null
                }
            }
            .distinct()
            .sorted()
            .toList()
    }
}
