package com.pockethost.app.setup

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.InputStream

object JreExtractor {

    data class RuntimeSpec(
        val id: String,
        val assetDir: String,
        val extractedDirName: String,
        val markerName: String,
        val displayName: String
    )

    val RUNTIME_JAVA_25 = RuntimeSpec(
        id = "java25",
        assetDir = "java/jre25",
        extractedDirName = "jre25",
        markerName = "jre_v25_extracted",
        displayName = "Java 25"
    )

    val RUNTIME_JAVA_26 = RUNTIME_JAVA_25

    val RUNTIME_JAVA_21 = RuntimeSpec(
        id = "java21",
        assetDir = "java/jre21",
        extractedDirName = "jre21",
        markerName = "jre_v21_extracted",
        displayName = "Java 21"
    )

    val RUNTIME_JAVA_17 = RuntimeSpec(
        id = "java17",
        assetDir = "java/jre17",
        extractedDirName = "jre17",
        markerName = "jre_v17_2_extracted",
        displayName = "Java 17"
    )

    /** Last-resort fallback used in internal error paths. */
    private val DEFAULT_RUNTIME = RUNTIME_JAVA_21

    /** Returns the best available runtime for the current device ABI. */
    fun defaultRuntimeForDevice(): RuntimeSpec {
        return RUNTIME_JAVA_21
    }

    fun isAtLeast126(versionId: String): Boolean {
        val minor = parseMinecraftMinor(versionId)
        return minor != null && minor >= 26
    }

    fun parseMinecraftMinor(versionId: String): Int? {
        val trimmed = versionId.trim()
        if (trimmed.startsWith("1.")) {
            val parts = trimmed.removePrefix("1.").split('.', '-', '_')
            return parts.firstOrNull()?.toIntOrNull()
        }
        return trimmed.substringBefore('.').toIntOrNull()
    }

    fun runtimeForVersion(versionId: String): RuntimeSpec {
        val minor = parseMinecraftMinor(versionId) ?: return RUNTIME_JAVA_21
        return when {
            minor >= 26 -> RUNTIME_JAVA_25
            minor in 21..25 -> RUNTIME_JAVA_21
            minor == 20 -> {
                if (versionId.contains("1.20.5") || versionId.contains("1.20.6")) {
                    RUNTIME_JAVA_21
                } else {
                    RUNTIME_JAVA_17
                }
            }
            else -> RUNTIME_JAVA_17 // 1.7.10 - 1.19.x uses Java 17
        }
    }

    fun findExtractedRuntime(context: Context): RuntimeSpec? {
        return if (isExtracted(context, RUNTIME_JAVA_25)) RUNTIME_JAVA_25
        else if (isExtracted(context, RUNTIME_JAVA_21)) RUNTIME_JAVA_21
        else if (isExtracted(context, RUNTIME_JAVA_17)) RUNTIME_JAVA_17
        else null
    }

    private fun normalizeAndroidPath(path: String): String {
        return if (path.startsWith("/data/user/0/")) {
            path.replaceFirst("/data/user/0/", "/data/data/")
        } else {
            path
        }
    }

    fun getJreDir(context: Context, runtime: RuntimeSpec = defaultRuntimeForDevice()): File {
        val rawPath = File(context.filesDir, runtime.extractedDirName).absolutePath
        return File(normalizeAndroidPath(rawPath))
    }

    fun getJavaBinary(context: Context, runtime: RuntimeSpec = defaultRuntimeForDevice()): File =
        File(getJreDir(context, runtime), "bin/java")

    fun isExtracted(context: Context, runtime: RuntimeSpec = defaultRuntimeForDevice()): Boolean {
        val jreDir = getJreDir(context, runtime)
        val marker = File(context.filesDir, runtime.markerName)
        return marker.exists() && hasRequiredRuntimeFiles(jreDir)
    }

    fun extractIfNeeded(
        context: Context,
        runtime: RuntimeSpec = defaultRuntimeForDevice(),
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ) {
        if (runtime == RUNTIME_JAVA_26 && !isExtracted(context, RUNTIME_JAVA_21)) {
            runCatching { extractIfNeeded(context, RUNTIME_JAVA_21, onProgress) }
        }
        val jreDir = getJreDir(context, runtime)
        val marker = File(context.filesDir, runtime.markerName)
        onProgress(0, "Checking Minecraft Runtime...")

        if (marker.exists() && hasRequiredRuntimeFiles(jreDir)) {
            // Even on a cache-hit, guarantee native libraries and java binary permissions are set.
            normalizeRuntimeLayout(context, jreDir)
            fixPermissions(File(jreDir, "bin"))
            onProgress(100, "Minecraft Runtime Ready")
            return
        }

        val assetChildren = context.assets.list(runtime.assetDir).orEmpty()
        if (assetChildren.isEmpty()) {
            if (runtime != DEFAULT_RUNTIME) {
                extractIfNeeded(context, DEFAULT_RUNTIME, onProgress)
                return
            }
            throw IllegalStateException(
                "Missing app/src/main/assets/${runtime.assetDir}/. Please verify bundled assets."
            )
        }

        onProgress(2, "Preparing Minecraft Runtime...")
        deleteLegacyRuntimeDir(context, runtime)
        if (jreDir.exists()) {
            jreDir.deleteRecursively()
        }
        jreDir.mkdirs()

        when {
            hasExpandedRuntimeLayout(context.assets, runtime.assetDir) -> {
                val totalFiles = countAssetFiles(context.assets, runtime.assetDir).coerceAtLeast(1)
                val copiedFiles = intArrayOf(0)
                copyAssetFolder(
                    assets = context.assets,
                    assetPath = runtime.assetDir,
                    destPath = jreDir.absolutePath,
                    totalFiles = totalFiles,
                    copiedFiles = copiedFiles,
                    onProgress = onProgress
                )
            }
            hasComponentRuntimeLayout(context.assets, runtime.assetDir) -> {
                extractComponentRuntime(context, runtime.assetDir, jreDir, onProgress)
            }
            else -> {
                if (runtime != DEFAULT_RUNTIME) {
                    extractIfNeeded(context, DEFAULT_RUNTIME, onProgress)
                    return
                }
                throw IllegalStateException(
                    "Unsupported assets/${runtime.assetDir} layout. Expected either extracted bin/lib " +
                        "contents or component archives like universal.tar.xz and bin-arm64.tar.xz."
                )
            }
        }

        onProgress(76, "Optimizing Minecraft Runtime...")
        flattenSingleRootDir(jreDir)
        onProgress(82, "Finalizing Minecraft Runtime...")
        normalizeRuntimeLayout(context, jreDir)
        onProgress(88, "Applying Runtime Permissions...")
        fixPermissions(jreDir)

        if (!hasRequiredRuntimeFiles(jreDir)) {
            jreDir.deleteRecursively()
            marker.delete()
            throw IllegalStateException(
                "Extracted runtime is incomplete. Expected lib/libjli.so and " +
                    "lib/server/libjvm.so under assets/${runtime.assetDir}/."
            )
        }

        marker.writeText("ok")
        onProgress(100, "Minecraft Runtime Ready")
    }

    fun forceReextract(
        context: Context,
        runtime: RuntimeSpec = defaultRuntimeForDevice(),
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ) {
        resetRuntime(context, runtime)
        extractIfNeeded(context, runtime, onProgress)
    }

    private fun hasRequiredRuntimeFiles(jreDir: File): Boolean {
        val modules = File(jreDir, "lib/modules")
        val libjli = File(jreDir, "lib/libjli.so")
        val libjvm = File(jreDir, "lib/server/libjvm.so")
        val jvmCfg = File(jreDir, "lib/jvm.cfg")
        return (modules.exists() || (libjli.exists() && jvmCfg.exists()))
    }

    fun launchCandidatesForVersion(versionId: String): List<RuntimeSpec> {
        val primary = runtimeForVersion(versionId)
        return when (primary) {
            RUNTIME_JAVA_25 -> listOf(RUNTIME_JAVA_25, RUNTIME_JAVA_21, RUNTIME_JAVA_17)
            RUNTIME_JAVA_17 -> listOf(RUNTIME_JAVA_17, RUNTIME_JAVA_21, RUNTIME_JAVA_25)
            else -> listOf(RUNTIME_JAVA_21, RUNTIME_JAVA_17, RUNTIME_JAVA_25)
        }
    }

    private fun resetRuntime(context: Context, runtime: RuntimeSpec) {
        File(context.filesDir, runtime.markerName).delete()
        getJreDir(context, runtime).deleteRecursively()
        deleteLegacyRuntimeDir(context, runtime)
    }

    private fun deleteLegacyRuntimeDir(context: Context, runtime: RuntimeSpec) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val legacyDir = File(context.codeCacheDir, runtime.extractedDirName)
        if (legacyDir.exists()) {
            legacyDir.deleteRecursively()
        }
    }

    private fun hasExpandedRuntimeLayout(assets: AssetManager, assetDir: String): Boolean {
        val entries = assets.list(assetDir).orEmpty().toSet()
        return "bin" in entries || "lib" in entries
    }

    private fun hasComponentRuntimeLayout(assets: AssetManager, assetDir: String): Boolean {
        val entries = assets.list(assetDir).orEmpty().toSet()
        if ("universal.tar.xz" !in entries) return false
        val archName = abiArchiveName()
        return "bin-$archName.tar.xz" in entries
    }

    private fun extractComponentRuntime(
        context: Context,
        assetDir: String,
        jreDir: File,
        onProgress: (Int, String) -> Unit
    ) {
        extractTarXzAsset(
            assets = context.assets,
            assetPath = "$assetDir/universal.tar.xz",
            destDir = jreDir,
            progressStart = 5,
            progressEnd = 40,
            statusLabel = "Extracting Runtime Core...",
            onProgress = onProgress
        )
        extractTarXzAsset(
            assets = context.assets,
            assetPath = "$assetDir/bin-${abiArchiveName()}.tar.xz",
            destDir = jreDir,
            progressStart = 40,
            progressEnd = 75,
            statusLabel = "Extracting Runtime Binaries...",
            onProgress = onProgress
        )
    }

    private fun parseMinecraftJavaMajor(versionId: String): Int? {
        val trimmed = versionId.trim()

        if (trimmed.startsWith("1.")) {
            val version = Regex("""\d+(?:\.\d+){1,2}""").find(trimmed)?.value ?: return null
            val parts = version.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size < 2) return null
            return parts[1]
        } else {
            // Support 26.x-style release train (Minecraft 1.21+) and other non-standard version trains
            val firstPart = trimmed.substringBefore('.').toIntOrNull()
            if (firstPart != null && firstPart >= 26) {
                return firstPart
            }
        }
        return null
    }

    private fun abiArchiveName(): String {
        val is64Bit = android.os.Process.is64Bit()
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            (abi.contains("arm64") || abi.contains("aarch64")) && is64Bit -> "arm64"
            abi.contains("arm") || abi.contains("arm64") || abi.contains("aarch64") -> "arm"
            abi.contains("x86_64") && is64Bit -> "x86_64"
            abi.contains("x86") || abi.contains("x86_64") -> "x86"
            else -> if (is64Bit) "arm64" else "arm"
        }
    }

    private fun runtimeArchDirName(): String {
        val is64Bit = android.os.Process.is64Bit()
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            (abi.contains("arm64") || abi.contains("aarch64")) && is64Bit -> "aarch64"
            abi.contains("arm") || abi.contains("arm64") || abi.contains("aarch64") -> "aarch32"
            abi.contains("x86_64") && is64Bit -> "amd64"
            abi.contains("x86") || abi.contains("x86_64") -> "i386"
            else -> if (is64Bit) "aarch64" else "aarch32"
        }
    }

    private fun fixPermissions(dir: File) {
        dir.walkTopDown().forEach { file ->
            file.setReadable(true, false)
            if (file.isDirectory) {
                file.setExecutable(true, false)
            } else {
                file.setExecutable(true, false)
                // JRE files (especially .so libraries) must be read-only on Android 14+ (API 34+)
                // to comply with W^X (Write or Execute) restrictions and avoid dlopen crashes.
                file.setWritable(false, false)
            }
        }
    }

    /**
     * Cheap boot-time guard: ensures the java binary is executable without a full re-extract.
     * Call this before launching any server process. Returns true if the binary is ready.
     */
    fun ensureJavaBinaryExecutable(context: Context, runtime: RuntimeSpec = defaultRuntimeForDevice()): Boolean {
        val javaBin = getJavaBinary(context, runtime)
        if (!javaBin.exists()) return false
        if (!javaBin.canExecute()) {
            // Restore execute bit — can be lost after app updates or filesystem remounts
            javaBin.setExecutable(true, false)
            // Also chmod the rest of bin/ in case other launchers (java, keytool) lost it too
            fixPermissions(File(getJreDir(context, runtime), "bin"))
        }
        return javaBin.canExecute()
    }

    private fun copyAssetFolder(
        assets: AssetManager,
        assetPath: String,
        destPath: String,
        totalFiles: Int,
        copiedFiles: IntArray,
        onProgress: (Int, String) -> Unit
    ) {
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            assets.open(assetPath).use { input ->
                File(destPath).also { it.parentFile?.mkdirs() }
                    .outputStream()
                    .use { output -> input.copyTo(output) }
            }
            copiedFiles[0] += 1
            val percent = (2 + ((copiedFiles[0].toFloat() * 73) / totalFiles).toInt()).coerceIn(2, 75)
            onProgress(percent, "Copying Minecraft Runtime Files...")
            return
        }

        File(destPath).mkdirs()
        children.forEach { child ->
            copyAssetFolder(
                assets = assets,
                assetPath = "$assetPath/$child",
                destPath = "$destPath/$child",
                totalFiles = totalFiles,
                copiedFiles = copiedFiles,
                onProgress = onProgress
            )
        }
    }

    private fun extractTarXzAsset(
        assets: AssetManager,
        assetPath: String,
        destDir: File,
        progressStart: Int,
        progressEnd: Int,
        statusLabel: String,
        onProgress: (Int, String) -> Unit
    ) {
        val totalEntries = countTarXzEntries(assets, assetPath).coerceAtLeast(1)
        assets.open(assetPath).use { input ->
            extractTarXzStream(
                input = input,
                destDir = destDir,
                totalEntries = totalEntries,
                progressStart = progressStart,
                progressEnd = progressEnd,
                statusLabel = statusLabel,
                onProgress = onProgress
            )
        }
    }

    private fun extractTarXzStream(
        input: InputStream,
        destDir: File,
        totalEntries: Int,
        progressStart: Int,
        progressEnd: Int,
        statusLabel: String,
        onProgress: (Int, String) -> Unit
    ) {
        var processedEntries = 0
        XZCompressorInputStream(input).use { xzInput ->
            TarArchiveInputStream(xzInput).use { tarInput ->
                while (true) {
                    val entry = tarInput.nextEntry as? TarArchiveEntry ?: break
                    val relative = entry.name
                        .removePrefix("./")
                        .removePrefix("/")
                        .takeIf { it.isNotBlank() }
                        ?: continue

                    val outFile = File(destDir, relative)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { output ->
                            tarInput.copyTo(output)
                        }
                        // Apply Unix permission bits stored in the TAR header.
                        // Without this, bin/java is extracted as 0644 (not executable).
                        val mode = entry.mode
                        outFile.setReadable((mode and 0b100_000_000) != 0, false)
                        outFile.setWritable((mode and 0b010_000_000) != 0, false)
                        outFile.setExecutable((mode and 0b001_000_000) != 0, false)
                    }
                    processedEntries += 1
                    val percent = (progressStart + ((processedEntries.toFloat() * (progressEnd - progressStart)) / totalEntries).toInt()).coerceIn(progressStart, progressEnd)
                    onProgress(percent, statusLabel)
                }
            }
        }
    }

    private fun countAssetFiles(assets: AssetManager, assetPath: String): Int {
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) return 1
        return children.sumOf { child -> countAssetFiles(assets, "$assetPath/$child") }
    }

    private fun countTarXzEntries(assets: AssetManager, assetPath: String): Int {
        var count = 0
        assets.open(assetPath).use { input ->
            XZCompressorInputStream(input).use { xzInput ->
                TarArchiveInputStream(xzInput).use { tarInput ->
                    while (tarInput.nextEntry as? TarArchiveEntry != null) {
                        count += 1
                    }
                }
            }
        }
        return count
    }

    private fun normalizeRuntimeLayout(context: Context, jreDir: File) {
        val libDir = File(jreDir, "lib")
        val archDir = File(libDir, runtimeArchDirName())
        val archJli = File(archDir, "jli/libjli.so")
        val archJvm = File(archDir, "server/libjvm.so")

        if (archJli.exists()) {
            copyFileIfMissing(archJli, File(libDir, "libjli.so"))
            copyFileIfMissing(archJli, File(libDir, "jli/libjli.so"))
        }

        if (archJvm.exists()) {
            copyFileIfMissing(archJvm, File(libDir, "server/libjvm.so"))
        }

        // Copy native JRE libraries from APK nativeLibraryDir if missing from extracted JRE
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val nativeJli = File(nativeLibDir, "libjli.so")
        val nativeJvm = File(nativeLibDir, "libjvm.so")
        if (nativeJli.exists()) {
            copyFileIfMissing(nativeJli, File(libDir, "libjli.so"))
            copyFileIfMissing(nativeJli, File(libDir, "jli/libjli.so"))
        }
        if (nativeJvm.exists()) {
            copyFileIfMissing(nativeJvm, File(libDir, "server/libjvm.so"))
        }

        // Copy all native shared runtime libraries and configs from jre-runtime into target libDir if missing
        val java21Dir = getJreDir(context, RUNTIME_JAVA_21)
        val j21LibDir = File(java21Dir, "lib")
        if (j21LibDir.exists() && jreDir != java21Dir) {
            j21LibDir.walkTopDown().filter { it.isFile && it.name != "modules" }.forEach { file ->
                val relativePath = file.relativeTo(j21LibDir).path
                val targetFile = File(libDir, relativePath)
                copyFileIfMissing(file, targetFile)
            }
        }
    }

    private fun copyFileIfMissing(source: File, target: File) {
        if (target.exists()) return
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun flattenSingleRootDir(dir: File) {
        val children = dir.listFiles().orEmpty()
        if (children.size != 1 || !children[0].isDirectory) return

        val root = children[0]
        if (root.name == "lib" || root.name == "bin") return

        root.listFiles().orEmpty().forEach { child ->
            child.renameTo(File(dir, child.name))
        }
        root.delete()
    }
}
