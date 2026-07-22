package com.pocketcraft.server.server

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pocketcraft.server.data.model.ServerType
import java.io.File

object ServerJarImporter {
    sealed class ImportResult {
        data class Success(val file: File) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    fun importServerJar(
        context: Context,
        uri: Uri,
        targetFile: File,
        serverType: ServerType
    ): ImportResult {
        return try {
            val fileName = getFileName(context, uri) ?: "server.jar"
            if (!fileName.endsWith(".jar", ignoreCase = true)) {
                return ImportResult.Error("Please select a server .jar file.")
            }

            val fileSize = getFileSize(context, uri)
            val minBytes = if (serverType == ServerType.FABRIC) 10_000L else 1_000_000L
            if (fileSize in 1 until minBytes) {
                return ImportResult.Error("Selected file is too small to be a valid ${serverType.displayName} server JAR.")
            }

            targetFile.parentFile?.mkdirs()
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return ImportResult.Error("Could not read selected file.")

            if (!targetFile.exists() || targetFile.length() < minBytes) {
                targetFile.delete()
                return ImportResult.Error("Imported JAR appears invalid. Please select the official server JAR.")
            }

            try {
                java.util.zip.ZipFile(targetFile).use { zip ->
                    if (zip.size() == 0) {
                        throw java.util.zip.ZipException("Empty ZIP archive")
                    }
                }
            } catch (error: Exception) {
                targetFile.delete()
                return ImportResult.Error("The selected file is not a valid server JAR archive.")
            }

            ImportResult.Success(targetFile)
        } catch (error: Exception) {
            ImportResult.Error("Failed to import server JAR: ${error.message}")
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
        } ?: 0L
    }
}
