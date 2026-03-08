"""
fix_overlay_service.py
Run from repo root:  python fix_overlay_service.py
Then: cd Sujood && ./gradlew assembleDebug
"""
import os

path = os.path.join(
    "Sujood","app","src","main","java","com","sujood","app",
    "service","PrayerLockOverlayService.kt"
)

with open(path, encoding="utf-8") as f:
    src = f.read()

# ── Fix 1: pass adhanUrl into playAdhan() so 'settings' isn't needed there ──
# Change the call site first (inside onStartCommand coroutine)
src = src.replace(
    "if (settings.adhanEnabled)    launch(Dispatchers.Main) { playAdhan() }",
    "val adhanUrl = settings.adhanSoundUrl.ifEmpty { \"https://cdn.islamic.network/quran/audio/128/ar.alafasy/1.mp3\" }\n"
    "            if (settings.adhanEnabled)    launch(Dispatchers.Main) { playAdhan(adhanUrl) }"
)

# ── Fix 2: update the function signature to accept the URL ──
src = src.replace(
    "private fun playAdhan() {\n        try {\n            val adhanUrl = \"https://cdn.islamic.network/quran/audio/128/ar.alafasy/1.mp3\"",
    "private fun playAdhan(adhanUrl: String) {\n        try {"
)

# ── Fix 3: resolve setDataSource ambiguity — cast the String explicitly ──
# Replace bare  setDataSource(adhanUrl)  with explicit String cast
src = src.replace(
    "                setDataSource(adhanUrl)\n                isLooping = false",
    "                setDataSource(adhanUrl as String)\n                isLooping = false"
)

# Fix the fallback setDataSource(applicationContext, ...) — that's fine, leave it

with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)

print("  ✓  service/PrayerLockOverlayService.kt  patched")
print("\nNow run:")
print("  cd Sujood")
print("  ./gradlew assembleDebug")
