package com.pockethost.app.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig

object FeatureGate {
    private val remoteConfig: FirebaseRemoteConfig
        get() = FirebaseRemoteConfig.getInstance()

    fun isBedrockCrossplayEnabled(): Boolean {
        return remoteConfig.getBoolean(RemoteConfigKeys.FEATURE_BEDROCK_CROSSPLAY_ENABLED)
    }

    fun isSubdomainRoutingEnabled(): Boolean {
        return remoteConfig.getBoolean(RemoteConfigKeys.FEATURE_SUBDOMAIN_ROUTING_ENABLED)
    }

    fun getDisabledRegions(): Set<String> {
        val raw = remoteConfig.getString(RemoteConfigKeys.RELAY_REGION_DISABLED_LIST)
        if (raw.isBlank()) return emptySet()
        return raw.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun isBelowMinSupportedVersion(currentVersionCode: Int): Boolean {
        return currentVersionCode < getMinSupportedVersionCode()
    }

    fun isBelowRecommendedVersion(currentVersionCode: Int): Boolean {
        return currentVersionCode < getRecommendedVersionCode()
    }

    fun getMinSupportedVersionCode(): Int {
        return remoteConfig.getLong(RemoteConfigKeys.MIN_SUPPORTED_VERSION_CODE).toInt()
    }

    fun getRecommendedVersionCode(): Int {
        return remoteConfig.getLong(RemoteConfigKeys.RECOMMENDED_VERSION_CODE).toInt()
    }

    fun getReviewPromptMinStarts(): Int {
        return remoteConfig.getLong(RemoteConfigKeys.REVIEW_PROMPT_MIN_SUCCESSFUL_STARTS).toInt()
    }

    fun isReviewPromptEnabled(): Boolean {
        return remoteConfig.getBoolean(RemoteConfigKeys.REVIEW_PROMPT_ENABLED)
    }
}
