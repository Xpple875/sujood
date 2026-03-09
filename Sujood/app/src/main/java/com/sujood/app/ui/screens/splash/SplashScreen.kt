package com.sujood.app.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val BgTeal      = Color(0xFF0D1829)   // slightly deeper than icon bg for full screen
private val IconBg      = Color(0xFF0D2233)    // exact icon background
private val PrimaryBlue = Color(0xFF1132D4)

@Composable
fun SplashScreen(onNavigate: () -> Unit) {
    var showText     by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(700)
        showText = true
        kotlinx.coroutines.delay(500)
        showSubtitle = true
        kotlinx.coroutines.delay(1500)
        onNavigate()
    }

    val inf = rememberInfiniteTransition(label = "splash")
    val iconScale by inf.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale"
    )
    val glowAlpha by inf.animateFloat(
        initialValue = 0.15f, targetValue = 0.40f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(BgTeal),
        contentAlignment = Alignment.Center
    ) {
        StarField(Modifier.fillMaxSize())

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.weight(1f))

            // Icon: rounded square with exact icon background + dome
            Box(
                modifier = Modifier.size(120.dp).scale(iconScale),
                contentAlignment = Alignment.Center
            ) {
                // Soft blue outer glow ring
                Canvas(Modifier.size(140.dp)) {
                    drawCircle(brush = Brush.radialGradient(
                        listOf(PrimaryBlue.copy(alpha = glowAlpha), Color.Transparent)
                    ))
                }
                // Icon background square (rounded via Canvas clip)
                Canvas(Modifier.size(120.dp)) {
                    val r = size.width * 0.22f   // corner radius ~26dp at 120dp size
                    val path = Path().apply {
                        addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), r, r))
                    }
                    // Slight shadow / border illusion
                    drawPath(path, Color(0xFF0A1A28))
                    // Inset the actual icon bg
                    val ip = Path().apply {
                        val inset = size.width * 0.03f
                        addRoundRect(RoundRect(
                            Rect(inset, inset, size.width - inset, size.height - inset), r, r))
                    }
                    drawPath(ip, IconBg)
                    // Draw dome
                    drawDomeIcon(Color.White, IconBg)
                }
            }

            Spacer(Modifier.height(36.dp))
            Text("Sujood",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light, letterSpacing = 6.sp),
                color = Color.White,
                modifier = Modifier.alpha(if (showText) 1f else 0f))
            Spacer(Modifier.height(10.dp))
            Text("Connecting to the Divine",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.40f),
                modifier = Modifier.alpha(if (showSubtitle) 1f else 0f))

            Spacer(Modifier.weight(1f))

            // Pulsing dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(if (showSubtitle) 1f else 0f).padding(bottom = 56.dp)) {
                repeat(3) { i ->
                    val da by inf.animateFloat(
                        initialValue = 0.2f, targetValue = 0.85f,
                        animationSpec = infiniteRepeatable(
                            tween(550, delayMillis = i * 180, easing = LinearEasing),
                            RepeatMode.Reverse),
                        label = "d$i")
                    Canvas(Modifier.size(6.dp)) { drawCircle(PrimaryBlue.copy(alpha = da)) }
                }
            }
        }
    }
}

/**
 * Draw the onion dome + crescent exactly matching the provided icon image.
 * s = canvas side length (square DrawScope).
 */
private fun DrawScope.drawDomeIcon(white: Color, bg: Color) {
    val s = size.width

    // ── Onion dome body ────────────────────────────────────────────────────
    // Shape: wide base, smooth curves up to narrow neck, then bulge, then finial point.
    // Approximate the silhouette as a filled path.
    val dome = Path().apply {
        // Base: flat bottom at ~y=0.80s, spans from ~x=0.25s to x=0.75s
        // Curves up via cubic beziers to top finial at (cx, 0.30s)
        val cx = s * 0.50f
        val by = s * 0.80f   // base y
        val ty = s * 0.30f   // finial tip y
        val bL = s * 0.24f   // base left x
        val bR = s * 0.76f   // base right x

        moveTo(bL, by)
        // Left side: cubic up to the wide belly then curves in to neck then out to finial
        cubicTo(
            bL, by - s * 0.06f,       // control 1: straight up at base
            s * 0.18f, by - s * 0.22f, // control 2: belly bulge
            cx, ty                     // finial tip
        )
        // Right mirror
        cubicTo(
            s * 0.82f, by - s * 0.22f,
            bR, by - s * 0.06f,
            bL, by   // back to start via bottom — we close below
        )
        // Flat bottom
        moveTo(bL, by)
        lineTo(bR, by)
        cubicTo(bR, by - s*0.06f, s*0.82f, by - s*0.22f, cx, ty)
        cubicTo(s*0.18f, by - s*0.22f, bL, by - s*0.06f, bL, by)
        close()
    }
    drawPath(dome, white)

    // ── Crescent moon ──────────────────────────────────────────────────────
    // Positioned just above the finial tip
    val crescentCx = s * 0.50f
    val crescentCy = s * 0.20f
    val outerR = s * 0.075f
    val innerR = s * 0.060f
    val innerOffset = s * 0.028f   // shift inner circle to create crescent

    drawCircle(white,  radius = outerR, center = Offset(crescentCx, crescentCy))
    drawCircle(bg,     radius = innerR, center = Offset(crescentCx + innerOffset, crescentCy - innerOffset * 0.3f))
}

@Composable
private fun StarField(modifier: Modifier) {
    val stars = remember {
        List(30) { Star(Random.nextFloat(), Random.nextFloat(),
            Random.nextFloat() * 1.6f + 0.5f, (Random.nextFloat() * 0.3f + 0.2f).coerceIn(0f,1f)) }
    }
    val inf = rememberInfiniteTransition(label = "stars")
    val alphas = stars.map { star ->
        inf.animateFloat(initialValue = star.a * 0.3f, targetValue = star.a,
            animationSpec = infiniteRepeatable(
                tween((Random.nextInt(1400) + 700), easing = LinearEasing), RepeatMode.Reverse),
            label = "s${star.hashCode()}")
    }
    Canvas(modifier) {
        stars.forEachIndexed { i, st ->
            drawCircle(Color.White.copy(alpha = alphas[i].value.coerceIn(0f,1f)),
                radius = st.r, center = Offset(st.x * size.width, st.y * size.height))
        }
    }
}

private data class Star(val x: Float, val y: Float, val r: Float, val a: Float)
