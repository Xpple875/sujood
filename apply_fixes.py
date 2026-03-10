"""
Sujood App Icon Generator
-------------------------
Rewrites ALL icon files (PNGs + XMLs) so Android picks up the new icon.
Run from your repo root (same folder as .git):

    pip install Pillow
    python generate_icon.py

Then:
    git add .
    git commit -m "feat: update app icon"
    git push
    cd Sujood && ./gradlew assembleDebug
"""

import os, sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.abspath(__file__))
RES  = os.path.join(ROOT, "Sujood", "app", "src", "main", "res")

SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

# ── Icon drawing ──────────────────────────────────────────────────────────────

def make_icon(final_size, rounded):
    SCALE = 4
    S     = final_size * SCALE
    BG    = (13, 34, 51, 255)     # #0D2233 — exact splash screen bg
    FG    = (255, 255, 255, 255)

    img  = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    if rounded:
        r = int(S * 0.24)
        draw.rounded_rectangle([0, 0, S-1, S-1], radius=r, fill=BG)
    else:
        draw.rectangle([0, 0, S-1, S-1], fill=BG)

    icon_r = S * 0.36
    cx, cy = S // 2, S // 2
    def sc(v):    return v * icon_r / 12.0
    def pt(x, y): return (cx + sc(x-12), cy + sc(y-12))

    draw.rectangle([pt(2,15), pt(22,21)], fill=FG)

    draw.rectangle([pt(3.5,9), pt(5.5,15)], fill=FG)
    draw.polygon([pt(3.5,9), pt(4.5,7), pt(5.5,9)], fill=FG)
    br = sc(0.7); bx,by = pt(4.5,6.3)
    draw.ellipse([bx-br,by-br,bx+br,by+br], fill=FG)

    draw.rectangle([pt(18.5,9), pt(20.5,15)], fill=FG)
    draw.polygon([pt(18.5,9), pt(19.5,7), pt(20.5,9)], fill=FG)
    bx,by = pt(19.5,6.3)
    draw.ellipse([bx-br,by-br,bx+br,by+br], fill=FG)

    dome_cx,dome_top = pt(12,9); dome_w=sc(8); dome_h=sc(5.5)
    draw.ellipse([dome_cx-dome_w, dome_top, dome_cx+dome_w, dome_top+dome_h*2], fill=FG)
    draw.rectangle([dome_cx-dome_w-2, dome_top+dome_h, dome_cx+dome_w+2, dome_top+dome_h*2+4], fill=BG)

    dw=sc(1.8); dh=sc(3.5); dcx,dcy = pt(12,21)
    draw.rectangle([dcx-dw,dcy-dh,dcx+dw,dcy], fill=BG)
    draw.ellipse([dcx-dw,dcy-dh-dw,dcx+dw,dcy-dh+dw], fill=BG)

    cr=sc(0.9); ccx,ccy = pt(12,8.5)
    draw.ellipse([ccx-cr,ccy-cr,ccx+cr,ccy+cr], fill=FG)
    draw.ellipse([ccx-cr*0.75,ccy-cr*1.1,ccx+cr*0.75,ccy+cr*0.5], fill=BG)

    return img.resize((final_size, final_size), Image.LANCZOS)

# ── XML content ───────────────────────────────────────────────────────────────

# Adaptive icon XML (goes in each mipmap folder)
ADAPTIVE_XML = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
'''

# Background drawable — solid #0D2233
BACKGROUND_XML = '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0D2233"/>
</shape>
'''

# Foreground drawable — Material Icons mosque vector path
FOREGROUND_XML = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M21.32,9.55C21.76,9.39 22,8.92 21.84,8.48C21.47,7.5 20.61,6.76 19.57,6.54L19,6.42V6C19,4.9 18.1,4 17,4C15.9,4 15,4.9 15,6V6.42L14.43,6.54C13.39,6.76 12.53,7.5 12.16,8.48C12,8.92 12.24,9.39 12.68,9.55L14,10.05V11H10V10.05L11.32,9.55C11.76,9.39 12,8.92 11.84,8.48C11.47,7.5 10.61,6.76 9.57,6.54L9,6.42V6C9,4.9 8.1,4 7,4C5.9,4 5,4.9 5,6V6.42L4.43,6.54C3.39,6.76 2.53,7.5 2.16,8.48C2,8.92 2.24,9.39 2.68,9.55L4,10.05V11H2V13H4V20H2V22H22V20H20V13H22V11H20V10.05L21.32,9.55ZM10,20H7V17C7,15.9 7.9,15 9,15H10V20ZM13,20H11V15H13V20ZM17,20H14V15H15C16.1,15 17,15.9 17,17V20Z"/>
</vector>
'''

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    if not os.path.isdir(RES):
        print("❌ Can't find Sujood/app/src/main/res")
        print("   Make sure you run this from your repo root (same folder as .git)")
        sys.exit(1)

    print("Writing icon files...\n")

    # 1. Write PNGs into every mipmap folder
    for folder, size in SIZES.items():
        out_dir = os.path.join(RES, folder)
        os.makedirs(out_dir, exist_ok=True)

        make_icon(size, rounded=False).save(os.path.join(out_dir, "ic_launcher.png"))
        make_icon(size, rounded=True ).save(os.path.join(out_dir, "ic_launcher_round.png"))

        # Also overwrite the XML in each mipmap folder
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            with open(os.path.join(out_dir, name), "w", encoding="utf-8") as f:
                f.write(ADAPTIVE_XML)

        print(f"  ✅ {folder:20s}  {size}x{size}px  (png + xml)")

    # 2. Overwrite the drawable XMLs that define foreground/background
    drawable_dir = os.path.join(RES, "drawable")
    os.makedirs(drawable_dir, exist_ok=True)

    with open(os.path.join(drawable_dir, "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
        f.write(BACKGROUND_XML)
    print("\n  ✅ drawable/ic_launcher_background.xml")

    with open(os.path.join(drawable_dir, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
        f.write(FOREGROUND_XML)
    print("  ✅ drawable/ic_launcher_foreground.xml")

    print("\n✅ All icon files updated!")
    print("\nNow run:")
    print("  git add .")
    print('  git commit -m "feat: update app icon"')
    print("  git push")
    print("  cd Sujood && ./gradlew assembleDebug")
    print("\nThen uninstall the old app from your phone before installing the new APK.")
    print("Android caches icons — uninstalling first guarantees the new one shows up.")

if __name__ == "__main__":
    main()
