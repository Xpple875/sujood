package com.sujood.app.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlin.random.Random

private val BgDark      = Color(0xFF101322)
private val PrimaryBlue = Color(0xFF1132D4)

@Composable
fun SplashScreen(onNavigate: () -> Unit) {
    var showText     by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        showText = true
        kotlinx.coroutines.delay(600)
        showSubtitle = true
        kotlinx.coroutines.delay(1400)
        onNavigate()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val textAlpha     = if (showText)     1f else 0f
    val subtitleAlpha = if (showSubtitle) 1f else 0f

    Box(
        modifier = Modifier.fillMaxSize().background(BgDark)
            .background(Brush.radialGradient(
                listOf(PrimaryBlue.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(0f, 0f), radius = 900f)),
        contentAlignment = Alignment.Center
    ) {
        // Star field
        StarField(modifier = Modifier.fillMaxSize())

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.weight(1f))

            // Glow ring + mosque icon
            Box(
                modifier = Modifier.size(140.dp).scale(iconScale),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow
                Canvas(modifier = Modifier.size(140.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(PrimaryBlue.copy(alpha = glowAlpha * 0.5f), Color.Transparent)
                        )
                    )
                }
                // Blue circle background
                Canvas(modifier = Modifier.size(100.dp)) {
                    drawCircle(color = PrimaryBlue.copy(alpha = 0.18f))
                    drawCircle(
                        color = PrimaryBlue.copy(alpha = 0.35f),
                        style = Stroke(width = 1.5f)
                    )
                    // Draw mosque icon
                    drawMosqueIcon(Color.White)
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Sujood",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 6.sp
                ),
                color = Color.White,
                modifier = Modifier.alpha(textAlpha)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Connecting to the Divine",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.alpha(subtitleAlpha)
            )

            Spacer(Modifier.weight(1f))

            // Bottom progress dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(subtitleAlpha).padding(bottom = 60.dp)
            ) {
                repeat(3) { i ->
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.2f, targetValue = 0.8f,
                        animationSpec = infiniteRepeatable(
                            tween(600, delayMillis = i * 200, easing = LinearEasing),
                            RepeatMode.Reverse
                        ),
                        label = "dot$i"
                    )
                    Canvas(modifier = Modifier.size(6.dp)) {
                        drawCircle(color = PrimaryBlue.copy(alpha = dotAlpha))
                    }
                }
            }
        }
    }
}

/** Draw a minimalist mosque silhouette in Canvas coordinates scaled to the DrawScope size */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMosqueIcon(tint: Color) {
    val s  = size.width  // square canvas
    val cx = s / 2f

    // ── Ground line ──
    drawRect(tint, topLeft = Offset(s * 0.12f, s * 0.80f), size = Size(s * 0.76f, s * 0.06f))

    // ── Main hall ──
    drawRect(tint, topLeft = Offset(s * 0.27f, s * 0.45f), size = Size(s * 0.46f, s * 0.35f))

    // ── Central dome ──
    drawArc(
        color = tint,
        startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(s * 0.27f, s * 0.26f), size = Size(s * 0.46f, s * 0.38f)
    )

    // ── Arched door (cutout) ──
    drawRect(BgDark, topLeft = Offset(cx - s*0.09f, s*0.54f), size = Size(s*0.18f, s*0.26f))
    drawArc(BgDark, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(cx - s*0.09f, s*0.44f), size = Size(s*0.18f, s*0.18f))

    // ── Left minaret ──
    drawRect(tint, topLeft = Offset(s*0.14f, s*0.40f), size = Size(s*0.08f, s*0.40f))
    drawArc(tint, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(s*0.14f, s*0.32f), size = Size(s*0.08f, s*0.12f))

    // ── Right minaret ──
    drawRect(tint, topLeft = Offset(s*0.78f, s*0.40f), size = Size(s*0.08f, s*0.40f))
    drawArc(tint, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(s*0.78f, s*0.32f), size = Size(s*0.08f, s*0.12f))

    // ── Crescent on dome tip ──
    drawCircle(tint, radius = s*0.06f, center = Offset(cx, s*0.20f))
    drawCircle(BgDark, radius = s*0.05f, center = Offset(cx + s*0.025f, s*0.185f))
}

@Composable
private fun StarField(modifier: Modifier = Modifier) {
    val stars = remember {
        List(35) { Star(Random.nextFloat(), Random.nextFloat(),
            Random.nextFloat() * 1.8f + 0.5f,
            (Random.nextFloat() * 0.35f + 0.25f).coerceIn(0f, 1f)) }
    }
    val inf = rememberInfiniteTransition(label = "stars")
    val alphas = stars.map { star ->
        inf.animateFloat(
            initialValue = star.baseAlpha * 0.4f,
            targetValue  = star.baseAlpha,
            animationSpec = infiniteRepeatable(
                tween((Random.nextInt(1500) + 800), easing = LinearEasing),
                RepeatMode.Reverse
            ),
            label = "s${star.hashCode()}"
        )
    }
    Canvas(modifier = modifier) {
        stars.forEachIndexed { i, star ->
            drawCircle(
                color  = Color.White.copy(alpha = alphas[i].value.coerceIn(0f, 1f)),
                radius = star.size,
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }
    }
}

private data class Star(val x: Float, val y: Float, val size: Float, val baseAlpha: Float)
