package com.sujood.app.ui.screens.monetization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun MonetizationScreen(
    userPreferences: UserPreferences,
    onContinue: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = DeepNavy
    ) { paddingValues ->
        AnimatedGradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(64.dp))

                // Premium Icon/Visual
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(LavenderGlow.copy(alpha = 0.1f))
                        .border(1.dp, LavenderGlow.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Star,
                        contentDescription = null,
                        tint = LavenderGlow,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Unlock the Full Experience",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Support the development of Sujood and enjoy a pure, ad-free experience.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Premium Card (Layered)
                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(160.dp)
                            .align(Alignment.Center)
                            .offset(y = (-8).dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(LavenderGlow.copy(alpha = 0.05f))
                    )

                    FrostedGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // TODO: Launch Google Play Billing Flow for Premium
                                scope.launch {
                                    userPreferences.setOnboardingCompleted()
                                    onContinue()
                                }
                            },
                        cornerRadius = 24.dp,
                        contentPadding = 24.dp,
                        borderColor = LavenderGlow.copy(alpha = 0.4f)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Premium Supporter",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "One-time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LavenderGlow,
                                    modifier = Modifier
                                        .background(LavenderGlow.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Ad-free forever\n• Support local development\n• Exclusive future features",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Free version link
                TextButton(
                    onClick = {
                        scope.launch {
                            userPreferences.setOnboardingCompleted()
                            onContinue()
                        }
                    }
                ) {
                    Text(
                        text = "Continue with limited free version",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    )
                }
            }
        }
    }
}