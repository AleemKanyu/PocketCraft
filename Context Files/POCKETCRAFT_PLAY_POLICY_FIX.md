# PocketCraft — Google Play Policy Fix Agent

## Context
PocketCraft was removed from Google Play for violating the Device and Network Abuse policy.
The app was downloading executable JAR files at runtime from external URLs.
This agent implements a full fix across three areas.

## Flagged URLs to Eliminate (All Must Go)
- `https://api.papermc.io/...paper-x.x.x.jar`
- `https://cdn.modrinth.com/.../Geyser-Spigot.jar`
- `https://download.geysermc.org/.../floodgate-spigot.jar`
- Any other URL that downloads a `.jar`, `.dex`, or `.so` file at runtime

---

## PART 1 — Bundle Geyser + Floodgate Into Assets

### 1.1 Add JAR Files to Assets
Place these files in `app/src/main/assets/plugins/`:
```
app/src/main/assets/plugins/Geyser-Spigot.jar
app/src/main/assets/plugins/floodgate-spigot.jar
```
Download the latest stable versions manually from:
- Geyser: https://geysermc.org/download (pick Spigot build)
- Floodgate: https://geysermc.org/download (Floodgate > Spigot)

### 1.2 Create BundledPluginInstaller.kt
Location: `app/src/main/java/com/pocketcraft/server/server/BundledPluginInstaller.kt`

```kotlin
package com.pocketcraft.server.server

import android.content.Context
import java.io.File

object BundledPluginInstaller {

    private val BUNDLED_PLUGINS = listOf(
        "Geyser-Spigot.jar",
        "floodgate-spigot.jar"
    )

    /**
     * Copies all bundled plugins from assets/plugins/ to the server's plugins directory.
     * Skips files that already exist (won't overwrite user-deleted plugins).
     * Call this every time a server is set up or on first launch.
     */
    fun installBundledPlugins(context: Context, serverDir: File) {
        val pluginsDir = File(serverDir, "plugins").also { it.mkdirs() }

        for (pluginName in BUNDLED_PLUGINS) {
            val destFile = File(pluginsDir, pluginName)
            if (!destFile.exists()) {
                try {
                    context.assets.open("plugins/$pluginName").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Log but don't crash — plugin install is non-fatal
                }
            }
        }
    }

    /**
     * Force reinstall all bundled plugins (e.g. after app update with newer plugin versions).
     */
    fun reinstallBundledPlugins(context: Context, serverDir: File) {
        val pluginsDir = File(serverDir, "plugins").also { it.mkdirs() }
        for (pluginName in BUNDLED_PLUGINS) {
            try {
                val destFile = File(pluginsDir, pluginName)
                context.assets.open("plugins/$pluginName").use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
```

### 1.3 Call BundledPluginInstaller During Server Setup
Find where the server directory is initialized (likely `ServerSetupManager.kt` or `ServerFileManager.kt`) and add:

```kotlin
// After server directory is created and before server first launch:
BundledPluginInstaller.installBundledPlugins(context, serverDir)
```

### 1.4 Delete All Runtime Download Code for Geyser/Floodgate
Search the entire codebase for:
- `geysermc.org`
- `Geyser-Spigot`
- `floodgate`
- `modrinth.com`

Delete or comment out any code that downloads these files over the network.
Replace with a call to `BundledPluginInstaller.installBundledPlugins()`.

---

## PART 2 — Server JAR: Browser Redirect + File Picker

Replace the current runtime Paper/Purpur/Fabric JAR download with a manual flow.

### 2.1 Define Server Type URLs
Create `ServerTypeDownloadUrls.kt`:

```kotlin
package com.pocketcraft.server.server

object ServerTypeDownloadUrls {
    fun getDownloadPageUrl(serverType: String, version: String): String {
        return when (serverType.lowercase()) {
            "paper" -> "https://papermc.io/downloads/paper"
            "purpur" -> "https://purpurmc.org/downloads"
            "fabric" -> "https://fabricmc.net/use/server/"
            "vanilla" -> "https://minecraft.net/en-us/download/server"
            else -> "https://papermc.io/downloads/paper"
        }
    }
}
```

### 2.2 Update Version Selector — Replace Download With Bottom Sheet

When the user selects a server version, instead of downloading, show a bottom sheet.

Create `ServerJarPickerBottomSheet.kt`:

```kotlin
package com.pocketcraft.server.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.server.ServerTypeDownloadUrls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerJarPickerBottomSheet(
    serverType: String,
    version: String,
    onDismiss: () -> Unit,
    onJarSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val downloadUrl = ServerTypeDownloadUrls.getDownloadPageUrl(serverType, version)

    val jarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onJarSelected(uri)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Install $serverType $version",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Download the server JAR from the official website, then select it here.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Step 1
            StepCard(
                step = "1",
                title = "Download the server JAR",
                description = "Opens the official $serverType download page in your browser."
            ) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Download Page")
                }
            }

            // Step 2
            StepCard(
                step = "2",
                title = "Select the downloaded JAR",
                description = "After downloading, come back here and pick the file."
            ) {
                OutlinedButton(
                    onClick = {
                        jarPickerLauncher.launch(arrayOf("application/java-archive", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select JAR File")
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    step: String,
    title: String,
    description: String,
    action: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge { Text(step) }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action()
        }
    }
}
```

### 2.3 Handle the Selected JAR URI

Create `ServerJarImporter.kt`:

```kotlin
package com.pocketcraft.server.server

import android.content.Context
import android.net.Uri
import java.io.File

object ServerJarImporter {

    sealed class ImportResult {
        data class Success(val file: File) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    /**
     * Validates and copies the user-selected JAR to the server directory.
     */
    fun importServerJar(context: Context, uri: Uri, serverDir: File): ImportResult {
        return try {
            // Validate it's actually a JAR
            val fileName = getFileName(context, uri) ?: "server.jar"
            if (!fileName.endsWith(".jar")) {
                return ImportResult.Error("Selected file is not a JAR file")
            }

            // Validate file size (server JARs are typically 10MB–100MB)
            val fileSize = getFileSize(context, uri)
            if (fileSize < 1_000_000L) { // less than 1MB is suspicious
                return ImportResult.Error("File is too small to be a valid server JAR")
            }

            // Copy to server directory
            val destFile = File(serverDir, "server.jar")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return ImportResult.Error("Could not read selected file")

            ImportResult.Success(destFile)
        } catch (e: Exception) {
            ImportResult.Error("Failed to import JAR: ${e.message}")
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            it.moveToFirst()
            it.getLong(sizeIndex)
        } ?: 0L
    }
}
```

### 2.4 Wire It Up in Your Version Selector Screen

In the version selector composable, replace the `onVersionSelected` logic:

```kotlin
// Before (delete this):
viewModel.downloadServerJar(serverType, version)

// After:
var showJarPicker by remember { mutableStateOf(false) }
var selectedVersion by remember { mutableStateOf("") }

// When user taps a version:
onVersionTap = { version ->
    selectedVersion = version
    showJarPicker = true
}

// Show bottom sheet:
if (showJarPicker) {
    ServerJarPickerBottomSheet(
        serverType = serverType,
        version = selectedVersion,
        onDismiss = { showJarPicker = false },
        onJarSelected = { uri ->
            showJarPicker = false
            val result = ServerJarImporter.importServerJar(context, uri, serverDir)
            when (result) {
                is ImportResult.Success -> {
                    // Mark version as installed, proceed
                    viewModel.onServerJarInstalled(selectedVersion)
                }
                is ImportResult.Error -> {
                    // Show snackbar/toast with result.message
                }
            }
        }
    )
}
```

---

## PART 3 — Plugin Browser Redesign

### 3.1 Plugin Browser Flow (New)
The plugin browser UI can stay the same. Only the install action changes.

When user taps "Install" on a plugin:
1. Open the plugin's page in the browser (Modrinth/SpigotMC URL)
2. Show a "Select Downloaded Plugin" button that opens a file picker
3. On file selected → validate → copy to `server/plugins/`

### 3.2 Define Plugin Source URLs

```kotlin
package com.pocketcraft.server.plugins

object PluginSourceUrls {
    // Map plugin IDs to their official pages
    // Add more as needed
    private val PLUGIN_PAGES = mapOf(
        "essentialsx" to "https://essentialsx.net/downloads.html",
        "worldedit" to "https://enginehub.org/worldedit",
        "luckperms" to "https://luckperms.net/download",
        "vault" to "https://www.spigotmc.org/resources/vault.34315/",
        "chunky" to "https://modrinth.com/plugin/chunky",
        // For plugins without a known page, fall back to Modrinth search
    )

    fun getPluginPage(pluginId: String, pluginName: String): String {
        return PLUGIN_PAGES[pluginId.lowercase()]
            ?: "https://modrinth.com/plugins?q=${Uri.encode(pluginName)}"
    }
}
```

### 3.3 Create PluginInstallBottomSheet.kt

```kotlin
package com.pocketcraft.server.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginInstallBottomSheet(
    pluginName: String,
    pluginPageUrl: String,
    onDismiss: () -> Unit,
    onPluginSelected: (Uri) -> Unit
) {
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPluginSelected(uri)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Install $pluginName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Download from the official page, then select the JAR here.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(pluginPageUrl))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Plugin Page")
            }

            OutlinedButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf("application/java-archive", "*/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Downloaded JAR")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
```

### 3.4 Create PluginImporter.kt

```kotlin
package com.pocketcraft.server.plugins

import android.content.Context
import android.net.Uri
import java.io.File

object PluginImporter {

    sealed class ImportResult {
        data class Success(val file: File) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    fun importPlugin(context: Context, uri: Uri, serverDir: File): ImportResult {
        return try {
            val fileName = getFileName(context, uri)
                ?: return ImportResult.Error("Could not read file name")

            if (!fileName.endsWith(".jar")) {
                return ImportResult.Error("Please select a .jar file")
            }

            val pluginsDir = File(serverDir, "plugins").also { it.mkdirs() }
            val destFile = File(pluginsDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return ImportResult.Error("Could not read file")

            ImportResult.Success(destFile)
        } catch (e: Exception) {
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        }
    }
}
```

### 3.5 Wire Into Plugin Browser Screen

In your plugin browser composable, find the "Install" button and replace:

```kotlin
// Before (delete):
viewModel.downloadPlugin(plugin.downloadUrl)

// After:
var showInstallSheet by remember { mutableStateOf(false) }
var selectedPlugin by remember { mutableStateOf<Plugin?>(null) }

// On install tap:
onInstallTap = { plugin ->
    selectedPlugin = plugin
    showInstallSheet = true
}

// Show bottom sheet:
if (showInstallSheet && selectedPlugin != null) {
    PluginInstallBottomSheet(
        pluginName = selectedPlugin!!.name,
        pluginPageUrl = PluginSourceUrls.getPluginPage(selectedPlugin!!.id, selectedPlugin!!.name),
        onDismiss = { showInstallSheet = false },
        onPluginSelected = { uri ->
            showInstallSheet = false
            val result = PluginImporter.importPlugin(context, uri, serverDir)
            when (result) {
                is PluginImporter.ImportResult.Success -> viewModel.onPluginInstalled(result.file.name)
                is PluginImporter.ImportResult.Error -> viewModel.showError(result.message)
            }
        }
    )
}
```

---

## PART 4 — Cleanup Checklist

Before building the new AAB, verify all of these:

### Delete / Remove
- [ ] Any `DownloadManager` usage that fetches JARs
- [ ] Any `OkHttp` / `Retrofit` calls to papermc.io, modrinth.com, geysermc.org that download files
- [ ] Any coroutine/worker that downloads JARs in the background
- [ ] `PaperDownloader.kt` or similar classes (delete entirely)
- [ ] `GeyserInstaller.kt` or similar classes (delete entirely)
- [ ] Hardcoded download URLs for any `.jar` files

### Search Codebase For These Strings (must all be gone):
```
api.papermc.io
modrinth.com
cdn.modrinth.com
geysermc.org
download.geysermc.org
.jar" // any string ending in .jar that's a URL
```

### Add / Verify
- [ ] `assets/plugins/Geyser-Spigot.jar` exists
- [ ] `assets/plugins/floodgate-spigot.jar` exists
- [ ] `BundledPluginInstaller.installBundledPlugins()` called on server setup
- [ ] `ServerJarPickerBottomSheet` shown instead of download on version select
- [ ] `PluginInstallBottomSheet` shown instead of download on plugin install

---

## PART 5 — Resubmit to Google Play

After implementing and testing:

1. Increment `versionCode` and `versionName` in `build.gradle`
2. Build a new AAB: `./gradlew bundleRelease`
3. Go to Play Console → App bundle explorer → deactivate version `29670729`
4. Create new Production release with the new AAB
5. In release notes, mention: *"Redesigned server and plugin installation to use manual file selection per Google Play policy guidelines"*
6. Set to 100% rollout → Review release → Send for review

---

## Notes for Agent
- Do not modify any server runtime logic — only the download/install entry points change
- The `plugins/` folder management (enable/disable/delete plugins) is unaffected
- Geyser config generation is unaffected — only the JAR copy method changes
- If the app has a "check for updates" feature for plugins, replace with "Open plugin page" button
- Test the SAF file picker on Android 10, 12, and 14 — URI permissions differ slightly
