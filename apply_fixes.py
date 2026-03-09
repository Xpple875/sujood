import os

path = os.path.join("Sujood","app","src","main","java","com","sujood","app",
                    "ui","screens","splash","SplashScreen.kt")
with open(path, encoding="utf-8") as f:
    s = f.read()

# Fix 1: add missing RoundRect import
if "import androidx.compose.ui.geometry.RoundRect" not in s:
    s = s.replace(
        "import androidx.compose.ui.geometry.Rect",
        "import androidx.compose.ui.geometry.Rect\nimport androidx.compose.ui.geometry.RoundRect\nimport androidx.compose.ui.geometry.CornerRadius"
    )

# Fix 2: RoundRect(Rect(...), r, r) -> RoundRect(Rect(...), CornerRadius(r))
# The two-float constructor doesn't exist in older compose; use CornerRadius overload
s = s.replace(
    "addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), r, r))",
    "addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(r)))"
)
s = s.replace(
    "addRoundRect(RoundRect(\n                        Rect(inset, inset, size.width - inset, size.height - inset), r, r))",
    "addRoundRect(RoundRect(Rect(inset, inset, size.width - inset, size.height - inset), CornerRadius(r)))"
)

with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(s)
print("  \u2713 SplashScreen.kt (fixed RoundRect)")

print("\nDone! Run:")
print("  git add .")
print("  git commit -m \"fix: RoundRect CornerRadius in SplashScreen\"")
print("  git push")
print("  cd Sujood && ./gradlew assembleDebug")
