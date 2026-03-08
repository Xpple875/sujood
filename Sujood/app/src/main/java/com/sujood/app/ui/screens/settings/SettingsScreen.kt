package com.sujood.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── Design tokens (matching mockup) ──────────────────────────────────────────
private val BgDark      = Color(0xFF101322)
private val PrimaryBlue = Color(0xFF1132D4)
private val GlassFill   = Color(0xFF1132D4).copy(alpha = 0.05f)
private val GlassDiv    = Color(0xFFFFFFFF).copy(alpha = 0.05f)
private val GlassBrd    = Color(0xFFFFFFFF).copy(alpha = 0.10f)
private val SlateMuted  = Color(0xFF64748B)  // slate-500 for section labels
private val SlateText   = Color(0xFF94A3B8)  // slate-400 for subtitles

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
    var showGracePeriodDialog  by remember { mutableStateOf(false) }
    var showCityDialog         by remember { mutableStateOf(false) }
    var showLockTriggerDialog  by remember { mutableStateOf(false) }
    var showLockDurationDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .background(
                Brush.radialGradient(
                    listOf(PrimaryBlue.copy(alpha = 0.15f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 1000f
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.07f))
                            .border(1.dp, GlassBrd, CircleShape)
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            // ── Profile card ─────────────────────────────────────────────
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp)
                        .clickable { showNameDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar
                        Box(modifier = Modifier.size(72.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue.copy(alpha = 0.20f))
                                    .border(2.dp, PrimaryBlue.copy(alpha = 0.50f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = settings.name.firstOrNull()?.uppercase() ?: "S",
                                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                            // Edit badge
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue)
                                    .border(2.dp, BgDark, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, null,
                                    tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.name.ifEmpty { "Tap to set name" },
                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("sujood@app.com", fontSize = 13.sp, color = SlateText)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(PrimaryBlue.copy(alpha = 0.20f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text("PREMIUM MEMBER", fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold, color = PrimaryBlue,
                                    letterSpacing = 1.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── Location & Prayer ────────────────────────────────────────
            item { SectionLabel("Location & Prayer") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(
                        icon = Icons.Default.LocationOn, title = "Prayer Location",
                        subtitle = when {
                            settings.savedCity.isNotEmpty() -> settings.savedCity
                            settings.savedLatitude != 0.0  -> "GPS Active"
                            else -> "Not set"
                        },
                        trailing = {
                            if (settings.useGpsLocation || settings.savedLatitude != 0.0) {
                                Text("GPS Active", fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
                            } else {
                                Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = { showCityDialog = true }
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.Calculate, title = "Calculation Method",
                        subtitle = settings.calculationMethod.displayName,
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = { showMethodDialog = true }
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.Mosque, title = "Madhab",
                        subtitle = settings.madhab.displayName + " / Standard",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = { showMadhabDialog = true }
                    )
                }
            }

            // ── Notifications ────────────────────────────────────────────
            item { SectionLabel("Notifications") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    // Master toggle — Daily Prayer Alerts (uses Fajr as proxy for "all on")
                    val allOn = settings.fajrNotificationEnabled &&
                                settings.dhuhrNotificationEnabled &&
                                settings.asrNotificationEnabled &&
                                settings.maghribNotificationEnabled &&
                                settings.ishaNotificationEnabled
                    SettingsRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Daily Prayer Alerts",
                        trailing = {
                            Switch(
                                checked = allOn,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        Prayer.entries.forEach { p ->
                                            userPreferences.saveNotificationEnabled(p.name, enabled)
                                        }
                                        rescheduleAlarms(context, userPreferences)
                                    }
                                },
                                colors = blueSwitchColors()
                            )
                        }
                    )
                    GlassDivider()

                    // Per-prayer sound grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val prayers = listOf(
                            Prayer.FAJR    to settings.fajrNotificationEnabled,
                            Prayer.DHUHR   to settings.dhuhrNotificationEnabled,
                            Prayer.ASR     to settings.asrNotificationEnabled,
                            Prayer.MAGHRIB to settings.maghribNotificationEnabled,
                            Prayer.ISHA    to settings.ishaNotificationEnabled
                        )
                        prayers.forEach { (prayer, enabled) ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (enabled) PrimaryBlue.copy(alpha = 0.20f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        1.dp,
                                        if (enabled) PrimaryBlue.copy(alpha = 0.40f) else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        scope.launch {
                                            userPreferences.saveNotificationEnabled(prayer.name, !enabled)
                                            rescheduleAlarms(context, userPreferences)
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(prayer.displayName, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold, color = Color.White)
                                Icon(
                                    imageVector = if (enabled) Icons.Default.VolumeUp else Icons.Default.Vibration,
                                    contentDescription = null,
                                    tint = if (enabled) PrimaryBlue else SlateText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    GlassDivider()

                    // Adhan sound row
                    SettingsRow(
                        icon = Icons.Default.MusicNote,
                        title = "Adhan Sound",
                        subtitle = "Mishary Al-Afasy",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) }
                    )
                    GlassDivider()

                    // Vibration toggle
                    SettingsRow(
                        icon = Icons.Default.Vibration,
                        title = "Vibration",
                        trailing = {
                            Switch(
                                checked = settings.vibrationEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { userPreferences.saveAudioSettings(settings.adhanEnabled, enabled) }
                                },
                                colors = blueSwitchColors()
                            )
                        }
                    )
                }
            }

            // ── Audio & Haptics ──────────────────────────────────────────
            item { SectionLabel("Audio & Haptics") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(
                        icon = Icons.Default.VolumeUp,
                        title = "Sound Effects",
                        trailing = {
                            Switch(
                                checked = settings.adhanEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { userPreferences.saveAudioSettings(enabled, settings.vibrationEnabled) }
                                },
                                colors = blueSwitchColors()
                            )
                        }
                    )
                }
            }

            // ── App Preferences ──────────────────────────────────────────
            item { SectionLabel("App Preferences") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(
                        icon = Icons.Default.Language,
                        title = "Language",
                        trailing = { Text("English (US)", fontSize = 12.sp, color = SlateText) }
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.FileUpload,
                        title = "Data Backup",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // ── Data Management ──────────────────────────────────────────
            item { SectionLabel("Data Management") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(
                        icon = Icons.Default.FileUpload,
                        title = "Export Prayer History",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) }
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.DeleteSweep,
                        title = "Clear Cache",
                        trailing = { Text("42 MB", fontSize = 12.sp, color = SlateMuted) }
                    )
                }
            }

            // ── About ────────────────────────────────────────────────────
            item { SectionLabel("About") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "App Version",
                        trailing = { Text("v1.0.0", fontSize = 12.sp, color = SlateText) }
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.SupportAgent,
                        title = "Support & Feedback",
                        trailing = { Icon(Icons.Default.OpenInNew, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:support@sujood.app"))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // ── Sign Out button ──────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.20f), CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.05f))
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                            .clickable { /* sign-out action — wired when auth is added */ }
                    ) {
                        Text("Sign Out", fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444).copy(alpha = 0.80f))
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
        ChangeCityDialog(settings.savedCity, { showCityDialog = false },
            { city -> scope.launch { userPreferences.saveLocationSettings(false, city, "", 0.0, 0.0) }; showCityDialog = false },
            { scope.launch { userPreferences.saveLocationSettings(true, "", "", 0.0, 0.0) }; showCityDialog = false }
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
    if (showGracePeriodDialog) {
        GracePeriodDialog(settings.gracePeriodMinutes, { showGracePeriodDialog = false }) { minutes ->
            scope.launch { userPreferences.saveGracePeriod(minutes) }; showGracePeriodDialog = false
        }
    }
    if (showLockTriggerDialog) {
        TriggerDialog(settings.lockTriggerMinutes, { showLockTriggerDialog = false }) { minutes ->
            scope.launch { userPreferences.saveLockSettings(settings.lockMode, minutes, settings.lockDurationMinutes) }
            showLockTriggerDialog = false
        }
    }
    if (showLockDurationDialog) {
        DurationDialog(settings.lockDurationMinutes, { showLockDurationDialog = false }) { minutes ->
            scope.launch { userPreferences.saveLockSettings(settings.lockMode, settings.lockTriggerMinutes, minutes) }
            showLockDurationDialog = false
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = SlateMuted,
        modifier = Modifier
            .padding(horizontal = 26.dp)
            .padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, GlassBrd, RoundedCornerShape(16.dp))
    ) { content() }
}

@Composable
private fun GlassDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlassDiv)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Blue icon square
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaryBlue.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = SlateText, modifier = Modifier.padding(top = 1.dp))
            }
        }

        trailing?.invoke()
    }
}

@Composable
private fun blueSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor  = Color.White,
    checkedTrackColor  = PrimaryBlue,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color(0xFF334155)  // slate-700
)

// ── Dialogs (all functionality preserved) ────────────────────────────────────

@Composable
private fun NameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your Name") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = GlassBorder)) },
        confirmButton = { Button(onClick = { onConfirm(name) }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = MidnightBlue)
}

@Composable
private fun ChangeCityDialog(currentCity: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, onUseGps: () -> Unit) {
    var city by remember { mutableStateOf(currentCity) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Change Location") },
        text = { Column { OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City Name") }, placeholder = { Text("e.g. Dubai") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = GlassBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)); Spacer(Modifier.height(12.dp)); TextButton(onClick = onUseGps) { Text("Use GPS instead", color = LavenderGlow) } } },
        confirmButton = { Button(onClick = { if (city.isNotBlank()) onConfirm(city.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = MidnightBlue)
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
private fun GracePeriodDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Grace Period") },
        text = { Column { options.forEach { m -> Row(Modifier.fillMaxWidth().clickable { onSelect(m) }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(if (m == 0) "No grace period" else "$m minutes"); if (m == currentMinutes) Icon(Icons.Default.Check, null, tint = PrimaryBlue) } } } },
        confirmButton = {}, containerColor = MidnightBlue)
}

@Composable
private fun TriggerDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Lock Trigger") },
        text = { Column { options.forEach { m -> Row(Modifier.fillMaxWidth().clickable { onSelect(m) }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(if (m == 0) "At prayer time" else "$m min after prayer"); if (m == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber) } } } },
        confirmButton = {}, containerColor = MidnightBlue)
}

@Composable
private fun DurationDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(5, 10, 15, 20, 30, 60)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Lock Duration") },
        text = { Column { options.forEach { m -> Row(Modifier.fillMaxWidth().clickable { onSelect(m) }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("$m minutes"); if (m == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber) } } } },
        confirmButton = {}, containerColor = MidnightBlue)
}

private suspend fun rescheduleAlarms(
    context: android.content.Context,
    userPreferences: UserPreferences
) {
    val settings   = userPreferences.userSettings.first()
    val app        = context.applicationContext as SujoodApplication
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
