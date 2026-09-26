package com.example.data.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/** Real Google Sign-In (Credential Manager) -> Firebase Auth. */
class AuthManager(context: Context) {

    private val appContext = context.applicationContext

    sealed class SignInResult {
        data class Success(val name: String?) : SignInResult()
        object Cancelled : SignInResult()
        data class Error(val message: String) : SignInResult()
    }

    private fun auth(): FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (e: Throwable) { null }

    val currentUser: FirebaseUser? get() = auth()?.currentUser

    val userFlow: Flow<FirebaseUser?> = callbackFlow {
        val a = auth()
        if (a == null) {
            trySend(null)
            awaitClose { }
        } else {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
            a.addAuthStateListener(listener)
            awaitClose { a.removeAuthStateListener(listener) }
        }
    }

    /** SHA-1 of the key this APK is signed with - this exact value must be added in Firebase console. */
    fun signingSha1(): String? = try {
        val pm = appContext.packageManager
        val sig = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }
        sig?.let { MessageDigest.getInstance("SHA-1").digest(it.toByteArray()).joinToString(":") { b -> "%02X".format(b) } }
    } catch (e: Exception) { null }

    private fun setupHint(): String =
        "\n\nFirebase console > Project settings > com.novaflix.app > Add fingerprint:\nSHA-1: ${signingSha1() ?: "?"}\n" +
            "(and enable Google sign-in under Authentication > Sign-in method)"

    private fun webClientId(): String {
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return if (id != 0) appContext.getString(id) else FALLBACK_WEB_CLIENT_ID
    }

    /** [activityContext] MUST be an Activity (the account picker needs a window). */
    suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        val a = auth() ?: return SignInResult.Error("Firebase services are not initialized")
        return try {
            val option = GetSignInWithGoogleOption.Builder(webClientId()).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val cred = response.credential
            if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInResult.Error("Unexpected response received from Google authentication")
            }
            val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)

            val current = a.currentUser
            if (current != null && current.isAnonymous) {
                // keep the guest's data: link the Google account to the same uid
                try {
                    current.linkWithCredential(firebaseCred).await()
                } catch (e: FirebaseAuthUserCollisionException) {
                    // that Google account already exists -> sign into it (data gets merged by the session sync)
                    a.signInWithCredential(firebaseCred).await()
                }
            } else {
                a.signInWithCredential(firebaseCred).await()
            }
            runCatching { a.currentUser?.reload()?.await() }
            SignInResult.Success(googleCred.displayName ?: a.currentUser?.displayName)
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.Error("No Google account selected or sign-in configuration incomplete." + setupHint())
        } catch (e: GetCredentialException) {
            Log.w("AuthManager", "GetCredentialException: ${e.type} ${e.message}")
            SignInResult.Error("Google sign-in failed: ${e.message ?: e.type}" + setupHint())
        } catch (e: Exception) {
            Log.w("AuthManager", "signIn error: ${e.message}", e)
            SignInResult.Error(e.localizedMessage ?: "Sign-in failed")
        }
    }

    suspend fun signOut() {
        val a = auth() ?: return
        try {
            a.signOut()
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w("AuthManager", "signOut note: ${e.message}")
        }
        // back to a silent guest session so the content list keeps loading
        try { a.signInAnonymously().await() } catch (e: Exception) { Log.w("AuthManager", "anon sign-in note: ${e.message}") }
    }

    companion object {
        // web client from google-services.json (used only if the generated resource is missing)
        const val FALLBACK_WEB_CLIENT_ID = "567787515235-cb3rlrg7ep11fri41otde90fn2m8qpk0.apps.googleusercontent.com"
    }
}
