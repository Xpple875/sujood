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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.SujoodApplication
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.usecase.GetPrayerStreakUseCase
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun InsightsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as SujoodApplication
    val repository = remember {
        PrayerTimesRepository(RetrofitClient.aladhanApiService, app.database.prayerLogDao())
    }
    val streakUseCase = remember { GetPrayerStreakUseCase(repository) }

    var currentStreak by remember { mutableStateOf(0) }
    var longestStreak by remember { mutableStateOf(0) }
    var totalPrayers by remember { mutableStateOf(0) }
    var weeklyData by remember { mutableStateOf(listOf(0, 0, 0, 0, 0, 0, 0)) }
    var monthlyProgress by remember { mutableStateOf<Map<Prayer, Float>>(emptyMap()) }

    val allLogs by repository.getAllPrayerLogs().collectAsState(initial = emptyList())

    LaunchedEffect(allLogs) {
        withContext(Dispatchers.Default) {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = Calendar.getInstance()

            totalPrayers = allLogs.size
            currentStreak = streakUseCase()

            // Longest streak
            val allFullDates = allLogs
                .groupBy { it.date }
                .filter { (_, logs) -> logs.map { it.prayer.name }.toSet().size >= 5 }
                .keys
                .sortedDescending()

            var maxStreak = 0
            var runStreak = 0
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

            // Weekly data (Mon=0 .. Sun=6)
            val weekly = MutableList(7) { 0 }
            val weekStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            for (log in allLogs) {
                val logDate = fmt.parse(log.date) ?: continue
                val logCal = Calendar.getInstance().apply { time = logDate }
                if (!logCal.before(weekStart) && !logCal.after(today)) {
                    val dow = ((logCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7)
                    weekly[dow] = (weekly[dow] + 1).coerceAtMost(5)
                }
            }
            weeklyData = weekly

            // Monthly progress per prayer
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val daysInMonth = today.get(Calendar.DAY_OF_MONTH)
            val monthMap = mutableMapOf<Prayer, Float>()
            for (prayer in Prayer.entries) {
                val completedDays = allLogs
                    .filter { it.prayer == prayer }
                    .map { it.date }
                    .toSet()
                    .count { dateStr ->
                        val d = fmt.parse(dateStr) ?: return@count false
                        val c = Calendar.getInstance().apply { time = d }
                        !c.before(monthStart) && !c.after(today)
                    }
                monthMap[prayer] = (completedDays.toFloat() / daysInMonth).coerceIn(0f, 1f)
            }
            monthlyProgress = monthMap
        }
    }

    AnimatedGradientBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Insights", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light, color = Color.White)
            }

            // Streak Card
            item {
                FrostedGlassCard(modifier = Modifier.fillMaxWidth(),
                    borderColor = if (currentStreak > 0) WarmAmber.copy(alpha = 0.5f) else GlassBorder) {
                    Row(modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Current Streak", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥", fontSize = 32.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$currentStreak days", style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Light, color = WarmAmber)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Best", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$longestStreak days", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium, color = LavenderGlow)
                        }
                    }
                }
            }

            // Weekly Heatmap
            item {
                Text("This Week", style = MaterialTheme.typography.titleMedium, color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp))
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        val days = listOf("M", "T", "W", "T", "F", "S", "S")
                        weeklyData.forEachIndexed { index, count ->
                            val intensity = count / 5f
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(when {
                                            count == 5 -> WarmAmber.copy(alpha = intensity)
                                            count >= 3 -> SoftPurple.copy(alpha = intensity)
                                            count > 0 -> LavenderGlow.copy(alpha = intensity * 0.5f)
                                            else -> MidnightBlue
                                        })
                                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (count > 0) {
                                        Text("$count", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(days[index], style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // Monthly Progress
            item {
                Text("Monthly Progress", style = MaterialTheme.typography.titleMedium, color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp))
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Prayer.entries.forEach { prayer ->
                            val progress = monthlyProgress[prayer] ?: 0f
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(prayer.displayName, style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White, modifier = Modifier.width(80.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = SoftPurple, trackColor = MidnightBlue
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                                    modifier = Modifier.width(40.dp))
                            }
                        }
                    }
                }
            }

            // Total
            item {
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$totalPrayers", style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Light, color = SoftPurple)
                            Text("Total Prayers Logged", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            // Motivational
            item {
                val message = when {
                    currentStreak == 0 -> "Every journey begins with a single step. Start your streak today!"
                    currentStreak < 7 -> "Great start! Keep going to build a consistent habit."
                    currentStreak < 14 -> "You're doing amazing! Consistency is key to success."
                    else -> "SubhanAllah! You're on fire! May Allah keep you steadfast."
                }
                FrostedGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = LavenderGlow.copy(alpha = 0.3f)) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💡", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}
