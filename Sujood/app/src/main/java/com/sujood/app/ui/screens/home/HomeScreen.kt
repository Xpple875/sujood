package com.sujood.app.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerTime
import com.sujood.app.domain.usecase.GetNextPrayerUseCase
import com.sujood.app.domain.usecase.GetPrayerStreakUseCase
import com.sujood.app.domain.usecase.LogPrayerCompletionUseCase
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.components.CountdownTimer
import com.sujood.app.ui.components.DailyQuoteCard
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.components.GreetingSection
import com.sujood.app.ui.components.PrayerStreakCard
import com.sujood.app.ui.components.PrayerTimeCard
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    userPreferences: UserPreferences = UserPreferences(LocalContext.current),
    repository: PrayerTimesRepository? = null
) {
    val context = LocalContext.current
    val database = (context.applicationContext as? com.sujood.app.SujoodApplication)?.database
    val repo = repository ?: remember {
        database?.let {
            PrayerTimesRepository(
                RetrofitClient.aladhanApiService,
                it.prayerLogDao()
            )
        }
    }

    val viewModel: HomeViewModel = remember(userPreferences, repo) {
        if (repo != null) {
            HomeViewModel(
                repository = repo,
                userPreferences = userPreferences,
                logPrayerCompletionUseCase = LogPrayerCompletionUseCase(repo),
                getNextPrayerUseCase = GetNextPrayerUseCase(),
                getPrayerStreakUseCase = GetPrayerStreakUseCase(repo)
            )
        } else {
            throw IllegalStateException("Repository not available")
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var visible by remember { mutableStateOf(false) }

    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // On every app open: try to refresh using saved location, otherwise use GPS
    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted) {
            viewModel.initializeAndRefresh(context)
        }
    }

    LaunchedEffect(locationPermission.status.isGranted) {
        if (!locationPermission.status.isGranted) {
            locationPermission.launchPermissionRequest()
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    AnimatedGradientBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            !locationPermission.status.isGranted -> {
                LocationPermissionCard(
                    onRequestPermission = { locationPermission.launchPermissionRequest() }
                )
            }
            uiState.isLoading || uiState.isLoadingLocation -> {
                LoadingContent()
            }
            uiState.showCityInput -> {
                CityInputContent(
                    error = uiState.error,
                    onSubmitCity = { city ->
                        viewModel.fetchPrayerTimesByCity(context, city)
                    },
                    onRetry = { viewModel.fetchPrayerTimes(context) }
                )
            }
            uiState.error != null && uiState.prayerTimes.isEmpty() -> {
                ErrorContent(
                    error = uiState.error!!,
                    onRetry = { viewModel.fetchPrayerTimes(context) },
                    onUseCity = { viewModel.showCityInput() },
                    context = context
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    item {
                        HeroSection(
                            userName = uiState.userName,
                            nextPrayerInfo = uiState.nextPrayerInfo,
                            prayerTimes = uiState.prayerTimes,
                            completedPrayers = uiState.completedPrayersToday.toSet(),
                            onCompleteClick = { viewModel.logPrayerCompletion(it) }
                        )
                    }

                    // ── Streak card FIRST ──
                    item {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(600, delayMillis = 150)) +
                                    slideInVertically(tween(600, delayMillis = 150)) { 30 }
                        ) {
                            PrayerStreakCard(
                                streakDays = uiState.streakDays,
                                completedPrayersToday = uiState.completedPrayersToday,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(800, delayMillis = 200)) +
                                    slideInVertically(tween(800, delayMillis = 200)) { 50 }
                        ) {
                            Text(
                                text = "Today's Prayers",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                            )
                        }
                    }

                    itemsIndexed(uiState.prayerTimes) { index, prayerTime ->
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(400, delayMillis = 250 + index * 80)) +
                                    slideInVertically(tween(400, delayMillis = 250 + index * 80)) { 30 }
                        ) {
                            PrayerTimeCard(
                                prayerTime = prayerTime,
                                isCompleted = uiState.completedPrayersToday.toSet().contains(prayerTime.prayer),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                onClick = {
                                    viewModel.logPrayerCompletion(prayerTime.prayer)
                                }
                            )
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(600, delayMillis = 700))
                        ) {
                            DailyQuoteCard(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    userName: String,
    nextPrayerInfo: GetNextPrayerUseCase.NextPrayerInfo?,
    prayerTimes: List<PrayerTime>,
    completedPrayers: Set<Prayer>,
    onCompleteClick: (Prayer) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
    ) {
        // Background gradient layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0F2A),
                            Color(0xFF1B1B3A),
                            Color(0xFF2E1A47)
                        )
                    )
                )
        )

        // Subtle radial glow spots
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(SoftPurple.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.BottomStart)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(WarmAmber.copy(alpha = 0.07f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GreetingSection(
                userName = userName,
                nextPrayer = nextPrayerInfo?.prayer
            )

            Spacer(modifier = Modifier.height(12.dp))

            val currentTimeProgress = getDayProgress(System.currentTimeMillis())

            SunArcWidget(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                currentTimeProgress = currentTimeProgress,
                prayerTimes = prayerTimes,
                completedPrayers = completedPrayers
            )

            Spacer(modifier = Modifier.height(12.dp))

            nextPrayerInfo?.let { nextInfo ->
                CountdownTimer(
                    nextPrayerName = nextInfo.prayer.displayName,
                    timeRemainingMillis = nextInfo.timeRemainingMillis,
                    modifier = Modifier.fillMaxWidth(),
                    isCompleted = completedPrayers.contains(nextInfo.prayer),
                    onCompleteClick = { onCompleteClick(nextInfo.prayer) }
                )
            }
        }
    }
}

@Composable
private fun SunArcWidget(
    modifier: Modifier = Modifier,
    currentTimeProgress: Float,
    prayerTimes: List<PrayerTime>,
    completedPrayers: Set<Prayer>
) {
    val animatedProgress by animateFloatAsState(
        targetValue = currentTimeProgress,
        animationSpec = tween(durationMillis = 1500, easing = LinearOutSlowInEasing),
        label = "sunProgress"
    )

    // Map 0–1 over the full 24h to 180°–360° (left to right across the top)
    // We display sunrise (6AM = 0.25) at 0° and sunset (6PM = 0.75) at 180°
    // So the arc maps [0.25 .. 0.75] in time to [180° .. 360°] sweep
    fun timeToArcAngle(progress: Float): Double {
        // clamp to [0, 1]
        val p = progress.coerceIn(0f, 1f)
        // 0.25 (6am) → 180°, 0.75 (6pm) → 360°
        return 180.0 + (p * 360.0)
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            // Arc center is BELOW the canvas — so we see only the top half
            val centerY = size.height * 1.05f
            val arcRadius = size.width * 0.42f

            // ── 1. Subtle background glow ring ──
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SoftPurple.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = arcRadius + 30f
                ),
                radius = arcRadius + 30f,
                center = Offset(centerX, centerY)
            )

            // ── 2. Dashed arc baseline ──
            val arcPath = Path().apply {
                addArc(
                    oval = androidx.compose.ui.geometry.Rect(
                        centerX - arcRadius, centerY - arcRadius,
                        centerX + arcRadius, centerY + arcRadius
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f
                )
            }
            drawPath(
                path = arcPath,
                color = Color.White.copy(alpha = 0.10f),
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                )
            )

            // ── 3. Prayer time dots on the arc ──
            prayerTimes.forEach { prayerTime ->
                val prog = getDayProgress(prayerTime.timestamp)
                if (prog in 0.18f..0.82f) {
                    // Map to arc angle: progress 0→1 maps to 180°→360°
                    val angleDeg = 180.0 + (prog * 180.0)
                    val rad = Math.toRadians(angleDeg)
                    val ptX = centerX + arcRadius * cos(rad).toFloat()
                    val ptY = centerY + arcRadius * sin(rad).toFloat()

                    val isCompleted = completedPrayers.contains(prayerTime.prayer)

                    // Glow halo
                    drawCircle(
                        color = if (isCompleted) WarmAmber.copy(alpha = 0.25f) else SoftPurple.copy(alpha = 0.15f),
                        radius = 10f,
                        center = Offset(ptX, ptY)
                    )
                    // Dot
                    drawCircle(
                        color = if (isCompleted) WarmAmber else Color.White.copy(alpha = 0.4f),
                        radius = 4.5f,
                        center = Offset(ptX, ptY)
                    )
                }
            }

            // ── 4. Sun indicator ──
            val sunAngleDeg = 180.0 + (animatedProgress * 180.0)
            val sunRad = Math.toRadians(sunAngleDeg)
            val sunX = centerX + arcRadius * cos(sunRad).toFloat()
            val sunY = centerY + arcRadius * sin(sunRad).toFloat()

            // Outer soft glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(WarmAmber.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(sunX, sunY),
                    radius = 55f
                ),
                radius = 55f,
                center = Offset(sunX, sunY)
            )
            // Mid glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(WarmAmber.copy(alpha = 0.6f), Color.Transparent),
                    center = Offset(sunX, sunY),
                    radius = 22f
                ),
                radius = 22f,
                center = Offset(sunX, sunY)
            )
            // Core
            drawCircle(color = WarmAmber, radius = 11f, center = Offset(sunX, sunY))
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 5f, center = Offset(sunX, sunY))
        }
    }
}

/**
 * Calculates the progress of a given timestamp within its 24-hour day (0.0 to 1.0).
 */
private fun getDayProgress(timestamp: Long): Float {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val hours = calendar.get(Calendar.HOUR_OF_DAY)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)
    val totalSecondsInDay = 24 * 60 * 60
    val currentSeconds = (hours * 3600) + (minutes * 60) + seconds
    return (currentSeconds.toFloat() / totalSecondsInDay).coerceIn(0f, 1f)
}

@Composable
private fun LocationPermissionCard(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FrostedGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = SoftPurple,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Location Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sujood needs your location to calculate accurate prayer times for your city.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = SoftPurple, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Getting your location...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun CityInputContent(
    error: String?,
    onSubmitCity: (String) -> Unit,
    onRetry: () -> Unit
) {
    var cityName by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val quickCities = listOf(
        "Dubai", "Abu Dhabi", "Sharjah", "London", "Toronto", "New York"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = WarmAmber,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Where are you praying?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Light,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Type your city or pick from the list below",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── City text field ──
            OutlinedTextField(
                value = cityName,
                onValueChange = { cityName = it },
                label = { Text("Search City") },
                placeholder = { Text("e.g. Dubai") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SoftPurple,
                    unfocusedBorderColor = GlassBorder,
                    focusedLabelColor = SoftPurple,
                    cursorColor = SoftPurple,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    unfocusedLabelColor = TextSecondary
                ),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        val trimmed = cityName.trim()
                        if (trimmed.isNotBlank()) onSubmitCity(trimmed)
                    }
                )
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Common Cities",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )

            // Quick city chips — two rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickCities.take(3).forEach { city ->
                    CityChip(
                        city = city,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            cityName = city
                            focusManager.clearFocus()
                            onSubmitCity(city)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickCities.drop(3).forEach { city ->
                    CityChip(
                        city = city,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            cityName = city
                            focusManager.clearFocus()
                            onSubmitCity(city)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    val trimmed = cityName.trim()
                    if (trimmed.isNotBlank()) onSubmitCity(trimmed)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = cityName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Confirm Location", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onRetry) {
                Text("Detect my location automatically", color = LavenderGlow)
            }
        }
    }
}

@Composable
private fun CityChip(
    city: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, SoftPurple.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = city,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onUseCity: () -> Unit,
    context: Context
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FrostedGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            cornerRadius = 24.dp,
            borderColor = WarmAmber.copy(alpha = 0.4f)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = WarmAmber,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Couldn't access your location",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Please make sure location permission is granted and GPS is on, or enter your city manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                if (error.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MidnightBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Settings")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedActionButton(
                        onClick = onUseCity,
                        modifier = Modifier.weight(1f)
                    ) { Text("Enter City", color = SoftPurple) }

                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun OutlinedActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SoftPurple, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MidnightBlue.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
