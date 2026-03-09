package com.sujood.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.domain.model.BottomNavItem

private val PrimaryBlue = Color(0xFF1132D4)
private val NavBg       = Color(0xFF0D1120)
private val NavBgLight  = Color(0xFF141829)

@Composable
fun GlassmorphicBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Dhikr,
        BottomNavItem.Qibla,
        BottomNavItem.Insights,
        BottomNavItem.Settings
    )

    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 32.dp,
                shape = shape,
                spotColor = PrimaryBlue.copy(alpha = 0.25f),
                ambientColor = PrimaryBlue.copy(alpha = 0.10f)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NavBgLight, NavBg)))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.06f),
                    Color.Transparent
                )),
                shape = shape
            )
            .navigationBarsPadding()
            .height(72.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                NavItem(
                    item = item,
                    isSelected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.weight(1f)   // weight IS valid inside Row scope here
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.38f),
        animationSpec = tween(200), label = "iconColor"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryBlue else Color.White.copy(alpha = 0.38f),
        animationSpec = tween(200), label = "labelColor"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(250), label = "glow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Blue pill glow behind selected icon
            if (glowAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(PrimaryBlue.copy(alpha = 0.18f * glowAlpha))
                        .border(
                            1.dp,
                            PrimaryBlue.copy(alpha = 0.30f * glowAlpha),
                            RoundedCornerShape(17.dp)
                        )
                )
            }
            // .scale() instead of graphicsLayer — no extra import needed
            Icon(
                imageVector = navIcon(item),
                contentDescription = item.title,
                tint = iconColor,
                modifier = Modifier.size(24.dp).scale(iconScale)
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = item.title.uppercase(),
            color = labelColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp
        )
    }
}

private fun navIcon(item: BottomNavItem): ImageVector = when (item) {
    BottomNavItem.Home     -> Icons.Filled.Home
    BottomNavItem.Dhikr    -> Icons.Filled.Lock
    BottomNavItem.Qibla    -> Icons.Filled.Explore
    BottomNavItem.Insights -> Icons.Filled.TrendingUp
    BottomNavItem.Settings -> Icons.Filled.Settings
}
