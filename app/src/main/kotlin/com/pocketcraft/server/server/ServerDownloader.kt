package com.pocketcraft.server.server

import android.content.Context
import android.util.Log
import com.pocketcraft.server.service.PluginManager
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.setup.JreExtractor
import com.pocketcraft.server.setup.PaperMcDownloader

object ServerDownloader {
    private const val TAG = "ServerDownloader"

    /**
     * Full preparation: extract JRE if needed, then download the Paper JAR.
     *
     * All [onStatus] and [onProgress] callbacks are invoked on the calling dispatcher.
     * Use from a coroutine on the Main dispatcher if you need to update Compose state.
     */
    suspend fun prepareVersion(
        context: Context,
        versionId: String,
        onStatus: suspend (String) -> Unit,
        onProgress: suspend (Int) -> Unit
    ) {
        if (!JreExtractor.isExtracted(context)) {
            onStatus("Preparing Java runtime…")
            JreExtractor.extractIfNeeded(context)
        } else {
            onStatus("Java runtime ready.")
        }

        if (!ServerFileManager.isServerJarReady(context, versionId)) {
            onStatus("Downloading Paper $versionId…")
            // The Flow is already flowOn(IO), so collecting here is safe from Main.
            PaperMcDownloader(PaperMcDownloader.buildClient(), versionId)
                .download(ServerFileManager.getServerDir(context, versionId))
                .collect { progress -> onProgress(progress) }
        } else {
            onStatus("Server files already downloaded.")
            onProgress(100)
        }

        ServerFileManager.prepareEula(context, versionId)
        onStatus("Installing built-in Bedrock bridge…")
        PluginManager.ensureBedrockBridgePlugins(context, versionId)
            .getOrElse { throw it }
        PluginManager.enforceBedrockBridgeLocalConfig(context, versionId)
        onStatus("Server files ready.")
    }

    /**
     * Simplified download for the AutoDownloadScreen.
     *
     * Must be called from a Main-dispatcher coroutine so that the [onProgress]
     * and [onStatus] lambdas can safely update Compose state.
     *
     * The Flow inside [PaperMcDownloader] runs on IO via .flowOn(IO), so network
     * work never blocks the main thread.
     */
    suspend fun downloadPaperJarOnMain(
        context: Context,
        versionId: String,
        onStatus: (String) -> Unit,
        onProgress: (Int) -> Unit,
        forceDownload: Boolean = false
    ) {
        Log.i(TAG, "downloadPaperJarOnMain version=$versionId forceDownload=$forceDownload")
        if (!JreExtractor.isExtracted(context)) {
            onStatus("Preparing Java runtime…")
            JreExtractor.extractIfNeeded(context)
        } else {
            onStatus("Java runtime ready.")
        }

        val jarReady = ServerFileManager.isServerJarReady(context, versionId)
        Log.i(TAG, "download check version=$versionId jarReady=$jarReady forceDownload=$forceDownload")
        if (forceDownload || !jarReady) {
            onStatus("Downloading Paper $versionId…")
            if (forceDownload) {
                runCatching { ServerFileManager.getServerJarFile(context, versionId).delete() }
                Log.i(TAG, "force deleted existing jar for version=$versionId")
            }
            // Collect the IO-backed flow on whichever dispatcher the caller is on (Main).
            // Each emit() crosses into Main because collect {} runs on that dispatcher.
            PaperMcDownloader(PaperMcDownloader.buildClient(), versionId)
                .download(ServerFileManager.getServerDir(context, versionId))
                .collect { p -> onProgress(p) }
            Log.i(TAG, "download finished version=$versionId")
        } else {
            onStatus("Server files already downloaded.")
            onProgress(100)
            Log.w(TAG, "download skipped because jar already ready for version=$versionId")
        }

        ServerFileManager.prepareEula(context, versionId)
        onStatus("Installing built-in Bedrock bridge…")
        PluginManager.ensureBedrockBridgePlugins(context, versionId)
            .getOrElse { throw it }
        PluginManager.enforceBedrockBridgeLocalConfig(context, versionId)
        onStatus("Done.")
        Log.i(TAG, "version preparation completed version=$versionId")
    }

    // Legacy — kept for compatibility but prefer downloadPaperJarOnMain
    suspend fun downloadPaperJar(
        context: Context,
        versionId: String,
        onProgress: (Int) -> Unit
    ) {
        downloadPaperJarOnMain(
            context    = context,
            versionId  = versionId,
            onStatus   = {},
            onProgress = onProgress,
            forceDownload = false
        )
    }
}
