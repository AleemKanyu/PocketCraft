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
import com.pocketcraft.server.server.ServerJarManager
import com.pocketcraft.server.data.repository.ServerConfigRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

const val PROGRESS_KEY = "progress_message"
const val PROGRESS_PERCENT = "progress_percent"
const val STEP_KEY = "step"
const val WORLD_SEED_KEY = "world_seed"
const val SERVER_VERSION_KEY = "server_version"
const val WORLD_NAME_KEY = "world_name"

/**
 * WorkManager worker that orchestrates first-launch setup:
 * 1. Extract JRE
 * 2. Validate imported server JAR
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
            val serverVersion = inputData.getString(SERVER_VERSION_KEY) ?: ""
            val worldName = inputData.getString(WORLD_NAME_KEY) ?: "default"

            // Step 1: Extract JRE
            setProgress(data("Extracting Java runtime…", 0, 1))
            val runtime = JreExtractor.runtimeForVersion(serverVersion)
            JreExtractor.extractIfNeeded(applicationContext, runtime)
            setProgressSync(data("Java runtime ready.", 10, 1))

            // Step 2: Validate imported Server JAR
            setProgress(data("Checking imported server $serverVersion…", 20, 2))
            
            // Use ServerFileManager to get the correct isolated directory
            val versionDir = ServerFileManager.getServerDir(applicationContext, worldName)
            val configRepo = ServerConfigRepository(applicationContext)
            val config = configRepo.loadConfig()
            
            val jarFile = ServerFileManager.getServerJarFile(applicationContext, serverVersion, config.serverType)
            ServerJarManager.resolveJar(
                serverType = config.serverType,
                gameVersion = serverVersion,
                customJarPath = config.customJarPath,
                targetFile = jarFile,
                onProgress = { percent ->
                    val overall = 20 + (percent * 0.5).toInt()
                    setProgressAsync(data("Checking imported server $serverVersion… $percent%", overall, 2))
                }
            ).collect { jarFile -> }

            // Step 3: Skip EULA (User will accept on first launch)
            setProgress(data("Preparing Server Environment…", 72, 3))

            // Step 4: Write server config
            val worldSeed = inputData.getString(WORLD_SEED_KEY).orEmpty()
            ServerFileManager.prepareServerProperties(applicationContext, worldName)
            val props = ServerPropertiesHelper.readProperties(versionDir)
            props["level-seed"] = worldSeed
            ServerPropertiesHelper.saveProperties(versionDir, props)

            // Step 5: Install built-in Bedrock bridge plugins
            setProgress(data("Installing Bedrock bridge plugins…", 90, 5))
            PluginManager.ensureBedrockBridgePlugins(
                context = applicationContext,
                worldName = worldName
            ).getOrElse { error ->
                throw IllegalStateException(
                    "Could not install built-in Bedrock bridge plugins: ${error.message}",
                    error
                )
            }
            PluginManager.enforceBedrockBridgeLocalConfig(applicationContext, worldName)

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
