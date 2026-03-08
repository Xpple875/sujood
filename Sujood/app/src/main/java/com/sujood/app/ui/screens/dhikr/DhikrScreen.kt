package com.sujood.app.ui.screens.dhikr

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.domain.model.Dhikr
import com.sujood.app.domain.model.LockMode
import com.sujood.app.domain.model.UserSettings
import com.sujood.app.domain.model.defaultDhikrList
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DhikrScreen() {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val settings by userPreferences.userSettings.collectAsState(initial = UserSettings())
    
    var showOverlayPreview by remember { mutableStateOf(false) }

    if (showOverlayPreview) {
        OverlayPreviewScreen(onDismiss = { showOverlayPreview = false })
        return
    }

    AnimatedGradientBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Header ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var showHelp by remember { mutableStateOf(false) }
                    
                    if (showHelp) {
                        HelpOverlay(onDismiss = { showHelp = false })
                    }

                    Box(modifier = Modifier.size(24.dp)) // Spacer for symmetry

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = SoftPurple,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Prayer Lock",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Light,
                            color = Color.White
                        )
                        Text(
                            text = "Focus. Pray. Return.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = "Help",
                        tint = LavenderGlow,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { showHelp = true }
                    )
                }
            }

            // ── Lock Control Card ──
            item {
                FrostedGlassCard(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    cornerRadius = 24.dp
                ) {
                    Column {
                        // Mode selector
                        Text(
                            text = "LOCK MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LockMode.entries.forEach { mode ->
                                val isSelected = settings.lockMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) SoftPurple.copy(alpha = 0.25f)
                                            else MidnightBlue.copy(alpha = 0.4f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) SoftPurple else GlassBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                userPreferences.saveLockSettings(
                                                    mode,
                                                    settings.lockTriggerMinutes,
                                                    settings.lockDurationMinutes
                                                )
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = null,
                                            tint = if (isSelected) SoftPurple else TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = mode.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) Color.White else TextSecondary,
                                            textAlign = TextAlign.Center,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── App Selection (Only if App Overlay Mode) ──
            if (settings.lockMode == LockMode.APP_OVERLAY) {
                item {
                    AppSelectorCard(
                        selectedApps = settings.lockedAppsPackageNames,
                        onAppsChanged = { newApps ->
                            CoroutineScope(Dispatchers.IO).launch {
                                userPreferences.saveLockBehavior(settings.minLockDurationMinutes, newApps)
                            }
                        }
                    )
                }
            }

            // ── Trigger timing ──
            item {
                FrostedGlassCard(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    cornerRadius = 24.dp
                ) {
                    Column {
                        Text(
                            text = "TRIGGER TIMING",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val triggerOptions = listOf(0, 5, 10, 15, 30)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            triggerOptions.forEach { minutes ->
                                val isSelected = settings.lockTriggerMinutes == minutes
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) WarmAmber.copy(alpha = 0.2f)
                                            else MidnightBlue.copy(alpha = 0.4f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) WarmAmber.copy(alpha = 0.7f) else GlassBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                userPreferences.saveLockSettings(
                                                    settings.lockMode, minutes, settings.lockDurationMinutes
                                                )
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (minutes == 0) "Now" else "+${minutes}m",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) WarmAmber else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (settings.lockTriggerMinutes == 0) "Lock activates at prayer time"
                                   else "Lock activates ${settings.lockTriggerMinutes} minutes after prayer time",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // ── Minimum Prayer Duration ──
            item {
                FrostedGlassCard(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    cornerRadius = 24.dp
                ) {
                    Column {
                        Text(
                            text = "MINIMUM PRAYER DURATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val durationOptions = listOf(0, 2, 5, 10)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            durationOptions.forEach { minutes ->
                                val isSelected = settings.minLockDurationMinutes == minutes
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) LavenderGlow.copy(alpha = 0.2f)
                                            else MidnightBlue.copy(alpha = 0.4f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) LavenderGlow.copy(alpha = 0.7f) else GlassBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                userPreferences.saveLockBehavior(minutes, settings.lockedAppsPackageNames)
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (minutes == 0) "None" else "${minutes}m",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) LavenderGlow else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (settings.minLockDurationMinutes == 0) "No minimum duration enforced"
                                   else "Lock cannot be dismissed for at least ${settings.minLockDurationMinutes} minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // ── Preview Button ──
            item {
                FrostedGlassCard(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    cornerRadius = 24.dp,
                    borderColor = LavenderGlow.copy(alpha = 0.4f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = LavenderGlow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Preview Lock Screen",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "See what the overlay looks like when a prayer time arrives",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showOverlayPreview = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SoftPurple
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Show Preview", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ── Divider ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(GlassBorder))
                    Text(
                        text = "  Tasbih  ",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(GlassBorder))
                }
            }

            // ── Tasbih Section ──
            item { TasbihSection(context) }
        }
    }
}

@Composable
private fun TasbihSection(context: Context) {
    var selectedDhikr by remember { mutableStateOf(defaultDhikrList[0]) }
    var currentCount by remember { mutableIntStateOf(0) }
    var isCompleted by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isCompleted) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // Dhikr card display
    FrostedGlassCard(
        modifier = Modifier.padding(horizontal = 20.dp),
        cornerRadius = 28.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = selectedDhikr.arabic,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = selectedDhikr.transliteration,
                style = MaterialTheme.typography.titleMedium,
                color = LavenderGlow,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedDhikr.meaning,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Dhikr selector
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(defaultDhikrList) { dhikr ->
            DhikrSelectionItem(
                dhikr = dhikr,
                isSelected = selectedDhikr.id == dhikr.id,
                onSelect = {
                    selectedDhikr = dhikr
                    currentCount = 0
                    isCompleted = false
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(36.dp))

    // Counter circle
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(MidnightBlue.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
                .border(1.dp, GlassBorder.copy(alpha = 0.2f), CircleShape)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    if (currentCount < selectedDhikr.targetCount) {
                        vibrate()
                        currentCount++
                        if (currentCount >= selectedDhikr.targetCount) isCompleted = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentCount.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.ExtraLight,
                    color = if (isCompleted) WarmAmber else Color.White
                )
                Text(
                    text = "/ ${selectedDhikr.targetCount}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
            }
        }

        Canvas(modifier = Modifier.size(240.dp)) {
            val progress = currentCount.toFloat() / selectedDhikr.targetCount
            // Background ring
            drawArc(
                color = Color.White.copy(alpha = 0.06f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
            // Progress ring
            if (progress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            if (isCompleted) WarmAmber else SoftPurple,
                            if (isCompleted) WarmAmber.copy(alpha = 0.6f) else LavenderGlow
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }

    if (currentCount > 0) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            FrostedGlassCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable {
                        currentCount = 0
                        isCompleted = false
                    },
                cornerRadius = 24.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Reset Counter",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun DhikrSelectionItem(dhikr: Dhikr, isSelected: Boolean, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSelected) SoftPurple.copy(alpha = 0.25f) else MidnightBlue.copy(alpha = 0.3f))
                .border(1.dp, if (isSelected) SoftPurple else GlassBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, null, tint = SoftPurple, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = dhikr.name.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dhikr.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Color.White else TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// ── Overlay Preview Screen ──
@Composable
fun OverlayPreviewScreen(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A1F),
                        Color(0xFF1A0A2E),
                        Color(0xFF0A0A1F)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Decorative glow
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(SoftPurple.copy(alpha = 0.3f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = LavenderGlow,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "الله أكبر",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Light
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Time for Asr Prayer",
                style = MaterialTheme.typography.headlineMedium,
                color = LavenderGlow,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Light
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "04:32 PM",
                style = MaterialTheme.typography.displaySmall,
                color = WarmAmber,
                fontWeight = FontWeight.ExtraLight
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "\"Indeed, prayer has been decreed upon the believers at specified times.\"",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Text(
                text = "— Quran 4:103",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(64.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("I've Prayed ✓", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "← Dismiss Preview",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 16.dp).clickable { onDismiss() }
            )
        }
    }
}

@Composable
private fun HelpOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { onDismiss() } // Dimiss on click outside
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        FrostedGlassCard(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            cornerRadius = 32.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.End
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SoftPurple,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "How it works",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                HelpItem(
                    title = "Aggressive Lock",
                    desc = "Whole Phone mode blocks everything except the Sujood app until you finish praying."
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                HelpItem(
                    title = "App Overlay",
                    desc = "Locks specific distracting apps (like TikTok or Instagram) when it's prayer time."
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                HelpItem(
                    title = "Minimum Duration",
                    desc = "The lock stays active for at least 5 minutes to ensure you actually prayed."
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SoftPurple)
                ) {
                    Text("Got it")
                }
            }
        }
    }
}

@Composable
private fun HelpItem(title: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = WarmAmber)
        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun AppSelectorCard(selectedApps: String, onAppsChanged: (String) -> Unit) {
    val commonApps = listOf(
        "com.zhiliaoapp.musically" to "TikTok",
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
        "com.twitter.android" to "X (Twitter)",
        "com.snapchat.android" to "Snapchat",
        "com.google.android.youtube" to "YouTube"
    )
    
    val selectedList = remember(selectedApps) { selectedApps.split(",").filter { it.isNotEmpty() } }

    FrostedGlassCard(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        cornerRadius = 24.dp
    ) {
        Column {
            Text(
                text = "BLOCK SPECIFIC APPS",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            commonApps.forEach { (pkg, name) ->
                val isBlocked = selectedList.contains(pkg)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newList = if (isBlocked) {
                                selectedList.filter { it != pkg }
                            } else {
                                selectedList + pkg
                            }
                            onAppsChanged(newList.joinToString(","))
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = name, color = if (isBlocked) WarmAmber else Color.White)
                    Switch(
                        checked = isBlocked,
                        onCheckedChange = { 
                            val newList = if (it) selectedList + pkg else selectedList.filter { it != pkg }
                            onAppsChanged(newList.joinToString(","))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WarmAmber,
                            checkedTrackColor = WarmAmber.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }
}
