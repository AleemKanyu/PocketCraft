package com.pocketcraft.server.broadcast

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
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
    private const val BROADCAST_CACHE_PREFS = "broadcast_cache"
    private const val BROADCAST_CACHE_KEY = "cached_broadcasts_json"

    fun getBroadcastsFlow(context: Context, appVersionCode: Int): Flow<List<BroadcastMessage>> = callbackFlow {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(BROADCAST_CACHE_PREFS, Context.MODE_PRIVATE)
        val hasCache = prefs.contains(BROADCAST_CACHE_KEY)
        
        if (hasCache) {
            trySend(loadCachedBroadcasts(appContext, appVersionCode))
        } else {
            trySend(listOf(defaultOfflineBroadcast()))
        }

        val listener = db.collection("broadcasts")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w("BroadcastManager", "Broadcast snapshot unavailable: ${error?.message ?: "null snapshot"}")
                    val cached = loadCachedBroadcasts(appContext, appVersionCode)
                    if (cached.isNotEmpty()) {
                        Log.i("BroadcastManager", "Using ${cached.size} cached broadcast(s) while Firestore is unavailable.")
                        trySend(cached)
                    } else {
                        val fallback = listOf(defaultOfflineBroadcast())
                        Log.i("BroadcastManager", "Using built-in offline fallback broadcast.")
                        trySend(fallback)
                    }
                    return@addSnapshotListener
                }

                val now = Date()
                val messages = snapshot.documents.mapNotNull { doc ->
                    val title = doc.firstString("title", "headline", "name")
                        .orEmpty()
                        .trim()
                        .ifBlank { "Important Broadcast" }
                    val body = doc.firstString("body", "message", "description", "text", "content")
                        .orEmpty()
                        .trim()
                        .ifBlank { "Hosted on PocketCraft !" }

                    val expiresAt = doc.firstDate("expiresAt", "expiry", "expires")
                    val targetMinVersion = doc.firstInt("targetMinVersion", "minVersion", "versionCode")
                    val createdAt = doc.firstDate("createdAt", "created", "timestamp")
                    val dismissible = doc.firstBoolean("dismissible", "canDismiss", "dismissable") ?: true
                    val active = doc.firstBoolean("active", "enabled", "show") ?: true
                    val type = doc.firstString("type", "severity", "category").normalizeType(default = "warning")

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
                cacheBroadcasts(appContext, messages)
                Log.i("BroadcastManager", "Loaded ${messages.size} active broadcast(s) from Firestore.")
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
            val active = json.optBoolean("active", true)
            if (!active) return null

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
                    dismissible = json.optBoolean("dismissible", true),
                    active = true
                )
            }
        }.getOrNull()
    }

    private fun defaultOfflineBroadcast(): BroadcastMessage {
        return BroadcastMessage(
            id = "offline_maintenance_banner",
            title = "Mantainance Break !",
            body = "You might face server disconnections for some time ,so play on wifi for now",
            type = "warning",
            createdAt = Date(),
            dismissible = false,
            active = true
        )
    }

    private fun cacheBroadcasts(context: Context, messages: List<BroadcastMessage>) {
        val payload = JSONArray().apply {
            messages.forEach { message ->
                put(JSONObject().apply {
                    put("id", message.id)
                    put("title", message.title)
                    put("body", message.body)
                    put("type", message.type)
                    put("createdAt", message.createdAt?.time ?: JSONObject.NULL)
                    put("expiresAt", message.expiresAt?.time ?: JSONObject.NULL)
                    put("targetMinVersion", message.targetMinVersion)
                    put("dismissible", message.dismissible)
                    put("active", message.active)
                })
            }
        }.toString()

        context.getSharedPreferences(BROADCAST_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(BROADCAST_CACHE_KEY, payload)
            .apply()
    }

    private fun loadCachedBroadcasts(context: Context, appVersionCode: Int): List<BroadcastMessage> {
        val raw = context.getSharedPreferences(BROADCAST_CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(BROADCAST_CACHE_KEY, null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val now = Date()
            JSONArray(raw).let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val json = array.optJSONObject(index) ?: continue
                        val createdAt = json.optLongOrNull("createdAt")?.let { Date(it) }
                        val expiresAt = json.optLongOrNull("expiresAt")?.let { Date(it) }
                        val targetMinVersion = json.optInt("targetMinVersion", 0)
                        if (expiresAt != null && expiresAt.before(now)) continue
                        if (targetMinVersion > appVersionCode) continue

                        add(
                            BroadcastMessage(
                                id = json.optString("id").ifBlank { "cached_broadcast_$index" },
                                title = json.optString("title").ifBlank { "Important Broadcast" },
                                body = json.optString("body").ifBlank { "Hosted on PocketCraft !" },
                                type = json.optString("type").normalizeType(default = "warning"),
                                createdAt = createdAt,
                                expiresAt = expiresAt,
                                targetMinVersion = targetMinVersion,
                                dismissible = json.optBoolean("dismissible", true),
                                active = json.optBoolean("active", true)
                            )
                        )
                    }
                }.sortedByDescending { it.createdAt ?: Date(0) }
            }
        }.getOrElse {
            Log.w("BroadcastManager", "Failed to decode cached broadcasts: ${it.message}")
            emptyList()
        }
    }

    private fun String?.normalizeType(default: String = "info"): String {
        return when (this?.lowercase()?.trim()) {
            "warning" -> "warning"
            "critical" -> "critical"
            "info" -> "info"
            else -> default
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun DocumentSnapshot.firstString(vararg keys: String): String? {
        return keys.asSequence()
            .mapNotNull { key -> getString(key)?.trim()?.takeIf { it.isNotBlank() } }
            .firstOrNull()
    }

    private fun DocumentSnapshot.firstBoolean(vararg keys: String): Boolean? {
        return keys.asSequence()
            .mapNotNull { key ->
                when (val value = get(key)) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    is String -> when (value.trim().lowercase()) {
                        "true", "1", "yes", "on" -> true
                        "false", "0", "no", "off" -> false
                        else -> null
                    }
                    else -> null
                }
            }
            .firstOrNull()
    }

    private fun DocumentSnapshot.firstInt(vararg keys: String): Int {
        return keys.asSequence()
            .mapNotNull { key ->
                when (val value = get(key)) {
                    is Number -> value.toInt()
                    is String -> value.trim().toIntOrNull()
                    else -> null
                }
            }
            .firstOrNull() ?: 0
    }

    private fun DocumentSnapshot.firstDate(vararg keys: String): Date? {
        return keys.asSequence()
            .mapNotNull { key ->
                when (val value = get(key)) {
                    is Timestamp -> value.toDate()
                    is Date -> value
                    is Number -> Date(value.toLong())
                    else -> null
                }
            }
            .firstOrNull()
    }
}
