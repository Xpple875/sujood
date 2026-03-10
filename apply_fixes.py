"""
Sujood App Icon — uses your custom icon.png
Run from repo root:  python fix_icon.py
Requires: pip install Pillow
"""
import os, sys
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
RES  = os.path.join(ROOT, "Sujood", "app", "src", "main", "res")
SRC  = os.path.join(ROOT, "icon.png")

FOLDERS = ["mipmap-mdpi","mipmap-hdpi","mipmap-xhdpi","mipmap-xxhdpi","mipmap-xxxhdpi"]
SIZES   = {"mipmap-mdpi":48,"mipmap-hdpi":72,"mipmap-xhdpi":96,"mipmap-xxhdpi":144,"mipmap-xxxhdpi":192}

if not os.path.isdir(RES):
    print("❌ Can't find Sujood/app/src/main/res — run from repo root"); sys.exit(1)
if not os.path.exists(SRC):
    print("❌ Can't find icon.png — place it in the repo root next to this script"); sys.exit(1)

src = Image.open(SRC).convert("RGBA")
print(f"Loaded icon.png ({src.size[0]}x{src.size[1]}px)\n")

# Step 1: DELETE the old PNGs that conflict with the XMLs
deleted = 0
for folder in FOLDERS:
    for name in ("ic_launcher.png", "ic_launcher_round.png", "ic_launcher_foreground.png"):
        p = os.path.join(RES, folder, name)
        if os.path.exists(p):
            os.remove(p)
            deleted += 1
print(f"  🗑  Removed {deleted} old PNG files\n")

# Step 2: Write resized PNGs under a NEW name: ic_launcher_image.png
for folder in FOLDERS:
    d    = os.path.join(RES, folder)
    size = SIZES[folder]
    os.makedirs(d, exist_ok=True)
    resized = src.resize((size, size), Image.LANCZOS)
    resized.save(os.path.join(d, "ic_launcher_image.png"))
    print(f"  ✅ {folder:20s}  ic_launcher_image.png  {size}x{size}px")

# Step 3: Update the XML files to reference ic_launcher_image via a bitmap drawable
# The adaptive icon foreground points to a bitmap drawable that wraps our PNG
FOREGROUND_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@mipmap/ic_launcher_image"
    android:gravity="fill"/>
"""

BACKGROUND_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0E3854"/>
</shape>
"""

ADAPTIVE_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/transparent"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""

drawable = os.path.join(RES, "drawable")
os.makedirs(drawable, exist_ok=True)

with open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
    f.write(FOREGROUND_XML)
with open(os.path.join(drawable, "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
    f.write(BACKGROUND_XML)
print("\n  ✅ drawable/ic_launcher_foreground.xml")
print("  ✅ drawable/ic_launcher_background.xml")

for folder in FOLDERS:
    d = os.path.join(RES, folder)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(d, name), "w", encoding="utf-8") as f:
            f.write(ADAPTIVE_XML)
print("  ✅ All mipmap ic_launcher*.xml updated\n")

print("""✅ Done! Now run:
  git add .
  git commit -m "feat: custom app icon"
  git push
  cd Sujood && ./gradlew assembleDebug

Then UNINSTALL the old app from your phone, then install the new APK.
""")
