package com.sujood.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.domain.model.BottomNavItem

private val PrimaryBlue   = Color(0xFF1132D4)
private val NavBackground = Color(0xFF0D1020).copy(alpha = 0.85f)
private val GlassStroke   = Color(0xFFFFFFFF).copy(alpha = 0.10f)
private val TextMuted     = Color(0xFF94A3B8)

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

    // Outer padding layer — sits above the system nav bar
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // The pill container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50.dp))
                .background(NavBackground)
                // Glass border
                .then(
                    Modifier.background(
                        brush = Brush.verticalGradient(
                            listOf(GlassStroke, Color.Transparent)
                        )
                    )
                )
        ) {
            // Hair-line top border to simulate the glass edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(1.dp)
                    .align(Alignment.TopCenter)
                    .background(GlassStroke)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    NavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onNavigate(item.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else TextMuted,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tint"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryBlue else TextMuted,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "labelColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .scale(scale)
    ) {
        // Icon — active gets a filled blue circle, inactive gets nothing
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .then(
                    if (isSelected) {
                        Modifier
                            .clip(CircleShape)
                            .background(PrimaryBlue)
                    } else Modifier
                )
        ) {
            Icon(
                imageVector = getIconForItem(item),
                contentDescription = item.title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Text(
            text = item.title.uppercase(),
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
            letterSpacing = 1.sp
        )
    }
}

private fun getIconForItem(item: BottomNavItem): ImageVector {
    return when (item) {
        BottomNavItem.Home     -> Icons.Default.Home
        BottomNavItem.Dhikr   -> Icons.Default.Lock
        BottomNavItem.Qibla   -> Icons.Default.Explore
        BottomNavItem.Insights -> Icons.Default.BarChart
        BottomNavItem.Settings -> Icons.Default.Settings
    }
}
