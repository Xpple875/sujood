package com.sujood.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.ui.components.GlassmorphicBottomNavBar
import com.sujood.app.ui.screens.dhikr.DhikrScreen
import com.sujood.app.ui.screens.home.HomeScreen
import com.sujood.app.ui.screens.insights.InsightsScreen
import com.sujood.app.ui.screens.onboarding.EmotionalOnboardingScreen
import com.sujood.app.ui.screens.qibla.QiblaScreen
import com.sujood.app.ui.screens.monetization.MonetizationScreen
import com.sujood.app.ui.screens.splash.SplashScreen
import com.sujood.app.ui.screens.settings.SettingsScreen
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.SujoodTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var userPreferences: UserPreferences
    private lateinit var repository: PrayerTimesRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled silently — HomeScreen shows error if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Request overlay (SYSTEM_ALERT_WINDOW) permission if not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)
        ) {
            val overlayIntent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
        }

        userPreferences = UserPreferences(this)
        repository = PrayerTimesRepository(
            RetrofitClient.aladhanApiService,
            (application as SujoodApplication).database.prayerLogDao()
        )

        setContent {
            SujoodTheme {
                SujoodApp(
                    userPreferences = userPreferences,
                    repository = repository
                )
            }
        }
    }
}

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Monetization : Screen("monetization")
    data object Home : Screen("home")
    data object Dhikr : Screen("dhikr")
    data object Qibla : Screen("qibla")
    data object Insights : Screen("insights")
    data object Settings : Screen("settings")
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SujoodApp(
    userPreferences: UserPreferences,
    repository: PrayerTimesRepository
) {
    val navController = rememberNavController()
    val settings by userPreferences.userSettings.collectAsState(initial = com.sujood.app.domain.model.UserSettings())
    val hasCompletedOnboarding = settings.hasCompletedOnboarding
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Splash.route

    val screensWithBottomNav = listOf(
        Screen.Home.route,
        Screen.Dhikr.route,
        Screen.Qibla.route,
        Screen.Insights.route,
        Screen.Settings.route
    )

    val showBottomNavBar = currentRoute in screensWithBottomNav

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        // Scaffold with NO bottomBar — navbar is overlaid as a floating Box below
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {}
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                modifier = Modifier
                    .fillMaxSize()
                    // Don't use innerPadding — we overlay the navbar manually
            ) {
                composable(Screen.Splash.route) {
                    // Wait for DataStore to emit before deciding which screen to go to.
                    // Without this, hasCompletedOnboarding is always the default (false)
                    // at first read, causing onboarding to re-show every launch.
                    val splashSettings by userPreferences.userSettings.collectAsState(
                        initial = null
                    )
                    SplashScreen(
                        onNavigate = {
                            // If DataStore hasn't emitted yet, do nothing — splash will call
                            // onNavigate again after the animation delay
                            val loaded = splashSettings ?: return@SplashScreen
                            if (loaded.hasCompletedOnboarding) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Screen.Onboarding.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable(Screen.Onboarding.route) {
                    EmotionalOnboardingScreen(
                        userPreferences = userPreferences,
                        onComplete = {
                            navController.navigate(Screen.Monetization.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Monetization.route) {
                    MonetizationScreen(
                        userPreferences = userPreferences,
                        onContinue = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Monetization.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Home.route) {
                    HomeScreen(
                        userPreferences = userPreferences,
                        repository = repository
                    )
                }

                composable(Screen.Dhikr.route) {
                    DhikrScreen()
                }

                composable(Screen.Qibla.route) {
                    QiblaScreen()
                }

                composable(Screen.Insights.route) {
                    InsightsScreen()
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        userPreferences = userPreferences,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // ── Floating navbar overlay — sits above content, transparent corners ──
        AnimatedVisibility(
            visible = showBottomNavBar,
            enter = fadeIn() + slideInVertically { it },
            exit  = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            GlassmorphicBottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { newRoute ->
                    navController.navigate(newRoute) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    } // outer Box
}