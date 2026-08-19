package com.pockethost.app.integrations

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.pockethost.app.R
import com.pockethost.app.data.preferences.AppPreferences
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AccountManager {
    private val driveScope = Scope(DriveScopes.DRIVE_APPDATA)

    fun currentUser(): FirebaseUser? = FirebaseAuth.getInstance().currentUser

    fun currentDriveAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, driveScope)) account else null
    }

    fun googleSignInIntent(context: Context): Intent {
        return GoogleSignIn.getClient(context, googleOptions(context)).signInIntent
    }

    fun signInWithEmail(
        context: Context,
        email: String,
        password: String,
        onResult: (Result<FirebaseUser>) -> Unit
    ) {
        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    AppPreferences(context).firebaseUserUid = user.uid
                    onResult(Result.success(user))
                } else {
                    onResult(Result.failure(IllegalStateException("No user was returned.")))
                }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    fun createAccountWithEmail(
        context: Context,
        email: String,
        password: String,
        onResult: (Result<FirebaseUser>) -> Unit
    ) {
        FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    AppPreferences(context).firebaseUserUid = user.uid
                    onResult(Result.success(user))
                } else {
                    onResult(Result.failure(IllegalStateException("No user was returned.")))
                }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    fun sendPasswordReset(
        email: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        FirebaseAuth.getInstance()
            .sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { error -> onResult(Result.failure(error)) }
    }

    /** Sends an email-verification link to the currently signed-in user's address. */
    fun sendEmailVerification(onResult: (Result<Unit>) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult(Result.failure(IllegalStateException("No signed-in user.")))
            return
        }
        user.sendEmailVerification()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /** Reloads the Firebase user from the server and returns whether their email is now verified. */
    fun reloadAndCheckVerified(onResult: (Result<Boolean>) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult(Result.failure(IllegalStateException("No signed-in user.")))
            return
        }
        user.reload()
            .addOnSuccessListener { onResult(Result.success(user.isEmailVerified)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /**
     * Re-authenticates the current email/password user with [password], then permanently deletes
     * their account, Firestore data, and Drive backups.
     * Required when Firebase throws [FirebaseAuthRecentLoginRequiredException] on plain delete.
     */
    suspend fun reauthenticateAndDeleteAccount(context: Context, password: String): Result<String> {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?: return Result.failure(IllegalStateException("No signed-in account to delete."))
        val email = firebaseUser.email
            ?: return Result.failure(IllegalStateException("Cannot re-authenticate: no email on account."))
        return runCatching {
            val credential = EmailAuthProvider.getCredential(email, password)
            firebaseUser.reauthenticate(credential).awaitVoidTask()
            runCatching {
                FirebaseFirestore.getInstance()
                    .collection("user_settings")
                    .document(firebaseUser.uid)
                    .delete()
                    .awaitVoidTask()
            }
            runCatching {
                val account = currentDriveAccount(context)
                if (account != null) DriveBackupManager.deleteAllCloudBackups(context, account)
            }
            runCatching {
                GoogleSignIn.getClient(context, googleOptions(context))
                    .revokeAccess()
                    .awaitVoidTask()
            }
            firebaseUser.delete().awaitVoidTask()
            FirebaseAuth.getInstance().signOut()
            AppPreferences(context).firebaseUserUid = null
            AppPreferences(context).dashboardSecret = null
            "Account deleted permanently."
        }
    }

    fun completeGoogleSignIn(
        context: Context,
        data: Intent?,
        onResult: (googleAccount: GoogleSignInAccount?, user: FirebaseUser?, errorMessage: String?) -> Unit
    ) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = try {
            task.getResult(ApiException::class.java)
        } catch (error: ApiException) {
            onResult(null, null, googleErrorMessage(error.statusCode))
            return
        } ?: run {
            onResult(null, null, "Google sign-in did not return an account.")
            return
        }

        val idToken = account.idToken
        if (idToken.isNullOrBlank()) {
            onResult(null, null, "Google sign-in completed, but no ID token was returned.")
            return
        }

        FirebaseAuth.getInstance()
            .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    AppPreferences(context).firebaseUserUid = user.uid
                }
                onResult(account, result.user, null)
            }
            .addOnFailureListener { error ->
                onResult(account, null, error.userFacingMessage())
            }
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        // Firebase sign-out is purely local — invoke the callback immediately so the UI
        // never gets stuck waiting on a network call.
        FirebaseAuth.getInstance().signOut()
        AppPreferences(context).firebaseUserUid = null
        AppPreferences(context).dashboardSecret = null
        onComplete()
        // Best-effort: revoke Google session in the background. Wrapped in runCatching so
        // any exception (e.g. missing web_client_id resource) is silently discarded and
        // cannot affect the already-updated UI state.
        runCatching {
            GoogleSignIn.getClient(context, googleOptions(context)).signOut()
        }
    }

    suspend fun deleteAccount(context: Context): Result<String> {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?: return Result.failure(IllegalStateException("No signed-in account to delete."))
        return runCatching {
            // Delete Firestore user data (best-effort)
            runCatching {
                FirebaseFirestore.getInstance()
                    .collection("user_settings")
                    .document(firebaseUser.uid)
                    .delete()
                    .awaitVoidTask()
            }
            // Delete Google Drive backups if present (best-effort)
            runCatching {
                val account = currentDriveAccount(context)
                if (account != null) {
                    DriveBackupManager.deleteAllCloudBackups(context, account)
                }
            }
            // Revoke Google access (best-effort)
            runCatching {
                GoogleSignIn.getClient(context, googleOptions(context))
                    .revokeAccess()
                    .awaitVoidTask()
            }
            // Delete the Firebase account — may throw FirebaseAuthRecentLoginRequiredException
            firebaseUser.delete().awaitVoidTask()
            FirebaseAuth.getInstance().signOut()
            AppPreferences(context).firebaseUserUid = null
            AppPreferences(context).dashboardSecret = null
            "Account deleted permanently."
        }
    }

    private fun googleOptions(context: Context): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .requestScopes(driveScope)
            .build()
    }

    private fun googleErrorMessage(statusCode: Int): String {
        return when (statusCode) {
            CommonStatusCodes.NETWORK_ERROR -> "Google sign-in failed due to a network error."
            CommonStatusCodes.CANCELED -> "Google sign-in was cancelled."
            CommonStatusCodes.SIGN_IN_REQUIRED -> "Please choose a Google account to continue."
            else -> "Google sign-in failed (${CommonStatusCodes.getStatusCodeString(statusCode)})."
        }
    }
}

private fun Throwable.userFacingMessage(): String {
    return when (this) {
        is FirebaseAuthInvalidUserException -> "No account exists for that email yet."
        is FirebaseAuthInvalidCredentialsException -> "That email or password is invalid."
        is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException ->
            "For security, please sign out and sign back in before deleting your account."
        else -> message ?: javaClass.simpleName
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        if (continuation.isActive) continuation.cancel()
    }
}

/** Awaits a [Task] that returns [Void] (nullable), safely resolving the null result as [Unit]. */
private suspend fun com.google.android.gms.tasks.Task<Void>.awaitVoidTask(): Unit = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener {
        if (continuation.isActive) continuation.resume(Unit)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        if (continuation.isActive) continuation.cancel()
    }
}
