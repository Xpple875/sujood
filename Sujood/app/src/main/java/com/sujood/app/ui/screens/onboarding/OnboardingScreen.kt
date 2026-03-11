package com.sujood.app.ui.screens.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.GlassBackground
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val iconColor: Color = SoftPurple
)

val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Default.Lock,
        title = "Stay Mindful",
        description = "Sujood helps you stay consistent with your prayers by gently reminding you until you've completed each salah.",
        iconColor = SoftPurple
    ),
    OnboardingPage(
        icon = Icons.Default.LocationOn,
        title = "Accurate Times",
        description = "Get accurate prayer times based on your location. Our app uses trusted calculation methods from the Aladhan API.",
        iconColor = WarmAmber
    ),
    OnboardingPage(
        icon = Icons.Default.Notifications,
        title = "Enable Permissions",
        description = "Grant these permissions so Sujood can deliver Adhan notifications and prayer lock — even when the app is in the background.",
        iconColor = LavenderGlow
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DeepNavy,
                        Color(0xFF151B2E),
                        Color(0xFF2D1F4E)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button at top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < onboardingPages.size - 1) {
                    TextButton(onClick = onOnboardingComplete) {
                        Text(
                            text = "Skip",
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page == onboardingPages.size - 1) {
                    // Interactive permissions page
                    PermissionsPageContent(page = onboardingPages[page], pageIndex = page)
                } else {
                    OnboardingPageContent(page = onboardingPages[page], pageIndex = page)
                }
            }

            // Page indicators
            Row(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(onboardingPages.size) { index ->
                    val color by animateFloatAsState(
                        targetValue = if (pagerState.currentPage == index) 1f else 0.3f,
                        animationSpec = tween(300),
                        label = "indicator"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SoftPurple.copy(alpha = color))
                    )
                }
            }

            // Next/Get Started button
            Button(
                onClick = {
                    if (pagerState.currentPage < onboardingPages.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onOnboardingComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoftPurple
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = if (pagerState.currentPage < onboardingPages.size - 1) "Next" else "Get Started",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    pageIndex: Int
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(500, delayMillis = pageIndex * 100))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                page.iconColor.copy(alpha = 0.3f),
                                GlassBackground
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = page.iconColor
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun PermissionsPageContent(
    page: OnboardingPage,
    pageIndex: Int
) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager

    var notifGranted by remember { mutableStateOf(false) }
    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var batteryGranted by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    // Notification permission launcher (Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(500, delayMillis = pageIndex * 100))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                page.iconColor.copy(alpha = 0.3f),
                                GlassBackground
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = page.iconColor
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Permission rows
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Notifications
                PermissionRow(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Required for Adhan alerts",
                    granted = notifGranted,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notifGranted = true
                        }
                    }
                )

                // Display over other apps
                PermissionRow(
                    icon = Icons.Default.Lock,
                    title = "Display Over Apps",
                    subtitle = "Required for prayer lock screen",
                    granted = overlayGranted,
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                        // We can't get a result from system settings, user comes back manually
                        overlayGranted = Settings.canDrawOverlays(context)
                    }
                )

                // Battery optimization — the critical one
                PermissionRow(
                    icon = Icons.Default.BatteryFull,
                    title = "Disable Battery Optimization",
                    subtitle = "Critical — prevents Adhan from being killed in background",
                    granted = batteryGranted,
                    accentColor = if (!batteryGranted) Color(0xFFF59E0B) else null,
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                        batteryGranted = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    accentColor: Color? = null,
    onClick: () -> Unit
) {
    val rowColor = accentColor ?: SoftPurple
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1F35))
            .border(
                width = 1.dp,
                color = if (granted) Color(0xFF22C55E).copy(alpha = 0.4f)
                        else rowColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon bubble
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (granted) Color(0xFF22C55E).copy(alpha = 0.15f)
                    else rowColor.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (granted) Color(0xFF22C55E) else rowColor
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.width(10.dp))

        // Status badge
        if (granted) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Granted",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF22C55E)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(rowColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Grant",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = rowColor
                )
            }
        }
    }
}
