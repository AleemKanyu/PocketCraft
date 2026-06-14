# Default ProGuard rules for Android.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class *

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# PocketCraft — Core models and services
-keep class com.pocketcraft.server.data.model.** { *; }
-keep class com.pocketcraft.server.service.** { *; }
-keep class com.pocketcraft.server.NativeLauncher { *; }

# Server lifecycle — keep entire classes so broadcast routing and StateFlow
# field names survive R8 minification in release builds.
-keep class com.pocketcraft.server.server.ServerLauncher { *; }
-keep class com.pocketcraft.server.server.ServerHostService { *; }
-keep class com.pocketcraft.server.server.ServerHostService$* { *; }
-keep class com.pocketcraft.server.server.ServerHostService$Companion { *; }

# Status detection — looksLikeServerReady, handleObservedOutputLine, onServerReady
# must not be renamed; they are invoked by name via reflection in debug builds
# and their string-match logic must survive intact in release.
-keepclassmembers class com.pocketcraft.server.server.ServerHostService {
    private *** looksLikeServerReady(java.lang.String);
    private *** handleObservedOutputLine(java.lang.String, java.lang.String);
    private *** onServerReady();
    private *** setServerReadyState(boolean);
    private *** scheduleServerReadyFallback(java.lang.String);
}

# ConsoleParser object — isDone(), parseTps(), parseJoin(), parseLeave() drive
# all UI state transitions; the companion Regex fields must not be stripped.
-keep class com.pocketcraft.server.service.ConsoleParser { *; }
-keep class com.pocketcraft.server.service.ConsoleParser$* { *; }

# ServerStateHolder outer class — Compose mutableStateOf fields (isRunning,
# isStarting, serverJoinable, etc.) are accessed by Compose runtime via
# reflection; keep all members of the outer class too.
-keep class com.pocketcraft.server.ui.screens.ServerStateHolder { *; }
-keep class com.pocketcraft.server.ui.screens.ServerStateHolder$* { *; }
-keep class com.pocketcraft.server.ui.screens.ServerStatus { *; }

# AppPreferences — eulaAccepted and other SharedPreferences wrappers are
# accessed from both UI and service; ensure property names survive.
-keep class com.pocketcraft.server.data.preferences.AppPreferences { *; }
-keep class com.pocketcraft.server.data.preferences.AppPreferencesKeys { *; }
-keep class com.pocketcraft.server.data.preferences.AppPreferencesStore { *; }

# ServerFileManager — prepareEula / isEulaAccepted must not be inlined away.
-keep class com.pocketcraft.server.service.ServerFileManager { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Google API Client / Drive models
# Drive's generated File model uses Data.nullOf(ContentRestriction.class) in a
# static initializer so R8 must not strip or reshape these reflective model types.
-keep class com.google.api.client.util.Key { *; }
-keep class com.google.api.client.util.GenericData { *; }
-keep class com.google.api.client.json.GenericJson { *; }
-keep class com.google.api.client.util.Data { *; }
-keep class com.google.api.client.util.ArrayMap { *; }
-keep class com.google.api.client.googleapis.json.GoogleJsonError { *; }
-keep class com.google.api.client.googleapis.json.GoogleJsonErrorContainer { *; }
-keep class com.google.api.client.googleapis.json.** { *; }
-keep class com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential { *; }
-keep class com.google.api.client.http.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.services.drive.model.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**

# Kotlin — preserve metadata so coroutines, StateFlow, and companion objects
# function correctly in release builds.
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlin.jvm.JvmStatic *;
}
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# kotlinx.coroutines StateFlow / MutableStateFlow internals
-keep class kotlinx.coroutines.flow.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# gRPC Rules
-keep class io.grpc.** { *; }
-keep interface io.grpc.** { *; }
-dontwarn io.grpc.**

# Protobuf Rules
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
