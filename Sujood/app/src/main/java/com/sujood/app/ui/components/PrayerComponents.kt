package com.sujood.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerTime
import com.sujood.app.ui.theme.AsrColor
import com.sujood.app.ui.theme.DhuhrColor
import com.sujood.app.ui.theme.FajrColor
import com.sujood.app.ui.theme.IshaColor
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MaghribColor
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.SuccessGreen
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber

@Composable
fun PrayerTimeCard(
    prayerTime: PrayerTime,
    isCompleted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val prayerColor = getPrayerColor(prayerTime.prayer)
    
    FrostedGlassCard(
        modifier = modifier,
        borderColor = if (isCompleted) SuccessGreen.copy(alpha = 0.5f) else prayerColor.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prayer indicator dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(prayerColor, prayerColor.copy(alpha = 0.5f))
                            )
                        )
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = prayerTime.prayer.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = prayerTime.prayer.arabicName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prayerTime.time,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Light,
                    color = if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.onSurface
                )
                
                if (isCompleted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CountdownTimer(
    nextPrayerName: String,
    timeRemainingMillis: Long,
    modifier: Modifier = Modifier,
    isCompleted: Boolean = false,
    onCompleteClick: () -> Unit = {}
) {
    val hours = (timeRemainingMillis / (1000 * 60 * 60)).toInt()
    val minutes = ((timeRemainingMillis / (1000 * 60)) % 60).toInt()
    val seconds = ((timeRemainingMillis / 1000) % 60).toInt()
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Time for",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = nextPrayerName,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
                color = SoftPurple
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Circular tick box
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) SuccessGreen.copy(alpha = 0.2f) 
                        else Color.White.copy(alpha = 0.1f)
                    )
                    .border(
                        1.5.dp, 
                        if (isCompleted) SuccessGreen else Color.White.copy(alpha = 0.3f), 
                        CircleShape
                    )
                    .clickable { onCompleteClick() },
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Countdown display - Simple text as requested, no boxes
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = WarmAmber
            )
        }
    }
}

@Composable
private fun CountdownUnit(
    value: Int,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MidnightBlue.copy(alpha = 0.8f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = WarmAmber
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

fun getPrayerColor(prayer: Prayer): Color {
    return when (prayer) {
        Prayer.FAJR -> FajrColor
        Prayer.DHUHR -> DhuhrColor
        Prayer.ASR -> AsrColor
        Prayer.MAGHRIB -> MaghribColor
        Prayer.ISHA -> IshaColor
    }
}
