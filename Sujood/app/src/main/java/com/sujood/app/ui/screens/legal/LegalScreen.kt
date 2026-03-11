package com.sujood.app.ui.screens.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgTop      = Color(0xFF0A0E1A)
private val BgBottom   = Color(0xFF0D1230)
private val CardBg     = Color(0xFF111827)
private val AccentBlue = Color(0xFF3B82F6)
private val TextMain   = Color(0xFFE2E8F0)
private val TextMuted  = Color(0xFF64748B)

enum class LegalType { PRIVACY_POLICY, TERMS_OF_SERVICE }

data class LegalSection(val heading: String, val body: String)

private val PRIVACY_SECTIONS = listOf(
    LegalSection(
        "Overview",
        "Sujood is committed to protecting your privacy. " +
        "This Privacy Policy explains how we collect, use, and safeguard your information " +
        "when you use the Sujood prayer app. By using the app you agree to this policy."
    ),
    LegalSection(
        "Information We Collect",
        "Account information: When you sign in with Google we receive your name, email address, " +
        "and profile photo from Google. We use this only to personalise your experience.\n\n" +
        "Location data: If you grant location permission, we use your GPS coordinates " +
        "solely to calculate accurate prayer times. Coordinates are stored locally on " +
        "your device and are never transmitted to our servers.\n\n" +
        "Prayer activity: Your prayer log is stored locally on your device to power the " +
        "Insights screen. This data does not leave your device.\n\n" +
        "App preferences: Settings such as calculation method, notification preferences, " +
        "and prayer lock configuration are stored locally using Android DataStore."
    ),
    LegalSection(
        "How We Use Your Information",
        "- Display your name and photo within the app\n" +
        "- Calculate accurate prayer times based on your location\n" +
        "- Track your prayer completion for personal insights\n" +
        "- Send local notifications for prayer times\n" +
        "- Restore premium purchase status across reinstalls"
    ),
    LegalSection(
        "Data Storage and Security",
        "All personal data including location, prayer logs, and settings is stored " +
        "exclusively on your device. We do not operate servers that store your personal information.\n\n" +
        "Authentication is handled by Google Firebase, subject to Google's own privacy policy. " +
        "We implement HTTPS-only network communication and Android's secure DataStore."
    ),
    LegalSection(
        "Third-Party Services",
        "Aladhan API: We send your location to aladhan.com to retrieve prayer times. " +
        "No personally identifiable information beyond location is included.\n\n" +
        "Google Firebase Authentication: Used for Google Sign-In. Subject to Google's Privacy Policy.\n\n" +
        "Google Play Billing: Used for the optional premium purchase. Payment is handled " +
        "entirely by Google Play and never seen by us."
    ),
    LegalSection(
        "Children's Privacy",
        "Sujood is not directed at children under 13. We do not knowingly collect personal " +
        "information from children under 13. Contact us if you believe a child has provided " +
        "us with personal information and we will delete it promptly."
    ),
    LegalSection(
        "Your Rights",
        "You may delete your account and all associated data at any time by signing out " +
        "and uninstalling the app. Since all personal data is stored locally, uninstalling " +
        "removes all your data from your device.\n\n" +
        "For questions contact us at privacy@sujood.app."
    ),
    LegalSection(
        "Contact",
        "Email: privacy@sujood.app\nApp: Sujood - Prayer Times and Lock"
    )
)

private val TERMS_SECTIONS = listOf(
    LegalSection(
        "Acceptance of Terms",
        "By downloading or using Sujood you agree to be bound by these Terms of Service. " +
        "If you do not agree to these terms, please do not use the app."
    ),
    LegalSection(
        "Description of Service",
        "Sujood is a Muslim prayer companion app providing accurate prayer times, " +
        "a Qibla compass, prayer tracking, Dhikr reminders, and an optional prayer lock feature. " +
        "The app is available in free and premium versions."
    ),
    LegalSection(
        "Premium Purchase",
        "The optional Premium Supporter purchase is a one-time payment of 2.99 USD " +
        "(or equivalent in your local currency) that unlocks an ad-free experience.\n\n" +
        "Purchases are processed by Google Play and subject to Google Play's refund policy. " +
        "Premium status can be restored on any device signed into the same account " +
        "via Restore Purchase in the app."
    ),
    LegalSection(
        "Acceptable Use",
        "You agree not to:\n" +
        "- Reverse engineer or decompile the app\n" +
        "- Use the app for any unlawful purpose\n" +
        "- Attempt to interfere with the app's functionality\n" +
        "- Misrepresent your identity when using Google Sign-In"
    ),
    LegalSection(
        "Prayer Time Accuracy",
        "Prayer times are calculated using the Aladhan API based on your location and " +
        "selected calculation method. While we strive for accuracy, prayer times are " +
        "approximations. We recommend verifying times with your local mosque."
    ),
    LegalSection(
        "Prayer Lock Feature",
        "The prayer lock feature is a voluntary productivity tool requiring the " +
        "Display over other apps permission. We are not responsible for any consequences " +
        "arising from use of this feature including missed communications during an active session."
    ),
    LegalSection(
        "Disclaimer of Warranties",
        "Sujood is provided as-is without warranties of any kind. We do not guarantee " +
        "that the app will be error-free, uninterrupted, or that prayer times will be " +
        "100% accurate for all calculation methods and locations."
    ),
    LegalSection(
        "Limitation of Liability",
        "To the maximum extent permitted by law, we shall not be liable for any indirect, " +
        "incidental, or consequential damages arising from your use of Sujood."
    ),
    LegalSection(
        "Contact",
        "Email: support@sujood.app\nApp: Sujood - Prayer Times and Lock"
    )
)

@Composable
fun LegalScreen(
    type: LegalType,
    onBack: () -> Unit
) {
    val title = when (type) {
        LegalType.PRIVACY_POLICY   -> "Privacy Policy"
        LegalType.TERMS_OF_SERVICE -> "Terms of Service"
    }
    val sections = when (type) {
        LegalType.PRIVACY_POLICY   -> PRIVACY_SECTIONS
        LegalType.TERMS_OF_SERVICE -> TERMS_SECTIONS
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // Top bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Header card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    AccentBlue.copy(alpha = 0.15f),
                                    Color(0xFF1E3A8A).copy(alpha = 0.2f)
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Last updated: March 2026",
                            fontSize = 12.sp,
                            color = AccentBlue.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (type == LegalType.PRIVACY_POLICY)
                                "Your privacy matters to us. This document explains how Sujood handles your data."
                            else
                                "Please read these terms carefully before using Sujood.",
                            fontSize = 13.sp,
                            color = TextMuted,
                            lineHeight = 18.sp
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Sections
            items(sections) { section ->
                LegalSectionCard(section)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun LegalSectionCard(section: LegalSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(CardBg, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 18.dp)
                    .background(AccentBlue, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = section.heading,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = section.body,
            fontSize = 13.sp,
            color = TextMain.copy(alpha = 0.75f),
            lineHeight = 20.sp
        )
    }
}
