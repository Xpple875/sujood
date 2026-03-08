package com.sujood.app.ui.screens.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.launch

data class PainPoint(
    val id: String,
    val emoji: String,
    val title: String,
    val solution: String
)

val painPoints = listOf(
    PainPoint("phone", "📱", "I get distracted by my phone", "Sujood locks your phone at prayer time so nothing pulls you away"),
    PainPoint("forgot", "⏰", "I forget when prayer time comes", "Live countdown and adhan notifications keep you aware all day"),
    PainPoint("fajr", "😴", "I struggle to wake up for Fajr", "Fajr alarm cuts through do not disturb so you never miss dawn prayer"),
    PainPoint("inconsistent", "🔄", "I pray inconsistently", "Streak tracker keeps you accountable day after day")
)

@Composable
fun EmotionalOnboardingScreen(
    userPreferences: UserPreferences,
    onComplete: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState > initialState) {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            } else {
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            }
        },
        label = "onboarding"
    ) { screen ->
        when (screen) {
            0 -> WelcomeScreen(onBegin = { currentScreen = 1 })
            1 -> PainPointScreen(onContinue = { currentScreen = 2 })
            2 -> SolutionScreen(onContinue = { currentScreen = 3 })
            3 -> NameInputScreen(userPreferences = userPreferences, onContinue = { currentScreen = 4 })
            4 -> PermissionsScreen(userPreferences = userPreferences, context = context, onContinue = { currentScreen = 5 })
            5 -> ReadyScreen(userPreferences = userPreferences, onEnter = onComplete)
        }
    }
}

@Composable
private fun WelcomeScreen(onBegin: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "moon")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(DeepNavy, Color(0xFF0F1729), Color(0xFF1A0F2E), Color(0xFF2D1F4E))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(SoftPurple.copy(alpha = glowAlpha.coerceIn(0f, 1f)), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("☪", fontSize = 80.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text("Prayer Lock", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light, color = Color.White)
            Text("| Sujood", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light, color = SoftPurple)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Never miss a prayer again", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onBegin,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Begin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PainPointScreen(onContinue: () -> Unit) {
    val selectedPainPoints = remember { mutableStateListOf<String>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = listOf(DeepNavy, Color(0xFF151B2E), Color(0xFF2D1F4E))))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text("We know the struggle...", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("What gets in the way of your salah?", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(32.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(painPoints) { painPoint ->
                    val isSelected = selectedPainPoints.contains(painPoint.id)
                    FrostedGlassCard(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (isSelected) selectedPainPoints.remove(painPoint.id)
                            else selectedPainPoints.add(painPoint.id)
                        }.then(if (isSelected) Modifier.border(2.dp, SoftPurple, RoundedCornerShape(16.dp)) else Modifier),
                        cornerRadius = 16.dp,
                        borderColor = if (isSelected) SoftPurple else GlassBorder
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(painPoint.emoji, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(painPoint.title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                            Spacer(modifier = Modifier.weight(1f))
                            if (isSelected) Icon(Icons.Default.Check, null, tint = SoftPurple)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                shape = RoundedCornerShape(28.dp),
                enabled = selectedPainPoints.isNotEmpty()
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SolutionScreen(onContinue: () -> Unit) {
    val selectedSolutions = remember { mutableStateListOf(*painPoints.take(2).map { it.id }.toTypedArray()) }

    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(DeepNavy, Color(0xFF151B2E), Color(0xFF2D1F4E))))) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp))
            Text("Here's how Sujood helps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(painPoints.filter { selectedSolutions.contains(it.id) }) { painPoint ->
                    FrostedGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, borderColor = SoftPurple.copy(alpha = 0.5f)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(SoftPurple.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, null, tint = SoftPurple, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(painPoint.solution, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = SoftPurple), shape = RoundedCornerShape(28.dp)) {
                Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NameInputScreen(userPreferences: UserPreferences, onContinue: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(DeepNavy, Color(0xFF151B2E), Color(0xFF2D1F4E))))) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(modifier = Modifier.weight(1f))

            Text("What should we call you?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("We'll use this to personalize your experience", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Your Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SoftPurple, unfocusedBorderColor = GlassBorder, focusedLabelColor = SoftPurple, cursorColor = SoftPurple, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    scope.launch { userPreferences.saveUserName(name) }
                    focusManager.clearFocus()
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionsScreen(userPreferences: UserPreferences, context: Context, onContinue: () -> Unit) {
    val scope = rememberCoroutineScope()
    
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val notificationsPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }
    
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Update overlay state when returning to app
    LaunchedEffect(Unit) {
        while(true) {
            canDrawOverlays.value = Settings.canDrawOverlays(context)
            if (locationPermissionState.status.isGranted && 
                (notificationsPermissionState?.status?.isGranted != false) && 
                canDrawOverlays.value) {
                scope.launch { userPreferences.setOnboardingCompleted() }
                onContinue()
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    fun handleGrantClick() {
        when {
            !locationPermissionState.status.isGranted -> {
                locationPermissionState.launchPermissionRequest()
            }
            notificationsPermissionState != null && !notificationsPermissionState.status.isGranted -> {
                notificationsPermissionState.launchPermissionRequest()
            }
            !canDrawOverlays.value -> {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(DeepNavy, Color(0xFF151B2E), Color(0xFF2D1F4E))))) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp))
            Text("Powering your Salah", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Sujood needs these to keep you consistent", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(32.dp))

            val permissionItems = listOf(
                PermissionItem(
                    icon = Icons.Default.LocationOn,
                    title = "Location",
                    description = "For accurate prayer times",
                    isGranted = locationPermissionState.status.isGranted
                ),
                PermissionItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    description = "For Adhan reminders",
                    isGranted = notificationsPermissionState?.status?.isGranted ?: true
                ),
                PermissionItem(
                    icon = Icons.Default.Lock,
                    title = "Display over apps",
                    description = "To lock your screen",
                    isGranted = canDrawOverlays.value
                )
            )

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(permissionItems) { item ->
                    FrostedGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        borderColor = if (item.isGranted) SoftPurple.copy(alpha = 0.5f) else GlassBorder
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (item.isGranted) SoftPurple else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge, color = if (item.isGranted) Color.White else Color.White.copy(alpha = 0.7f))
                                Text(item.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            if (item.isGranted) {
                                Icon(Icons.Default.Check, null, tint = SoftPurple)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { handleGrantClick() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                shape = RoundedCornerShape(28.dp)
            ) {
                val buttonText = when {
                    !locationPermissionState.status.isGranted -> "Grant Location"
                    notificationsPermissionState != null && !notificationsPermissionState.status.isGranted -> "Grant Notifications"
                    !canDrawOverlays.value -> "Grant Overlay"
                    else -> "All Set!"
                }
                Text(buttonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    scope.launch { userPreferences.setOnboardingCompleted() }
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MidnightBlue, contentColor = TextSecondary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Skip for now", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private data class PermissionItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String,
    val isGranted: Boolean
)

@Composable
private fun ReadyScreen(userPreferences: UserPreferences, onEnter: () -> Unit) {
    val userName by userPreferences.userName.collectAsState(initial = "")
    val scope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "celebration")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(1500), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )

    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(DeepNavy, Color(0xFF1A1035), Color(0xFF2D1F4E), Color(0xFF3D2F5E))))) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(modifier = Modifier.weight(1f))

            Box(modifier = Modifier.size(120.dp).scale(scale).clip(CircleShape).background(brush = Brush.radialGradient(colors = listOf(WarmAmber.copy(alpha = 0.4f), SoftPurple.copy(alpha = 0.2f), Color.Transparent))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("☪", fontSize = 50.sp, color = Color.White)
                    Text("⭐", fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("You're all set, ${if (userName.isNotEmpty()) userName else "there"}!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Your journey to consistent salah starts now", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    scope.launch { userPreferences.setOnboardingCompleted() }
                    onEnter()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Enter Sujood", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
