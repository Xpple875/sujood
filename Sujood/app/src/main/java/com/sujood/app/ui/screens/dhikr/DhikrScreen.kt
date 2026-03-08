package com.sujood.app.ui.screens.dhikr

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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
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
                                                AppIconBox(packageName = app.packageName, appName = app.name, isLocked = isLocked)
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
private fun AppIconBox(packageName: String, appName: String, isLocked: Boolean) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null }
    }
    val bgColor = if (isLocked) PrimaryBlue.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f)
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            val bmp = remember(icon) {
                android.graphics.Bitmap.createScaledBitmap(
                    icon.toBitmap(), 72, 72, true
                ).asImageBitmap()
            }
            androidx.compose.foundation.Image(
                bitmap = bmp,
                contentDescription = appName,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
            )
        } else {
            // Fallback: brand-coloured initial
            val fallbackBg = when (appName.first().uppercaseChar()) {
                'T' -> if (appName.contains("Tik")) Color(0xFF010101) else Color(0xFF1DA1F2)
                'I' -> Color(0xFFE1306C)
                'Y' -> Color(0xFFFF0000)
                'S' -> Color(0xFFFFFC00)
                'F' -> Color(0xFF1877F2)
                'W' -> Color(0xFF25D366)
                'R' -> Color(0xFFFF4500)
                'N' -> Color(0xFFE50914)
                else -> PrimaryBlue
            }
            Box(modifier = Modifier.fillMaxSize().background(fallbackBg, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                Text(appName.first().uppercase(), fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (appName.startsWith("S") && appName.contains("nap")) Color.Black else Color.White)
            }
        }
    }
}

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
