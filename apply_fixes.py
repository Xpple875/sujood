"""
Fix app icon to exactly match the splash screen tile.
Run from repo root:  python fix_icon.py
"""
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
RES  = os.path.join(ROOT, "Sujood", "app", "src", "main", "res")

if not os.path.isdir(RES):
    print("❌ Can't find Sujood/app/src/main/res — run from repo root")
    raise SystemExit(1)

# ── Foreground: Icons.Filled.Mosque scaled to exactly match the splash ────────
# Splash shows mosque at 62dp inside a 110dp tile = 56.4% of tile.
# Adaptive icon foreground canvas = 108dp.
# To get the same ratio: mosque fills 60.9dp, centered → 23.6dp padding each side.
# Achieved by expanding the viewport: viewportWidth/Height = 42.58
# with the 24x24 path group translated by +9.29 on each axis.
FOREGROUND_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="42.58"
    android:viewportHeight="42.58">
    <group
        android:translateX="9.29"
        android:translateY="9.29">
        <path
            android:fillColor="#FFFFFF"
            android:pathData="M21.32,9.55C21.76,9.39 22,8.92 21.84,8.48C21.47,7.5 20.61,6.76 19.57,6.54L19,6.42V6C19,4.9 18.1,4 17,4C15.9,4 15,4.9 15,6V6.42L14.43,6.54C13.39,6.76 12.53,7.5 12.16,8.48C12,8.92 12.24,9.39 12.68,9.55L14,10.05V11H10V10.05L11.32,9.55C11.76,9.39 12,8.92 11.84,8.48C11.47,7.5 10.61,6.76 9.57,6.54L9,6.42V6C9,4.9 8.1,4 7,4C5.9,4 5,4.9 5,6V6.42L4.43,6.54C3.39,6.76 2.53,7.5 2.16,8.48C2,8.92 2.24,9.39 2.68,9.55L4,10.05V11H2V13H4V20H2V22H22V20H20V13H22V11H20V10.05L21.32,9.55ZM10,20H7V17C7,15.9 7.9,15 9,15H10V20ZM13,20H11V15H13V20ZM17,20H14V15H15C16.1,15 17,15.9 17,17V20Z"/>
    </group>
</vector>
"""

# ── Background: solid #0D2233 — exact splash IconBg colour ───────────────────
BACKGROUND_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0D2233"/>
</shape>
"""

# ── Adaptive icon XML used in every mipmap folder ─────────────────────────────
ADAPTIVE_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""

# Write drawable files
drawable = os.path.join(RES, "drawable")
os.makedirs(drawable, exist_ok=True)

with open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
    f.write(FOREGROUND_XML)
print("✅ drawable/ic_launcher_foreground.xml  (mosque, sized to match splash)")

with open(os.path.join(drawable, "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
    f.write(BACKGROUND_XML)
print("✅ drawable/ic_launcher_background.xml  (#0D2233 background)")

# Update every mipmap XML
for folder in ["mipmap-mdpi","mipmap-hdpi","mipmap-xhdpi","mipmap-xxhdpi","mipmap-xxxhdpi"]:
    d = os.path.join(RES, folder)
    os.makedirs(d, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(d, name), "w", encoding="utf-8") as f:
            f.write(ADAPTIVE_XML)
print("✅ All mipmap ic_launcher*.xml updated")

print("""
✅ Done! Now run:
  git add .
  git commit -m "fix: app icon matches splash screen exactly"
  git push
  cd Sujood && ./gradlew assembleDebug

Then UNINSTALL the old app from your phone, then install the new APK.
""")
