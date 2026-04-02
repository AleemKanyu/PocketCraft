# PocketCraft – Definitive Server Fix (Android 13, arm64)
> Complete ground-up fix. JVM loads via dlopen in-process. Paper JAR invoked via URLClassLoader reflection. Works on Android 11–14.

---

## 🧾 Context

- App: **PocketCraft** — hosts a Minecraft Java server on Android
- Device: **Android 13, arm64 (aarch64)**
- Problem: Server never starts on any Android version
- Root cause 1: `ProcessBuilder` is blocked by Android's linker namespace on API 30+
- Root cause 2: Paper's main class cannot be found via `FindClass()` — it requires `URLClassLoader`
- Root cause 3: JRE `.so` files need recursive `chmod` after extraction

**The user will manually copy the JRE from a PojavLauncher APK.**
**Place the extracted JRE folder contents into: `app/src/main/assets/jre-runtime/`**

---

## 📋 Step 0 — Manual JRE Setup (Do This Before Running the Prompt)

1. Download the latest **PojavLauncher APK** from:
   `https://github.com/PojavLauncherTeam/PojavLauncher/releases`

2. Rename `PojavLauncher.apk` → `PojavLauncher.zip`

3. Extract the ZIP and find the JRE folder inside:
   ```
   assets/components/jre-21/
   ```

4. Copy the **entire contents** of `jre-21/` into your project at:
   ```
   app/src/main/assets/jre-runtime/
   ```

5. Final structure must look like:
   ```
   app/src/main/assets/jre-runtime/
   ├── bin/
   │   └── java
   ├── lib/
   │   ├── libjli.so          ← must exist
   │   ├── libjava.so
   │   ├── server/
   │   │   └── libjvm.so      ← must exist
   │   └── ...
   ├── conf/
   └── release
   ```

> The asset folder is named `jre-runtime` to avoid confusion with old paths.

---

## ✅ What Needs to Be Built / Fixed

---

### 1. `app/build.gradle.kts` — NDK + Asset Compression Fix

```kotlin
android {
    defaultConfig {
        externalNativeBuild {
            cmake { cppFlags("") }
        }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path    = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // CRITICAL: Prevent compression of .so and .jar files
    // Compressed .so files cannot be dlopen'd at runtime
    androidResources {
        noCompress += listOf("so", "jar", "xz", "gz", "jks")
    }
}
```

---

### 2. `AndroidManifest.xml` — Required Flags

```xml
<application
    android:allowNativeHeapPointerTagging="false"
    android:extractNativeLibs="true"
    ...>
```

---

### 3. `app/src/main/cpp/CMakeLists.txt`

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("pocketcraft")

add_library(launcher SHARED launcher.c)

find_library(log-lib log)
find_library(dl-lib  dl)

target_link_libraries(launcher ${log-lib} ${dl-lib})
```

---

### 4. `app/src/main/cpp/launcher.c` — Complete Native JVM Launcher

```c
#include <jni.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <android/log.h>

#define TAG  "PocketCraft"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef jint (*JNI_CreateJavaVM_fn)(JavaVM**, void**, void*);

JNIEXPORT jint JNICALL
Java_com_pocketcraft_server_NativeLauncher_launchJVM(
    JNIEnv* env, jobject thiz,
    jstring jJrePath,
    jstring jJarPath,
    jstring jServerDir,
    jstring jTmpDir,
    jint    maxRamMb
) {
    const char* jrePath   = (*env)->GetStringUTFChars(env, jJrePath,   NULL);
    const char* jarPath   = (*env)->GetStringUTFChars(env, jJarPath,   NULL);
    const char* serverDir = (*env)->GetStringUTFChars(env, jServerDir, NULL);
    const char* tmpDir    = (*env)->GetStringUTFChars(env, jTmpDir,    NULL);

    LOGI("JAR:    %s", jarPath);
    LOGI("JRE:    %s", jrePath);
    LOGI("Dir:    %s", serverDir);
    LOGI("Tmp:    %s", tmpDir);

    /* Step 1: dlopen libjli.so (MUST be before libjvm.so) */
    char libjliPath[512];
    snprintf(libjliPath, sizeof(libjliPath), "%s/lib/libjli.so", jrePath);
    LOGI("dlopen: %s", libjliPath);
    void* jliHandle = dlopen(libjliPath, RTLD_NOW | RTLD_GLOBAL);
    if (!jliHandle) { LOGE("libjli.so FAILED: %s", dlerror()); return -1; }
    LOGI("libjli.so ✓");

    /* Step 2: dlopen libjvm.so */
    char libjvmPath[512];
    snprintf(libjvmPath, sizeof(libjvmPath), "%s/lib/server/libjvm.so", jrePath);
    LOGI("dlopen: %s", libjvmPath);
    void* jvmHandle = dlopen(libjvmPath, RTLD_NOW | RTLD_GLOBAL);
    if (!jvmHandle) { LOGE("libjvm.so FAILED: %s", dlerror()); return -2; }
    LOGI("libjvm.so ✓");

    /* Step 3: resolve JNI_CreateJavaVM */
    JNI_CreateJavaVM_fn createVM =
        (JNI_CreateJavaVM_fn) dlsym(jvmHandle, "JNI_CreateJavaVM");
    if (!createVM) { LOGE("JNI_CreateJavaVM not found: %s", dlerror()); return -3; }

    /* Step 4: build JVM options */
    char xmx[64], xms[64], javaHome[512], libPath[2048], tmpOpt[512];
    snprintf(xmx,      sizeof(xmx),      "-Xmx%dm",            maxRamMb);
    snprintf(xms,      sizeof(xms),      "-Xms512m");
    snprintf(javaHome, sizeof(javaHome), "-Djava.home=%s",      jrePath);
    snprintf(tmpOpt,   sizeof(tmpOpt),   "-Djava.io.tmpdir=%s", tmpDir);
    snprintf(libPath,  sizeof(libPath),
        "-Djava.library.path="
        "%s/lib:%s/lib/server:%s/lib/aarch64:%s/lib/aarch64/server:"
        "%s/lib/aarch64/jli:%s/lib/jli:/system/lib64:/vendor/lib64",
        jrePath, jrePath, jrePath, jrePath, jrePath, jrePath);

    JavaVMOption opts[] = {
        { xmx,                                   NULL },
        { xms,                                   NULL },
        { javaHome,                              NULL },
        { libPath,                               NULL },
        { tmpOpt,                                NULL },
        { "-DPaper.IgnoreJavaVersion=true",      NULL },
        { "-Dsun.zip.disableMemoryMapping=true", NULL },
        { "-Djdk.attach.allowAttachSelf=true",   NULL },
        { "-XX:+UseG1GC",                        NULL },
        { "-XX:+DisableAttachMechanism",         NULL },
        { "-XX:+UnlockDiagnosticVMOptions",      NULL },
        { "-XX:-UseCompressedOops",              NULL },
        { "-XX:-UseCompressedClassPointers",     NULL },
        { "-XX:MaxGCPauseMillis=200",            NULL },
        { "-XX:G1HeapRegionSize=8M",             NULL },
    };

    JavaVMInitArgs vmArgs;
    vmArgs.version            = JNI_VERSION_1_8;
    vmArgs.nOptions           = sizeof(opts) / sizeof(opts[0]);
    vmArgs.options            = opts;
    vmArgs.ignoreUnrecognized = JNI_TRUE;

    /* Step 5: create JVM in-process */
    JavaVM* newJvm = NULL;
    JNIEnv* newEnv = NULL;
    LOGI("JNI_CreateJavaVM...");
    jint rc = createVM(&newJvm, (void**)&newEnv, &vmArgs);
    if (rc != JNI_OK) { LOGE("JNI_CreateJavaVM FAILED: %d", rc); return -4; }
    LOGI("JVM created ✓");

    /* Step 6: set working directory */
    chdir(serverDir);

    /*
     * Step 7: Load Paper JAR via URLClassLoader + invoke main via reflection
     *
     * WHY: Paper is a bootstrapper JAR. Its main class is NOT on the system
     * classpath. FindClass() will never find it. Must use URLClassLoader
     * to load the class from inside the JAR, then invoke via reflection.
     */
    char jarUrl[1024];
    snprintf(jarUrl, sizeof(jarUrl), "file://%s", jarPath);
    LOGI("JAR URL: %s", jarUrl);

    jclass    clsURL  = (*newEnv)->FindClass(newEnv, "java/net/URL");
    jmethodID ctorURL = (*newEnv)->GetMethodID(newEnv, clsURL,
                            "<init>", "(Ljava/lang/String;)V");
    jobject   objURL  = (*newEnv)->NewObject(newEnv, clsURL, ctorURL,
                            (*newEnv)->NewStringUTF(newEnv, jarUrl));
    if ((*newEnv)->ExceptionCheck(newEnv)) {
        (*newEnv)->ExceptionDescribe(newEnv);
        (*newEnv)->ExceptionClear(newEnv);
        LOGE("Failed to create URL"); return -5;
    }

    jobjectArray urlArr = (*newEnv)->NewObjectArray(newEnv, 1, clsURL, NULL);
    (*newEnv)->SetObjectArrayElement(newEnv, urlArr, 0, objURL);

    jclass    clsCL     = (*newEnv)->FindClass(newEnv, "java/lang/ClassLoader");
    jmethodID midGetSys = (*newEnv)->GetStaticMethodID(newEnv, clsCL,
                              "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
    jobject   sysLoader = (*newEnv)->CallStaticObjectMethod(newEnv, clsCL, midGetSys);

    jclass    clsUCL  = (*newEnv)->FindClass(newEnv, "java/net/URLClassLoader");
    jmethodID ctorUCL = (*newEnv)->GetMethodID(newEnv, clsUCL,
                            "<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    jobject   loader  = (*newEnv)->NewObject(newEnv, clsUCL, ctorUCL, urlArr, sysLoader);
    if ((*newEnv)->ExceptionCheck(newEnv)) {
        (*newEnv)->ExceptionDescribe(newEnv);
        (*newEnv)->ExceptionClear(newEnv);
        LOGE("Failed to create URLClassLoader"); return -6;
    }

    const char* candidates[] = {
        "io.papermc.paperclip.Main",
        "org.bukkit.craftbukkit.Main",
        "net.minecraft.server.Main",
        NULL
    };

    jmethodID midLoad  = (*newEnv)->GetMethodID(newEnv, clsUCL,
                             "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jclass mainClass   = NULL;
    for (int i = 0; candidates[i]; i++) {
        LOGI("Trying: %s", candidates[i]);
        jstring jName = (*newEnv)->NewStringUTF(newEnv, candidates[i]);
        mainClass = (jclass)(*newEnv)->CallObjectMethod(newEnv, loader, midLoad, jName);
        if ((*newEnv)->ExceptionCheck(newEnv)) {
            (*newEnv)->ExceptionClear(newEnv);
            mainClass = NULL;
        }
        if (mainClass) { LOGI("Found: %s ✓", candidates[i]); break; }
    }
    if (!mainClass) { LOGE("No main class found in: %s", jarPath); return -7; }

    jclass    clsClass    = (*newEnv)->FindClass(newEnv, "java/lang/Class");
    jclass    clsStrArr   = (*newEnv)->FindClass(newEnv, "[Ljava/lang/String;");
    jclass    clsClassObj = (*newEnv)->FindClass(newEnv, "java/lang/Class");
    jobjectArray paramArr = (*newEnv)->NewObjectArray(newEnv, 1, clsClassObj, NULL);
    (*newEnv)->SetObjectArrayElement(newEnv, paramArr, 0, clsStrArr);

    jmethodID midGetMethod = (*newEnv)->GetMethodID(newEnv, clsClass, "getMethod",
        "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
    jobject mainMethod = (*newEnv)->CallObjectMethod(newEnv, mainClass, midGetMethod,
        (*newEnv)->NewStringUTF(newEnv, "main"), paramArr);
    if ((*newEnv)->ExceptionCheck(newEnv) || !mainMethod) {
        (*newEnv)->ExceptionDescribe(newEnv);
        (*newEnv)->ExceptionClear(newEnv);
        LOGE("main() not found"); return -8;
    }

    jclass       clsStr   = (*newEnv)->FindClass(newEnv, "java/lang/String");
    jobjectArray srvArgs  = (*newEnv)->NewObjectArray(newEnv, 3, clsStr, NULL);
    (*newEnv)->SetObjectArrayElement(newEnv, srvArgs, 0,
        (*newEnv)->NewStringUTF(newEnv, "--nogui"));
    (*newEnv)->SetObjectArrayElement(newEnv, srvArgs, 1,
        (*newEnv)->NewStringUTF(newEnv, "--port"));
    (*newEnv)->SetObjectArrayElement(newEnv, srvArgs, 2,
        (*newEnv)->NewStringUTF(newEnv, "25565"));

    jclass       clsMethod  = (*newEnv)->FindClass(newEnv, "java/lang/reflect/Method");
    jmethodID    midInvoke  = (*newEnv)->GetMethodID(newEnv, clsMethod, "invoke",
        "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    jclass       clsObj     = (*newEnv)->FindClass(newEnv, "java/lang/Object");
    jobjectArray invokeArgs = (*newEnv)->NewObjectArray(newEnv, 1, clsObj, NULL);
    (*newEnv)->SetObjectArrayElement(newEnv, invokeArgs, 0, srvArgs);

    LOGI("Invoking Paper main()...");
    (*newEnv)->CallObjectMethod(newEnv, mainMethod, midInvoke, NULL, invokeArgs);
    if ((*newEnv)->ExceptionCheck(newEnv)) {
        (*newEnv)->ExceptionDescribe(newEnv);
        (*newEnv)->ExceptionClear(newEnv);
        LOGE("Exception from server main()");
    }

    (*env)->ReleaseStringUTFChars(env, jJrePath,   jrePath);
    (*env)->ReleaseStringUTFChars(env, jJarPath,   jarPath);
    (*env)->ReleaseStringUTFChars(env, jServerDir, serverDir);
    (*env)->ReleaseStringUTFChars(env, jTmpDir,    tmpDir);
    return 0;
}
```

---

### 5. `NativeLauncher.kt`

```kotlin
package com.pocketcraft.server

object NativeLauncher {
    init { System.loadLibrary("launcher") }

    external fun launchJVM(
        jrePath  : String,
        jarPath  : String,
        serverDir: String,
        tmpDir   : String,
        maxRamMb : Int
    ): Int
}
```

---

### 6. `JreExtractor.kt`

```kotlin
object JreExtractor {

    private const val ASSET_DIR   = "jre-runtime"
    private const val VERSION_TAG = "jre_v3_extracted"

    fun getJreDir(context: Context): File {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            context.codeCacheDir else context.filesDir
        return File(base, "jre-runtime")
    }

    fun extractIfNeeded(context: Context) {
        val jreDir = getJreDir(context)
        val marker = File(context.filesDir, VERSION_TAG)
        if (marker.exists() && jreDir.exists() && jreDir.list()?.isNotEmpty() == true) return
        if (jreDir.exists()) jreDir.deleteRecursively()
        jreDir.mkdirs()
        copyAssetFolder(context.assets, ASSET_DIR, jreDir.absolutePath)
        fixPermissions(jreDir)
        marker.createNewFile()
    }

    private fun fixPermissions(dir: File) {
        dir.walkTopDown().forEach { file ->
            file.setReadable(true, false)
            if (file.isFile) {
                file.setExecutable(true, false)
                file.setWritable(true, false)
            }
        }
    }

    private fun copyAssetFolder(
        assets: android.content.res.AssetManager,
        assetPath: String,
        destPath: String
    ) {
        val children = assets.list(assetPath)
        if (children.isNullOrEmpty()) {
            assets.open(assetPath).use { input ->
                File(destPath).also { it.parentFile?.mkdirs() }
                    .outputStream().use { input.copyTo(it) }
            }
        } else {
            File(destPath).mkdirs()
            children.forEach { child ->
                copyAssetFolder(assets, "$assetPath/$child", "$destPath/$child")
            }
        }
    }
}
```

---

### 7. `ServerFileManager.kt`

```kotlin
object ServerFileManager {
    fun getServerDir(context: Context, versionId: String): File =
        File(context.filesDir, "servers/$versionId").also { it.mkdirs() }

    fun getServerJarFile(context: Context, versionId: String): File =
        File(getServerDir(context, versionId), "paper-$versionId.jar")

    fun isServerJarReady(context: Context, versionId: String): Boolean {
        val jar = getServerJarFile(context, versionId)
        return jar.exists() && jar.length() > 1_000_000L
    }
}
```

---

### 8. `ServerLauncher.kt`

```kotlin
class ServerLauncher(private val context: Context) {

    fun startServer(
        versionId: String,
        onOutput : (String) -> Unit,
        onError  : (String) -> Unit,
        onStopped: () -> Unit
    ) {
        if (!ServerFileManager.isServerJarReady(context, versionId)) {
            onError("Server JAR not found for $versionId"); return
        }

        val jrePath   = JreExtractor.getJreDir(context).absolutePath
        val jarPath   = ServerFileManager.getServerJarFile(context, versionId).absolutePath
        val serverDir = ServerFileManager.getServerDir(context, versionId).absolutePath
        val tmpDir    = context.cacheDir.absolutePath

        val libjvm = File(jrePath, "lib/server/libjvm.so")
        if (!libjvm.exists()) {
            onError("libjvm.so not found — JRE may not be extracted correctly"); return
        }

        onOutput("[PocketCraft] Starting $versionId...")
        onOutput("[PocketCraft] JRE: $jrePath")
        onOutput("[PocketCraft] JAR: $jarPath")

        Thread {
            try {
                val result = NativeLauncher.launchJVM(jrePath, jarPath, serverDir, tmpDir, 1024)
                if (result != 0) onError("[PocketCraft] JVM exited with code $result")
            } catch (e: Exception) {
                onError("[PocketCraft] ${e.message}")
            } finally {
                onStopped()
            }
        }.apply { name = "mc-server-thread"; isDaemon = false }.start()
    }
}
```

---

### 9. `MainActivity.kt` — JRE Extraction on Startup

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketCraftTheme {
                var jreReady by remember { mutableStateOf(false) }
                var jreError by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        try {
                            JreExtractor.extractIfNeeded(applicationContext)
                            jreReady = true
                        } catch (e: Exception) {
                            jreError = e.message
                        }
                    }
                }

                when {
                    jreError != null -> ErrorScreen("Failed to prepare runtime:\n$jreError",
                        onRetry = { recreate() })
                    !jreReady -> SplashScreen(progress = 0.5f,
                        status = "Preparing Minecraft Runtime...")
                    else -> PocketCraftApp()
                }
            }
        }
    }
}
```

---

### 10. App Navigation

```kotlin
enum class Screen { VERSION_PICKER, DOWNLOADING, SERVER }

@Composable
fun PocketCraftApp() {
    var screen    by remember { mutableStateOf(Screen.VERSION_PICKER) }
    var versionId by remember { mutableStateOf("") }
    val context   = LocalContext.current

    when (screen) {
        Screen.VERSION_PICKER -> VersionPickerScreen { version ->
            versionId = version
            screen = if (ServerFileManager.isServerJarReady(context, version))
                Screen.SERVER else Screen.DOWNLOADING
        }
        Screen.DOWNLOADING -> AutoDownloadScreen(versionId) { screen = Screen.SERVER }
        Screen.SERVER      -> ServerScreen(versionId)
    }
}
```

---

## 📁 File Structure

```
PocketCraft/
├── app/src/main/
│   ├── assets/
│   │   └── jre-runtime/           ← COPY JRE HERE from PojavLauncher APK
│   │       ├── bin/java
│   │       ├── lib/libjli.so
│   │       └── lib/server/libjvm.so
│   ├── cpp/
│   │   ├── launcher.c             ← Full implementation above
│   │   └── CMakeLists.txt
│   ├── AndroidManifest.xml
│   └── java/com/pocketcraft/
│       ├── NativeLauncher.kt
│       ├── JreExtractor.kt
│       ├── ServerFileManager.kt
│       ├── server/ServerLauncher.kt
│       └── ui/
│           ├── theme/Color.kt, Theme.kt, Type.kt
│           └── screens/
│               ├── SplashScreen.kt
│               ├── VersionPickerScreen.kt
│               ├── AutoDownloadScreen.kt
│               ├── ServerScreen.kt
│               └── ErrorScreen.kt
└── app/build.gradle.kts
```

---

## ☑️ Quality Checklist

- [ ] JRE copied into `app/src/main/assets/jre-runtime/`
- [ ] `lib/libjli.so` and `lib/server/libjvm.so` both exist inside it
- [ ] `noCompress` covers `so`, `jar`, `xz`, `gz` in `build.gradle.kts`
- [ ] `allowNativeHeapPointerTagging="false"` + `extractNativeLibs="true"` in manifest
- [ ] `launcher.c` has 5 params including `jTmpDir`
- [ ] `dlopen(libjli.so)` called before `dlopen(libjvm.so)`
- [ ] Paper loaded via `URLClassLoader`, NOT `FindClass()`
- [ ] `main()` invoked via `Method.invoke()` reflection
- [ ] `NativeLauncher.kt` signature matches C (5 params)
- [ ] `JreExtractor` uses `codeCacheDir` on Android 10+
- [ ] `fixPermissions()` walks entire `jre-runtime/` tree
- [ ] `libjvm.so` existence checked in `ServerLauncher` before launch
- [ ] No `ProcessBuilder` anywhere in the codebase

---

## 🔁 Flow

```
App Opens
    ↓
JreExtractor.extractIfNeeded()
  → copies jre-runtime/ from assets → codeCacheDir/jre-runtime/
  → fixPermissions() on every .so file
    ↓
VersionPickerScreen — user picks version
    ↓
JAR exists? YES → ServerScreen | NO → Download → ServerScreen
    ↓
User taps ▶ Start Server
    ↓
NativeLauncher.launchJVM(jrePath, jarPath, serverDir, tmpDir, 1024)
    ↓
launcher.c:
  dlopen(libjli.so)  ✓  — inside app process, no linker restrictions
  dlopen(libjvm.so)  ✓  — loads fine from codeCacheDir
  JNI_CreateJavaVM() ✓  — JVM boots in-process
  URLClassLoader(file:///paper.jar)
  loadClass("io.papermc.paperclip.Main") ✓
  Method.invoke() → main(--nogui, --port, 25565)
    ↓
Paper bootstraps → Minecraft server starts ✅
```
