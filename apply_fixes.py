import os, re

SRC = os.path.join("Sujood","app","src","main","java","com","sujood","app")

def read(rel):
    with open(os.path.join(SRC, *rel.split("/")), encoding="utf-8") as f:
        return f.read()

def write(rel, content):
    p = os.path.join(SRC, *rel.split("/"))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  \u2713 {rel}")

# ══════════════════════════════════════════════════════════════════════════════
# 1. BottomNavBar.kt — redesign to match screenshot
#    - Floating pill shape, rounded top corners only
#    - Better icons: Home filled, Lock, Compass, Analytics (TrendingUp), Settings
#    - Blue glow pill under selected icon
#    - Labels uppercase 9sp
# ══════════════════════════════════════════════════════════════════════════════
navbar = '''\
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.domain.model.BottomNavItem

private val PrimaryBlue  = Color(0xFF1132D4)
private val NavBg        = Color(0xFF0D1120)
private val NavBgLight   = Color(0xFF141829)

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

    // Floating pill with rounded top corners only
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 32.dp,
                shape = shape,
                spotColor = PrimaryBlue.copy(alpha = 0.25f),
                ambientColor = PrimaryBlue.copy(alpha = 0.1f)
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(NavBgLight, NavBg)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent
                    )
                ),
                shape = shape
            )
            .navigationBarsPadding()
            .height(72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                NavItem(
                    item = item,
                    isSelected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) }
                )
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
        animationSpec = tween(200),
        label = "color"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryBlue else Color.White.copy(alpha = 0.38f),
        animationSpec = tween(200),
        label = "labelColor"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(250),
        label = "glow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Icon with glow pill behind it when selected
        Box(contentAlignment = Alignment.Center) {
            // Glow pill background
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
            Icon(
                imageVector = navIcon(item, isSelected),
                contentDescription = item.title,
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
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

private fun navIcon(item: BottomNavItem, selected: Boolean): ImageVector = when (item) {
    BottomNavItem.Home      -> if (selected) Icons.Filled.Home      else Icons.Filled.Home
    BottomNavItem.Dhikr     -> if (selected) Icons.Filled.Lock      else Icons.Filled.Lock
    BottomNavItem.Qibla     -> if (selected) Icons.Filled.Explore   else Icons.Filled.Explore
    BottomNavItem.Insights  -> if (selected) Icons.Filled.TrendingUp else Icons.Filled.TrendingUp
    BottomNavItem.Settings  -> if (selected) Icons.Filled.Settings  else Icons.Filled.Settings
}
'''
write("ui/components/BottomNavBar.kt", navbar)

# ══════════════════════════════════════════════════════════════════════════════
# 2. DhikrScreen.kt — full fresh patch: replace emoji AppEntry with
#    PackageManager real icons. Applied to the ORIGINAL file (emoji version).
# ══════════════════════════════════════════════════════════════════════════════
dhikr = read("ui/screens/dhikr/DhikrScreen.kt")

# Step A: add needed imports
if "import androidx.compose.ui.graphics.asImageBitmap" not in dhikr:
    dhikr = dhikr.replace(
        "import com.sujood.app.domain.model.LockMode",
        "import androidx.compose.ui.graphics.asImageBitmap\n"
        "import androidx.core.graphics.drawable.toBitmap\n"
        "import com.sujood.app.domain.model.LockMode"
    )

# Step B: replace AppEntry data class + COMMON_APPS list (emoji version)
old_entry = re.compile(
    r'private data class AppEntry\(val name: String, val packageName: String, val emoji: String\)\s*\n'
    r'private val COMMON_APPS = listOf\(.*?\)',
    re.DOTALL
)
new_entry = (
    'private data class AppEntry(val name: String, val packageName: String, val brandColor: Long)\n\n'
    'private val COMMON_APPS = listOf(\n'
    '    AppEntry("TikTok",      "com.zhiliaoapp.musically",        0xFF010101),\n'
    '    AppEntry("Instagram",   "com.instagram.android",           0xFFE1306C),\n'
    '    AppEntry("YouTube",     "com.google.android.youtube",      0xFFFF0000),\n'
    '    AppEntry("X / Twitter", "com.twitter.android",             0xFF1A1A1A),\n'
    '    AppEntry("Snapchat",    "com.snapchat.android",            0xFFFFD000),\n'
    '    AppEntry("Facebook",    "com.facebook.katana",             0xFF1877F2),\n'
    '    AppEntry("WhatsApp",    "com.whatsapp",                    0xFF25D366),\n'
    '    AppEntry("Reddit",      "com.reddit.frontpage",            0xFFFF4500),\n'
    '    AppEntry("Netflix",     "com.netflix.mediaclient",         0xFFE50914),\n'
    '    AppEntry("Twitch",      "tv.twitch.android.app",           0xFF9146FF),\n'
    ')'
)
dhikr, n = old_entry.subn(new_entry, dhikr, count=1)
print(f"  {'✓' if n else '!'} AppEntry replaced ({n} match)")

# Step C: replace the emoji icon Box with AppIconBox call
# Target the Box(...) { Text(app.emoji...) } pattern
old_icon_box = re.compile(
    r'Box\(modifier = Modifier\.size\(36\.dp\)\.clip\(RoundedCornerShape\(10\.dp\)\)\s*'
    r'\.background\(if \(isLocked\).*?\},\s*'
    r'contentAlignment = Alignment\.Center\) \{\s*'
    r'Text\(app\.emoji.*?\)\s*\}',
    re.DOTALL
)
new_icon_box = 'AppIconBox(app.packageName, app.name, app.brandColor)'
dhikr, n = old_icon_box.subn(new_icon_box, dhikr, count=1)
print(f"  {'✓' if n else '!'} icon Box replaced ({n} match)")

# Step D: inject AppIconBox composable if not present
if "fun AppIconBox" not in dhikr:
    composable = '''
@Composable
private fun AppIconBox(packageName: String, appName: String, brandColor: Long) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            android.graphics.Bitmap.createScaledBitmap(
                drawable.toBitmap(), 96, 96, true
            ).asImageBitmap()
        } catch (e: Exception) { null }
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(brandColor)),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = iconBitmap,
                contentDescription = appName,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
            )
        } else {
            Text(
                text = appName.first().uppercase(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (brandColor == 0xFFFFD000L) Color.Black else Color.White
            )
        }
    }
}

'''
    dhikr = dhikr.replace(
        "@Composable\nprivate fun GlassCard(",
        composable + "@Composable\nprivate fun GlassCard("
    )
    print("  \u2713 AppIconBox injected")

write("ui/screens/dhikr/DhikrScreen.kt", dhikr)

# ══════════════════════════════════════════════════════════════════════════════
# 3. HomeScreen.kt — fix icon references (safe icons only)
# ══════════════════════════════════════════════════════════════════════════════
home = read("ui/screens/home/HomeScreen.kt")

# Remove any bad outlined imports
for bad in [
    "import androidx.compose.material.icons.outlined.WbSunny\n",
    "import androidx.compose.material.icons.outlined.WbTwilight\n",
    "import androidx.compose.material.icons.outlined.Nightlight\n",
    "import androidx.compose.material.icons.outlined.NightsStay\n",
    "import androidx.compose.material.icons.outlined.LightMode\n",
    "import androidx.compose.material.icons.outlined.Mosque\n",
]:
    home = home.replace(bad, "")

# Add safe icon imports if needed
if "import androidx.compose.material.icons.filled.WbSunny" not in home:
    home = home.replace(
        "import androidx.compose.material.icons.filled.Person",
        "import androidx.compose.material.icons.filled.Person\n"
        "import androidx.compose.material.icons.filled.WbSunny\n"
        "import androidx.compose.material.icons.filled.Brightness3\n"
        "import androidx.compose.material.icons.filled.NightlightRound\n"
        "import androidx.compose.material.icons.filled.Flare"
    )

# Fix the icon when block — replace any version of the qualified/unresolved refs
icon_when_pattern = re.compile(
    r'val prayerIcon = when \(prayerTime\.prayer\) \{.*?\}',
    re.DOTALL
)
new_icon_when = (
    'val prayerIcon = when (prayerTime.prayer) {\n'
    '                    Prayer.FAJR    -> Icons.Filled.Flare\n'
    '                    Prayer.DHUHR   -> Icons.Filled.WbSunny\n'
    '                    Prayer.ASR     -> Icons.Filled.WbSunny\n'
    '                    Prayer.MAGHRIB -> Icons.Filled.Brightness3\n'
    '                    Prayer.ISHA    -> Icons.Filled.NightlightRound\n'
    '                }'
)
home, n = icon_when_pattern.subn(new_icon_when, home, count=1)
print(f"  {'✓' if n else '!'} HomeScreen icon when replaced ({n} match)")

# If the icon box still uses Canvas (original file), replace it
if "Canvas(modifier = Modifier.size(22.dp))" in home:
    old_canvas_box = (
        '            // ── Sleek Canvas icon — no emoji, no stock Android icons ──\n'
        '            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(iconBg),\n'
        '                contentAlignment = Alignment.Center) {\n'
        '                Canvas(modifier = Modifier.size(22.dp)) {\n'
        '                    drawPrayerIcon(prayerTime.prayer, iconTint)\n'
        '                }\n'
        '            }'
    )
    new_icon_box_home = (
        '            Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),\n'
        '                contentAlignment = Alignment.Center) {\n'
        '                val prayerIcon = when (prayerTime.prayer) {\n'
        '                    Prayer.FAJR    -> Icons.Filled.Flare\n'
        '                    Prayer.DHUHR   -> Icons.Filled.WbSunny\n'
        '                    Prayer.ASR     -> Icons.Filled.WbSunny\n'
        '                    Prayer.MAGHRIB -> Icons.Filled.Brightness3\n'
        '                    Prayer.ISHA    -> Icons.Filled.NightlightRound\n'
        '                }\n'
        '                Icon(imageVector = prayerIcon, contentDescription = prayerTime.prayer.displayName,\n'
        '                    tint = iconTint, modifier = Modifier.size(24.dp))\n'
        '            }'
    )
    home = home.replace(old_canvas_box, new_icon_box_home, 1)
    print("  \u2713 HomeScreen Canvas box replaced with Icon")

    # Remove drawPrayerIcon function
    draw_fn = re.compile(
        r'\n/\*\* Canvas-drawn.*?^\}\n',
        re.DOTALL | re.MULTILINE
    )
    home = draw_fn.sub("\n", home, count=1)
    print("  \u2713 HomeScreen drawPrayerIcon function removed")

write("ui/screens/home/HomeScreen.kt", home)

print()
print("Done! Run:")
print("  git add .")
print("  git commit -m \"feat: new navbar, real app icons, Material Icon prayer icons\"")
print("  git push")
print("  cd Sujood && ./gradlew assembleDebug")
