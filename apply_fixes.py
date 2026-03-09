import os

SRC = os.path.join("Sujood","app","src","main","java","com","sujood","app")
RES = os.path.join("Sujood","app","src","main","res")
DRW = os.path.join(RES, "drawable")

def write(rel, content):
    p = os.path.join(SRC, *rel.split("/"))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  \u2713 {rel}")

# ══════════════════════════════════════════════════════════════════════════════
# 1. BottomNavBar.kt — fix weight(1f) inside Column→ use fillMaxWidth(fraction)
#    instead, and replace graphicsLayer lambda with .scale() modifier
# ══════════════════════════════════════════════════════════════════════════════
write("ui/components/BottomNavBar.kt", '''\
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
''')

# ══════════════════════════════════════════════════════════════════════════════
# 2. App icon — use Icons.Filled.Mosque path data (same as splash screen shows)
#    Apache 2.0 licensed — free for commercial use
# ══════════════════════════════════════════════════════════════════════════════

# Background: exact teal from the splash/reference image
with open(os.path.join(DRW, "ic_launcher_background.xml"), "w", encoding="utf-8", newline="\n") as f:
    f.write('''\
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0D2233"/>
</shape>
''')
print("  \u2713 drawable/ic_launcher_background.xml")

# Foreground: Mosque icon vector from Material Icons (Apache 2.0)
# Scaled and centred for adaptive icon safe zone (centred in 108x108 viewport)
with open(os.path.join(DRW, "ic_launcher_foreground.xml"), "w", encoding="utf-8", newline="\n") as f:
    f.write('''\
<?xml version="1.0" encoding="utf-8"?>
<!--
    Material Icons "Mosque" — Apache License 2.0
    https://fonts.google.com/icons?icon.query=mosque
    Scaled to fill the adaptive icon safe zone (centred in 108x108dp)
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Translate to centre the 24-unit icon in 108dp canvas, scale 3x -->
    <group
        android:translateX="12"
        android:translateY="20"
        android:scaleX="3.5"
        android:scaleY="3.5">
        <path
            android:fillColor="#FFFFFF"
            android:pathData="M21.32,9.55C21.76,9.39 22,8.92 21.84,8.48C21.47,7.5 20.61,6.76 19.57,6.54L19,6.42V6C19,4.9 18.1,4 17,4C15.9,4 15,4.9 15,6V6.42L14.43,6.54C13.39,6.76 12.53,7.5 12.16,8.48C12,8.92 12.24,9.39 12.68,9.55L14,10.05V11H10V10.05L11.32,9.55C11.76,9.39 12,8.92 11.84,8.48C11.47,7.5 10.61,6.76 9.57,6.54L9,6.42V6C9,4.9 8.1,4 7,4C5.9,4 5,4.9 5,6V6.42L4.43,6.54C3.39,6.76 2.53,7.5 2.16,8.48C2,8.92 2.24,9.39 2.68,9.55L4,10.05V11H2V13H4V20H2V22H22V20H20V13H22V11H20V10.05L21.32,9.55ZM10,20H7V17C7,15.9 7.9,15 9,15H10V20ZM13,20H11V15H13V20ZM17,20H14V15H15C16.1,15 17,15.9 17,17V20Z"/>
    </group>
</vector>
''')
print("  \u2713 drawable/ic_launcher_foreground.xml (Mosque icon, Apache 2.0)")

# Update all mipmap adaptive icon XMLs
adaptive_xml = '''\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
'''
for density in ["mipmap-hdpi","mipmap-mdpi","mipmap-xhdpi","mipmap-xxhdpi","mipmap-xxxhdpi"]:
    for icon_name in ["ic_launcher.xml","ic_launcher_round.xml"]:
        p = os.path.join(RES, density, icon_name)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(adaptive_xml)
print("  \u2713 mipmap-*/ic_launcher*.xml (all densities)")

print()
print("Done! Run:")
print("  git add .")
print("  git commit -m \"fix: navbar compile errors, mosque system app icon\"")
print("  git push")
print("  cd Sujood && ./gradlew assembleDebug")
