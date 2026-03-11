"""
Sujood App Icon Generator
-------------------------
Generates all mipmap icon sizes and places them directly into your project.
Just run this from your repo root (same folder as .git):

    python generate_icon.py

Then rebuild:
    cd Sujood && ./gradlew assembleDebug
"""

import os, sys
from PIL import Image, ImageDraw

# ── Output paths relative to repo root ───────────────────────────────────────
SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

def make_icon(final_size, rounded):
    """Render the mosque icon tile at final_size px, optionally with rounded corners."""
    SCALE = 4
    S     = final_size * SCALE
    BG    = (13, 34, 51, 255)    # #0D2233 — matches splash screen IconBg exactly
    FG    = (255, 255, 255, 255) # white icon

    img  = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Background — flat or rounded square
    if rounded:
        r = int(S * 0.24)   # matches RoundedCornerShape(26.dp) in 110dp tile
        draw.rounded_rectangle([0, 0, S - 1, S - 1], radius=r, fill=BG)
    else:
        draw.rectangle([0, 0, S - 1, S - 1], fill=BG)

    # Icon centred, occupies ~72% of tile (mirrors 62dp icon in 110dp container)
    icon_r = S * 0.36
    cx, cy = S // 2, S // 2

    def sc(v):      return v * icon_r / 12.0
    def pt(x, y):   return (cx + sc(x - 12), cy + sc(y - 12))

    # Base platform
    draw.rectangle([pt(2, 15), pt(22, 21)], fill=FG)

    # Left minaret shaft + cap + ball
    draw.rectangle([pt(3.5, 9),  pt(5.5, 15)], fill=FG)
    draw.polygon(  [pt(3.5, 9),  pt(4.5, 7), pt(5.5, 9)], fill=FG)
    br = sc(0.7)
    bx, by = pt(4.5, 6.3)
    draw.ellipse([bx - br, by - br, bx + br, by + br], fill=FG)

    # Right minaret shaft + cap + ball
    draw.rectangle([pt(18.5, 9), pt(20.5, 15)], fill=FG)
    draw.polygon(  [pt(18.5, 9), pt(19.5, 7), pt(20.5, 9)], fill=FG)
    bx, by = pt(19.5, 6.3)
    draw.ellipse([bx - br, by - br, bx + br, by + br], fill=FG)

    # Central dome — full ellipse, bottom half masked
    dome_cx, dome_top = pt(12, 9)
    dome_w = sc(8); dome_h = sc(5.5)
    draw.ellipse([dome_cx - dome_w, dome_top,
                  dome_cx + dome_w, dome_top + dome_h * 2], fill=FG)
    draw.rectangle([dome_cx - dome_w - 2, dome_top + dome_h,
                    dome_cx + dome_w + 2, dome_top + dome_h * 2 + 4], fill=BG)

    # Central door arch cutout
    dw = sc(1.8); dh = sc(3.5)
    dcx, dcy = pt(12, 21)
    draw.rectangle([dcx - dw, dcy - dh, dcx + dw, dcy], fill=BG)
    draw.ellipse(  [dcx - dw, dcy - dh - dw, dcx + dw, dcy - dh + dw], fill=BG)

    # Crescent on dome tip
    cr = sc(0.9)
    ccx, ccy = pt(12, 8.5)
    draw.ellipse([ccx - cr,        ccy - cr,        ccx + cr,        ccy + cr       ], fill=FG)
    draw.ellipse([ccx - cr * 0.75, ccy - cr * 1.1,  ccx + cr * 0.75, ccy + cr * 0.5], fill=BG)

    # Downsample 4x → final size for smooth edges
    return img.resize((final_size, final_size), Image.LANCZOS)


def main():
    root = os.path.dirname(os.path.abspath(__file__))
    res  = os.path.join(root, "Sujood", "app", "src", "main", "res")

    if not os.path.isdir(res):
        print("❌ Can't find Sujood/app/src/main/res — make sure you're running this")
        print("   from your repo root (same folder as .git)")
        sys.exit(1)

    print("Generating icons...\n")
    for folder, size in SIZES.items():
        out_dir = os.path.join(res, folder)
        os.makedirs(out_dir, exist_ok=True)

        # ic_launcher.png — square (used as legacy icon and adaptive foreground)
        flat = make_icon(size, rounded=False)
        flat.save(os.path.join(out_dir, "ic_launcher.png"))

        # ic_launcher_round.png — rounded square (used on phones that show round icons)
        rnd  = make_icon(size, rounded=True)
        rnd.save(os.path.join(out_dir, "ic_launcher_round.png"))

        print(f"  ✅ {folder:20s}  {size}x{size}px")

    print(f"\n✅ Done! All icons written to Sujood/app/src/main/res/")
    print("\nNow rebuild:")
    print('  cd Sujood && ./gradlew assembleDebug')

if __name__ == "__main__":
    main()
