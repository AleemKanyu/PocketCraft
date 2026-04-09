package com.pocketcraft.server.setup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.pocketcraft.server.service.PluginManager
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.service.ServerPropertiesHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import java.io.File

const val PROGRESS_KEY = "progress_message"
const val PROGRESS_PERCENT = "progress_percent"
const val STEP_KEY = "step"
const val WORLD_SEED_KEY = "world_seed"
const val SERVER_VERSION_KEY = "server_version"

/**
 * WorkManager worker that orchestrates first-launch setup:
 * 1. Extract JRE
 * 2. Download PaperMC JAR
 * 3. Write eula.txt
 * 4. Write server.properties
 */
@HiltWorker
class SetupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val serverVersion = inputData.getString(SERVER_VERSION_KEY) ?: "1.20.4"

            // Step 1: Extract JRE
            setProgress(data("Extracting Java runtime…", 0, 1))
            JreExtractor.extractIfNeeded(applicationContext)
            setProgressSync(data("Java runtime ready.", 10, 1))

            // Step 2: Download PaperMC
            setProgress(data("Downloading PaperMC $serverVersion…", 20, 2))
            val client = OkHttpClient()
            val downloader = PaperMcDownloader(client, serverVersion)
            
            // Use ServerFileManager to get the correct version-specific directory
            val versionDir = ServerFileManager.getServerDir(applicationContext, serverVersion)
            
            downloader.download(versionDir).collect { percent ->
                val overall = 20 + (percent * 0.5).toInt()
                setProgress(data("Downloading PaperMC $serverVersion… $percent%", overall, 2))
            }

            // Step 3: Write eula.txt
            setProgress(data("Accepting EULA…", 72, 3))
            java.io.File(versionDir, "eula.txt")
                .writeText("eula=true\n")

            // Step 4: Write server config
            setProgress(data("Writing server config…", 80, 4))
            val worldSeed = inputData.getString(WORLD_SEED_KEY).orEmpty()
            ServerFileManager.prepareServerProperties(applicationContext, serverVersion)
            val props = ServerPropertiesHelper.readProperties(versionDir)
            props["level-seed"] = worldSeed
            ServerPropertiesHelper.saveProperties(versionDir, props)

            // Step 5: Install built-in Bedrock bridge plugins
            setProgress(data("Installing Bedrock bridge plugins…", 90, 5))
            PluginManager.ensureBedrockBridgePlugins(
                context = applicationContext,
                versionId = serverVersion
            ).getOrElse { error ->
                throw IllegalStateException(
                    "Could not install built-in Bedrock bridge plugins: ${error.message}",
                    error
                )
            }
            PluginManager.enforceBedrockBridgeLocalConfig(applicationContext, serverVersion)

            // Step 6: Mark complete
            setProgress(data("Setup complete!", 100, 6))
            File(applicationContext.filesDir, ".setup_done").writeText("done")

            Result.success()
        } catch (e: Exception) {
            Log.e("SetupWorker", "Setup failed", e)
            Result.failure(
                Data.Builder().putString("error", e.message ?: "Unknown error").build()
            )
        }
    }

    private fun data(message: String, percent: Int, step: Int) = Data.Builder()
        .putString(PROGRESS_KEY, message)
        .putInt(PROGRESS_PERCENT, percent)
        .putInt(STEP_KEY, step)
        .build()

    private suspend fun setProgressSync(data: Data) {
        setProgress(data)
    }
}
