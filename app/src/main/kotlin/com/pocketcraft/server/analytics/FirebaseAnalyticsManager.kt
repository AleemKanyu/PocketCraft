package com.pocketcraft.server.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Manages Firebase Analytics events and user properties throughout the app.
 * Handles event tracking with proper error handling.
 */
object FirebaseAnalyticsManager {
    private var analytics: FirebaseAnalytics? = null

    fun initialize(context: Context, collectionEnabled: Boolean = true) {
        try {
            analytics = Firebase.analytics
            analytics?.setAnalyticsCollectionEnabled(collectionEnabled)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setCollectionEnabled(enabled: Boolean) {
        try {
            analytics?.setAnalyticsCollectionEnabled(enabled)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Track a simple event with optional parameters
     */
    fun logEvent(eventName: String, params: Map<String, String>? = null) {
        try {
            val bundle = Bundle().apply {
                params?.forEach { (key, value) ->
                    putString(key, value)
                }
            }
            analytics?.logEvent(eventName, bundle)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Track server-related events
     */
    fun logServerStarted(versionId: String, maxPlayers: Int) {
        logEvent("server_started", mapOf(
            "version_id" to versionId,
            "max_players" to maxPlayers.toString()
        ))
    }

    fun logServerStopped(versionId: String, duration: Long) {
        logEvent("server_stopped", mapOf(
            "version_id" to versionId,
            "duration_seconds" to duration.toString()
        ))
    }

    fun logServerCrashed(versionId: String, errorMessage: String?) {
        logEvent("server_crashed", mapOf(
            "version_id" to versionId,
            "error" to (errorMessage ?: "unknown")
        ))
    }

    fun logPlayerJoined(playerName: String, totalPlayersOnline: Int) {
        logEvent("player_joined", mapOf(
            "player_name" to playerName,
            "total_players" to totalPlayersOnline.toString()
        ))
    }

    fun logPlayerLeft(playerName: String) {
        logEvent("player_left", mapOf(
            "player_name" to playerName
        ))
    }

    fun logBackupCreated(worldName: String, backupSize: Long) {
        logEvent("backup_created", mapOf(
            "world_name" to worldName,
            "backup_size_mb" to (backupSize / (1024 * 1024)).toString()
        ))
    }

    fun logBackupRestored(worldName: String, backupName: String) {
        logEvent("backup_restored", mapOf(
            "world_name" to worldName,
            "backup_name" to backupName
        ))
    }

    fun logSettingsChanged(settingName: String, value: String) {
        logEvent("setting_changed", mapOf(
            "setting_name" to settingName,
            "value" to value
        ))
    }

    fun logThemeChanged(theme: String) {
        logEvent("theme_changed", mapOf(
            "theme" to theme
        ))
    }

    fun logModInstalled(modName: String, source: String) {
        logEvent("mod_installed", mapOf(
            "mod_name" to modName,
            "source" to source
        ))
    }

    fun logPlayerWhitelistAdded(playerName: String) {
        logEvent("player_whitelist_added", mapOf(
            "player_name" to playerName
        ))
    }

    fun logPlayerWhitelistRemoved(playerName: String) {
        logEvent("player_whitelist_removed", mapOf(
            "player_name" to playerName
        ))
    }

    /**
     * Set user properties for analytics segmentation
     */
    fun setUserProperty(propertyName: String, value: String) {
        try {
            analytics?.setUserProperty(propertyName, value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setRelayServer(serverLocation: String) {
        setUserProperty("relay_server", serverLocation)
    }

    fun setMinecraftVersion(version: String) {
        setUserProperty("minecraft_version", version)
    }
}
