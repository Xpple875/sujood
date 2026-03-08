import os

SRC = os.path.join("Sujood", "app", "src", "main", "java", "com", "sujood", "app")
RES = os.path.join("Sujood", "app", "src", "main", "res")

files = {}

# ── 1. BottomNavBar.kt — reverted to original style but with blue instead of purple ──
files[('src', 'ui/components/BottomNavBar.kt')] = \
'''package com.sujood.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.domain.model.BottomNavItem
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.MidnightBlue

// Primary blue to match the app accent colour
private val PrimaryBlue = Color(0xFF1132D4)

@Composable
fun GlassmorphicBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Dhikr,
        BottomNavItem.Qibla,
        BottomNavItem.Insights,
        BottomNavItem.Settings
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.05f),
                        MidnightBlue.copy(alpha = 0.7f),
                        MidnightBlue.copy(alpha = 0.9f)
                    )
                )
            )
            .height(84.dp)
            .navigationBarsPadding()
    ) {
        // Hair-line top border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route
                NavItem(
                    item = item,
                    isSelected = isSelected,
                    onClick = { onNavigate(item.route) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val color by animateColorAsState(
        targetValue = if (isSelected) PrimaryBlue else Color.White.copy(alpha = 0.4f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .scale(scale)
                .then(
                    if (isSelected) {
                        Modifier.background(
                            color = PrimaryBlue.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                    } else Modifier
                )
        ) {
            Icon(
                imageVector = getIconForItem(item),
                contentDescription = item.title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            fontSize = 10.sp
        )
    }
}

private fun getIconForItem(item: BottomNavItem): ImageVector {
    return when (item) {
        BottomNavItem.Home     -> Icons.Default.Home
        BottomNavItem.Dhikr   -> Icons.Default.Lock
        BottomNavItem.Qibla   -> Icons.Default.Explore
        BottomNavItem.Insights -> Icons.Default.BarChart
        BottomNavItem.Settings -> Icons.Default.Settings
    }
}
'''

# ── 2. InsightsScreen.kt — full redesign matching the HTML/screenshot mockup ──
files[('src', 'ui/screens/insights/InsightsScreen.kt')] = \
'''package com.sujood.app.ui.screens.insights

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
    "Verily, in the remembrance of Allah do hearts find rest. – Ar-Ra\'d 13:28",
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
            modifier = Modifier.fillMaxSize(),
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
                            text = "\u201c$quote\u201d",
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
'''

# ── 3. SettingsScreen.kt — always included to protect fragile import ──
files[('src', 'ui/screens/settings/SettingsScreen.kt')] = \
'''package com.sujood.app.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.sujood.app.SujoodApplication
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.notifications.PrayerAlarmScheduler
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.domain.model.CalculationMethod
import com.sujood.app.domain.model.LockMode
import com.sujood.app.domain.model.Madhab
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.UserSettings
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by userPreferences.userSettings.collectAsState(initial = UserSettings())
    var showNameDialog by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }
    var showMadhabDialog by remember { mutableStateOf(false) }
    var showGracePeriodDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var showLockTriggerDialog by remember { mutableStateOf(false) }
    var showLockDurationDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy)
            )
        },
        containerColor = DeepNavy
    ) { paddingValues ->
        AnimatedGradientBackground(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsSectionHeader("Profile")
                SettingsCard {
                    SettingsClickableItem(icon = Icons.Default.Person, title = "Name",
                        subtitle = settings.name.ifEmpty { "Not set" }, onClick = { showNameDialog = true })
                }
                SettingsSectionHeader("Location")
                SettingsCard {
                    SettingsClickableItem(icon = Icons.Default.LocationOn, title = "Prayer Location",
                        subtitle = if (settings.savedCity.isNotEmpty()) settings.savedCity
                                   else if (settings.savedLatitude != 0.0) "GPS: %.2f°, %.2f°".format(settings.savedLatitude, settings.savedLongitude)
                                   else "Not set — tap to change",
                        onClick = { showCityDialog = true })
                }
                SettingsSectionHeader("Prayer Settings")
                SettingsCard {
                    SettingsClickableItem(title = "Calculation Method", subtitle = settings.calculationMethod.displayName, onClick = { showMethodDialog = true })
                    SettingsDivider()
                    SettingsClickableItem(title = "Madhab (Asr)", subtitle = settings.madhab.displayName, onClick = { showMadhabDialog = true })
                    SettingsDivider()
                    SettingsClickableItem(title = "Grace Period",
                        subtitle = if (settings.gracePeriodMinutes == 0) "No grace period" else "${settings.gracePeriodMinutes} minutes",
                        onClick = { showGracePeriodDialog = true })
                }
                SettingsSectionHeader("Notifications")
                SettingsCard {
                    Text("Receive a notification when each prayer time arrives",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp))
                    Prayer.entries.forEach { prayer ->
                        SettingsToggleItem(
                            title = "${prayer.displayName} Notification",
                            isEnabled = when (prayer) {
                                Prayer.FAJR -> settings.fajrNotificationEnabled
                                Prayer.DHUHR -> settings.dhuhrNotificationEnabled
                                Prayer.ASR -> settings.asrNotificationEnabled
                                Prayer.MAGHRIB -> settings.maghribNotificationEnabled
                                Prayer.ISHA -> settings.ishaNotificationEnabled
                            },
                            onToggle = { enabled ->
                                scope.launch { userPreferences.saveNotificationEnabled(prayer.name, enabled); rescheduleAlarms(context, userPreferences) }
                            }
                        )
                        if (prayer != Prayer.entries.last()) SettingsDivider()
                    }
                }
                SettingsSectionHeader("Prayer Lock")
                SettingsCard {
                    Text("Lock the phone at prayer time and block distractions",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp))
                    Prayer.entries.forEach { prayer ->
                        SettingsToggleItem(
                            title = "Lock for ${prayer.displayName}",
                            isEnabled = when (prayer) {
                                Prayer.FAJR -> settings.fajrLockEnabled
                                Prayer.DHUHR -> settings.dhuhrLockEnabled
                                Prayer.ASR -> settings.asrLockEnabled
                                Prayer.MAGHRIB -> settings.maghribLockEnabled
                                Prayer.ISHA -> settings.ishaLockEnabled
                            },
                            onToggle = { enabled ->
                                scope.launch { userPreferences.saveLockEnabled(prayer.name, enabled); rescheduleAlarms(context, userPreferences) }
                            }
                        )
                        if (prayer != Prayer.entries.last()) SettingsDivider()
                    }
                }
                SettingsSectionHeader("Lock Behavior")
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Lock Mode", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LockMode.entries.forEach { mode ->
                                val isSelected = settings.lockMode == mode
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .clickable { scope.launch { userPreferences.saveLockSettings(mode, settings.lockTriggerMinutes, settings.lockDurationMinutes) } }
                                        .background(if (isSelected) SoftPurple.copy(alpha = 0.2f) else MidnightBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(mode.displayName, style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) SoftPurple else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    SettingsClickableItem(title = "Trigger Timing",
                        subtitle = if (settings.lockTriggerMinutes == 0) "At prayer time" else "${settings.lockTriggerMinutes} minutes after prayer",
                        onClick = { showLockTriggerDialog = true })
                    SettingsDivider()
                    SettingsClickableItem(title = "Lock Duration", subtitle = "${settings.lockDurationMinutes} minutes",
                        onClick = { showLockDurationDialog = true })
                }
                SettingsSectionHeader("Audio & Haptics")
                SettingsCard {
                    SettingsToggleItem(title = "Adhan Sound", subtitle = "Play adhan when prayer time arrives",
                        isEnabled = settings.adhanEnabled,
                        onToggle = { enabled -> scope.launch { userPreferences.saveAudioSettings(enabled, settings.vibrationEnabled) } })
                    SettingsDivider()
                    SettingsToggleItem(title = "Vibration", subtitle = "Vibrate at prayer time",
                        isEnabled = settings.vibrationEnabled,
                        onToggle = { enabled -> scope.launch { userPreferences.saveAudioSettings(settings.adhanEnabled, enabled) } })
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showNameDialog) NameDialog(settings.name, { showNameDialog = false }) { name -> scope.launch { userPreferences.saveUserName(name) }; showNameDialog = false }
    if (showCityDialog) ChangeCityDialog(settings.savedCity, { showCityDialog = false },
        { city -> scope.launch { userPreferences.saveLocationSettings(false, city, "", 0.0, 0.0) }; showCityDialog = false },
        { scope.launch { userPreferences.saveLocationSettings(true, "", "", 0.0, 0.0) }; showCityDialog = false })
    if (showMethodDialog) CalculationMethodDialog(settings.calculationMethod, { showMethodDialog = false }) { method -> scope.launch { userPreferences.saveCalculationMethod(method) }; showMethodDialog = false }
    if (showMadhabDialog) MadhabDialog(settings.madhab, { showMadhabDialog = false }) { madhab -> scope.launch { userPreferences.saveMadhab(madhab) }; showMadhabDialog = false }
    if (showGracePeriodDialog) GracePeriodDialog(settings.gracePeriodMinutes, { showGracePeriodDialog = false }) { minutes -> scope.launch { userPreferences.saveGracePeriod(minutes) }; showGracePeriodDialog = false }
    if (showLockTriggerDialog) TriggerDialog(settings.lockTriggerMinutes, { showLockTriggerDialog = false }) { minutes -> scope.launch { userPreferences.saveLockSettings(settings.lockMode, minutes, settings.lockDurationMinutes) }; showLockTriggerDialog = false }
    if (showLockDurationDialog) DurationDialog(settings.lockDurationMinutes, { showLockDurationDialog = false }) { minutes -> scope.launch { userPreferences.saveLockSettings(settings.lockMode, settings.lockTriggerMinutes, minutes) }; showLockDurationDialog = false }
}

@Composable private fun SettingsSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = SoftPurple, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp))
}
@Composable private fun SettingsCard(content: @Composable () -> Unit) {
    FrostedGlassCard(cornerRadius = 20.dp, contentPadding = 0.dp) { Column { content() } }
}
@Composable private fun SettingsClickableItem(title: String, subtitle: String, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (icon != null) { Icon(imageVector = icon, contentDescription = null, tint = SoftPurple, modifier = Modifier.size(22.dp)); Spacer(modifier = Modifier.width(14.dp)) }
            Column { Text(title, style = MaterialTheme.typography.bodyLarge); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}
@Composable private fun SettingsToggleItem(title: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit, subtitle: String? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(checked = isEnabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = SoftPurple, checkedTrackColor = SoftPurple.copy(alpha = 0.4f), uncheckedThumbColor = TextSecondary, uncheckedTrackColor = MidnightBlue))
    }
}
@Composable private fun SettingsDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(GlassBorder))
}
@Composable private fun NameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your Name") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SoftPurple, unfocusedBorderColor = GlassBorder)) },
        confirmButton = { Button(onClick = { onConfirm(name) }, colors = ButtonDefaults.buttonColors(containerColor = SoftPurple)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = MidnightBlue)
}
@Composable private fun ChangeCityDialog(currentCity: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, onUseGps: () -> Unit) {
    var city by remember { mutableStateOf(currentCity) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Change Location") },
        text = { Column { OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City Name") }, placeholder = { Text("e.g. Dubai") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SoftPurple, unfocusedBorderColor = GlassBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)); Spacer(modifier = Modifier.height(12.dp)); TextButton(onClick = onUseGps) { Text("Use GPS instead", color = LavenderGlow) } } },
        confirmButton = { Button(onClick = { if (city.isNotBlank()) onConfirm(city.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = SoftPurple)) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = MidnightBlue)
}
@Composable private fun CalculationMethodDialog(currentMethod: CalculationMethod, onDismiss: () -> Unit, onSelect: (CalculationMethod) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Calculation Method") }, text = { Column { CalculationMethod.entries.forEach { method -> Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(method) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(method.displayName); if (method == currentMethod) Icon(Icons.Default.Check, null, tint = SoftPurple) } } } }, confirmButton = {}, containerColor = MidnightBlue)
}
@Composable private fun MadhabDialog(currentMadhab: Madhab, onDismiss: () -> Unit, onSelect: (Madhab) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Select Madhab") }, text = { Column { Madhab.entries.forEach { madhab -> Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(madhab) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(madhab.displayName); if (madhab == currentMadhab) Icon(Icons.Default.Check, null, tint = SoftPurple) } } } }, confirmButton = {}, containerColor = MidnightBlue)
}
@Composable private fun GracePeriodDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Grace Period") }, text = { Column { options.forEach { minutes -> Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(if (minutes == 0) "No grace period" else "$minutes minutes"); if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = SoftPurple) } } } }, confirmButton = {}, containerColor = MidnightBlue)
}
@Composable private fun TriggerDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Lock Trigger") }, text = { Column { options.forEach { minutes -> Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(if (minutes == 0) "At prayer time" else "$minutes minutes after prayer"); if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber) } } } }, confirmButton = {}, containerColor = MidnightBlue)
}
@Composable private fun DurationDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(5, 10, 15, 20, 30, 60)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Lock Duration") }, text = { Column { options.forEach { minutes -> Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("$minutes minutes"); if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber) } } } }, confirmButton = {}, containerColor = MidnightBlue)
}
private suspend fun rescheduleAlarms(context: android.content.Context, userPreferences: com.sujood.app.data.local.datastore.UserPreferences) {
    val settings   = userPreferences.userSettings.first()
    val app        = context.applicationContext as SujoodApplication
    val repository = PrayerTimesRepository(RetrofitClient.aladhanApiService, app.database.prayerLogDao())
    val result = when {
        settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 -> repository.getPrayerTimes(settings.savedLatitude, settings.savedLongitude, settings.calculationMethod, settings.madhab)
        settings.savedCity.isNotEmpty() -> repository.getPrayerTimesByCity(settings.savedCity)
        else -> return
    }
    result.onSuccess { prayerTimes ->
        val scheduler = PrayerAlarmScheduler(context)
        val notif = com.sujood.app.domain.model.Prayer.entries.map { p -> when (p) { com.sujood.app.domain.model.Prayer.FAJR -> settings.fajrNotificationEnabled; com.sujood.app.domain.model.Prayer.DHUHR -> settings.dhuhrNotificationEnabled; com.sujood.app.domain.model.Prayer.ASR -> settings.asrNotificationEnabled; com.sujood.app.domain.model.Prayer.MAGHRIB -> settings.maghribNotificationEnabled; com.sujood.app.domain.model.Prayer.ISHA -> settings.ishaNotificationEnabled } }.toBooleanArray()
        val lock  = com.sujood.app.domain.model.Prayer.entries.map { p -> when (p) { com.sujood.app.domain.model.Prayer.FAJR -> settings.fajrLockEnabled; com.sujood.app.domain.model.Prayer.DHUHR -> settings.dhuhrLockEnabled; com.sujood.app.domain.model.Prayer.ASR -> settings.asrLockEnabled; com.sujood.app.domain.model.Prayer.MAGHRIB -> settings.maghribLockEnabled; com.sujood.app.domain.model.Prayer.ISHA -> settings.ishaLockEnabled } }.toBooleanArray()
        scheduler.scheduleAllAlarms(prayerTimes, notif, lock, settings.gracePeriodMinutes)
    }
}
'''

# ── Write all files ──
for (kind, relpath), content in files.items():
    base = SRC if kind == 'src' else RES
    fullpath = os.path.join(base, relpath.replace("/", os.sep))
    os.makedirs(os.path.dirname(fullpath), exist_ok=True)
    with open(fullpath, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  ✓ {relpath}")

print("\nDone! Now run:")
print("  cd Sujood")
print("  ./gradlew assembleDebug")
