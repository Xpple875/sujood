package com.sujood.app.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.R
import kotlin.random.Random

private val BgDark      = Color(0xFF0D1829)
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
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale"
    )
    val glowAlpha by inf.animateFloat(
        initialValue = 0.12f, targetValue = 0.38f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        // Radial blue glow top-left
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(PrimaryBlue.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(0f, 0f), radius = 900f
            )
        ))

        StarField(Modifier.fillMaxSize())

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.weight(1f))

            // Glowing icon container
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Canvas(Modifier.size(150.dp)) {
                    drawCircle(brush = Brush.radialGradient(
                        listOf(PrimaryBlue.copy(alpha = glowAlpha), Color.Transparent)
                    ))
                }
                // Your custom icon PNG — already has rounded corners + background baked in
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_image),
                    contentDescription = "Sujood",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .scale(iconScale)
                        .size(110.dp)
                        .clip(RoundedCornerShape(26.dp))
                )
            }

            Spacer(Modifier.height(36.dp))

            Text(
                "Sujood",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light, letterSpacing = 6.sp),
                color = Color.White,
                modifier = Modifier.alpha(if (showText) 1f else 0f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Get Closer to Allah",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.30f),
                modifier = Modifier.alpha(if (showSubtitle) 1f else 0f)
            )

            Spacer(Modifier.weight(1f))

            // Pulsing dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .alpha(if (showSubtitle) 1f else 0f)
                    .padding(bottom = 56.dp)
            ) {
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

@Composable
private fun StarField(modifier: Modifier) {
    val stars = remember {
        List(30) {
            Star(Random.nextFloat(), Random.nextFloat(),
                Random.nextFloat() * 1.6f + 0.5f,
                (Random.nextFloat() * 0.3f + 0.2f).coerceIn(0f, 1f))
        }
    }
    val inf = rememberInfiniteTransition(label = "stars")
    val alphas = stars.map { star ->
        inf.animateFloat(
            initialValue = star.a * 0.3f, targetValue = star.a,
            animationSpec = infiniteRepeatable(
                tween((Random.nextInt(1400) + 700), easing = LinearEasing),
                RepeatMode.Reverse),
            label = "s${star.hashCode()}")
    }
    Canvas(modifier) {
        stars.forEachIndexed { i, st ->
            drawCircle(Color.White.copy(alpha = alphas[i].value.coerceIn(0f, 1f)),
                radius = st.r, center = Offset(st.x * size.width, st.y * size.height))
        }
    }
}

private data class Star(val x: Float, val y: Float, val r: Float, val a: Float)
