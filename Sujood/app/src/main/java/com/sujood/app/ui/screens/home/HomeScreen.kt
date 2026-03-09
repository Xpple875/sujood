package com.sujood.app.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.sujood.app.ui.components.DailyQuoteCard
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

private val PrimaryBlue    = Color(0xFF1132D4)
private val BackgroundDark = Color(0xFF101322)
private val SurfaceCard    = Color(0xFF1A1F35)
private val BorderSubtle   = Color(0x40FFFFFF)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    userPreferences: UserPreferences = UserPreferences(LocalContext.current),
    repository: PrayerTimesRepository? = null
) {
    val context  = LocalContext.current
    val database = (context.applicationContext as? com.sujood.app.SujoodApplication)?.database
    val repo     = repository ?: remember {
        database?.let { PrayerTimesRepository(RetrofitClient.aladhanApiService, it.prayerLogDao()) }
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
        } else throw IllegalStateException("Repository not available")
    }

    val uiState   by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var visible   by remember { mutableStateOf(false) }
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted) viewModel.initializeAndRefresh(context)
    }
    LaunchedEffect(locationPermission.status.isGranted) {
        if (!locationPermission.status.isGranted) locationPermission.launchPermissionRequest()
    }
    LaunchedEffect(Unit) { delay(100); visible = true }

    // ── Inline location picker ─────────────────────────────────────────────
    var showLocationDialog by remember { mutableStateOf(false) }
    if (showLocationDialog) {
        var locQuery    by remember { mutableStateOf("") }
        var locSelected by remember { mutableStateOf("") }
        val locFm = LocalFocusManager.current
        val locCities = listOf(
            "Dubai","Abu Dhabi","Sharjah","Ajman","Al Ain",
            "Riyadh","Jeddah","Mecca","Medina",
            "London","Manchester","Birmingham",
            "New York","Los Angeles","Chicago","Toronto",
            "Cairo","Alexandria","Istanbul",
            "Kuala Lumpur","Jakarta","Karachi","Lahore",
            "Dhaka","Mumbai","Delhi",
            "Doha","Kuwait City","Muscat","Manama","Amman",
            "Lagos","Nairobi","Singapore","Sydney"
        ).sorted()
        val locFiltered = remember(locQuery) {
            if (locQuery.length < 2) emptyList()
            else locCities.filter { it.contains(locQuery, ignoreCase = true) }.take(6)
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Change Location", color = Color.White) },
            text  = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value        = locQuery,
                        onValueChange = { locQuery = it; locSelected = "" },
                        label        = { Text("Search city") },
                        singleLine   = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White
                        )
                    )
                    if (locFiltered.isNotEmpty() && locSelected.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1F35))
                            .border(1.dp, Color.White.copy(alpha = 0.1f),
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        ) {
                            locFiltered.forEachIndexed { idx, city ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { locSelected = city; locQuery = city; locFm.clearFocus() }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, null,
                                        tint     = PrimaryBlue,
                                        modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(city, fontSize = 13.sp, color = Color.White)
                                }
                                if (idx < locFiltered.lastIndex)
                                    androidx.compose.material3.HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        val city = locSelected.ifEmpty { locQuery }.trim()
                        if (city.isNotBlank()) {
                            viewModel.fetchPrayerTimesByCity(context, city)
                            showLocationDialog = false
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue)
                ) { Text("Confirm") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showLocationDialog = false }
                ) { Text("Cancel") }
            },
            containerColor = Color(0xFF0D1020)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Box(modifier = Modifier.fillMaxSize().background(
            brush = Brush.radialGradient(
                colors = listOf(PrimaryBlue.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(0f, 0f), radius = 600f
            )
        ))
        Box(modifier = Modifier.fillMaxSize().background(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF9333EA).copy(alpha = 0.10f), Color.Transparent),
                center = Offset(Float.MAX_VALUE, Float.MAX_VALUE), radius = 800f
            )
        ))

        when {
            !locationPermission.status.isGranted ->
                LocationPermissionCard(onRequestPermission = { locationPermission.launchPermissionRequest() })
            uiState.isLoading || uiState.isLoadingLocation -> LoadingContent()
            uiState.showCityInput -> CityInputContent(
                error = uiState.error,
                onSubmitCity = { city -> viewModel.fetchPrayerTimesByCity(context, city) },
                onRetry = { viewModel.fetchPrayerTimes(context) }
            )
            uiState.error != null && uiState.prayerTimes.isEmpty() -> ErrorContent(
                error = uiState.error!!,
                onRetry = { viewModel.fetchPrayerTimes(context) },
                onUseCity = { viewModel.showCityInput() },
                context = context
            )
            else -> LazyColumn(
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
                        streakDays = uiState.streakDays,
                        onCompleteClick = { viewModel.logPrayerCompletion(it) }
                    )
                }
                item {
                    AnimatedVisibility(visible = visible, enter = fadeIn(tween(400, delayMillis = 100))) {
                        Text(
                            text = "DAILY PRAYERS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.45f),
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }
                }
                itemsIndexed(uiState.prayerTimes) { index, prayerTime ->
                    val isCompleted = uiState.completedPrayersToday.contains(prayerTime.prayer)
                    val np = uiState.nextPrayerInfo
                    val isCurrent = np != null && np.isCurrentPrayer && np.prayer == prayerTime.prayer
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(400, delayMillis = 150 + index * 70)) +
                                slideInVertically(tween(400, delayMillis = 150 + index * 70)) { 24 }
                    ) {
                        PrayerRow(
                            prayerTime = prayerTime,
                            isCompleted = isCompleted,
                            isCurrent = isCurrent,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            onClick = { viewModel.logPrayerCompletion(prayerTime.prayer) }
                        )
                    }
                }
                item {
                    AnimatedVisibility(visible = visible, enter = fadeIn(tween(600, delayMillis = 600))) {
                        DailyQuoteCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(
    userName: String,
    nextPrayerInfo: GetNextPrayerUseCase.NextPrayerInfo?,
    prayerTimes: List<PrayerTime>,
    completedPrayers: Set<Prayer>,
    streakDays: Int,
    onCompleteClick: (Prayer) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                spotColor = PrimaryBlue.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(brush = Brush.verticalGradient(colors = listOf(PrimaryBlue.copy(alpha = 0.18f), BackgroundDark)))
            .border(width = 1.dp,
                brush = Brush.verticalGradient(colors = listOf(PrimaryBlue.copy(alpha = 0.3f), Color.Transparent)),
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(42.dp).clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.2f))
                        .border(1.dp, PrimaryBlue.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null,
                            tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                    }
                    Row {
                        Text("Salam, ", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = userName.ifEmpty { "Friend" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
                Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { showLocationDialog = true },
                    contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.EditLocation,
                        contentDescription = "Change location",
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            SunArcWidget(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                prayerTimes = prayerTimes,
                completedPrayers = completedPrayers,
                nextPrayerInfo = nextPrayerInfo
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("FAJR","DHUHR","ASR","MAGHRIB","ISHA").forEach { name ->
                    Text(text = name, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.35f))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            StreakAndDotsCard(
                streakDays = streakDays,
                prayerTimes = prayerTimes,
                completedPrayers = completedPrayers
            )
        }
    }
}

// ── Sun arc ───────────────────────────────────────────────────────────────────

@Composable
private fun SunArcWidget(
    modifier: Modifier = Modifier,
    prayerTimes: List<PrayerTime>,
    completedPrayers: Set<Prayer>,
    nextPrayerInfo: GetNextPrayerUseCase.NextPrayerInfo?
) {
    val currentProgress = getDayProgress(System.currentTimeMillis())
    val animatedProgress by animateFloatAsState(
        targetValue = currentProgress,
        animationSpec = tween(1500, easing = LinearOutSlowInEasing),
        label = "sun"
    )
    val pillText = when {
        nextPrayerInfo != null && nextPrayerInfo.isCurrentPrayer -> "Time for ${nextPrayerInfo.prayer.displayName}"
        nextPrayerInfo != null -> "Next: ${nextPrayerInfo.prayer.displayName}"
        else -> ""
    }
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height + size.height * 0.05f
            val r  = size.width * 0.44f
            val arcPath = Path().apply {
                addArc(oval = Rect(cx - r, cy - r, cx + r, cy + r),
                    startAngleDegrees = 180f, sweepAngleDegrees = 180f)
            }
            drawPath(arcPath, color = Color.White.copy(alpha = 0.18f),
                style = Stroke(width = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
            val sunAngle = Math.toRadians(180.0 + animatedProgress * 180.0)
            val sunX = cx + r * cos(sunAngle).toFloat()
            val sunY = cy + r * sin(sunAngle).toFloat()
            drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFFBBF24).copy(alpha = 0.35f), Color.Transparent),
                center = Offset(sunX, sunY), radius = 40f), radius = 40f, center = Offset(sunX, sunY))
            drawCircle(color = Color(0xFFFBBF24), radius = 9f, center = Offset(sunX, sunY))
        }
        if (pillText.isNotEmpty()) {
            Row(modifier = Modifier.clip(CircleShape)
                .background(BackgroundDark.copy(alpha = 0.75f))
                .border(1.dp, PrimaryBlue.copy(alpha = 0.3f), CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(20.dp).clip(CircleShape)
                    .border(1.5.dp, PrimaryBlue.copy(alpha = 0.6f), CircleShape))
                Text(text = pillText, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ── Streak & dots ─────────────────────────────────────────────────────────────

@Composable
private fun StreakAndDotsCard(streakDays: Int, prayerTimes: List<PrayerTime>, completedPrayers: Set<Prayer>) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(brush = Brush.horizontalGradient(listOf(PrimaryBlue.copy(alpha = 0.22f), Color(0xFF9333EA).copy(alpha = 0.10f))))
        .border(1.dp, PrimaryBlue.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
        .padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("TODAY'S PRAYERS", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp, color = PrimaryBlue)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$streakDays", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Days", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            val prayers = prayerTimes.map { it.prayer }
            val top = prayers.take(3); val bottom = prayers.drop(3)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrayerDotRow(prayers = top, completedPrayers = completedPrayers)
                Row(modifier = Modifier.padding(end = 20.dp)) {
                    PrayerDotRow(prayers = bottom, completedPrayers = completedPrayers)
                }
            }
        }
    }
}

@Composable
private fun PrayerDotRow(prayers: List<Prayer>, completedPrayers: Set<Prayer>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        prayers.forEach { prayer ->
            val done = completedPrayers.contains(prayer)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape)
                    .background(if (done) PrimaryBlue else Color.White.copy(alpha = 0.07f))
                    .border(2.dp, BackgroundDark.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text(prayer.displayName.first().toString(), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (done) Color.White else Color.White.copy(alpha = 0.3f))
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(prayer.displayName.uppercase(), fontSize = 7.sp, fontWeight = FontWeight.Bold,
                    color = if (done) PrimaryBlue else Color.White.copy(alpha = 0.25f),
                    letterSpacing = 0.5.sp)
            }
        }
    }
}

// ── Prayer row ────────────────────────────────────────────────────────────────

@Composable
private fun PrayerRow(
    prayerTime: PrayerTime,
    isCompleted: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor     = if (isCurrent) PrimaryBlue.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f)
    val borderColor = if (isCurrent) PrimaryBlue.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.07f)
    val borderWidth = if (isCurrent) 2.dp else 1.dp

    val (iconBg, iconTint) = when (prayerTime.prayer) {
        Prayer.FAJR    -> Color(0xFF172554).copy(alpha = 0.7f) to Color(0xFF60A5FA)
        Prayer.DHUHR   -> PrimaryBlue.copy(alpha = 0.9f)      to Color.White
        Prayer.ASR     -> Color(0xFF431407).copy(alpha = 0.7f) to Color(0xFFFB923C)
        Prayer.MAGHRIB -> Color(0xFF4C0519).copy(alpha = 0.7f) to Color(0xFFF87171)
        Prayer.ISHA    -> Color(0xFF1E1B4B).copy(alpha = 0.7f) to Color(0xFFA5B4FC)
    }

    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),
                contentAlignment = Alignment.Center) {
                // Material Icons Extended — guaranteed available in Compose 1.6 / BOM 2024.02
                val prayerIcon = when (prayerTime.prayer) {
                    Prayer.FAJR    -> Icons.Filled.Flare
                    Prayer.DHUHR   -> Icons.Filled.WbSunny
                    Prayer.ASR     -> Icons.Filled.WbSunny
                    Prayer.MAGHRIB -> Icons.Filled.Brightness3
                    Prayer.ISHA    -> Icons.Filled.NightlightRound
                }
                Icon(imageVector = prayerIcon, contentDescription = prayerTime.prayer.displayName,
                    tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(text = prayerTime.prayer.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) PrimaryBlue else Color.White)
                Text(text = prayerTime.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f))
            }
        }

        // Right indicator — clean checkmark (no LocationOn icon)
        when {
            isCompleted -> Canvas(modifier = Modifier.size(26.dp)) {
                // Green filled circle with white check
                drawCircle(color = Color(0xFF22C55E), radius = size.minDimension / 2f)
                val s = size.minDimension
                val path = Path().apply {
                    moveTo(s * 0.28f, s * 0.52f)
                    lineTo(s * 0.44f, s * 0.68f)
                    lineTo(s * 0.72f, s * 0.36f)
                }
                drawPath(path, Color.White, style = Stroke(width = 2.5f,
                    cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            isCurrent   -> Text("CURRENT", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = PrimaryBlue, letterSpacing = 1.sp)
            else        -> Box(modifier = Modifier.size(22.dp).clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape))
        }
    }
}


private fun getDayProgress(timestamp: Long): Float {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val h = calendar.get(Calendar.HOUR_OF_DAY)
    val m = calendar.get(Calendar.MINUTE)
    val s = calendar.get(Calendar.SECOND)
    return ((h * 3600 + m * 60 + s).toFloat() / (24 * 60 * 60)).coerceIn(0f, 1f)
}

// ── Support screens ───────────────────────────────────────────────────────────

@Composable
private fun LocationPermissionCard(onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
        FrostedGlassCard(modifier = Modifier.fillMaxWidth().padding(24.dp), cornerRadius = 24.dp) {
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null,
                    tint = PrimaryBlue, modifier = Modifier.size(52.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Location Permission Required", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sujood needs your location to calculate accurate prayer times.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(16.dp)) { Text("Grant Permission", fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Getting your location...", style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun CityInputContent(error: String?, onSubmitCity: (String) -> Unit, onRetry: () -> Unit) {
    val allCities = remember {
        listOf(
            "Dubai, UAE","Abu Dhabi, UAE","Sharjah, UAE","Ajman, UAE","Al Ain, UAE",
            "Riyadh, Saudi Arabia","Jeddah, Saudi Arabia","Mecca, Saudi Arabia","Medina, Saudi Arabia",
            "London, UK","Manchester, UK","Birmingham, UK","Glasgow, UK",
            "New York, USA","Los Angeles, USA","Chicago, USA","Houston, USA",
            "Toronto, Canada","Montreal, Canada","Vancouver, Canada",
            "Cairo, Egypt","Alexandria, Egypt",
            "Istanbul, Turkey","Ankara, Turkey",
            "Kuala Lumpur, Malaysia","Jakarta, Indonesia",
            "Karachi, Pakistan","Lahore, Pakistan","Islamabad, Pakistan",
            "Dhaka, Bangladesh","Mumbai, India","Delhi, India",
            "Paris, France","Berlin, Germany","Amsterdam, Netherlands",
            "Lagos, Nigeria","Nairobi, Kenya","Casablanca, Morocco",
            "Doha, Qatar","Kuwait City, Kuwait","Manama, Bahrain","Muscat, Oman",
            "Amman, Jordan","Beirut, Lebanon","Baghdad, Iraq","Tehran, Iran",
            "Sydney, Australia","Singapore, Singapore","Tokyo, Japan",
            "Cape Town, South Africa","Johannesburg, South Africa"
        ).sortedBy { it }
    }
    var query        by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val filtered = remember(query) {
        if (query.length < 2) emptyList()
        else allCities.filter { it.contains(query, ignoreCase = true) }.take(6)
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Default.MyLocation, contentDescription = null,
                tint = Color(0xFFFBBF24), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text("Where are you praying?", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Light, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Start typing your city to search", style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(28.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it; selectedCity = "" },
                label = { Text("Search City") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue, unfocusedBorderColor = BorderSubtle,
                    focusedLabelColor = PrimaryBlue, cursorColor = PrimaryBlue,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            if (filtered.isNotEmpty() && selectedCity.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))) {
                    filtered.forEachIndexed { idx, city ->
                        Row(modifier = Modifier.fillMaxWidth()
                            .clickable { selectedCity = city; query = city; focusManager.clearFocus() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null,
                                tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(city, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                        if (idx < filtered.lastIndex)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                    }
                }
            }
            if (error != null && selectedCity.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = {
                val city = selectedCity.ifEmpty { query }.trim()
                if (city.isNotBlank()) { focusManager.clearFocus(); onSubmitCity(city) }
            },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = selectedCity.isNotEmpty() || query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(if (selectedCity.isNotEmpty()) "Use $selectedCity" else "Search",
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text("Detect my location automatically", color = PrimaryBlue)
            }
        }
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit, onUseCity: () -> Unit, context: Context) {
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
        FrostedGlassCard(modifier = Modifier.fillMaxWidth().padding(24.dp),
            cornerRadius = 24.dp, borderColor = Color(0xFFFBBF24).copy(alpha = 0.4f)) {
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null,
                    tint = Color(0xFFFBBF24), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Couldn't access your location", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Please enable location permission and GPS, or enter your city manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onUseCity, modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)) { Text("Enter City", color = PrimaryBlue) }
                    Button(onClick = onRetry, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)) { Text("Retry") }
                }
            }
        }
    }
}
