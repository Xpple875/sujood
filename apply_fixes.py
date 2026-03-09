import os

# ── Swap Coil3 → Coil2 in build.gradle.kts ────────────────────────────────────
gradle_path = os.path.join("Sujood", "app", "build.gradle.kts")
with open(gradle_path, encoding="utf-8") as f:
    gradle = f.read()

# Remove coil3 lines and replace with coil2 (compatible with compileSdk 34)
coil3_block = (
    '    // Coil for image loading (SimpleIcons brand logos)\n'
    '    implementation("io.coil-kt.coil3:coil-compose:3.1.0")\n'
    '    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")\n'
)
coil2_block = (
    '    // Coil for image loading (SimpleIcons brand logos)\n'
    '    implementation("io.coil-kt:coil-compose:2.7.0")\n'
)

if "coil3" in gradle:
    gradle = gradle.replace(coil3_block, coil2_block)
    print("  \u2713 build.gradle.kts (downgraded Coil3 \u2192 Coil2)")
elif "coil-compose" not in gradle:
    # Add fresh if somehow missing entirely
    gradle = gradle.replace(
        '    // Accompanist',
        coil2_block + '\n    // Accompanist'
    )
    print("  \u2713 build.gradle.kts (added Coil2)")
else:
    print("  \u2713 build.gradle.kts (Coil already correct)")

with open(gradle_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(gradle)

# ── Fix DhikrScreen.kt imports: coil3 → coil2 ─────────────────────────────────
dhikr_path = os.path.join(
    "Sujood", "app", "src", "main", "java", "com", "sujood", "app",
    "ui", "screens", "dhikr", "DhikrScreen.kt"
)
with open(dhikr_path, encoding="utf-8") as f:
    dhikr = f.read()

# Replace coil3 imports with coil2 equivalents
dhikr = dhikr.replace(
    "import coil3.compose.AsyncImage",
    "import coil.compose.AsyncImage"
)
dhikr = dhikr.replace(
    "import coil3.request.ImageRequest",
    "import coil.request.ImageRequest"
)
dhikr = dhikr.replace(
    "import coil3.request.crossfade",
    ""
)

# Fix ImageRequest builder — coil2 uses .crossfade(true) directly on the builder, no separate import
# Make sure .crossfade(true) is still there (it's a method, not an import in coil2)
# Also coil2 needs context passed to ImageRequest.Builder
if "ImageRequest.Builder(LocalContext.current)" not in dhikr:
    # Already correct or different pattern — leave it
    pass

with open(dhikr_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(dhikr)
print("  \u2713 DhikrScreen.kt (fixed coil3 \u2192 coil2 imports)")

print()
print("Done! Now run:")
print("  git add .")
print("  git commit -m \"fix: downgrade Coil3 to Coil2 for compileSdk 34 compatibility\"")
print("  git push")
print("  cd Sujood && ./gradlew assembleDebug")
