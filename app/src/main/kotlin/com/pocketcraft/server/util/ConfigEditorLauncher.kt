package com.pocketcraft.server.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ConfigEditorLauncher {

    fun openConfig(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "Config file not found: ${file.name}", Toast.LENGTH_LONG).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = "text/plain"
        val editIntent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        val pm: PackageManager = context.packageManager
        val editors = pm.queryIntentActivities(editIntent, PackageManager.MATCH_DEFAULT_ONLY)

        when {
            editors.isNotEmpty() -> {
                if (editors.size == 1) {
                    editIntent.setPackage(editors[0].activityInfo.packageName)
                    context.startActivity(editIntent)
                } else {
                    context.startActivity(Intent.createChooser(editIntent, "Edit ${file.name} with"))
                }
            }

            else -> {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val viewers = pm.queryIntentActivities(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
                if (viewers.isEmpty()) {
                    Toast.makeText(
                        context,
                        "No text editor installed to open ${file.name}.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    context.startActivity(Intent.createChooser(viewIntent, "Open ${file.name} with"))
                }
            }
        }
    }
}
