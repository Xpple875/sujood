package com.sujood.app.ui.screens.settings

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
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Light
                    )
                },
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
        AnimatedGradientBackground(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Profile ──
                SettingsSectionHeader("Profile")
                SettingsCard {
                    SettingsClickableItem(
                        icon = Icons.Default.Person,
                        title = "Name",
                        subtitle = settings.name.ifEmpty { "Not set" },
                        onClick = { showNameDialog = true }
                    )
                }

                // ── Location ──
                SettingsSectionHeader("Location")
                SettingsCard {
                    SettingsClickableItem(
                        icon = Icons.Default.LocationOn,
                        title = "Prayer Location",
                        subtitle = if (settings.savedCity.isNotEmpty()) settings.savedCity
                                   else if (settings.savedLatitude != 0.0) "GPS: %.2f°, %.2f°".format(settings.savedLatitude, settings.savedLongitude)
                                   else "Not set — tap to change",
                        onClick = { showCityDialog = true }
                    )
                }

                // ── Prayer Calculation ──
                SettingsSectionHeader("Prayer Settings")
                SettingsCard {
                    SettingsClickableItem(
                        title = "Calculation Method",
                        subtitle = settings.calculationMethod.displayName,
                        onClick = { showMethodDialog = true }
                    )
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Madhab (Asr)",
                        subtitle = settings.madhab.displayName,
                        onClick = { showMadhabDialog = true }
                    )
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Grace Period",
                        subtitle = if (settings.gracePeriodMinutes == 0) "No grace period" else "${settings.gracePeriodMinutes} minutes",
                        onClick = { showGracePeriodDialog = true }
                    )
                }

                // ── Notifications ──
                SettingsSectionHeader("Notifications")
                SettingsCard {
                    Text(
                        text = "Receive a notification when each prayer time arrives",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                    )
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
                                scope.launch {
                                    userPreferences.saveNotificationEnabled(prayer.name, enabled)
                                    rescheduleAlarms(context, userPreferences)
                                }
                            }
                        )
                        if (prayer != Prayer.entries.last()) SettingsDivider()
                    }
                }

                // ── Prayer Lock ──
                SettingsSectionHeader("Prayer Lock")
                SettingsCard {
                    Text(
                        text = "Lock the phone at prayer time and block distractions",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                    )
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
                                scope.launch {
                                    userPreferences.saveLockEnabled(prayer.name, enabled)
                                    rescheduleAlarms(context, userPreferences)
                                }
                            }
                        )
                        if (prayer != Prayer.entries.last()) SettingsDivider()
                    }
                }

                // ── Lock Behavior ──
                SettingsSectionHeader("Lock Behavior")
                SettingsCard {
                    // Lock Mode
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Lock Mode", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LockMode.entries.forEach { mode ->
                                val isSelected = settings.lockMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            scope.launch {
                                                userPreferences.saveLockSettings(mode, settings.lockTriggerMinutes, settings.lockDurationMinutes)
                                            }
                                        }
                                        .background(
                                            if (isSelected) SoftPurple.copy(alpha = 0.2f) else MidnightBlue.copy(alpha = 0.5f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) SoftPurple else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Trigger Timing",
                        subtitle = if (settings.lockTriggerMinutes == 0) "At prayer time"
                                   else "${settings.lockTriggerMinutes} minutes after prayer",
                        onClick = { showLockTriggerDialog = true }
                    )
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Lock Duration",
                        subtitle = "${settings.lockDurationMinutes} minutes",
                        onClick = { showLockDurationDialog = true }
                    )
                }

                // ── Audio & Haptics ──
                SettingsSectionHeader("Audio & Haptics")
                SettingsCard {
                    SettingsToggleItem(
                        title = "Adhan Sound",
                        subtitle = "Play adhan when prayer time arrives",
                        isEnabled = settings.adhanEnabled,
                        onToggle = { enabled ->
                            scope.launch { userPreferences.saveAudioSettings(enabled, settings.vibrationEnabled) }
                        }
                    )
                    SettingsDivider()
                    SettingsToggleItem(
                        title = "Vibration",
                        subtitle = "Vibrate at prayer time",
                        isEnabled = settings.vibrationEnabled,
                        onToggle = { enabled ->
                            scope.launch { userPreferences.saveAudioSettings(settings.adhanEnabled, enabled) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Dialogs
    if (showNameDialog) {
        NameDialog(
            currentName = settings.name,
            onDismiss = { showNameDialog = false },
            onConfirm = { name -> scope.launch { userPreferences.saveUserName(name) }; showNameDialog = false }
        )
    }
    if (showCityDialog) {
        ChangeCityDialog(
            currentCity = settings.savedCity,
            onDismiss = { showCityDialog = false },
            onConfirm = { city ->
                scope.launch {
                    userPreferences.saveLocationSettings(
                        useGps = false, city = city, country = "", latitude = 0.0, longitude = 0.0
                    )
                }
                showCityDialog = false
            },
            onUseGps = {
                scope.launch {
                    userPreferences.saveLocationSettings(useGps = true, city = "", country = "", latitude = 0.0, longitude = 0.0)
                }
                showCityDialog = false
            }
        )
    }
    if (showMethodDialog) {
        CalculationMethodDialog(
            currentMethod = settings.calculationMethod,
            onDismiss = { showMethodDialog = false },
            onSelect = { method -> scope.launch { userPreferences.saveCalculationMethod(method) }; showMethodDialog = false }
        )
    }
    if (showMadhabDialog) {
        MadhabDialog(
            currentMadhab = settings.madhab,
            onDismiss = { showMadhabDialog = false },
            onSelect = { madhab -> scope.launch { userPreferences.saveMadhab(madhab) }; showMadhabDialog = false }
        )
    }
    if (showGracePeriodDialog) {
        GracePeriodDialog(
            currentMinutes = settings.gracePeriodMinutes,
            onDismiss = { showGracePeriodDialog = false },
            onSelect = { minutes -> scope.launch { userPreferences.saveGracePeriod(minutes) }; showGracePeriodDialog = false }
        )
    }
    if (showLockTriggerDialog) {
        TriggerDialog(
            currentMinutes = settings.lockTriggerMinutes,
            onDismiss = { showLockTriggerDialog = false },
            onSelect = { minutes ->
                scope.launch { userPreferences.saveLockSettings(settings.lockMode, minutes, settings.lockDurationMinutes) }
                showLockTriggerDialog = false
            }
        )
    }
    if (showLockDurationDialog) {
        DurationDialog(
            currentMinutes = settings.lockDurationMinutes,
            onDismiss = { showLockDurationDialog = false },
            onSelect = { minutes ->
                scope.launch { userPreferences.saveLockSettings(settings.lockMode, settings.lockTriggerMinutes, minutes) }
                showLockDurationDialog = false
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = SoftPurple,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    FrostedGlassCard(cornerRadius = 20.dp, contentPadding = 0.dp) {
        Column { content() }
    }
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = SoftPurple, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SoftPurple,
                checkedTrackColor = SoftPurple.copy(alpha = 0.4f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = MidnightBlue
            )
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(GlassBorder))
}

// ── Dialogs ──

@Composable
private fun NameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your Name") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SoftPurple, unfocusedBorderColor = GlassBorder)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, colors = ButtonDefaults.buttonColors(containerColor = SoftPurple)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MidnightBlue
    )
}

@Composable
private fun ChangeCityDialog(
    currentCity: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onUseGps: () -> Unit
) {
    var city by remember { mutableStateOf(currentCity) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Location") },
        text = {
            Column {
                OutlinedTextField(
                    value = city, onValueChange = { city = it },
                    label = { Text("City Name") },
                    placeholder = { Text("e.g. Dubai") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SoftPurple, unfocusedBorderColor = GlassBorder,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onUseGps) {
                    Text("Use GPS instead", color = LavenderGlow)
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (city.isNotBlank()) onConfirm(city.trim()) },
                   colors = ButtonDefaults.buttonColors(containerColor = SoftPurple)) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MidnightBlue
    )
}

@Composable
private fun CalculationMethodDialog(currentMethod: CalculationMethod, onDismiss: () -> Unit, onSelect: (CalculationMethod) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calculation Method") },
        text = {
            Column {
                CalculationMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(method) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(method.displayName)
                        if (method == currentMethod) Icon(Icons.Default.Check, null, tint = SoftPurple)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun MadhabDialog(currentMadhab: Madhab, onDismiss: () -> Unit, onSelect: (Madhab) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Madhab") },
        text = {
            Column {
                Madhab.entries.forEach { madhab ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(madhab) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(madhab.displayName)
                        if (madhab == currentMadhab) Icon(Icons.Default.Check, null, tint = SoftPurple)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun GracePeriodDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grace Period") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (minutes == 0) "No grace period" else "$minutes minutes")
                        if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = SoftPurple)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun TriggerDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock Trigger") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (minutes == 0) "At prayer time" else "$minutes minutes after prayer")
                        if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun DurationDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(5, 10, 15, 20, 30, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock Duration") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$minutes minutes")
                        if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

// Helper: re-fetch prayer times with updated settings and reschedule alarms
private suspend fun rescheduleAlarms(
    context: android.content.Context,
    userPreferences: com.sujood.app.data.local.datastore.UserPreferences
) {
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
        val notif = com.sujood.app.domain.model.Prayer.entries.map { p ->
            when (p) {
                com.sujood.app.domain.model.Prayer.FAJR -> settings.fajrNotificationEnabled
                com.sujood.app.domain.model.Prayer.DHUHR -> settings.dhuhrNotificationEnabled
                com.sujood.app.domain.model.Prayer.ASR -> settings.asrNotificationEnabled
                com.sujood.app.domain.model.Prayer.MAGHRIB -> settings.maghribNotificationEnabled
                com.sujood.app.domain.model.Prayer.ISHA -> settings.ishaNotificationEnabled
            }
        }.toBooleanArray()
        val lock = com.sujood.app.domain.model.Prayer.entries.map { p ->
            when (p) {
                com.sujood.app.domain.model.Prayer.FAJR -> settings.fajrLockEnabled
                com.sujood.app.domain.model.Prayer.DHUHR -> settings.dhuhrLockEnabled
                com.sujood.app.domain.model.Prayer.ASR -> settings.asrLockEnabled
                com.sujood.app.domain.model.Prayer.MAGHRIB -> settings.maghribLockEnabled
                com.sujood.app.domain.model.Prayer.ISHA -> settings.ishaLockEnabled
            }
        }.toBooleanArray()
        scheduler.scheduleAllAlarms(prayerTimes, notif, lock, settings.gracePeriodMinutes)
    }
}
