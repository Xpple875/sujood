package com.sujood.app.data.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Single source of truth for authentication state.
 *
 * Wraps FirebaseAuth so the rest of the app never imports Firebase directly —
 * everything goes through this class. Makes it easy to swap auth providers
 * later or mock in tests.
 */
class AuthRepository(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Emits the current FirebaseUser whenever auth state changes (login/logout). */
    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Returns the current user synchronously (null if not signed in). */
    fun getCurrentUserSync(): FirebaseUser? = auth.currentUser

    /** True if a user is currently signed in. */
    val isSignedIn: Boolean get() = auth.currentUser != null

    /**
     * Builds the GoogleSignInClient with the Web Client ID from Firebase.
     * The Web Client ID comes from google-services.json automatically via
     * the R.string.default_web_client_id resource — no hardcoding needed.
     */
    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.sujood.app.R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Completes the Google Sign-In flow using the ID token returned by the
     * Google Sign-In intent. Called from the activity result callback.
     *
     * @return Result.success(FirebaseUser) on success, Result.failure on error.
     */
    suspend fun firebaseSignInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw Exception("Sign-in succeeded but user is null")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs the user out of both Firebase and Google (clears Google account
     * chooser cache so they can pick a different account next time).
     */
    suspend fun signOut() {
        auth.signOut()
        try {
            getGoogleSignInClient().signOut().await()
        } catch (_: Exception) {
            // Non-fatal — Firebase sign-out already completed
        }
    }

    /** Display name for the signed-in user, or null. */
    fun getDisplayName(): String? = auth.currentUser?.displayName

    /** Email for the signed-in user, or null. */
    fun getEmail(): String? = auth.currentUser?.email

    /** Profile photo URL string for the signed-in user, or null. */
    fun getPhotoUrl(): String? = auth.currentUser?.photoUrl?.toString()

    /** Firebase UID — stable unique ID for this user across devices. */
    fun getUid(): String? = auth.currentUser?.uid
}
