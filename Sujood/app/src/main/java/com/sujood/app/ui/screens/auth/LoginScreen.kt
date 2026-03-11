package com.sujood.app.ui.screens.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.sujood.app.data.auth.AuthRepository
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private val BgTop       = Color(0xFF0A0E1A)
private val BgBottom    = Color(0xFF0D1230)
private val CardBg      = Color(0xFF111827)
private val CardBorder  = Color(0xFF1E2A45)
private val PrimaryBlue = Color(0xFF1E40FF)
private val BlueBright  = Color(0xFF3B5BFF)
private val GoogleWhite = Color(0xFFF8F9FA)

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    userPreferences: com.sujood.app.data.local.datastore.UserPreferences,
    onSignedIn: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    // Google Sign-In launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    isLoading = true
                    errorMsg  = null
                    scope.launch {
                        val signInResult = authRepository.firebaseSignInWithGoogle(idToken)
                        isLoading = false
                        signInResult.fold(
                            onSuccess = { user ->
                                // Persist the Google display name so it shows immediately
                                // in HomeScreen ("Salam, Name") and Settings
                                val googleName = user.displayName
                                if (!googleName.isNullOrBlank()) {
                                    userPreferences.saveUserName(googleName)
                                }
                                onSignedIn()
                            },
                            onFailure = { e ->
                                errorMsg = "Sign-in failed. Please try again."
                                Log.e("LoginScreen", "Firebase sign-in failed", e)
                            }
                        )
                    }
                } else {
                    errorMsg = "Could not get account token. Please try again."
                }
            } catch (e: ApiException) {
                errorMsg = "Google Sign-In failed (${e.statusCode}). Please try again."
                Log.e("LoginScreen", "Google sign-in ApiException", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // App icon
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.sujood.app.R.mipmap.ic_launcher_image
                ),
                contentDescription = "Sujood",
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Welcome to Sujood",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Sign in to sync your prayer data\nacross all your devices.",
                fontSize = 15.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(52.dp))

            // Google Sign-In button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GoogleWhite)
                    .clickable(enabled = !isLoading) {
                        val client = authRepository.getGoogleSignInClient()
                        launcher.launch(client.signInIntent)
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = PrimaryBlue,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Google "G" logo as text — avoids needing an image asset
                        Text(
                            text = "G",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4285F4)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Continue with Google",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F1F1F)
                        )
                    }
                }
            }

            // Error message
            if (errorMsg != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = errorMsg!!,
                    fontSize = 13.sp,
                    color = Color(0xFFFF6B6B),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            // Skip / use without account
            TextButton(onClick = onSkip) {
                Text(
                    text = "Continue without an account",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                )
            }

            Spacer(Modifier.height(40.dp))

            // Privacy note
            Text(
                text = "We only use your Google account to sync\nyour data. We never post or share anything.",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.3f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}
