package com.sujood.app.ui.screens.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import kotlin.random.Random

@Composable
fun SplashScreen(onNavigate: () -> Unit) {
    var showText by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        showText = true
        kotlinx.coroutines.delay(800)
        showSubtitle = true
        kotlinx.coroutines.delay(1500)
        onNavigate()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    
    val moonScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "moonScale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val textAlpha = if (showText) 1f else 0f
    val subtitleAlpha = if (showSubtitle) 1f else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DeepNavy,
                        Color(0xFF0F1729),
                        Color(0xFF1A0F2E),
                        Color(0xFF2D1F4E)
                    )
                )
            )
    ) {
        StarField(modifier = Modifier.fillMaxSize())
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(moonScale),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SoftPurple.copy(alpha = (glowAlpha * 0.4f).coerceIn(0f, 1f)),
                                Color.Transparent
                            )
                        )
                    )
                }
                
                Canvas(modifier = Modifier.size(120.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                LavenderGlow.copy(alpha = (glowAlpha * 0.2f).coerceIn(0f, 1f)),
                                Color.Transparent
                            )
                        )
                    )
                }
                
                Canvas(modifier = Modifier.size(80.dp)) {
                    val path = Path().apply {
                        addArc(
                            oval = androidx.compose.ui.geometry.Rect(
                                0f, 0f,
                                size.width, size.height
                            ),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 360f
                        )
                    }
                    drawPath(path, Color.White)
                    
                    val cutoutPath = Path().apply {
                        addArc(
                            oval = androidx.compose.ui.geometry.Rect(
                                size.width * 0.2f, -size.height * 0.1f,
                                size.width * 1.2f, size.height * 0.9f
                            ),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 360f
                        )
                    }
                    drawPath(cutoutPath, DeepNavy)
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Sujood",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 6.sp
                ),
                color = Color.White,
                modifier = Modifier.alpha(textAlpha)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Connecting to the Divine",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.alpha(subtitleAlpha)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun StarField(modifier: Modifier = Modifier) {
    val stars = remember {
        List(30) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 2f + 1f,
                baseAlpha = (Random.nextFloat() * 0.3f + 0.4f).coerceIn(0f, 1f)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    
    val starAlphas = stars.associate { star ->
        star to infiniteTransition.animateFloat(
            initialValue = star.baseAlpha * 0.5f,
            targetValue = star.baseAlpha,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = (Random.nextInt(2000) + 1000),
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "star_${star.hashCode()}"
        )
    }

    Canvas(modifier = modifier) {
        stars.forEach { star ->
            val alpha = (starAlphas[star]?.value ?: star.baseAlpha).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = star.size,
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val baseAlpha: Float
)
