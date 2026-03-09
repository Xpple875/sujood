import os, re

SRC = os.path.join("Sujood","app","src","main","java","com","sujood","app")
RES = os.path.join("Sujood","app","src","main","res")
DRW = os.path.join(RES, "drawable")

def write_res(fname, content):
    p = os.path.join(DRW, fname)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  \u2713 drawable/{fname}")

def write_src(rel, content):
    p = os.path.join(SRC, *rel.split("/"))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  \u2713 {rel}")

def patch_src(rel, old, new, label=""):
    p = os.path.join(SRC, *rel.split("/"))
    with open(p, encoding="utf-8") as f:
        s = f.read()
    if old not in s:
        print(f"  ! not found [{label}] in {rel}")
        return
    s = s.replace(old, new, 1)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)
    print(f"  \u2713 {rel} [{label}]")

# ══════════════════════════════════════════════════════════════════════════════
# 1. APP ICON — proper viewport, no <group> transform needed
#    Use viewportWidth/Height = 24 and let the adaptive icon system scale it.
#    The Mosque path is defined in a 24x24 viewport — just set the canvas to
#    108x108 but keep viewport 24x24 so it fills perfectly centred.
# ══════════════════════════════════════════════════════════════════════════════
write_res("ic_launcher_foreground.xml", '''\
<?xml version="1.0" encoding="utf-8"?>
<!--
    Material Icons "Mosque" — Apache License 2.0
    viewport 24x24 fills the adaptive icon safe zone automatically
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M21.32,9.55C21.76,9.39 22,8.92 21.84,8.48C21.47,7.5 20.61,6.76 19.57,6.54L19,6.42V6C19,4.9 18.1,4 17,4C15.9,4 15,4.9 15,6V6.42L14.43,6.54C13.39,6.76 12.53,7.5 12.16,8.48C12,8.92 12.24,9.39 12.68,9.55L14,10.05V11H10V10.05L11.32,9.55C11.76,9.39 12,8.92 11.84,8.48C11.47,7.5 10.61,6.76 9.57,6.54L9,6.42V6C9,4.9 8.1,4 7,4C5.9,4 5,4.9 5,6V6.42L4.43,6.54C3.39,6.76 2.53,7.5 2.16,8.48C2,8.92 2.24,9.39 2.68,9.55L4,10.05V11H2V13H4V20H2V22H22V20H20V13H22V11H20V10.05L21.32,9.55ZM10,20H7V17C7,15.9 7.9,15 9,15H10V20ZM13,20H11V15H13V20ZM17,20H14V15H15C16.1,15 17,15.9 17,17V20Z"/>
</vector>
''')

write_res("ic_launcher_background.xml", '''\
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0D2233"/>
</shape>
''')

adaptive_xml = '''\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
'''
for density in ["mipmap-hdpi","mipmap-mdpi","mipmap-xhdpi","mipmap-xxhdpi","mipmap-xxxhdpi"]:
    for icon_name in ["ic_launcher.xml","ic_launcher_round.xml"]:
        p = os.path.join(RES, density, icon_name)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(adaptive_xml)
print("  \u2713 mipmap-*/ic_launcher*.xml")

# ══════════════════════════════════════════════════════════════════════════════
# 2. DhikrScreen — remove icon boxes, keep only app name + switch
#    (still the original emoji version on user's machine)
# ══════════════════════════════════════════════════════════════════════════════
dhikr_path = os.path.join(SRC, "ui","screens","dhikr","DhikrScreen.kt")
with open(dhikr_path, encoding="utf-8") as f:
    dhikr = f.read()

# Replace the inner Row that has [Box(emoji) + Text(name)] with just Text(name)
old_icon_row = '''\
                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                                    .background(if (isLocked) PrimaryBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)),
                                                    contentAlignment = Alignment.Center) {
                                                    Text(app.emoji, fontSize = 18.sp)
                                                }
                                                Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                            }'''

new_icon_row = '''\
                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                            }'''

if old_icon_row in dhikr:
    dhikr = dhikr.replace(old_icon_row, new_icon_row, 1)
    print("  \u2713 DhikrScreen icon boxes removed")
else:
    # Regex fallback — match any version of the emoji box
    pattern = re.compile(
        r'Row\(verticalAlignment = Alignment\.CenterVertically,\s*\n'
        r'\s*horizontalArrangement = Arrangement\.spacedBy\(12\.dp\)\) \{\s*\n'
        r'\s*Box\(modifier = Modifier\.size\(36\.dp\).*?Text\(app\.emoji.*?\)\s*\n'
        r'\s*\}\s*\n'
        r'\s*Text\(app\.name(.*?)\)\s*\n'
        r'\s*\}',
        re.DOTALL
    )
    def replace_row(m):
        name_args = m.group(1)
        return (
            f'Row(verticalAlignment = Alignment.CenterVertically,\n'
            f'                                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {{\n'
            f'                                                Text(app.name{name_args})\n'
            f'                                            }}'
        )
    dhikr, n = pattern.subn(replace_row, dhikr, count=1)
    print(f"  {'✓' if n else '!'} DhikrScreen icon boxes removed (regex, {n} match)")

with open(dhikr_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(dhikr)
print("  \u2713 DhikrScreen.kt saved")

# ══════════════════════════════════════════════════════════════════════════════
# 3. BottomNavBar — increase height from 72dp to 84dp for more top breathing room
# ══════════════════════════════════════════════════════════════════════════════
patch_src(
    "ui/components/BottomNavBar.kt",
    "            .height(72.dp)",
    "            .height(84.dp)",
    "height 72→84"
)

print()
print("Done! Run:")
print("  git add .")
print("  git commit -m \"fix: app icon viewport, remove icon boxes, taller navbar\"")
print("  git push")
print("  cd Sujood && ./gradlew assembleDebug")
