package com.pocketcraft.server.integrations

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

object GoogleSignInManager {
    private val driveScope = Scope(DriveScopes.DRIVE_APPDATA)

    fun signInIntent(context: Context): Intent {
        return GoogleSignIn.getClient(context, signInOptions()).signInIntent
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, driveScope)) account else null
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        GoogleSignIn.getClient(context, signInOptions()).signOut().addOnCompleteListener { onComplete() }
    }

    fun extractAccountFromResult(data: Intent?): GoogleSignInAccount? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return runCatching { task.result }.getOrNull()
    }

    private fun signInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(driveScope)
            .build()
    }
}
