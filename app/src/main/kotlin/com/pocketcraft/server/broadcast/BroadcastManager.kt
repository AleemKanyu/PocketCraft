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
    val active: Boolean = false,
    val title: String = "",
    val body: String = "",
    val type: String = "info",
    val dismissible: Boolean = true,
    val createdAt: Timestamp? = null,
    val targetMinVersion: Int = 0
)

object BroadcastManager {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val remoteConfig by lazy { FirebaseRemoteConfig.getInstance() }
    private const val BROADCAST_REMOTE_CONFIG_KEY = "broadcast_banner"
    private const val BROADCAST_CACHE_PREFS = "broadcast_cache"
    private const val BROADCAST_CACHE_KEY = "cached_broadcasts_json"

    fun getBroadcastsFlow(context: Context, appVersionCode: Int): Flow<List<BroadcastMessage>> = callbackFlow {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(BROADCAST_CACHE_PREFS, Context.MODE_PRIVATE)
        
        // Clear cached broadcasts on startup before Firestore fetch completes to avoid displaying stale/disabled warnings
        prefs.edit().remove(BROADCAST_CACHE_KEY).apply()
        trySend(emptyList())

        val listener = db.collection("broadcasts")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w("BroadcastManager", "Broadcast snapshot unavailable: ${error?.message ?: "null snapshot"}")
                    val cached = loadCachedBroadcasts(appContext, appVersionCode)
                    if (cached.isNotEmpty()) {
                        Log.i("BroadcastManager", "Using ${cached.size} cached broadcast(s) while Firestore is unavailable.")
                        trySend(cached)
                    }
                    return@addSnapshotListener
                }

                Log.d("BroadcastManager", "Fetched broadcasts snapshot from Firestore. Document count: ${snapshot.documents.size}")

                val messages = snapshot.documents.mapNotNull { doc ->
                    try {
                        val active = doc.firstBoolean("active", "enabled", "show") == true
                        val title = doc.firstString("title") ?: ""
                        val body = doc.firstString("body") ?: ""
                        val type = (doc.firstString("type") ?: "info").normalizeType()
                        val dismissible = doc.firstBoolean("dismissible") ?: true
                        val targetMinVersion = doc.firstInt("targetMinVersion")
                        val createdAtDate = doc.firstDate("createdAt")
                        val createdAt = createdAtDate?.let { Timestamp(it) }

                        val msg = BroadcastMessage(
                            id = doc.id,
                            active = active,
                            title = title,
                            body = body,
                            type = type,
                            dismissible = dismissible,
                            createdAt = createdAt,
                            targetMinVersion = targetMinVersion
                        )
                        Log.d("BroadcastManager", "Parsed broadcast document: id=${doc.id}, active=$active, title='$title', type='$type', dismissible=$dismissible, targetMinVersion=$targetMinVersion")
                        msg
                    } catch (e: Exception) {
                        Log.e("BroadcastManager", "Failed parsing broadcast document ${doc.id}: ${e.message}", e)
                        null
                    }
                }.filter { msg ->
                    val matchesVersion = appVersionCode >= msg.targetMinVersion
                    val isToShow = msg.active && matchesVersion

                    Log.d("BroadcastManager", "Broadcast decision for ${msg.id}: active=${msg.active}, appVersionCode=$appVersionCode, targetMinVersion=${msg.targetMinVersion}, matchesVersion=$matchesVersion -> show=$isToShow")
                    if (!msg.active) {
                        Log.d("BroadcastManager", "Skipped broadcast ${msg.id}: active flag is false.")
                    } else if (!matchesVersion) {
                        Log.d("BroadcastManager", "Skipped broadcast ${msg.id}: appVersionCode $appVersionCode < targetMinVersion ${msg.targetMinVersion}.")
                    } else {
                        Log.d("BroadcastManager", "Showing broadcast ${msg.id} now...")
                    }
                    isToShow
                }.sortedByDescending { it.createdAt?.seconds ?: 0 }

                cacheBroadcasts(appContext, messages)
                Log.i("BroadcastManager", "Loaded ${messages.size} active broadcast(s) from Firestore after filtering.")
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
            val active = json.optBoolean("active", false) || json.optBoolean("enabled", false)
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
                    createdAt = Timestamp.now(),
                    dismissible = json.optBoolean("dismissible", true),
                    active = true
                )
            }
        }.getOrNull()
    }



    private fun cacheBroadcasts(context: Context, messages: List<BroadcastMessage>) {
        val payload = JSONArray().apply {
            messages.forEach { message ->
                put(JSONObject().apply {
                    put("id", message.id)
                    put("title", message.title)
                    put("body", message.body)
                    put("type", message.type)
                    put("createdAt", message.createdAt?.seconds ?: JSONObject.NULL)
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
            JSONArray(raw).let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val json = array.optJSONObject(index) ?: continue
                        val createdAt = json.optLongOrNull("createdAt")?.let { Timestamp(it, 0) }
                        val targetMinVersion = json.optInt("targetMinVersion", 0)
                        val active = json.optBoolean("active", true)
                        if (!active || targetMinVersion > appVersionCode) continue

                        add(
                            BroadcastMessage(
                                id = json.optString("id").ifBlank { "cached_broadcast_$index" },
                                title = json.optString("title").ifBlank { "Important Broadcast" },
                                body = json.optString("body").ifBlank { "Hosted on PocketCraft !" },
                                type = json.optString("type").normalizeType(default = "warning"),
                                createdAt = createdAt,
                                targetMinVersion = targetMinVersion,
                                dismissible = json.optBoolean("dismissible", true),
                                active = json.optBoolean("active", true)
                            )
                        )
                    }
                }.sortedByDescending { it.createdAt?.seconds ?: 0 }
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
