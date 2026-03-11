package com.sujood.app.ui.screens.monetization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch

// Design tokens matching the mockup
private val BgTop        = Color(0xFF0A0E1A)
private val BgBottom     = Color(0xFF0D1230)
private val CardBg       = Color(0xFF111827)
private val CardBorder   = Color(0xFF1E2A45)
private val PrimaryBlue  = Color(0xFF1E40FF)
private val BlueBright   = Color(0xFF3B5BFF)
private val MoonBlue     = Color(0xFF1E3A8A)
private val CheckBg      = Color(0xFF1E3A6E)
private val CheckColor   = Color(0xFF93C5FD)

@Composable
fun MonetizationScreen(
    userPreferences: UserPreferences,
    onContinue: () -> Unit
) {
    val scope = rememberCoroutineScope()


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BgTop, BgBottom))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Close button
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable {
                            scope.launch {
                                userPreferences.setOnboardingCompleted()
                                onContinue()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Title
                Text(
                    text = "SUJOOD PREMIUM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── App icon ─────────────────────────────────────────────────────
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.sujood.app.R.mipmap.ic_launcher_image),
                contentDescription = "Sujood",
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(Modifier.height(32.dp))

            // ── Headline ─────────────────────────────────────────────────────
            Text(
                text = "Unlock the Full\nExperience",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 40.sp,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Support local development and enjoy an ad-free experience while you focus on your prayers.",
                fontSize = 15.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 40.dp)
            )

            Spacer(Modifier.height(36.dp))

            // ── Premium card ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // ONE-TIME badge top-right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(PrimaryBlue.copy(alpha = 0.15f))
                                .border(1.dp, PrimaryBlue.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "ONE-TIME",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                color = CheckColor
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Title + price
                    Text(
                        text = "Premium Supporter",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "\$2.99",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "forever",
                            fontSize = 15.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Feature list
                    listOf(
                        "Ad-free forever",
                        "Support local development",
                        "Exclusive future features"
                    ).forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CheckBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = CheckColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = feature,
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // CTA button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(PrimaryBlue, BlueBright)
                                )
                            )
                            .clickable {
                                scope.launch {
                                    userPreferences.setOnboardingCompleted()
                                    onContinue()
                                }
                            }
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Get Premium Access",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Skip link ────────────────────────────────────────────────────
            Text(
                text = "Continue with limited free version",
                fontSize = 13.sp,
                color = TextSecondary,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier
                    .clickable {
                        scope.launch {
                            userPreferences.setOnboardingCompleted()
                            onContinue()
                        }
                    }
                    .padding(8.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── Footer links ─────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                listOf("Privacy Policy", "Terms of Service", "Restore Purchase").forEach { label ->
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = TextTertiary,
                        modifier = Modifier.clickable { /* TODO */ }
                    )
                }
            }
        }
    }
}
