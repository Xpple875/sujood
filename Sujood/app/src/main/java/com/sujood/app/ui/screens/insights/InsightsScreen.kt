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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.domain.model.Prayer
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlin.math.min

@Composable
fun InsightsScreen() {
    val currentStreak = 5
    val longestStreak = 12
    val totalPrayers = 156
    val weeklyData = listOf(5, 4, 5, 3, 5, 4, 5) // 7 days
    val monthlyProgress = mapOf(
        Prayer.FAJR to 0.85f,
        Prayer.DHUHR to 0.92f,
        Prayer.ASR to 0.78f,
        Prayer.MAGHRIB to 0.95f,
        Prayer.ISHA to 0.88f
    )

    AnimatedGradientBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light,
                    color = Color.White
                )
            }

            // Streak Card
            item {
                FrostedGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (currentStreak > 0) WarmAmber.copy(alpha = 0.5f) else GlassBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Streak",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥", fontSize = 32.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$currentStreak days",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Light,
                                    color = WarmAmber
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Best",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$longestStreak days",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                                color = LavenderGlow
                            )
                        }
                    }
                }
            }

            // Weekly Heatmap
            item {
                Text(
                    text = "This Week",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val days = listOf("M", "T", "W", "T", "F", "S", "S")
                        weeklyData.forEachIndexed { index, count ->
                            val intensity = count / 5f
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                count == 5 -> WarmAmber.copy(alpha = intensity)
                                                count >= 3 -> SoftPurple.copy(alpha = intensity)
                                                count > 0 -> LavenderGlow.copy(alpha = intensity * 0.5f)
                                                else -> MidnightBlue
                                            }
                                        )
                                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (count > 0) {
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = days[index],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Monthly Progress
            item {
                Text(
                    text = "Monthly Progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Prayer.entries.forEach { prayer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = prayer.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.width(80.dp)
                                )
                                
                                LinearProgressIndicator(
                                    progress = monthlyProgress[prayer] ?: 0f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = SoftPurple,
                                    trackColor = MidnightBlue
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Text(
                                    text = "${((monthlyProgress[prayer] ?: 0f) * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.width(40.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Total Stats
            item {
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalPrayers",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Light,
                                color = SoftPurple
                            )
                            Text(
                                text = "Total Prayers",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Motivational Message
            item {
                val message = when {
                    currentStreak == 0 -> "Every journey begins with a single step. Start your streak today!"
                    currentStreak < 7 -> "Great start! Keep going to build a consistent habit."
                    currentStreak < 14 -> "You're doing amazing! Consistency is key to success."
                    else -> "SubhanAllah! You're on fire! May Allah keep you steadfast."
                }
                
                FrostedGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = LavenderGlow.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "💡",
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
