import os, re

SRC = os.path.join("Sujood","app","src","main","java","com","sujood","app")

# ── 1. HomeScreen.kt — fix unresolved icon references ─────────────────────────
home_path = os.path.join(SRC, "ui","screens","home","HomeScreen.kt")
with open(home_path, encoding="utf-8") as f:
    home = f.read()

# Remove the outlined imports that don't exist in this Compose version
for bad_import in [
    "import androidx.compose.material.icons.outlined.WbSunny\n",
    "import androidx.compose.material.icons.outlined.WbTwilight\n",
    "import androidx.compose.material.icons.outlined.Nightlight\n",
    "import androidx.compose.material.icons.outlined.NightsStay\n",
    "import androidx.compose.material.icons.outlined.LightMode\n",
    "import androidx.compose.material.icons.outlined.Mosque\n",
]:
    home = home.replace(bad_import, "")

# Add safe imports that definitely exist in material-icons-extended 1.6.x
if "import androidx.compose.material.icons.filled.WbSunny" not in home:
    home = home.replace(
        "import androidx.compose.material.icons.filled.Person",
        "import androidx.compose.material.icons.filled.Person\n"
        "import androidx.compose.material.icons.filled.WbSunny\n"
        "import androidx.compose.material.icons.filled.Brightness3\n"
        "import androidx.compose.material.icons.filled.NightlightRound\n"
        "import androidx.compose.material.icons.filled.Star\n"
        "import androidx.compose.material.icons.filled.Flare"
    )

# Replace the icon when block — use only guaranteed-safe icon names
# All in Icons.Filled.* which is same as Icons.Default.*
old_icon_when = """\
                val prayerIcon = when (prayerTime.prayer) {
                    Prayer.FAJR    -> androidx.compose.material.icons.outlined.WbTwilight
                    Prayer.DHUHR   -> androidx.compose.material.icons.outlined.LightMode
                    Prayer.ASR     -> androidx.compose.material.icons.outlined.WbSunny
                    Prayer.MAGHRIB -> androidx.compose.material.icons.outlined.Nightlight
                    Prayer.ISHA    -> androidx.compose.material.icons.outlined.NightsStay
                }"""

new_icon_when = """\
                // Material Icons Extended — guaranteed available in Compose 1.6 / BOM 2024.02
                val prayerIcon = when (prayerTime.prayer) {
                    Prayer.FAJR    -> Icons.Filled.Flare          // sunrise burst
                    Prayer.DHUHR   -> Icons.Filled.WbSunny        // full sun
                    Prayer.ASR     -> Icons.Filled.WbSunny        // afternoon sun
                    Prayer.MAGHRIB -> Icons.Filled.Brightness3    // crescent setting
                    Prayer.ISHA    -> Icons.Filled.NightlightRound // night crescent
                }"""

home = home.replace(old_icon_when, new_icon_when, 1)

with open(home_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(home)
print("  \u2713 HomeScreen.kt [icon references fixed]")

# ── 2. DhikrScreen.kt — fix AppIconBox missing isLocked param ─────────────────
dhikr_path = os.path.join(SRC, "ui","screens","dhikr","DhikrScreen.kt")
with open(dhikr_path, encoding="utf-8") as f:
    dhikr = f.read()

# The call site passes isLocked but our AppIconBox no longer takes it
# Fix: remove isLocked from the call (AppIconBox doesn't need it)
dhikr = dhikr.replace(
    "AppIconBox(app.packageName, app.name, app.brandColor, isLocked)",
    "AppIconBox(app.packageName, app.name, app.brandColor)"
)
# Also fix if the old emoji version still has it
dhikr = dhikr.replace(
    "AppIconBox(app.packageName, app.name, app.fallbackColor, isLocked)",
    "AppIconBox(app.packageName, app.name, app.brandColor)"
)

with open(dhikr_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(dhikr)
print("  \u2713 DhikrScreen.kt [AppIconBox isLocked param removed]")

print()
print("Done! Run:")
print("  git add .")
print("  git commit -m \"fix: correct Material Icon names, fix AppIconBox param\"")
print("  git push")
print("  cd Sujood && ./gradlew assembleDebug")
