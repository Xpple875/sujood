package com.sujood.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sujood.app.domain.model.Prayer
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.delay

@Composable
fun GreetingSection(
    userName: String,
    nextPrayer: Prayer?,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { -50 }
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Assalamu Alaikum",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = LavenderGlow
            )
            
            if (userName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
            }
            // "Time for X" removed here — shown by CountdownTimer below the sun arc
        }
    }
}

@Composable
fun DailyQuoteCard(
    modifier: Modifier = Modifier
) {
    val quotes = listOf(
        "Indeed, those who have believed and done righteous deeds - the Most Merciful will appoint for them affection. (9)",
        "The prayer is a cure for many diseases. (9)",
        "Whoever prays fajr, he is in Allah's protection. (9)",
        "Paradise has eight gates, and the middle gate is the gate of gratitude. (9)",
        "The best of people are those who are most beneficial to people. (9)",
        "He who thanks people, thanks Allah. (9)",
        "Seek knowledge from the cradle to the grave. (9)",
        "The strong person is not the one who overcomes others, but the one who controls himself in anger. (9)",
        "No one truly knows a nation until one has been inside its prisons. (9)",
        "He who wakes up for Fajr is under the protection of Allah. (9)"
    )
    
    // Get quote based on day of year
    val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    val quote = quotes[dayOfYear % quotes.size]
    val isHadith = quote.contains("(9)").let { true }

    FrostedGlassCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = WarmAmber,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.size(12.dp))
            
            Column {
                Text(
                    text = if (isHadith) "Hadith of the Day" else "Verse of the Day",
                    style = MaterialTheme.typography.labelMedium,
                    color = SoftPurple
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = quote.replace("(9)", "").trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun PrayerStreakCard(
    streakDays: Int,
    completedPrayersToday: List<Prayer>,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 50 }
    ) {
        FrostedGlassCard(
            modifier = modifier,
            cornerRadius = 16.dp,
            borderColor = if (streakDays > 0) WarmAmber.copy(alpha = 0.5f) else Color.Unspecified
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Prayer Streak",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "glow")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )
                        
                        Text(
                            text = streakDays.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Light,
                            color = if (streakDays > 0) WarmAmber.copy(alpha = alpha.coerceIn(0f, 1f)) else TextSecondary
                        )
                        
                        Text(
                            text = " days",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                
                // Progress indicator
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row {
                        Prayer.entries.forEach { prayer ->
                            val isCompleted = completedPrayersToday.contains(prayer)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCompleted) WarmAmber
                                        else Color.White.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }
                    
                    Text(
                        text = "${completedPrayersToday.size}/5",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
