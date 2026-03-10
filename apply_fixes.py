"""
Sujood App Icon — high resolution version
Run from repo root:  python fix_icon.py
Requires: pip install Pillow
"""
import os, sys
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
RES  = os.path.join(ROOT, "Sujood", "app", "src", "main", "res")
SRC  = os.path.join(ROOT, "icon.png")

# Use much larger sizes so Android doesn't have to upscale
SIZES = {
    "mipmap-mdpi":    128,
    "mipmap-hdpi":    192,
    "mipmap-xhdpi":   256,
    "mipmap-xxhdpi":  384,
    "mipmap-xxxhdpi": 512,
}

ADAPTIVE_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/transparent"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""

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

if not os.path.isdir(RES):
    print("❌ Can't find Sujood/app/src/main/res — run from repo root"); sys.exit(1)
if not os.path.exists(SRC):
    print("❌ Can't find icon.png in repo root"); sys.exit(1)

src = Image.open(SRC).convert("RGBA")
print(f"Loaded icon.png ({src.size[0]}x{src.size[1]}px)\n")

# Delete any old conflicting PNGs
deleted = 0
for folder in SIZES:
    for name in ("ic_launcher.png","ic_launcher_round.png","ic_launcher_foreground.png","ic_launcher_image.png"):
        p = os.path.join(RES, folder, name)
        if os.path.exists(p):
            os.remove(p); deleted += 1
if deleted:
    print(f"  🗑  Removed {deleted} old PNG files\n")

# Write high-res PNGs
for folder, size in SIZES.items():
    d = os.path.join(RES, folder)
    os.makedirs(d, exist_ok=True)
    resized = src.resize((size, size), Image.LANCZOS)
    resized.save(os.path.join(d, "ic_launcher_image.png"), optimize=True)

    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(d, name), "w", encoding="utf-8") as f:
            f.write(ADAPTIVE_XML)

    print(f"  ✅ {folder:20s}  {size}x{size}px")

# Write drawable XMLs
drawable = os.path.join(RES, "drawable")
os.makedirs(drawable, exist_ok=True)
with open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
    f.write(FOREGROUND_XML)
with open(os.path.join(drawable, "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
    f.write(BACKGROUND_XML)

print("""
  ✅ drawable XMLs updated

✅ Done! Now run:
  git add .
  git commit -m "fix: high res app icon"
  git push
  cd Sujood && ./gradlew assembleDebug

Then UNINSTALL the old app from your phone, then install the new APK.
""")
