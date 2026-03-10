"""
Fix app icon — points the foreground to the custom mosque drawable.
Run from repo root:  python fix_icon.py
"""
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
RES  = os.path.join(ROOT, "Sujood", "app", "src", "main", "res")

# The foreground just needs to reference ic_mosque_launcher instead of ic_launcher_foreground
ADAPTIVE_XML = '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/ic_launcher_background"/>\n    <foreground android:drawable="@drawable/ic_mosque_launcher"/>\n</adaptive-icon>\n'

# Background — exact splash screen colour
BACKGROUND_XML = '<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android"\n    android:shape="rectangle">\n    <solid android:color="#0D2233"/>\n</shape>\n'

if not os.path.isdir(RES):
    print("❌ Can't find Sujood/app/src/main/res — run from repo root")
    raise SystemExit(1)

# Fix background colour
bg_path = os.path.join(RES, "drawable", "ic_launcher_background.xml")
with open(bg_path, "w", encoding="utf-8") as f:
    f.write(BACKGROUND_XML)
print("✅ drawable/ic_launcher_background.xml — #0D2233 background")

# Update every mipmap XML to point to ic_mosque_launcher
mipmap_folders = ["mipmap-mdpi","mipmap-hdpi","mipmap-xhdpi","mipmap-xxhdpi","mipmap-xxxhdpi"]
for folder in mipmap_folders:
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        path = os.path.join(RES, folder, name)
        if os.path.exists(path):
            with open(path, "w", encoding="utf-8") as f:
                f.write(ADAPTIVE_XML)
    print(f"✅ {folder}/ic_launcher*.xml → ic_mosque_launcher")

print("\n✅ Done! Now run:")
print("  git add .")
print('  git commit -m "fix: use custom mosque icon"')
print("  git push")
print("  cd Sujood && ./gradlew assembleDebug")
print("\nThen UNINSTALL the old app from your phone before installing the new APK.")
