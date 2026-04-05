package com.pocketcraft.server.broadcast

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import java.util.Date

data class BroadcastMessage(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "info",
    val createdAt: Date? = null,
    val expiresAt: Date? = null,
    val targetMinVersion: Int = 0,
    val dismissible: Boolean = true,
    val active: Boolean = true
)

object BroadcastManager {
    private val db = FirebaseFirestore.getInstance()
    private val remoteConfig = FirebaseRemoteConfig.getInstance()
    private const val BROADCAST_REMOTE_CONFIG_KEY = "broadcast_banner"

    fun getBroadcastsFlow(appVersionCode: Int): Flow<List<BroadcastMessage>> = callbackFlow {
        val listener = db.collection("broadcasts")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val now = Date()
                val messages = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title").orEmpty()
                    val body = doc.getString("body").orEmpty()
                    if (title.isBlank() && body.isBlank()) return@mapNotNull null

                    val expiresAt = doc.getTimestamp("expiresAt")?.toDate()
                    val targetMinVersion = (doc.getLong("targetMinVersion") ?: 0L).toInt()
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()
                    val dismissible = doc.getBoolean("dismissible") ?: true
                    val active = doc.getBoolean("active") ?: true
                    val type = doc.getString("type").normalizeType()

                    if (expiresAt != null && expiresAt.before(now)) return@mapNotNull null
                    if (targetMinVersion > appVersionCode) return@mapNotNull null
                    if (!active) return@mapNotNull null

                    BroadcastMessage(
                        id = doc.id,
                        title = title,
                        body = body,
                        type = type,
                        createdAt = createdAt,
                        expiresAt = expiresAt,
                        targetMinVersion = targetMinVersion,
                        dismissible = dismissible,
                        active = active
                    )
                }.sortedByDescending { it.createdAt ?: Date(0) }
                trySend(messages)
            }

        awaitClose { listener.remove() }
    }

    fun initRemoteConfig(onBannerFetched: (BroadcastMessage?) -> Unit) {
        val settings = remoteConfigSettings { minimumFetchIntervalInSeconds = 60 }
        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(mapOf(BROADCAST_REMOTE_CONFIG_KEY to ""))

        fetchRemoteConfigBanner(onBannerFetched)
    }

    fun refreshRemoteConfig(onBannerFetched: (BroadcastMessage?) -> Unit) {
        fetchRemoteConfigBanner(onBannerFetched)
    }

    private fun fetchRemoteConfigBanner(onBannerFetched: (BroadcastMessage?) -> Unit) {
        remoteConfig.setDefaultsAsync(mapOf(BROADCAST_REMOTE_CONFIG_KEY to ""))

        remoteConfig.fetchAndActivate().addOnCompleteListener {
            onBannerFetched(parseRemoteConfigBanner())
        }
    }

    private fun parseRemoteConfigBanner(): BroadcastMessage? {
        val raw = remoteConfig.getString(BROADCAST_REMOTE_CONFIG_KEY)
        if (raw.isBlank()) return null

        return runCatching {
            val json = JSONObject(raw)
            val title = json.optString("title").trim()
            val body = json.optString("body").trim()
            if (title.isBlank() && body.isBlank()) {
                null
            } else {
                BroadcastMessage(
                    id = "remote_config_banner",
                    title = title,
                    body = body,
                    type = json.optString("type", "info").normalizeType(),
                    createdAt = Timestamp.now().toDate(),
                    dismissible = true,
                    active = true
                )
            }
        }.getOrNull()
    }

    private fun String?.normalizeType(): String {
        return when (this?.lowercase()?.trim()) {
            "warning" -> "warning"
            "critical" -> "critical"
            else -> "info"
        }
    }
}
