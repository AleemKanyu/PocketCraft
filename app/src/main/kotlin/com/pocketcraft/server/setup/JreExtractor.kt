package com.pocketcraft.server.setup

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

    private val RUNTIME_JAVA_21 = RuntimeSpec(
        id = "java21",
        assetDir = "jre-runtime",
        extractedDirName = "jre-runtime",
        markerName = "jre_v4_extracted",
        displayName = "Java 21"
    )

    private val RUNTIME_JAVA_25 = RuntimeSpec(
        id = "java25",
        assetDir = "jre-runtime-25",
        extractedDirName = "jre-runtime-25",
        markerName = "jre25_v1_extracted",
        displayName = "Java 25"
    )

    /** Last-resort fallback used in internal error paths. */
    private val DEFAULT_RUNTIME = RUNTIME_JAVA_21

    /** Returns the best available runtime for the current device ABI. */
    fun defaultRuntimeForDevice(): RuntimeSpec {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return if (abi.contains("arm64") || abi.contains("aarch64")) {
            // JRE 25 is 16 KB page-aligned — use it on all modern arm64 devices.
            RUNTIME_JAVA_25
        } else {
            // JRE 25 only ships arm64 binaries; fall back to JRE 21 for 32-bit.
            RUNTIME_JAVA_21
        }
    }

    fun runtimeForVersion(versionId: String): RuntimeSpec {
        val major = parseMajorVersion(versionId)
        return if (major != null && major >= 26) {
            RUNTIME_JAVA_25
        } else {
            defaultRuntimeForDevice()
        }
    }

    fun findExtractedRuntime(context: Context): RuntimeSpec? {
        return listOf(RUNTIME_JAVA_25, RUNTIME_JAVA_21)
            .firstOrNull { isExtracted(context, it) }
    }

    fun getJreDir(context: Context, runtime: RuntimeSpec = defaultRuntimeForDevice()): File {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.codeCacheDir
        } else {
            context.filesDir
        }
        return File(base, runtime.extractedDirName)
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
        val jreDir = getJreDir(context, runtime)
        val marker = File(context.filesDir, runtime.markerName)
        onProgress(0, "Checking Minecraft Runtime...")

        if (marker.exists() && hasRequiredRuntimeFiles(jreDir)) {
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
                "Missing app/src/main/assets/${runtime.assetDir}/. Copy the contents of " +
                "PojavLauncher assets/components/jre-21/ or jre-25/ into that folder and rebuild."
            )
        }

        onProgress(2, "Preparing Minecraft Runtime...")
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
        normalizeRuntimeLayout(jreDir)
        onProgress(88, "Applying Runtime Permissions...")
        fixPermissions(jreDir)

        if (!hasRequiredRuntimeFiles(jreDir)) {
            jreDir.deleteRecursively()
            marker.delete()
            throw IllegalStateException(
                "Extracted runtime is incomplete. Expected lib/libjli.so and " +
                    "lib/server/libjvm.so under assets/jre-runtime/."
            )
        }

        marker.writeText("ok")
        onProgress(100, "Minecraft Runtime Ready")
    }

    private fun hasRequiredRuntimeFiles(jreDir: File): Boolean {
        val libjli = File(jreDir, "lib/libjli.so")
        val libjvm = File(jreDir, "lib/server/libjvm.so")
        return libjli.exists() && libjvm.exists()
    }

    private fun hasExpandedRuntimeLayout(assets: AssetManager, assetDir: String): Boolean {
        val entries = assets.list(assetDir).orEmpty().toSet()
        return "bin" in entries && "lib" in entries
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

    private fun parseMajorVersion(versionId: String): Int? {
        val match = Regex("\\d+").find(versionId) ?: return null
        return match.value.toIntOrNull()
    }

    private fun abiArchiveName(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            abi.contains("arm64") || abi.contains("aarch64") -> "arm64"
            abi.contains("arm") -> "arm"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("x86") -> "x86"
            else -> "arm64"
        }
    }

    private fun runtimeArchDirName(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            abi.contains("arm64") || abi.contains("aarch64") -> "aarch64"
            abi.contains("arm") -> "aarch32"
            abi.contains("x86_64") -> "amd64"
            abi.contains("x86") -> "i386"
            else -> "aarch64"
        }
    }

    private fun fixPermissions(dir: File) {
        dir.walkTopDown().forEach { file ->
            file.setReadable(true, false)
            if (file.isDirectory) {
                file.setExecutable(true, false)
            } else {
                file.setExecutable(true, false)
                file.setWritable(true, false)
            }
        }
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

    private fun normalizeRuntimeLayout(jreDir: File) {
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
        root.listFiles().orEmpty().forEach { child ->
            child.renameTo(File(dir, child.name))
        }
        root.delete()
    }
}
