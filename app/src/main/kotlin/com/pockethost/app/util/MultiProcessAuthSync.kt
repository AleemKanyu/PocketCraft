package com.pockethost.app.util

import android.content.Context
import org.json.JSONObject
import java.io.File

object MultiProcessAuthSync {
    private fun getSyncFile(context: Context): File {
        return File(context.filesDir, "firebase_auth_sync.json")
    }

    @Synchronized
    fun writeAuthData(context: Context, uid: String?, secret: String?) {
        val file = getSyncFile(context)
        try {
            val obj = JSONObject()
            obj.put("uid", uid ?: "")
            obj.put("secret", secret ?: "")
            file.writeText(obj.toString())
            android.util.Log.d("MultiProcessAuthSync", "Wrote auth sync data: uid=$uid, secret=$secret")
        } catch (e: Exception) {
            android.util.Log.e("MultiProcessAuthSync", "Failed to write auth sync data", e)
        }
    }

    @Synchronized
    fun readUid(context: Context): String? {
        val file = getSyncFile(context)
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText())
            obj.optString("uid").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun readSecret(context: Context): String? {
        val file = getSyncFile(context)
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText())
            obj.optString("secret").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
