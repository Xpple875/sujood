"""
Sujood App Icon — uses your custom icon.png
Run from repo root (same folder as .git):

    pip install Pillow
    python fix_icon.py

Then:
    git add .
    git commit -m "feat: custom app icon"
    git push
    cd Sujood && ./gradlew assembleDebug

Then UNINSTALL the old app from your phone before installing the new APK.
"""
import os, sys
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
RES  = os.path.join(ROOT, "Sujood", "app", "src", "main", "res")

SRC  = os.path.join(ROOT, "icon.png")

SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

# Adaptive icon XML — points to the PNG we'll place in each mipmap folder
# For a PNG-based icon we use a simple <adaptive-icon> that references
# a solid background + the PNG as the foreground via a bitmap drawable.
ADAPTIVE_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
"""

ADAPTIVE_ROUND_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
"""

# Color resource for the background (matches icon's dark teal bg)
COLORS_ADDITION = '#0E3854'   # dark teal from your icon

def main():
    if not os.path.isdir(RES):
        print("❌ Can't find Sujood/app/src/main/res")
        print("   Make sure you run this from the repo root (same folder as .git)")
        sys.exit(1)

    if not os.path.exists(SRC):
        print(f"❌ Can't find icon.png in {ROOT}")
        print("   Place your icon.png in the repo root next to this script")
        sys.exit(1)

    src = Image.open(SRC).convert("RGBA")
    print(f"Loaded icon.png ({src.size[0]}x{src.size[1]}px)\n")

    # Write a resized PNG into every mipmap folder as both
    # ic_launcher.png and ic_launcher_foreground.png
    for folder, size in SIZES.items():
        d = os.path.join(RES, folder)
        os.makedirs(d, exist_ok=True)

        resized = src.resize((size, size), Image.LANCZOS)
        resized.save(os.path.join(d, "ic_launcher.png"))
        resized.save(os.path.join(d, "ic_launcher_round.png"))
        resized.save(os.path.join(d, "ic_launcher_foreground.png"))

        # Overwrite the XML files so they just point straight to the PNG
        # Using a simple non-adaptive launcher icon is the most reliable approach
        simple_xml = f"""\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/transparent"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
"""
        with open(os.path.join(d, "ic_launcher.xml"),       "w", encoding="utf-8") as f:
            f.write(simple_xml)
        with open(os.path.join(d, "ic_launcher_round.xml"), "w", encoding="utf-8") as f:
            f.write(simple_xml)

        print(f"  ✅ {folder:20s}  {size}x{size}px")

    # Also update the manifest to use the PNG directly (most reliable)
    # by overwriting ic_launcher_foreground in drawable with a bitmap reference
    drawable = os.path.join(RES, "drawable")
    os.makedirs(drawable, exist_ok=True)

    # Replace foreground XML with a bitmap that just shows the PNG
    foreground_xml = """\
<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@mipmap/ic_launcher_foreground"
    android:gravity="fill"/>
"""
    with open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
        f.write(foreground_xml)
    print("\n  ✅ drawable/ic_launcher_foreground.xml")

    background_xml = """\
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0E3854"/>
</shape>
"""
    with open(os.path.join(drawable, "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
        f.write(background_xml)
    print("  ✅ drawable/ic_launcher_background.xml")

    # Update every mipmap XML to use foreground PNG directly
    for folder in SIZES:
        d = os.path.join(RES, folder)
        xml = """\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/transparent"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
"""
        with open(os.path.join(d, "ic_launcher.xml"),       "w", encoding="utf-8") as f:
            f.write(xml)
        with open(os.path.join(d, "ic_launcher_round.xml"), "w", encoding="utf-8") as f:
            f.write(xml)

    print("""
✅ All done!

Now run:
  git add .
  git commit -m "feat: custom app icon"
  git push
  cd Sujood && ./gradlew assembleDebug

Then UNINSTALL the old app from your phone, then install the new APK.
""")

if __name__ == "__main__":
    main()
