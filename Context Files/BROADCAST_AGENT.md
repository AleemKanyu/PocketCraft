# BROADCAST_AGENT.md
# PocketCraft — Admin Broadcast Messaging System

## Overview

Implement a three-layer broadcast messaging system that lets the admin (Aleem) send custom messages to all PocketCraft users. The system uses:

1. **FCM Push Notifications** — real-time push even when app is closed
2. **Firebase Remote Config** — persistent in-app banner on app open
3. **Firestore `broadcasts` collection** — real-time in-app message cards with full control

All three layers are controlled from Firebase Console (no separate admin panel needed). The app displays messages automatically.

---

## Firebase Setup (Console — Done Manually Before Coding)

### Firestore Collection Structure

Create a collection called `broadcasts`. Each document represents one message.

Document fields:

```
broadcasts/{auto-id}
  title: String          — headline, e.g. "Scheduled Maintenance"
  body: String           — full message text
  type: String           — "info" | "warning" | "critical"
  createdAt: Timestamp   — when it was created
  expiresAt: Timestamp   — message auto-hides after this (optional, null = never)
  targetMinVersion: Int  — min app versionCode to show this to (optional, 0 = all)
  dismissible: Boolean   — whether user can X it away
  active: Boolean        — set to false to hide without deleting
```

To broadcast: go to Firestore Console → broadcasts → Add Document → fill fields → `active: true`.

### Remote Config Key

In Firebase Console → Remote Config, add a new parameter:

- Key: `broadcast_banner`
- Default value: `""` (empty string = no banner)
- Format: JSON string → `{"title":"...", "body":"...", "type":"info"}`

To broadcast: update the value, publish. App picks it up within 1 hour (or on next cold start).

### FCM Topic

All users will be subscribed to the topic `all_users` on app start. To send a push:
Firebase Console → Cloud Messaging → New Campaign → Topic → `all_users`.

---

## Android Implementation

### 1. Dependencies

Ensure these are in `app/build.gradle` (likely already present from FIREBASE_AGENT):

```kotlin
implementation("com.google.firebase:firebase-firestore-ktx")
implementation("com.google.firebase:firebase-messaging-ktx")
implementation("com.google.firebase:firebase-config-ktx")
implementation("com.google.firebase:firebase-inappmessaging-display-ktx")
```

---

### 2. New File: `BroadcastManager.kt`

Create at: `app/src/main/java/com/pocketcraft/server/broadcast/BroadcastManager.kt`

```kotlin
package com.pocketcraft.server.broadcast

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date

data class BroadcastMessage(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "info",          // "info" | "warning" | "critical"
    val createdAt: Date? = null,
    val expiresAt: Date? = null,
    val targetMinVersion: Int = 0,
    val dismissible: Boolean = true,
    val active: Boolean = true
)

object BroadcastManager {

    private val db = FirebaseFirestore.getInstance()
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    // --- Firestore live broadcast stream ---

    fun getBroadcastsFlow(appVersionCode: Int): Flow<List<BroadcastMessage>> = callbackFlow {
        val listener = db.collection("broadcasts")
            .whereEqualTo("active", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val now = Date()
                val messages = snapshot.documents.mapNotNull { doc ->
                    val msg = doc.toObject(BroadcastMessage::class.java)?.copy(id = doc.id)
                        ?: return@mapNotNull null
                    // Filter expired
                    if (msg.expiresAt != null && msg.expiresAt.before(now)) return@mapNotNull null
                    // Filter by version
                    if (msg.targetMinVersion > appVersionCode) return@mapNotNull null
                    msg
                }
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    // --- Remote Config banner ---

    fun initRemoteConfig(onBannerFetched: (BroadcastMessage?) -> Unit) {
        val settings = remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(mapOf("broadcast_banner" to ""))

        remoteConfig.fetchAndActivate().addOnCompleteListener {
            val raw = remoteConfig.getString("broadcast_banner")
            if (raw.isBlank()) {
                onBannerFetched(null)
                return@addOnCompleteListener
            }
            try {
                val json = org.json.JSONObject(raw)
                val msg = BroadcastMessage(
                    title = json.optString("title"),
                    body = json.optString("body"),
                    type = json.optString("type", "info")
                )
                onBannerFetched(msg)
            } catch (e: Exception) {
                onBannerFetched(null)
            }
        }
    }
}
```

---

### 3. New File: `PocketCraftMessagingService.kt`

Create at: `app/src/main/java/com/pocketcraft/server/broadcast/PocketCraftMessagingService.kt`

This handles FCM token registration and topic subscription.

```kotlin
package com.pocketcraft.server.broadcast

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log

class PocketCraftMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Re-subscribe to topic on token refresh
        subscribeToAllUsers()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // FCM handles notification display automatically when app is in background.
        // If you want custom in-app display when foregrounded, handle here.
        // For now, default FCM behavior is sufficient.
        Log.d("FCM", "Message received: ${message.notification?.title}")
    }

    companion object {
        fun subscribeToAllUsers() {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnCompleteListener { task ->
                    Log.d("FCM", if (task.isSuccessful) "Subscribed to all_users" else "Subscription failed")
                }
        }
    }
}
```

Register in `AndroidManifest.xml` inside `<application>`:

```xml
<service
    android:name=".broadcast.PocketCraftMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

---

### 4. New Composable: `BroadcastBanner.kt`

Create at: `app/src/main/java/com/pocketcraft/server/ui/components/BroadcastBanner.kt`

```kotlin
package com.pocketcraft.server.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.broadcast.BroadcastMessage

val InfoColor = Color(0xFF6C63FF)      // purple accent
val WarningColor = Color(0xFFFFB142)   // orange
val CriticalColor = Color(0xFFFF4757)  // red

@Composable
fun BroadcastBanner(
    message: BroadcastMessage,
    onDismiss: () -> Unit
) {
    val bgColor = when (message.type) {
        "warning" -> WarningColor.copy(alpha = 0.15f)
        "critical" -> CriticalColor.copy(alpha = 0.15f)
        else -> InfoColor.copy(alpha = 0.12f)
    }
    val accentColor = when (message.type) {
        "warning" -> WarningColor
        "critical" -> CriticalColor
        else -> InfoColor
    }
    val icon = when (message.type) {
        "warning", "critical" -> Icons.Default.Warning
        else -> Icons.Default.Info
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (message.title.isNotBlank()) {
                        Text(
                            text = message.title,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = message.body,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                if (message.dismissible) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
```

---

### 5. New ViewModel: `BroadcastViewModel.kt`

Create at: `app/src/main/java/com/pocketcraft/server/viewmodel/BroadcastViewModel.kt`

```kotlin
package com.pocketcraft.server.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcraft.server.broadcast.BroadcastManager
import com.pocketcraft.server.broadcast.BroadcastMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BroadcastViewModel(app: Application) : AndroidViewModel(app) {

    private val versionCode: Int = try {
        app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode.toInt()
    } catch (e: Exception) { 0 }

    // Live Firestore broadcasts
    val broadcasts: StateFlow<List<BroadcastMessage>> =
        BroadcastManager.getBroadcastsFlow(versionCode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dismissed message IDs this session
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())

    // Remote Config banner (one-time, shown once per session)
    private val _configBanner = MutableStateFlow<BroadcastMessage?>(null)
    val configBanner: StateFlow<BroadcastMessage?> = _configBanner

    init {
        BroadcastManager.initRemoteConfig { msg ->
            _configBanner.value = msg
        }
    }

    fun dismiss(id: String) {
        _dismissed.value = _dismissed.value + id
    }

    fun dismissConfigBanner() {
        _configBanner.value = null
    }

    val visibleBroadcasts: Flow<List<BroadcastMessage>> = combine(broadcasts, _dismissed) { msgs, dismissed ->
        msgs.filter { it.id !in dismissed }
    }
}
```

---

### 6. Integration in MainActivity or HomeScreen

In your `MainActivity.kt` or top-level composable (`HomeScreen`, `NavHost`), add the following:

```kotlin
// At top of your root composable or MainActivity setContent {}

val broadcastVm: BroadcastViewModel = viewModel()
val broadcasts by broadcastVm.visibleBroadcasts.collectAsState(initial = emptyList())
val configBanner by broadcastVm.configBanner.collectAsState()

// Subscribe to FCM topic once
LaunchedEffect(Unit) {
    PocketCraftMessagingService.subscribeToAllUsers()
}

// In your layout, above the main content:
Column {
    // Remote Config banner (highest priority, shown first)
    configBanner?.let { banner ->
        BroadcastBanner(message = banner, onDismiss = { broadcastVm.dismissConfigBanner() })
    }

    // Firestore broadcast banners
    broadcasts.forEach { msg ->
        BroadcastBanner(message = msg, onDismiss = { broadcastVm.dismiss(msg.id) })
    }

    // ... rest of your UI
}
```

---

### 7. FCM Notification Channels (Android 8+)

In `MainActivity.onCreate()` or `App.kt`:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val channel = NotificationChannel(
        "pocketcraft_broadcast",
        "PocketCraft Announcements",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Server updates and announcements from PocketCraft"
    }
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}
```

---

## How to Send a Broadcast (Admin Workflow)

### Option A — FCM Push (instant, reaches closed app)
1. Firebase Console → Cloud Messaging → Create your first campaign
2. Notification title + body → Target: Topic → `all_users`
3. Send now or schedule

### Option B — In-App Banner via Firestore (real-time, shows inside app)
1. Firebase Console → Firestore → `broadcasts` collection → Add document
2. Set fields: `title`, `body`, `type` (info/warning/critical), `active: true`, `dismissible: true`
3. Optionally set `expiresAt` as a Timestamp
4. Save — all active users see it within seconds

### Option C — Remote Config Banner (persistent until changed)
1. Firebase Console → Remote Config → `broadcast_banner`
2. Set value to: `{"title":"Update Available","body":"Version 1.2 is out — update for new features!","type":"info"}`
3. Publish → users see it on next app open

### Option D — Firebase In-App Messaging (rich cards, zero extra code)
1. Firebase Console → In-App Messaging → Create campaign
2. Design card/banner/modal with image, CTA button
3. Set trigger: App open
4. Publish → appears inside the app automatically

---

## Summary of Controllability

| What you want to do | Tool | How fast |
|---|---|---|
| Urgent alert to all users | FCM Push | Instant |
| In-app banner everyone sees | Firestore `broadcasts` | < 5 seconds |
| Persistent "new version" notice | Remote Config | Next app open |
| Rich card with image/button | Firebase In-App Messaging | Next app open |
| Target specific app version | Firestore `targetMinVersion` field | < 5 seconds |
| Auto-expire a message | Firestore `expiresAt` Timestamp | Automatic |
| Hide a message without deleting | Firestore `active: false` | < 5 seconds |

---

## Files to Create/Modify

| Action | File |
|---|---|
| Create | `broadcast/BroadcastManager.kt` |
| Create | `broadcast/PocketCraftMessagingService.kt` |
| Create | `ui/components/BroadcastBanner.kt` |
| Create | `viewmodel/BroadcastViewModel.kt` |
| Modify | `AndroidManifest.xml` — register service |
| Modify | `MainActivity.kt` or root composable — integrate banners + FCM subscribe |
| Modify | `app/build.gradle` — verify firebase-messaging, firebase-config deps |

---

## Notes

- `google-services.json` must be present and up to date (already done if Firebase is initialized)
- FCM topic subscription (`all_users`) is fire-and-forget — it survives uninstall/reinstall automatically
- For per-user targeting in the future: store FCM token in Firestore under `users/{userId}/fcmToken` and send to specific token
- In-App Messaging requires no extra setup if `firebase-inappmessaging-display` dependency is added — Firebase handles all rendering
