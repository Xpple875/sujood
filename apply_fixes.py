import os

ROOT = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(ROOT, "Sujood", "app", "src", "main", "java", "com", "sujood", "app")
path = os.path.join(BASE, "ui/screens/qibla/QiblaScreen.kt")

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# BUG on line 154:
#   val needleTarget = accumulatedHead + shortestDelta(cur, ((qiblaDirection - cur + 360f) % 360f))
#
# shortestDelta(from, to) computes shortest arc between two ABSOLUTE bearings.
# But ((qiblaDirection - cur + 360f) % 360f) already converts qiblaDirection into
# a relative offset from cur, so cur gets subtracted TWICE inside shortestDelta.
# Result: needle is ~100 degrees wrong whenever the phone isn't facing exactly North.
#
# FIX: pass qiblaDirection directly — shortestDelta handles wraparound by itself.

OLD = "    val needleTarget = accumulatedHead + shortestDelta(cur, ((qiblaDirection - cur + 360f) % 360f))"
NEW = "    val needleTarget = accumulatedHead + shortestDelta(cur, qiblaDirection)"

if OLD in content:
    content = content.replace(OLD, NEW)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("✅ QiblaScreen.kt fixed — needle now tracks Qibla correctly at all phone orientations")
else:
    for i, line in enumerate(content.splitlines(), 1):
        if "needleTarget" in line and "accumulatedHead" in line:
            print(f"❌ Couldn't auto-patch. Line {i} reads:")
            print(f"   {line.strip()}")
            print()
            print("Manually replace that line with:")
            print(f"   {NEW.strip()}")
