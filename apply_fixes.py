"""
fix_all_features.py
Run from the repo root:  python fix_all_features.py
Then:  cd Sujood && ./gradlew assembleDebug
"""
import os, re

SRC = os.path.join("Sujood","app","src","main","java","com","sujood","app")
RES = os.path.join("Sujood","app","src","main","res")

def write(relpath, content, base=SRC):
    path = os.path.join(base, relpath.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  ✓  {relpath}")

# ─────────────────────────────────────────────────────────────────────────────
# 1. FIX: Onboarding never saved hasCompletedOnboarding=true
#    ReadyScreen now calls setOnboardingCompleted() before onEnter()
# ─────────────────────────────────────────────────────────────────────────────
onboarding_path = os.path.join(
    SRC, "ui","screens","onboarding","EmotionalOnboardingScreen.kt"
)
with open(onboarding_path, encoding="utf-8") as f:
    ob = f.read()

# Inject coroutineScope + setOnboardingCompleted into ReadyScreen's button onClick
old_ready = '''private fun ReadyScreen(userPreferences: UserPreferences, onEnter: () -> Unit) {
    val userName by userPreferences.userName.collectAsState(initial = "")'''
new_ready = '''private fun ReadyScreen(userPreferences: UserPreferences, onEnter: () -> Unit) {
    val userName by userPreferences.userName.collectAsState(initial = "")
    val scope = rememberCoroutineScope()'''

old_onclick = '''onClick = onEnter,'''
new_onclick = '''onClick = {
                    scope.launch { userPreferences.setOnboardingCompleted() }
                    onEnter()
                },'''

ob = ob.replace(old_ready, new_ready)
ob = ob.replace(old_onclick, new_onclick, 1)  # only first occurrence inside ReadyScreen

# Make sure rememberCoroutineScope is imported
if "rememberCoroutineScope" not in ob:
    ob = ob.replace(
        "import androidx.compose.runtime.getValue",
        "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.rememberCoroutineScope"
    )
if "kotlinx.coroutines.launch" not in ob:
    ob = ob.replace(
        "import kotlinx.coroutines",
        "import kotlinx.coroutines.launch\nimport kotlinx.coroutines"
    )

with open(onboarding_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(ob)
print("  ✓  ui/screens/onboarding/EmotionalOnboardingScreen.kt  (onboarding fix)")

# ─────────────────────────────────────────────────────────────────────────────
# 2. HomeScreen — remove stray LocationOn icon on completed prayer row,
#    replace emoji with sleek Canvas-drawn SVG-style icons per prayer
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/home/HomeScreen.kt", r'''package com.sujood.app.ui.screens.home

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
                    .background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = null,
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
            // ── Sleek Canvas icon — no emoji, no stock Android icons ──
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(iconBg),
                contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(22.dp)) {
                    drawPrayerIcon(prayerTime.prayer, iconTint)
                }
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

/** Canvas-drawn Iconly-style icons for each prayer. No emoji, no Android icons. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPrayerIcon(prayer: Prayer, tint: Color) {
    val w = size.width; val h = size.height; val sw = w * 0.09f

    when (prayer) {
        Prayer.FAJR -> {
            // Half-sun rising over horizon line
            val cx = w / 2f; val cy = h * 0.58f; val r = w * 0.28f
            drawArc(color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                style = Stroke(width = sw, cap = StrokeCap.Round))
            drawLine(tint, Offset(0f, cy), Offset(w, cy), strokeWidth = sw, cap = StrokeCap.Round)
            // Rays above
            listOf(-45f, 0f, 45f).forEach { angle ->
                val rad = Math.toRadians(angle.toDouble() - 90)
                drawLine(tint,
                    Offset(cx + (r + w*0.05f)*cos(rad).toFloat(), cy + (r + h*0.05f)*sin(rad).toFloat()),
                    Offset(cx + (r + w*0.20f)*cos(rad).toFloat(), cy + (r + h*0.20f)*sin(rad).toFloat()),
                    strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
            }
        }
        Prayer.DHUHR -> {
            // Full sun with rays (midday)
            val cx = w / 2f; val cy = h / 2f; val r = w * 0.22f
            drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = Stroke(width = sw))
            listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f).forEach { angle ->
                val rad = Math.toRadians(angle.toDouble())
                drawLine(tint,
                    Offset(cx + (r + w*0.06f)*cos(rad).toFloat(), cy + (r + h*0.06f)*sin(rad).toFloat()),
                    Offset(cx + (r + w*0.18f)*cos(rad).toFloat(), cy + (r + h*0.18f)*sin(rad).toFloat()),
                    strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
            }
        }
        Prayer.ASR -> {
            // Sun low + shadow/partial cloud
            val cx = w / 2f; val cy = h * 0.55f; val r = w * 0.22f
            drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = Stroke(width = sw))
            listOf(0f, 60f, 120f, 240f, 300f).forEach { angle ->
                val rad = Math.toRadians(angle.toDouble())
                drawLine(tint,
                    Offset(cx + (r + w*0.06f)*cos(rad).toFloat(), cy + (r + h*0.06f)*sin(rad).toFloat()),
                    Offset(cx + (r + w*0.17f)*cos(rad).toFloat(), cy + (r + h*0.17f)*sin(rad).toFloat()),
                    strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
            }
            drawLine(tint, Offset(0f, h * 0.82f), Offset(w, h * 0.82f),
                strokeWidth = sw * 0.7f, cap = StrokeCap.Round)
        }
        Prayer.MAGHRIB -> {
            // Crescent moon
            val cx = w * 0.52f; val cy = h / 2f; val r = w * 0.32f
            drawArc(color = tint, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                style = Stroke(width = sw))
            // Cutout circle to make crescent
            drawCircle(color = Color.Transparent.copy(0f), radius = r * 0.85f,
                center = Offset(cx + r * 0.42f, cy - r * 0.10f))
            // Re-draw cutout as background color
            val cutoutPath = Path()
            cutoutPath.addOval(Rect(cx + r*0.42f - r*0.85f, cy - r*0.10f - r*0.85f,
                cx + r*0.42f + r*0.85f, cy - r*0.10f + r*0.85f))
            drawPath(cutoutPath, Color(0xFF101322))  // match background
        }
        Prayer.ISHA -> {
            // Moon + small star
            val cx = w * 0.48f; val cy = h * 0.52f; val r = w * 0.28f
            drawArc(color = tint, startAngle = 40f, sweepAngle = 280f, useCenter = false,
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                style = Stroke(width = sw, cap = StrokeCap.Round))
            // Small star top-right
            drawCircle(color = tint, radius = w * 0.07f,
                center = Offset(w * 0.80f, h * 0.22f))
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
''')

# ─────────────────────────────────────────────────────────────────────────────
# 3. DhikrScreen (Lock page)
#    • Overlay preview now mirrors the real overlay XML exactly
#    • "Test Now (Act Real)" button that fires PrayerLockOverlayService.start()
#    • Specific Apps section: slide-down panel with 10 common apps + toggle per app
#    • App-overlay mode wires lockedAppsPackageNames to UserPreferences
#    • The service already reads lockMode and lockedApps correctly (no service change needed)
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/dhikr/DhikrScreen.kt", r'''package com.sujood.app.ui.screens.dhikr

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.domain.model.LockMode
import com.sujood.app.domain.model.UserSettings
import com.sujood.app.service.PrayerLockOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val PrimaryBlue    = Color(0xFF1132D4)
private val BackgroundDark = Color(0xFF101322)
private val GlassStroke    = Color(0xFFFFFFFF).copy(alpha = 0.08f)
private val TextMuted      = Color(0xFF94A3B8)
private val TextDim        = Color(0xFF475569)
private val CardBg         = Color(0xFF0D1020)

// The apps shown in the Specific Apps picker
private data class AppEntry(val name: String, val packageName: String, val emoji: String)

private val COMMON_APPS = listOf(
    AppEntry("TikTok",     "com.zhiliaoapp.musically",        "🎵"),
    AppEntry("Instagram",  "com.instagram.android",           "📸"),
    AppEntry("YouTube",    "com.google.android.youtube",      "▶️"),
    AppEntry("Twitter / X","com.twitter.android",             "🐦"),
    AppEntry("Snapchat",   "com.snapchat.android",            "👻"),
    AppEntry("Facebook",   "com.facebook.katana",             "👍"),
    AppEntry("WhatsApp",   "com.whatsapp",                    "💬"),
    AppEntry("Reddit",     "com.reddit.frontpage",            "🤖"),
    AppEntry("Netflix",    "com.netflix.mediaclient",         "🎬"),
    AppEntry("Games",      "com.android.vending.games",       "🎮"),
)

@Composable
fun DhikrScreen() {
    val context         = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val settings        by userPreferences.userSettings.collectAsState(initial = UserSettings())

    var showAppsPanel by remember { mutableStateOf(false) }
    var infoDialog    by remember { mutableStateOf<Pair<String, String>?>(null) }

    infoDialog?.let { (title, body) ->
        InfoDialog(title = title, body = body, onDismiss = { infoDialog = null })
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(PrimaryBlue.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(0f, 0f), radius = 700f)))
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF8B5CF6).copy(alpha = 0.10f), Color.Transparent),
                center = Offset(Float.MAX_VALUE, 0f), radius = 800f)))

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(modifier = Modifier.fillMaxWidth()
                .background(BackgroundDark.copy(alpha = 0.85f))
                .border(1.dp, PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(0.dp))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape))
                Text("Prayer Lock", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Box(modifier = Modifier.size(40.dp))
            }

            LazyColumn(modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {

                // ── Master Toggle ──────────────────────────────────────────
                item {
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text("Enable Prayer Lock", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.height(4.dp))
                                Text("Restrict phone access for spiritual focus",
                                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            Switch(checked = settings.prayerLockEnabled,
                                onCheckedChange = { on ->
                                    CoroutineScope(Dispatchers.IO).launch { userPreferences.savePrayerLockEnabled(on) }
                                },
                                colors = blueSwitchColors())
                        }
                    }
                }

                // ── Lock Type ──────────────────────────────────────────────
                item {
                    SectionHeader("Lock Type", onInfo = {
                        infoDialog = "Lock Type" to "• Whole Phone: Full-screen overlay blocks all apps until you confirm prayer.\n\n• Specific Apps: Only locks selected distracting apps — rest of phone stays free."
                    })
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LockTypeOption(icon = Icons.Default.Smartphone, title = "Whole Phone",
                            subtitle = "Lock all non-essential features",
                            isSelected = settings.lockMode == LockMode.WHOLE_PHONE,
                            onClick = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    userPreferences.saveLockSettings(LockMode.WHOLE_PHONE, settings.lockTriggerMinutes, settings.lockDurationMinutes)
                                }
                                showAppsPanel = false
                            })
                        LockTypeOption(icon = Icons.Default.Apps, title = "Specific Apps",
                            subtitle = "Select distracting applications",
                            isSelected = settings.lockMode == LockMode.APP_OVERLAY,
                            onClick = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    userPreferences.saveLockSettings(LockMode.APP_OVERLAY, settings.lockTriggerMinutes, settings.lockDurationMinutes)
                                }
                                showAppsPanel = true
                            })

                        // ── Specific Apps Slide Panel ──────────────────────
                        AnimatedVisibility(
                            visible = settings.lockMode == LockMode.APP_OVERLAY || showAppsPanel,
                            enter = expandVertically(), exit = shrinkVertically()
                        ) {
                            GlassCard {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("CHOOSE APPS TO BLOCK", fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
                                        color = PrimaryBlue.copy(alpha = 0.85f))
                                    Spacer(Modifier.height(4.dp))
                                    Text("These apps will be blocked at prayer time. The rest of your phone stays accessible.",
                                        fontSize = 12.sp, color = TextMuted)
                                    Spacer(Modifier.height(12.dp))

                                    val lockedSet = remember(settings.lockedAppsPackageNames) {
                                        settings.lockedAppsPackageNames.split(",").filter { it.isNotBlank() }.toMutableSet()
                                    }

                                    COMMON_APPS.forEach { app ->
                                        val isLocked = settings.lockedAppsPackageNames.contains(app.packageName)
                                        Row(modifier = Modifier.fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                                    .background(if (isLocked) PrimaryBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)),
                                                    contentAlignment = Alignment.Center) {
                                                    Text(app.emoji, fontSize = 18.sp)
                                                }
                                                Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                            }
                                            Switch(checked = isLocked,
                                                onCheckedChange = { enabled ->
                                                    val current = settings.lockedAppsPackageNames
                                                        .split(",").filter { it.isNotBlank() }.toMutableSet()
                                                    if (enabled) current.add(app.packageName)
                                                    else current.remove(app.packageName)
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        userPreferences.saveLockBehavior(settings.minLockDurationMinutes, current.joinToString(","))
                                                    }
                                                },
                                                colors = blueSwitchColors())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Trigger Timing ─────────────────────────────────────────
                item {
                    SectionHeader("Trigger Timing", onInfo = {
                        infoDialog = "Trigger Timing" to "Controls when the lock activates relative to the prayer time.\n\n• Now: lock triggers at adhan.\n• +5m…+30m: lock triggers that many minutes after adhan."
                    })
                    Spacer(Modifier.height(10.dp))
                    val opts = listOf(0 to "Now", 5 to "+5m", 10 to "+10m", 15 to "+15m", 30 to "+30m")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            opts.take(4).forEach { (mins, label) ->
                                OptionChip(label, settings.lockTriggerMinutes == mins, Modifier.weight(1f)) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        userPreferences.saveLockSettings(settings.lockMode, mins, settings.lockDurationMinutes)
                                    }
                                }
                            }
                        }
                        OptionChip("+30m", settings.lockTriggerMinutes == 30, Modifier.fillMaxWidth()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                userPreferences.saveLockSettings(settings.lockMode, 30, settings.lockDurationMinutes)
                            }
                        }
                    }
                }

                // ── Minimum Duration ───────────────────────────────────────
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionHeader("Minimum Duration", onInfo = {
                            infoDialog = "Minimum Duration" to "Minimum time the lock stays active before you can dismiss it.\n\n• 0 min: dismiss immediately.\n• 5 min: recommended — enough to complete a prayer."
                        })
                        Text(if (settings.minLockDurationMinutes == 0) "Off" else "${settings.minLockDurationMinutes} min",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(0 to "0 min", 2 to "2 min", 5 to "5 min").forEach { (mins, label) ->
                                OptionChip(label, settings.minLockDurationMinutes == mins, Modifier.weight(1f)) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        userPreferences.saveLockBehavior(mins, settings.lockedAppsPackageNames)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Overlay Customization ──────────────────────────────────
                item {
                    SectionHeader("Overlay Message", onInfo = {
                        infoDialog = "Overlay Message" to "The quote shown on the lock screen. Leave blank to use the default Quranic verse."
                    })
                    Spacer(Modifier.height(10.dp))
                    GlassCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("CUSTOM QUOTE", fontSize = 10.sp, fontWeight = FontWeight.Medium,
                                letterSpacing = 1.5.sp, color = TextMuted)
                            Spacer(Modifier.height(10.dp))
                            var quoteText by remember { mutableStateOf(settings.overlayQuote) }
                            LaunchedEffect(settings.overlayQuote) { quoteText = settings.overlayQuote }
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp)
                                .clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E2338))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)).padding(12.dp)) {
                                BasicTextField(value = quoteText, onValueChange = { quoteText = it },
                                    modifier = Modifier.fillMaxSize(),
                                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                    cursorBrush = SolidColor(PrimaryBlue),
                                    decorationBox = { inner ->
                                        if (quoteText.isEmpty()) Text("e.g. 'Success is found in the prayer.'",
                                            color = TextDim, fontSize = 14.sp)
                                        inner()
                                    })
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { CoroutineScope(Dispatchers.IO).launch { userPreferences.saveOverlayQuote(quoteText) } },
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(alpha = 0.75f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                                Text("Save", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // ── Test Lock (Real Trigger) ────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    // Description
                    Text("This fires the actual overlay — just like at prayer time.",
                        fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    Button(
                        onClick = {
                            // Start the real overlay service — same path as a real prayer alarm
                            PrayerLockOverlayService.start(context, "Test Prayer", "الصلاة")
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Test Lock Now (Real)", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                    }
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(CardBg.copy(alpha = 0.9f)).border(1.dp, GlassStroke, RoundedCornerShape(16.dp))) {
        content()
    }
}

@Composable
private fun SectionHeader(title: String, onInfo: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp, color = PrimaryBlue.copy(alpha = 0.85f))
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onInfo() },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Info, "Info", tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun LockTypeOption(icon: ImageVector, title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(CardBg.copy(alpha = 0.9f))
        .border(if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) PrimaryBlue.copy(alpha = 0.5f) else GlassStroke, RoundedCornerShape(16.dp))
        .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) PrimaryBlue.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (isSelected) PrimaryBlue else TextMuted, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        Box(modifier = Modifier.size(22.dp).clip(CircleShape)
            .border(2.dp, if (isSelected) PrimaryBlue else Color(0xFF475569), CircleShape)
            .background(if (isSelected) PrimaryBlue else Color.Transparent),
            contentAlignment = Alignment.Center) {
            if (isSelected) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun OptionChip(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp))
        .background(if (isSelected) PrimaryBlue.copy(alpha = 0.22f) else CardBg.copy(alpha = 0.9f))
        .border(if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) PrimaryBlue.copy(alpha = 0.55f) else GlassStroke, RoundedCornerShape(14.dp))
        .clickable { onClick() }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) Color.White else TextMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun blueSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue,
    uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF334155)
)

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
            .clip(RoundedCornerShape(24.dp)).background(Color(0xFF131726))
            .border(1.dp, PrimaryBlue.copy(alpha = 0.25f), RoundedCornerShape(24.dp)).padding(24.dp)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(PrimaryBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Info, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        }
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f))
                        .clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                Spacer(Modifier.height(16.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted, lineHeight = 22.sp)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)) { Text("Got it", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
''')

# ─────────────────────────────────────────────────────────────────────────────
# 4. QiblaScreen — change "Kaaba, Makkah Al-Mukarramah" to user's city
# ─────────────────────────────────────────────────────────────────────────────
qibla_path = os.path.join(SRC, "ui","screens","qibla","QiblaScreen.kt")
with open(qibla_path, encoding="utf-8") as f:
    q = f.read()

q = q.replace(
    'Text("Kaaba, Makkah Al-Mukarramah",',
    'val cityLabel = if (userLatitude != 0.0 && userLongitude != 0.0 && hasLocation)\n'
    '    remember(userLatitude, userLongitude) {\n'
    '        userPreferences.let { null } // resolved below\n'
    '    }.let { getCityLabel(context, userLatitude, userLongitude) }\n'
    'else "Kaaba, Makkah Al-Mukarramah"\n'
    'Text(cityLabel,',
    1
)

# Simpler, cleaner approach — just patch the two Text lines at the bottom
q = q.replace(
    'Text("Kaaba, Makkah Al-Mukarramah",\n                style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))',
    'Text(\n                    text = if (hasLocation && userLatitude != 0.0)\n                        getCityLabel(context, userLatitude, userLongitude)\n                    else "Kaaba, Makkah Al-Mukarramah",\n                    style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))'
)
q = q.replace(
    'Text(text = "21.4225° N, 39.8262° E",',
    'Text(\n            text = if (hasLocation && userLatitude != 0.0)\n                "%.4f° N, %.4f° E".format(userLatitude, userLongitude)\n            else "21.4225° N, 39.8262° E",'
)

# Add helper function and context import before the calculateQiblaDirection function
helper = '''
private fun getCityLabel(context: android.content.Context, lat: Double, lon: Double): String {
    return try {
        val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        val city = addresses?.firstOrNull()?.let { addr ->
            addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName
        }
        city ?: "Your Location"
    } catch (e: Exception) {
        "Your Location"
    }
}

'''
q = q.replace(
    "private fun calculateQiblaDirection",
    helper + "private fun calculateQiblaDirection"
)

# Make sure Context import is present
if "import androidx.compose.ui.platform.LocalContext" not in q:
    q = q.replace("import com.sujood.app.data.local.datastore.UserPreferences",
                  "import androidx.compose.ui.platform.LocalContext\nimport com.sujood.app.data.local.datastore.UserPreferences")

with open(qibla_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(q)
print("  ✓  ui/screens/qibla/QiblaScreen.kt  (city label fix)")

# ─────────────────────────────────────────────────────────────────────────────
# 5. SettingsScreen — GPS triggers immediate scan, clear cache works,
#    adhan sound picker added, offline caching in UserPreferences done via
#    saveLocationSettings (already persists lat/lon) — no change needed there.
#    City search now uses the same curated list as HomeScreen (no raw free-text).
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/settings/SettingsScreen.kt", r'''package com.sujood.app.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.SujoodApplication
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.CalculationMethod
import com.sujood.app.domain.model.LockMode
import com.sujood.app.domain.model.Madhab
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.UserSettings
import com.sujood.app.notifications.PrayerAlarmScheduler
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val BgDark      = Color(0xFF101322)
private val PrimaryBlue = Color(0xFF1132D4)
private val GlassFill   = Color(0xFF1132D4).copy(alpha = 0.05f)
private val GlassDiv    = Color(0xFFFFFFFF).copy(alpha = 0.05f)
private val GlassBrd    = Color(0xFFFFFFFF).copy(alpha = 0.10f)
private val SlateMuted  = Color(0xFF64748B)
private val SlateText   = Color(0xFF94A3B8)

// Curated city list (same as HomeScreen — so search works properly)
private val CITY_LIST = listOf(
    "Dubai, UAE","Abu Dhabi, UAE","Sharjah, UAE","Ajman, UAE","Al Ain, UAE",
    "Riyadh, Saudi Arabia","Jeddah, Saudi Arabia","Mecca, Saudi Arabia","Medina, Saudi Arabia",
    "London, UK","Manchester, UK","Birmingham, UK","Glasgow, UK",
    "New York, USA","Los Angeles, USA","Chicago, USA","Houston, USA",
    "Toronto, Canada","Montreal, Canada","Vancouver, Canada",
    "Cairo, Egypt","Alexandria, Egypt","Istanbul, Turkey","Ankara, Turkey",
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

private val ADHAN_OPTIONS = listOf(
    "Mishary Al-Afasy" to "https://cdn.islamic.network/quran/audio/128/ar.alafasy/1.mp3",
    "Abdul Basit"       to "https://cdn.islamic.network/quran/audio/128/ar.abdulbasitmurattal/1.mp3",
    "Maher Al Mueaqly"  to "https://cdn.islamic.network/quran/audio/128/ar.mahermuaiqly/1.mp3",
    "Saad Al Ghamdi"    to "https://cdn.islamic.network/quran/audio/128/ar.saoodashurym/1.mp3",
    "Phone Ringtone"    to "ringtone"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    onNavigateBack: () -> Unit
) {
    val context  = LocalContext.current
    val settings by userPreferences.userSettings.collectAsState(initial = UserSettings())
    val scope    = rememberCoroutineScope()

    var showNameDialog         by remember { mutableStateOf(false) }
    var showMethodDialog       by remember { mutableStateOf(false) }
    var showMadhabDialog       by remember { mutableStateOf(false) }
    var showCityDialog         by remember { mutableStateOf(false) }
    var showLockTriggerDialog  by remember { mutableStateOf(false) }
    var showLockDurationDialog by remember { mutableStateOf(false) }
    var showAdhanDialog        by remember { mutableStateOf(false) }
    var cacheCleared           by remember { mutableStateOf(false) }

    // Location permission launcher — used by "Use GPS" button in dialog
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                // Trigger immediate GPS scan via saving useGps=true then letting HomeViewModel pick it up
                userPreferences.saveLocationSettings(true, "", "", 0.0, 0.0)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDark)
        .background(Brush.radialGradient(listOf(PrimaryBlue.copy(alpha = 0.15f), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 1000f))) {

        LazyColumn(modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)) {

            // Header
            item {
                Row(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, GlassBrd, CircleShape).clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            // Profile card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp).padding(top = 16.dp).clickable { showNameDialog = true }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.size(72.dp)) {
                            Box(modifier = Modifier.size(72.dp).clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.20f))
                                .border(2.dp, PrimaryBlue.copy(alpha = 0.50f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(settings.name.firstOrNull()?.uppercase() ?: "S",
                                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Box(modifier = Modifier.size(22.dp).align(Alignment.BottomEnd)
                                .clip(CircleShape).background(PrimaryBlue).border(2.dp, BgDark, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(settings.name.ifEmpty { "Tap to set name" },
                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("sujood@app.com", fontSize = 13.sp, color = SlateText)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.20f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)) {
                                Text("PREMIUM MEMBER", fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold, color = PrimaryBlue, letterSpacing = 1.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Location & Prayer
            item { SectionLabel("Location & Prayer") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(icon = Icons.Default.LocationOn, title = "Prayer Location",
                        subtitle = when {
                            settings.savedCity.isNotEmpty() -> settings.savedCity
                            settings.savedLatitude != 0.0  -> "GPS Active"
                            else -> "Not set"
                        },
                        trailing = {
                            if (settings.useGpsLocation || settings.savedLatitude != 0.0)
                                Text("GPS Active", fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
                            else
                                Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp))
                        },
                        onClick = { showCityDialog = true })
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.Calculate, title = "Calculation Method",
                        subtitle = settings.calculationMethod.displayName,
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = { showMethodDialog = true })
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.Mosque, title = "Madhab",
                        subtitle = settings.madhab.displayName + " / Standard",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = { showMadhabDialog = true })
                }
            }

            // Notifications
            item { SectionLabel("Notifications") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    val allOn = settings.fajrNotificationEnabled && settings.dhuhrNotificationEnabled &&
                                settings.asrNotificationEnabled && settings.maghribNotificationEnabled &&
                                settings.ishaNotificationEnabled
                    SettingsRow(icon = Icons.Default.NotificationsActive, title = "Daily Prayer Alerts",
                        trailing = {
                            Switch(checked = allOn, onCheckedChange = { enabled ->
                                scope.launch {
                                    Prayer.entries.forEach { userPreferences.saveNotificationEnabled(it.name, enabled) }
                                    rescheduleAlarms(context, userPreferences)
                                }
                            }, colors = blueSwitchColors())
                        })
                    GlassDivider()
                    // Per-prayer grid
                    Row(modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Prayer.FAJR to settings.fajrNotificationEnabled,
                               Prayer.DHUHR to settings.dhuhrNotificationEnabled,
                               Prayer.ASR to settings.asrNotificationEnabled,
                               Prayer.MAGHRIB to settings.maghribNotificationEnabled,
                               Prayer.ISHA to settings.ishaNotificationEnabled
                        ).forEach { (prayer, enabled) ->
                            Column(modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (enabled) PrimaryBlue.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f))
                                .border(1.dp, if (enabled) PrimaryBlue.copy(alpha = 0.40f) else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch {
                                        userPreferences.saveNotificationEnabled(prayer.name, !enabled)
                                        rescheduleAlarms(context, userPreferences)
                                    }
                                }.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(prayer.displayName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Icon(if (enabled) Icons.Default.VolumeUp else Icons.Default.Vibration,
                                    null, tint = if (enabled) PrimaryBlue else SlateText, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.MusicNote, title = "Adhan Sound",
                        subtitle = settings.adhanSoundName.ifEmpty { "Mishary Al-Afasy" },
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = { showAdhanDialog = true })
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.Vibration, title = "Vibration",
                        trailing = {
                            Switch(checked = settings.vibrationEnabled, onCheckedChange = { enabled ->
                                scope.launch { userPreferences.saveAudioSettings(settings.adhanEnabled, enabled) }
                            }, colors = blueSwitchColors())
                        })
                }
            }

            // Audio & Haptics
            item { SectionLabel("Audio & Haptics") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(icon = Icons.Default.VolumeUp, title = "Sound Effects",
                        trailing = {
                            Switch(checked = settings.adhanEnabled, onCheckedChange = { enabled ->
                                scope.launch { userPreferences.saveAudioSettings(enabled, settings.vibrationEnabled) }
                            }, colors = blueSwitchColors())
                        })
                }
            }

            // App Preferences
            item { SectionLabel("App Preferences") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(icon = Icons.Default.Language, title = "Language",
                        trailing = { Text("English (US)", fontSize = 12.sp, color = SlateText) })
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.FileUpload, title = "Data Backup",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) })
                }
            }

            // Data Management
            item { SectionLabel("Data Management") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(icon = Icons.Default.FileUpload, title = "Export Prayer History",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) })
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.DeleteSweep, title = "Clear Cache",
                        trailing = {
                            if (cacheCleared)
                                Text("Cleared!", fontSize = 12.sp, color = Color(0xFF22C55E), fontWeight = FontWeight.SemiBold)
                            else
                                Text("42 MB", fontSize = 12.sp, color = SlateMuted)
                        },
                        onClick = {
                            // Clear app cache directory
                            scope.launch {
                                try {
                                    context.cacheDir.deleteRecursively()
                                    context.externalCacheDir?.deleteRecursively()
                                } catch (_: Exception) {}
                                cacheCleared = true
                            }
                        })
                }
            }

            // About
            item { SectionLabel("About") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(icon = Icons.Default.Info, title = "App Version",
                        trailing = { Text("v1.0.0", fontSize = 12.sp, color = SlateText) })
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.SupportAgent, title = "Support & Feedback",
                        trailing = { Icon(Icons.Default.OpenInNew, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:support@sujood.app"))
                            context.startActivity(intent)
                        })
                }
            }

            // Sign Out
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.clip(CircleShape)
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.20f), CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.05f))
                        .padding(horizontal = 24.dp, vertical = 10.dp).clickable {}) {
                        Text("Sign Out", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444).copy(alpha = 0.80f))
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    if (showNameDialog) {
        NameDialog(settings.name, { showNameDialog = false }) { name ->
            scope.launch { userPreferences.saveUserName(name) }; showNameDialog = false
        }
    }
    if (showCityDialog) {
        CitySearchDialog(
            currentCity = settings.savedCity,
            onDismiss = { showCityDialog = false },
            onConfirm = { city ->
                scope.launch {
                    userPreferences.saveLocationSettings(false, city, "", 0.0, 0.0)
                    rescheduleAlarms(context, userPreferences)
                }
                showCityDialog = false
            },
            onUseGps = {
                // Request permission then immediately save useGps=true so HomeScreen re-fetches
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val pm = context.packageManager
                    val granted = pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, context.packageName) ==
                                  android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else {
                        scope.launch {
                            userPreferences.saveLocationSettings(true, "", "", 0.0, 0.0)
                        }
                    }
                } else {
                    scope.launch { userPreferences.saveLocationSettings(true, "", "", 0.0, 0.0) }
                }
                showCityDialog = false
            }
        )
    }
    if (showMethodDialog) {
        CalculationMethodDialog(settings.calculationMethod, { showMethodDialog = false }) { method ->
            scope.launch { userPreferences.saveCalculationMethod(method) }; showMethodDialog = false
        }
    }
    if (showMadhabDialog) {
        MadhabDialog(settings.madhab, { showMadhabDialog = false }) { madhab ->
            scope.launch { userPreferences.saveMadhab(madhab) }; showMadhabDialog = false
        }
    }
    if (showAdhanDialog) {
        AdhanPickerDialog(
            currentName = settings.adhanSoundName.ifEmpty { "Mishary Al-Afasy" },
            onDismiss = { showAdhanDialog = false },
            onSelect = { name, url ->
                scope.launch { userPreferences.saveAdhanSound(name, url) }
                showAdhanDialog = false
            }
        )
    }
}

// ── Components ────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        color = SlateMuted, modifier = Modifier.padding(horizontal = 26.dp).padding(top = 24.dp, bottom = 8.dp))
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(GlassFill)
        .border(1.dp, GlassBrd, RoundedCornerShape(16.dp))) { content() }
}

@Composable
private fun GlassDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassDiv))
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            .background(PrimaryBlue.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            if (subtitle != null)
                Text(subtitle, fontSize = 12.sp, color = SlateText, modifier = Modifier.padding(top = 1.dp))
        }
        trailing?.invoke()
    }
}

@Composable
private fun blueSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue,
    uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF334155))

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun NameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your Name") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = GlassBorder)) },
        confirmButton = { Button(onClick = { onConfirm(name) }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = MidnightBlue)
}

@Composable
private fun CitySearchDialog(
    currentCity: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onUseGps: () -> Unit
) {
    var query        by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf(currentCity) }
    val focusManager = LocalFocusManager.current
    val filtered = remember(query) {
        if (query.length < 2) emptyList()
        else CITY_LIST.filter { it.contains(query, ignoreCase = true) }.take(6)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Prayer Location") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it; selectedCity = "" },
                    label = { Text("Search city") }, placeholder = { Text("e.g. Dubai") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = GlassBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                if (filtered.isNotEmpty() && selectedCity.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1F35))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))) {
                        filtered.forEachIndexed { idx, city ->
                            Row(modifier = Modifier.fillMaxWidth()
                                .clickable { selectedCity = city; query = city; focusManager.clearFocus() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(city, fontSize = 13.sp, color = Color.White)
                            }
                            if (idx < filtered.lastIndex)
                                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onUseGps, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use GPS — scan my location now", color = PrimaryBlue)
                }
            }
        },
        confirmButton = { Button(onClick = { val c = selectedCity.ifEmpty { query }.trim(); if (c.isNotBlank()) onConfirm(c) },
            enabled = selectedCity.isNotEmpty() || query.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MidnightBlue)
}

@Composable
private fun CalculationMethodDialog(currentMethod: CalculationMethod, onDismiss: () -> Unit, onSelect: (CalculationMethod) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Calculation Method") },
        text = { Column { CalculationMethod.entries.forEach { method -> Row(Modifier.fillMaxWidth().clickable { onSelect(method) }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(method.displayName); if (method == currentMethod) Icon(Icons.Default.Check, null, tint = PrimaryBlue) } } } },
        confirmButton = {}, containerColor = MidnightBlue)
}

@Composable
private fun MadhabDialog(currentMadhab: Madhab, onDismiss: () -> Unit, onSelect: (Madhab) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Select Madhab") },
        text = { Column { Madhab.entries.forEach { madhab -> Row(Modifier.fillMaxWidth().clickable { onSelect(madhab) }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(madhab.displayName); if (madhab == currentMadhab) Icon(Icons.Default.Check, null, tint = PrimaryBlue) } } } },
        confirmButton = {}, containerColor = MidnightBlue)
}

@Composable
private fun AdhanPickerDialog(currentName: String, onDismiss: () -> Unit, onSelect: (String, String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Adhan Sound") },
        text = {
            Column {
                ADHAN_OPTIONS.forEach { (name, url) ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(name, url) }.padding(vertical = 12.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.MusicNote, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Text(name, color = Color.White)
                        }
                        if (name == currentName) Icon(Icons.Default.Check, null, tint = PrimaryBlue)
                    }
                }
            }
        },
        confirmButton = {}, containerColor = MidnightBlue)
}

private suspend fun rescheduleAlarms(context: android.content.Context, userPreferences: UserPreferences) {
    val settings = userPreferences.userSettings.first()
    val app = context.applicationContext as SujoodApplication
    val repository = PrayerTimesRepository(RetrofitClient.aladhanApiService, app.database.prayerLogDao())
    val result = when {
        settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 ->
            repository.getPrayerTimes(settings.savedLatitude, settings.savedLongitude, settings.calculationMethod, settings.madhab)
        settings.savedCity.isNotEmpty() -> repository.getPrayerTimesByCity(settings.savedCity)
        else -> return
    }
    result.onSuccess { prayerTimes ->
        val scheduler = PrayerAlarmScheduler(context)
        val notif = Prayer.entries.map { p -> when (p) { Prayer.FAJR -> settings.fajrNotificationEnabled; Prayer.DHUHR -> settings.dhuhrNotificationEnabled; Prayer.ASR -> settings.asrNotificationEnabled; Prayer.MAGHRIB -> settings.maghribNotificationEnabled; Prayer.ISHA -> settings.ishaNotificationEnabled } }.toBooleanArray()
        val lock  = Prayer.entries.map { p -> when (p) { Prayer.FAJR -> settings.fajrLockEnabled; Prayer.DHUHR -> settings.dhuhrLockEnabled; Prayer.ASR -> settings.asrLockEnabled; Prayer.MAGHRIB -> settings.maghribLockEnabled; Prayer.ISHA -> settings.ishaLockEnabled } }.toBooleanArray()
        scheduler.scheduleAllAlarms(prayerTimes, notif, lock, settings.gracePeriodMinutes)
    }
}
''')

# ─────────────────────────────────────────────────────────────────────────────
# 6. UserPreferences — add adhanSoundName + adhanSoundUrl fields
#    (needed by SettingsScreen and PrayerLockOverlayService)
# ─────────────────────────────────────────────────────────────────────────────
prefs_path = os.path.join(SRC, "data","local","datastore","UserPreferences.kt")
with open(prefs_path, encoding="utf-8") as f:
    prefs = f.read()

# Add two new keys after ADHAN_ENABLED
if "ADHAN_SOUND_NAME" not in prefs:
    prefs = prefs.replace(
        'val ADHAN_ENABLED = booleanPreferencesKey("adhan_enabled")',
        'val ADHAN_ENABLED = booleanPreferencesKey("adhan_enabled")\n'
        '        val ADHAN_SOUND_NAME = stringPreferencesKey("adhan_sound_name")\n'
        '        val ADHAN_SOUND_URL  = stringPreferencesKey("adhan_sound_url")'
    )

# Add fields to UserSettings data class mapping
if "adhanSoundName" not in prefs:
    prefs = prefs.replace(
        'adhanEnabled = preferences[PreferencesKeys.ADHAN_ENABLED] ?: true,',
        'adhanEnabled = preferences[PreferencesKeys.ADHAN_ENABLED] ?: true,\n'
        '            adhanSoundName = preferences[PreferencesKeys.ADHAN_SOUND_NAME] ?: "",\n'
        '            adhanSoundUrl  = preferences[PreferencesKeys.ADHAN_SOUND_URL]  ?: "",'
    )

# Add saveAdhanSound method
if "saveAdhanSound" not in prefs:
    prefs = prefs.replace(
        'suspend fun saveAudioSettings',
        'suspend fun saveAdhanSound(name: String, url: String) {\n'
        '        context.dataStore.edit { preferences ->\n'
        '            preferences[PreferencesKeys.ADHAN_SOUND_NAME] = name\n'
        '            preferences[PreferencesKeys.ADHAN_SOUND_URL]  = url\n'
        '        }\n'
        '    }\n\n'
        '    suspend fun saveAudioSettings'
    )

with open(prefs_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(prefs)
print("  ✓  data/local/datastore/UserPreferences.kt  (adhan sound fields)")

# ─────────────────────────────────────────────────────────────────────────────
# 7. UserSettings model — add adhanSoundName + adhanSoundUrl
# ─────────────────────────────────────────────────────────────────────────────
models_path = os.path.join(SRC, "domain","model","Models.kt")
with open(models_path, encoding="utf-8") as f:
    models = f.read()

if "adhanSoundName" not in models:
    models = models.replace(
        'val adhanEnabled: Boolean = true,',
        'val adhanEnabled: Boolean = true,\n'
        '    val adhanSoundName: String = "",\n'
        '    val adhanSoundUrl: String  = "",'
    )
    with open(models_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(models)
    print("  ✓  domain/model/Models.kt  (adhan sound fields)")
else:
    print("  –  domain/model/Models.kt  (already patched)")

# ─────────────────────────────────────────────────────────────────────────────
# 8. PrayerLockOverlayService — use saved adhan URL instead of hardcoded one
# ─────────────────────────────────────────────────────────────────────────────
svc_path = os.path.join(SRC, "service","PrayerLockOverlayService.kt")
with open(svc_path, encoding="utf-8") as f:
    svc = f.read()

svc = svc.replace(
    'val adhanUrl = "https://cdn.islamic.network/quran/audio/128/ar.alafasy/1.mp3"',
    'val adhanUrl = settings.adhanSoundUrl.ifEmpty { "https://cdn.islamic.network/quran/audio/128/ar.alafasy/1.mp3" }'
)

with open(svc_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(svc)
print("  ✓  service/PrayerLockOverlayService.kt  (use saved adhan URL)")

print("""
╔══════════════════════════════════════════════════════╗
║  All done!  Now run:                                 ║
║    cd Sujood                                         ║
║    ./gradlew assembleDebug                           ║
╚══════════════════════════════════════════════════════╝

Files changed:
  1. EmotionalOnboardingScreen.kt  — saves onboarding=true on Enter button
  2. HomeScreen.kt                 — sleek Canvas prayer icons, no LocationOn bug
  3. DhikrScreen.kt                — Test Lock (real), Specific Apps picker, no preview
  4. QiblaScreen.kt                — shows user's city name via Geocoder
  5. SettingsScreen.kt             — GPS instant scan, clear cache works,
                                     adhan picker, city dropdown search
  6. UserPreferences.kt            — adhanSoundName + adhanSoundUrl fields
  7. Models.kt (UserSettings)      — adhanSoundName + adhanSoundUrl fields
  8. PrayerLockOverlayService.kt   — reads saved adhan URL
""")
