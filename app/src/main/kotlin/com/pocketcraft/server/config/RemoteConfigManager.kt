package com.pocketcraft.server.config

import android.content.Context
import com.pocketcraft.server.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import com.pocketcraft.server.R

object RemoteConfigManager {
    private val _showDiscordButton = MutableStateFlow(true)
    private val _showInstagramButton = MutableStateFlow(false)
    
    val showDiscordButton: Flow<Boolean> = _showDiscordButton.asStateFlow()
    val showInstagramButton: Flow<Boolean> = _showInstagramButton.asStateFlow()
    
    private var isInitialized = false
    
    suspend fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            
            // Set default values
            remoteConfig.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else 3600)
                    .build()
            ).await()
            
            // Set in-app defaults
            remoteConfig.setDefaultsAsync(
                mapOf(
                    "show_discord_button" to true,
                    "show_instagram_button" to false
                )
            ).await()
            
            // Fetch and activate remote config
            remoteConfig.fetchAndActivate().await()
            
            // Update state
            _showDiscordButton.value = remoteConfig.getBoolean("show_discord_button")
            _showInstagramButton.value = remoteConfig.getBoolean("show_instagram_button")
            
            isInitialized = true
        } catch (e: Exception) {
            // If remote config fails, use defaults (already set)
            isInitialized = true
        }
    }
    
    suspend fun refreshConfig() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            remoteConfig.fetchAndActivate().await()
            
            _showDiscordButton.value = remoteConfig.getBoolean("show_discord_button")
            _showInstagramButton.value = remoteConfig.getBoolean("show_instagram_button")
        } catch (e: Exception) {
            // Keep existing values on error
        }
    }
    
    fun getShowDiscordButtonSync(): Boolean {
        return FirebaseRemoteConfig.getInstance().getBoolean("show_discord_button")
    }
    
    fun getShowInstagramButtonSync(): Boolean {
        return FirebaseRemoteConfig.getInstance().getBoolean("show_instagram_button")
    }
}
