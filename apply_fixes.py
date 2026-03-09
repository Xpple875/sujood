import os, re

SRC = os.path.join("Sujood","app","src","main","java","com","sujood","app")

def write(relpath, content):
    fullpath = os.path.join(SRC, *relpath.split("/"))
    os.makedirs(os.path.dirname(fullpath), exist_ok=True)
    with open(fullpath, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  \u2713 {relpath}")

def patch(relpath, old, new, label=""):
    fullpath = os.path.join(SRC, *relpath.split("/"))
    with open(fullpath, encoding="utf-8") as f:
        s = f.read()
    if old not in s:
        print(f"  ! WARNING: could not find patch target in {relpath} [{label}]")
        return s
    s = s.replace(old, new, 1)
    with open(fullpath, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)
    print(f"  \u2713 {relpath} [{label}]")
    return s

def read(relpath):
    fullpath = os.path.join(SRC, *relpath.split("/"))
    with open(fullpath, encoding="utf-8") as f:
        return f.read()

# ══════════════════════════════════════════════════════════════════════════════
# 1. HomeScreen.kt — replace Canvas drawPrayerIcon with Material Icons
# ══════════════════════════════════════════════════════════════════════════════
home = read("ui/screens/home/HomeScreen.kt")

# Add new icon imports (material icons extended already in deps)
home = home.replace(
    "import androidx.compose.material.icons.filled.LocationOn\n"
    "import androidx.compose.material.icons.filled.MyLocation\n"
    "import androidx.compose.material.icons.filled.Notifications\n"
    "import androidx.compose.material.icons.filled.Person",
    "import androidx.compose.material.icons.filled.LocationOn\n"
    "import androidx.compose.material.icons.filled.MyLocation\n"
    "import androidx.compose.material.icons.filled.Notifications\n"
    "import androidx.compose.material.icons.filled.Person\n"
    "import androidx.compose.material.icons.outlined.WbSunny\n"
    "import androidx.compose.material.icons.outlined.WbTwilight\n"
    "import androidx.compose.material.icons.outlined.Nightlight\n"
    "import androidx.compose.material.icons.outlined.NightsStay\n"
    "import androidx.compose.material.icons.outlined.LightMode\n"
    "import androidx.compose.material.icons.outlined.Mosque"
)

# Replace the Canvas icon box in PrayerRow with Icon composable
old_canvas_box = '''            // ── Sleek Canvas icon — no emoji, no stock Android icons ──
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(iconBg),
                contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(22.dp)) {
                    drawPrayerIcon(prayerTime.prayer, iconTint)
                }
            }'''

new_icon_box = '''            Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),
                contentAlignment = Alignment.Center) {
                val prayerIcon = when (prayerTime.prayer) {
                    Prayer.FAJR    -> androidx.compose.material.icons.outlined.WbTwilight
                    Prayer.DHUHR   -> androidx.compose.material.icons.outlined.LightMode
                    Prayer.ASR     -> androidx.compose.material.icons.outlined.WbSunny
                    Prayer.MAGHRIB -> androidx.compose.material.icons.outlined.Nightlight
                    Prayer.ISHA    -> androidx.compose.material.icons.outlined.NightsStay
                }
                Icon(imageVector = prayerIcon, contentDescription = prayerTime.prayer.displayName,
                    tint = iconTint, modifier = Modifier.size(24.dp))
            }'''

home = home.replace(old_canvas_box, new_icon_box, 1)

# Remove the entire drawPrayerIcon function (no longer needed)
drawicon_pattern = re.compile(
    r'\n/\*\* Canvas-drawn Iconly-style icons.*?^\}\n',
    re.DOTALL | re.MULTILINE
)
home = drawicon_pattern.sub("\n", home, count=1)

# Remove unused Canvas/drawscope/Stroke/StrokeCap/StrokeJoin/Size/Rect imports from drawing
# (keep Canvas for the sun arc widget)
# Remove cos/sin imports if now unused — actually keep them for SunArcWidget
# Remove Path import only if unused — keep for checkmark
# Remove drawscope.Stroke — still used in SunArcWidget
# Safe: just remove the Size import since it was only used in drawPrayerIcon
# Actually Size is also used in SunArcWidget so leave all — just clean the outlined imports via qualified names above

with open(os.path.join(SRC, "ui/screens/home/HomeScreen.kt"), "w", encoding="utf-8", newline="\n") as f:
    f.write(home)
print("  \u2713 ui/screens/home/HomeScreen.kt [prayer icons → Material Icons]")

# ══════════════════════════════════════════════════════════════════════════════
# 2. SplashScreen.kt — full rewrite: mosque Icon composable on teal bg
#    Uses Icons.Filled.Mosque from material-icons-extended
# ══════════════════════════════════════════════════════════════════════════════
splash = '''\
package com.sujood.app.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val BgDark      = Color(0xFF0D1829)
private val IconBg      = Color(0xFF0D2233)
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
                // Icon tile — rounded square matching reference image
                Box(
                    modifier = Modifier
                        .scale(iconScale)
                        .size(110.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(IconBg)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mosque,
                        contentDescription = "Sujood",
                        tint = Color.White,
                        modifier = Modifier.size(62.dp)
                    )
                }
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
                "Connecting to the Divine",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.40f),
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
'''
write("ui/screens/splash/SplashScreen.kt", splash)

# ══════════════════════════════════════════════════════════════════════════════
# 3. DhikrScreen.kt — replace emoji with PackageManager icons + brand colours
#    (combined from fix_v10 + fix_v11, applied fresh to the original file)
# ══════════════════════════════════════════════════════════════════════════════
dhikr = read("ui/screens/dhikr/DhikrScreen.kt")

# Add needed imports
if "import androidx.compose.ui.graphics.asImageBitmap" not in dhikr:
    dhikr = dhikr.replace(
        "import com.sujood.app.domain.model.LockMode",
        "import androidx.compose.ui.graphics.asImageBitmap\n"
        "import androidx.core.graphics.drawable.toBitmap\n"
        "import com.sujood.app.domain.model.LockMode"
    )

# Replace AppEntry data class + list
old_entry = ('private data class AppEntry(val name: String, val packageName: String, val emoji: String)\n\n'
             'private val COMMON_APPS = listOf(\n'
             '    AppEntry("TikTok",     "com.zhiliaoapp.musically",        "🎵"),\n'
             '    AppEntry("Instagram",  "com.instagram.android",           "📸"),\n'
             '    AppEntry("YouTube",    "com.google.android.youtube",      "▶️"),\n'
             '    AppEntry("Twitter / X","com.twitter.android",             "🐦"),\n'
             '    AppEntry("Snapchat",   "com.snapchat.android",            "👻"),\n'
             '    AppEntry("Facebook",   "com.facebook.katana",             "👍"),\n'
             '    AppEntry("WhatsApp",   "com.whatsapp",                    "💬"),\n'
             '    AppEntry("Reddit",     "com.reddit.frontpage",            "🤖"),\n'
             '    AppEntry("Netflix",    "com.netflix.mediaclient",         "🎬"),\n'
             '    AppEntry("Games",      "com.android.vending.games",       "🎮"),\n'
             ')')
new_entry = ('private data class AppEntry(val name: String, val packageName: String, val brandColor: Long)\n\n'
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
             ')')

if old_entry in dhikr:
    dhikr = dhikr.replace(old_entry, new_entry, 1)
    print("  \u2713 DhikrScreen AppEntry replaced")
else:
    print("  ! AppEntry block not found — may already be patched or different version")

# Replace emoji icon rendering with PackageManager AppIconBox call
old_icon_row = re.compile(
    r'Row\(verticalAlignment = Alignment\.CenterVertically,\s*'
    r'horizontalArrangement = Arrangement\.spacedBy\(12\.dp\)\) \{.*?'
    r'Text\(app\.name,.*?\)\s*\}',
    re.DOTALL
)
new_icon_row = (
    'Row(verticalAlignment = Alignment.CenterVertically,\n'
    '                                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {\n'
    '                                                AppIconBox(app.packageName, app.name, app.brandColor)\n'
    '                                                Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)\n'
    '                                            }'
)
dhikr, n = old_icon_row.subn(new_icon_row, dhikr, count=1)
if n: print("  \u2713 DhikrScreen icon row replaced")
else: print("  ! DhikrScreen icon row not matched")

# Inject AppIconBox composable before GlassCard
if "fun AppIconBox" not in dhikr:
    app_icon_composable = '''
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
        app_icon_composable + "@Composable\nprivate fun GlassCard("
    )
    print("  \u2713 DhikrScreen AppIconBox injected")

with open(os.path.join(SRC, "ui/screens/dhikr/DhikrScreen.kt"), "w", encoding="utf-8", newline="\n") as f:
    f.write(dhikr)
print("  \u2713 ui/screens/dhikr/DhikrScreen.kt saved")

# ══════════════════════════════════════════════════════════════════════════════
# 4. ic_launcher_foreground.xml — use Mosque icon path data (from Material Icons)
#    Background stays the exact teal #0D2233 from the reference image
# ══════════════════════════════════════════════════════════════════════════════
DRW = os.path.join("Sujood","app","src","main","res","drawable")

launcher_fg = '''\
<?xml version="1.0" encoding="utf-8"?>
<!-- App icon foreground: Mosque icon from Material Icons (Apache 2.0 licensed) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!--
        Material Icons "Mosque" path (Apache License 2.0 — free to use commercially)
        Source: https://fonts.google.com/icons?icon.query=mosque
    -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M21.32,9.55C21.76,9.39 22,8.92 21.84,8.48C21.47,7.5 20.61,6.76 19.57,6.54L19,6.42V6C19,4.9 18.1,4 17,4C15.9,4 15,4.9 15,6V6.42L14.43,6.54C13.39,6.76 12.53,7.5 12.16,8.48C12,8.92 12.24,9.39 12.68,9.55L14,10.05V11H10V10.05L11.32,9.55C11.76,9.39 12,8.92 11.84,8.48C11.47,7.5 10.61,6.76 9.57,6.54L9,6.42V6C9,4.9 8.1,4 7,4C5.9,4 5,4.9 5,6V6.42L4.43,6.54C3.39,6.76 2.53,7.5 2.16,8.48C2,8.92 2.24,9.39 2.68,9.55L4,10.05V11H2V13H4V20H2V22H22V20H20V13H22V11H20V10.05L21.32,9.55ZM10,20H7V17C7,15.9 7.9,15 9,15H10V20ZM13,20H11V15H13V20ZM17,20H14V15H15C16.1,15 17,15.9 17,17V20Z"/>
</vector>
'''

launcher_bg = '''\
<?xml version="1.0" encoding="utf-8"?>
<!-- Exact teal colour from reference icon image -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0D2233"/>
</shape>
'''

adaptive_xml = '''\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
'''

for fname, content in [("ic_launcher_foreground.xml", launcher_fg),
                        ("ic_launcher_background.xml", launcher_bg)]:
    p = os.path.join(DRW, fname)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  \u2713 drawable/{fname}")

RES = os.path.join("Sujood","app","src","main","res")
for density in ["mipmap-hdpi","mipmap-mdpi","mipmap-xhdpi","mipmap-xxhdpi","mipmap-xxxhdpi"]:
    for icon in ["ic_launcher.xml","ic_launcher_round.xml"]:
        p = os.path.join(RES, density, icon)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(adaptive_xml)
    print(f"  \u2713 {density}/ic_launcher*.xml")

print()
print("Done! Run:")
print("  git add .")
print("  git commit -m \"feat: Material Icons everywhere, mosque app icon, fixed app icons\"")
print("  git push")
print("  cd Sujood && ./gradlew assembleDebug")
