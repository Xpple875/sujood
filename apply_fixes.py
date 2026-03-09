import os, re

path = os.path.join("Sujood","app","src","main","java","com","sujood","app",
                    "ui","screens","dhikr","DhikrScreen.kt")
with open(path, encoding="utf-8") as f:
    s = f.read()

# Find every AppIconBox call and strip any extra args — keep only 3 params
# Replace any variant: AppIconBox(x, y, z, w) -> AppIconBox(x, y, z)
s = re.sub(
    r'AppIconBox\(([^,]+),\s*([^,]+),\s*([^,)]+),\s*[^)]+\)',
    r'AppIconBox(\1, \2, \3)',
    s
)

with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(s)
print("  \u2713 DhikrScreen.kt fixed")
print()
print("Run:")
print("  git add .")
print("  git commit -m \"fix: AppIconBox call args\"")
print("  git push")
print("  cd Sujood && ./gradlew assembleDebug")
