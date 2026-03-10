package com.sujood.app.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.SujoodApplication
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.usecase.GetPrayerStreakUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ── Design tokens matching the mockup exactly ──
private val BackgroundDark = Color(0xFF0A0C1A)
private val PrimaryBlue    = Color(0xFF1132D4)
private val AccentPurple   = Color(0xFF8B5CF6)
private val GlassFill      = Color(0xFFFFFFFF).copy(alpha = 0.03f)
private val GlassStroke    = Color(0xFFFFFFFF).copy(alpha = 0.08f)
private val TextMuted      = Color(0xFF94A3B8)  // slate-400
private val TextDimmer     = Color(0xFF64748B)  // slate-500
private val EmeraldGreen   = Color(0xFF34D399)  // emerald-400

// Reflection quotes that rotate based on streak
private val reflections = listOf(
    "Verily, in the remembrance of Allah do hearts find rest. – Ar-Ra'd 13:28",
    "Indeed, prayer prohibits immorality and wrongdoing. – Al-Ankabut 29:45",
    "And seek help through patience and prayer. – Al-Baqarah 2:45",
    "Establish prayer for My remembrance. – Ta-Ha 20:14",
    "Allah does not burden a soul beyond that it can bear. – Al-Baqarah 2:286"
)

@Composable
fun InsightsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as SujoodApplication
    val repository = remember {
        PrayerTimesRepository(RetrofitClient.aladhanApiService, app.database.prayerLogDao())
    }
    val streakUseCase = remember { GetPrayerStreakUseCase(repository) }

    var currentStreak    by remember { mutableStateOf(0) }
    var longestStreak    by remember { mutableStateOf(0) }
    var weeklyData       by remember { mutableStateOf(listOf(0, 0, 0, 0, 0, 0, 0)) }
    var weeklyConsistency by remember { mutableStateOf(0) }
    var weeklyDelta      by remember { mutableStateOf(0) }
    var monthlyProgress  by remember { mutableStateOf<Map<Prayer, Pair<Int, Int>>>(emptyMap()) }

    val allLogs by repository.getAllPrayerLogs().collectAsState(initial = emptyList())

    LaunchedEffect(allLogs) {
        withContext(Dispatchers.Default) {
            val fmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = Calendar.getInstance()

            currentStreak = streakUseCase()

            // ── Longest streak ──
            val allFullDates = allLogs
                .groupBy { it.date }
                .filter { (_, logs) -> logs.map { it.prayer.name }.toSet().size >= 5 }
                .keys.sortedDescending()

            var maxStreak = 0; var runStreak = 0
            for (i in allFullDates.indices) {
                if (i == 0) {
                    runStreak = 1
                } else {
                    val prev = fmt.parse(allFullDates[i - 1])
                    val curr = fmt.parse(allFullDates[i])
                    if (prev != null && curr != null) {
                        val diff = ((prev.time - curr.time) / (24 * 60 * 60 * 1000)).toInt()
                        if (diff == 1) runStreak++ else runStreak = 1
                    }
                }
                if (runStreak > maxStreak) maxStreak = runStreak
            }
            longestStreak = maxOf(maxStreak, currentStreak)

            // ── Weekly data (Mon=0..Sun=6) ──
            val weekly = MutableList(7) { 0 }
            val weekStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            for (log in allLogs) {
                val logDate = fmt.parse(log.date) ?: continue
                val logCal  = Calendar.getInstance().apply { time = logDate }
                if (!logCal.before(weekStart) && !logCal.after(today)) {
                    val dow = ((logCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7)
                    weekly[dow] = (weekly[dow] + 1).coerceAtMost(5)
                }
            }
            weeklyData = weekly

            // % of prayers done this week vs max possible (days elapsed × 5)
            val dayOfWeek = ((today.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7) + 1
            val maxThisWeek = dayOfWeek * 5
            val doneThisWeek = weekly.sum()
            weeklyConsistency = if (maxThisWeek > 0) ((doneThisWeek * 100) / maxThisWeek) else 0

            // last-week delta (simplified: positive if streak > 0)
            weeklyDelta = if (currentStreak > 0) 5 else 0

            // ── Monthly prayer breakdown ──
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val daysElapsed = today.get(Calendar.DAY_OF_MONTH)
            val map = mutableMapOf<Prayer, Pair<Int, Int>>()
            for (prayer in Prayer.entries) {
                val completed = allLogs
                    .filter { it.prayer == prayer }
                    .map { it.date }.toSet()
                    .count { ds ->
                        val d = fmt.parse(ds) ?: return@count false
                        val c = Calendar.getInstance().apply { time = d }
                        !c.before(monthStart) && !c.after(today)
                    }
                map[prayer] = Pair(completed, daysElapsed)
            }
            monthlyProgress = map
        }
    }

    // Pick a rotating quote based on streak
    val quote = reflections[currentStreak % reflections.size]
    val today = remember {
        val cal = java.util.Calendar.getInstance()
        val month = cal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, Locale.getDefault()) ?: ""
        val day   = cal.get(java.util.Calendar.DAY_OF_MONTH)
        "$month $day"
    }

    // ── Full background ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(PrimaryBlue.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(0f, 0f), radius = 1200f
                )
            )
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(AccentPurple.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(Float.MAX_VALUE, Float.MAX_VALUE), radius = 1200f
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {

            // ── Header ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Avatar placeholder
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.20f))
                                .border(2.dp, PrimaryBlue.copy(alpha = 0.50f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(
                            text = "Insights",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    // Notification bell in glass circle
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GlassFill)
                            .border(1.dp, GlassStroke, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.NotificationsNone, "Notifications",
                            tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── Weekly Consistency Card ──
            item {
                GlassCard(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        // Top row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    "Weekly Consistency",
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        "$weeklyConsistency%",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    if (weeklyDelta > 0) {
                                        Text(
                                            "+$weeklyDelta%",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EmeraldGreen
                                        )
                                    }
                                }
                            }
                            Text(
                                "Last 7 Days",
                                fontSize = 11.sp,
                                color = TextDimmer
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Bar chart
                        val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
                        val maxBarHeight = 120.dp

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(maxBarHeight + 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weeklyData.forEachIndexed { index, count ->
                                val fraction   = (count / 5f).coerceIn(0f, 1f)
                                val isToday    = index == ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7)
                                val barColor   = if (isToday) PrimaryBlue else PrimaryBlue.copy(alpha = 0.60f)
                                val labelColor = if (isToday) PrimaryBlue else TextMuted

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp)
                                            .height(maxBarHeight * fraction.coerceAtLeast(0.05f))
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(barColor)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        days[index],
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = labelColor,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Current Streak Card ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassFill)
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(PrimaryBlue, GlassStroke)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        // Left accent border
                        .then(
                            Modifier.drawWithCache {
                                onDrawWithContent {
                                    drawContent()
                                    drawLine(
                                        color = PrimaryBlue,
                                        start = Offset(0f, 0f),
                                        end = Offset(0f, size.height),
                                        strokeWidth = 4.dp.toPx()
                                    )
                                }
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "CURRENT STREAK",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                "$currentStreak Days",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Longest: $longestStreak Days",
                                fontSize = 13.sp,
                                color = Color(0xFFCBD5E1) // slate-300
                            )
                        }

                        // Verified badge circle
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.20f))
                                .border(1.dp, PrimaryBlue.copy(alpha = 0.30f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Verified, "Streak",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Decorative blur blob
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.BottomEnd)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(PrimaryBlue.copy(alpha = 0.08f), Color.Transparent)
                                )
                            )
                    )
                }
            }

            // ── Prayer Breakdown header ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 28.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Prayer Breakdown",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "(Last 30 Days)",
                        fontSize = 13.sp,
                        color = TextDimmer,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // ── Prayer rows ──
            val prayerConfigs = listOf(
                Triple(Prayer.FAJR,    PrimaryBlue,  "Consistent"),
                Triple(Prayer.DHUHR,   AccentPurple, "Great Progress"),
                Triple(Prayer.ASR,     PrimaryBlue,  "Exceptional"),
                Triple(Prayer.MAGHRIB, AccentPurple, "Good Work"),
                Triple(Prayer.ISHA,    PrimaryBlue,  "Keep Going")
            )

            items(prayerConfigs.size) { i ->
                val (prayer, ringColor, label) = prayerConfigs[i]
                val (completed, total) = monthlyProgress[prayer] ?: Pair(0, 30)
                val pct = if (total > 0) ((completed.toFloat() / total) * 100).toInt().coerceIn(0, 100) else 0

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Circular progress ring
                        CircularProgressRing(
                            progress = pct / 100f,
                            color = ringColor,
                            label = "$pct%"
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    prayer.displayName,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "$completed / $total",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                label,
                                color = ringColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Recent Reflection header ──
            item {
                Text(
                    "Recent Reflection",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 28.dp, bottom = 16.dp)
                )
            }

            // ── Quote card ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassFill)
                        .border(1.dp, PrimaryBlue.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Column {
                        // Quote mark badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryBlue.copy(alpha = 0.20f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("❝", color = PrimaryBlue, fontSize = 18.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "“$quote”",
                            color = Color(0xFFCBD5E1), // slate-300
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue)
                            )
                            Text(
                                "NOTE FROM $today".uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDimmer,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Circular progress ring drawn with Canvas ──
@Composable
private fun CircularProgressRing(
    progress: Float,
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .drawWithCache {
                val stroke    = 4.dp.toPx()
                val diameter  = size.minDimension - stroke
                val topLeft   = Offset(stroke / 2f, stroke / 2f)
                val arcSize   = Size(diameter, diameter)
                onDrawWithContent {
                    // Track
                    drawArc(
                        color      = Color(0xFF1E293B), // slate-800
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter  = false,
                        topLeft    = topLeft,
                        size       = arcSize,
                        style      = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // Progress arc
                    if (progress > 0f) {
                        drawArc(
                            color      = color,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter  = false,
                            topLeft    = topLeft,
                            size       = arcSize,
                            style      = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    drawContent()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// ── Reusable glass card ──
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}
