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

private val BgTop     = Color(0xFF0A0E1A)
private val BgBottom  = Color(0xFF0D1230)
private val CardBg    = Color(0xFF111827)
private val CardBorder = Color(0xFF1E2A45)
private val AccentBlue = Color(0xFF3B82F6)
private val TextMain  = Color(0xFFE2E8F0)
private val TextMuted = Color(0xFF64748B)

enum class LegalType { PRIVACY_POLICY, TERMS_OF_SERVICE }

data class LegalSection(val heading: String, val body: String)

private val PRIVACY_SECTIONS = listOf(
    LegalSection(
        "Overview",
        "Sujood ("we", "our", or "us") is committed to protecting your privacy. " +
        "This Privacy Policy explains how we collect, use, and safeguard your information " +
        "when you use the Sujood prayer app. By using the app you agree to this policy."
    ),
    LegalSection(
        "Information We Collect",
        "Account information: When you sign in with Google we receive your name, email address, " +
        "and profile photo from Google. We use this only to personalise your experience.

" +
        "Location data: If you grant location permission, we use your device GPS coordinates " +
        "solely to calculate accurate prayer times for your area. Coordinates are stored " +
        "locally on your device and are never transmitted to our servers.

" +
        "Prayer activity: We store your prayer log (which prayers you marked as completed) " +
        "locally on your device to power the Insights screen. This data does not leave your device.

" +
        "App preferences: Settings such as calculation method, notification preferences, and " +
        "prayer lock configuration are stored locally on your device using Android DataStore."
    ),
    LegalSection(
        "How We Use Your Information",
        "• Display your name and photo within the app
" +
        "• Calculate accurate prayer times based on your location
" +
        "• Track your prayer completion for personal insights
" +
        "• Send local notifications for prayer times (never remotely triggered)
" +
        "• Restore premium purchase status across reinstalls"
    ),
    LegalSection(
        "Data Storage & Security",
        "All personal data — location, prayer logs, and settings — is stored exclusively " +
        "on your device. We do not operate servers that store your personal information.

" +
        "Authentication is handled by Google Firebase, which is subject to Google's own " +
        "privacy policy. We only receive the basic profile information Google provides " +
        "during sign-in.

" +
        "We implement industry-standard security measures including HTTPS-only network " +
        "communication and Android's secure DataStore for local storage."
    ),
    LegalSection(
        "Third-Party Services",
        "Aladhan API (aladhan.com): We send your GPS coordinates or city name to the " +
        "Aladhan API to retrieve prayer times. No personally identifiable information is " +
        "included in these requests beyond location data.

" +
        "Google Firebase Authentication: Used for Google Sign-In. Subject to Google's " +
        "Privacy Policy at policies.google.com/privacy.

" +
        "Google Play Billing: Used to process the optional one-time premium purchase. " +
        "Payment information is handled entirely by Google Play and never seen by us."
    ),
    LegalSection(
        "Children's Privacy",
        "Sujood is not directed at children under 13. We do not knowingly collect personal " +
        "information from children under 13. If you believe a child has provided us with " +
        "personal information please contact us and we will delete it promptly."
    ),
    LegalSection(
        "Your Rights",
        "You may delete your account and all associated data at any time by signing out " +
        "and uninstalling the app. Since all personal data is stored locally, uninstalling " +
        "the app removes all your data from your device.

" +
        "For questions about your data or to exercise any privacy rights, contact us at " +
        "privacy@sujood.app."
    ),
    LegalSection(
        "Changes to This Policy",
        "We may update this Privacy Policy from time to time. We will notify you of any " +
        "material changes by updating the "Last updated" date below. Continued use of " +
        "the app after changes constitutes acceptance of the updated policy."
    ),
    LegalSection(
        "Contact",
        "If you have any questions about this Privacy Policy, please contact us:

" +
        "Email: privacy@sujood.app
" +
        "App: Sujood — Prayer Times & Lock"
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
        "Sujood is a Muslim prayer companion app that provides accurate prayer times, " +
        "a Qibla compass, prayer tracking, Dhikr reminders, and an optional prayer lock " +
        "feature. The app is available in free and premium versions."
    ),
    LegalSection(
        "Premium Purchase",
        "The optional Premium Supporter purchase is a one-time payment of \$2.99 USD " +
        "(or equivalent in your local currency) that unlocks an ad-free experience and " +
        "supports continued development.

" +
        "Purchases are processed by Google Play and are subject to Google Play's refund " +
        "policy. The premium status is tied to your Google Play account and can be " +
        "restored on any device signed into the same account via the "Restore Purchase" " +
        "option in the app."
    ),
    LegalSection(
        "Acceptable Use",
        "You agree not to:
" +
        "• Reverse engineer, decompile, or disassemble the app
" +
        "• Use the app for any unlawful purpose
" +
        "• Attempt to interfere with the app's functionality
" +
        "• Misrepresent your identity when using Google Sign-In"
    ),
    LegalSection(
        "Prayer Time Accuracy",
        "Prayer times are calculated using the Aladhan API based on your location and " +
        "selected calculation method. While we strive for accuracy, prayer times are " +
        "approximations. We recommend verifying times with your local mosque for " +
        "religious obligations."
    ),
    LegalSection(
        "Prayer Lock Feature",
        "The prayer lock feature is a voluntary productivity tool. It requires the " +
        ""Display over other apps" permission to function. We are not responsible " +
        "for any consequences arising from use of this feature including missed " +
        "communications during an active lock session."
    ),
    LegalSection(
        "Disclaimer of Warranties",
        "Sujood is provided "as is" without warranties of any kind. We do not guarantee " +
        "that the app will be error-free, uninterrupted, or that prayer times will be " +
        "100% accurate for all calculation methods and locations."
    ),
    LegalSection(
        "Limitation of Liability",
        "To the maximum extent permitted by law, we shall not be liable for any indirect, " +
        "incidental, or consequential damages arising from your use of Sujood."
    ),
    LegalSection(
        "Changes to Terms",
        "We reserve the right to modify these terms at any time. Material changes will " +
        "be communicated through the app. Continued use after changes constitutes " +
        "acceptance of the new terms."
    ),
    LegalSection(
        "Contact",
        "For questions about these Terms of Service:

" +
        "Email: support@sujood.app
" +
        "App: Sujood — Prayer Times & Lock"
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
    val lastUpdated = "Last updated: March 2026"
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
            // ── Top bar ──────────────────────────────────────────────────────
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

            // ── Header card ──────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(AccentBlue.copy(alpha = 0.15f), Color(0xFF1E3A8A).copy(alpha = 0.2f))
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
                            text = lastUpdated,
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

            // ── Sections ─────────────────────────────────────────────────────
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
            .then(
                Modifier.background(
                    Color.Transparent,
                    RoundedCornerShape(14.dp)
                )
            )
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
