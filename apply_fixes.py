import os, re

SRC = os.path.join("Sujood","app","src","main","java","com","sujood","app")

# ── MainActivity.kt ────────────────────────────────────────────────────────────
# Move navbar out of Scaffold bottomBar into a Box overlay so the rounded
# top corners float over content with no dark fill behind them.

path = os.path.join(SRC, "MainActivity.kt")
with open(path, encoding="utf-8") as f:
    s = f.read()

# Add Alignment import if not present
if "import androidx.compose.ui.Alignment" not in s:
    s = s.replace(
        "import androidx.compose.ui.Modifier",
        "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier"
    )

# Replace the Scaffold + bottomBar block with a Box overlay approach
old_scaffold = '''\
    Box(\n        modifier = Modifier\n            .fillMaxSize()\n            .background(DeepNavy)\n    ) {\n        Scaffold(\n            modifier = Modifier.fillMaxSize(),\n            containerColor = Color.Transparent,\n            bottomBar = {\n                if (showBottomNavBar) {\n                    AnimatedVisibility(\n                        visible = showBottomNavBar,\n                        enter = fadeIn() + slideInVertically { fullHeight -> fullHeight },\n                        exit = fadeOut() + slideOutVertically { fullHeight -> fullHeight }\n                    ) {\n                        GlassmorphicBottomNavBar(\n                            currentRoute = currentRoute,\n                            onNavigate = { newRoute ->\n                                navController.navigate(newRoute) {\n                                    popUpTo(Screen.Home.route) { saveState = true }\n                                    launchSingleTop = true\n                                    restoreState = true\n                                }\n                            }\n                        )\n                    }\n                }\n            }\n        ) { innerPadding ->\n            NavHost(\n                navController = navController,\n                startDestination = Screen.Splash.route,\n                modifier = Modifier\n                    .fillMaxSize()\n                    .padding(innerPadding)\n            ) {'''

new_scaffold = '''\
    Box(\n        modifier = Modifier\n            .fillMaxSize()\n            .background(DeepNavy)\n    ) {\n        // Scaffold with NO bottomBar — navbar is overlaid as a floating Box below\n        Scaffold(\n            modifier = Modifier.fillMaxSize(),\n            containerColor = Color.Transparent,\n            bottomBar = {}\n        ) { innerPadding ->\n            NavHost(\n                navController = navController,\n                startDestination = Screen.Splash.route,\n                modifier = Modifier\n                    .fillMaxSize()\n                    // Don't use innerPadding — we overlay the navbar manually\n            ) {'''

if old_scaffold in s:
    s = s.replace(old_scaffold, new_scaffold, 1)
    print("  \u2713 Scaffold bottomBar removed")
else:
    # Fallback: use regex to do the same replacement more flexibly
    import re
    pattern = re.compile(
        r'(Scaffold\s*\(\s*\n\s*modifier = Modifier\.fillMaxSize\(\),\s*\n\s*containerColor = Color\.Transparent,\s*\n\s*bottomBar = \{).*?(\} // end bottomBar\s*\n\s*\) \{ innerPadding ->)',
        re.DOTALL
    )
    # Just zero out the bottomBar content
    s = re.sub(
        r'bottomBar = \{[^}]*if \(showBottomNavBar\).*?}\s*\n\s*\}',
        'bottomBar = {}',
        s,
        count=1,
        flags=re.DOTALL
    )
    # Remove .padding(innerPadding) from NavHost modifier so content goes full height
    s = s.replace(
        '.padding(innerPadding)',
        '// padding handled by overlaid navbar'
    )
    print("  \u2713 Scaffold bottomBar zeroed out (fallback regex)")

# Now add the floating navbar overlay — insert it just before the closing of the outer Box
# We look for the closing of the Scaffold and add our overlay after it
# Pattern: find "} // end Scaffold" or just the last closing braces of the Scaffold block
# Strategy: add the overlay Box right after the Scaffold's closing brace, before the outer Box closes

# Find the innerPadding block closing and add overlay after Scaffold closes
# We insert the overlay Box as a sibling in the outer Box, after Scaffold
navbar_overlay = '''

        // ── Floating navbar overlay — sits above content, transparent corners ──
        AnimatedVisibility(
            visible = showBottomNavBar,
            enter = fadeIn() + slideInVertically { it },
            exit  = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            GlassmorphicBottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { newRoute ->
                    navController.navigate(newRoute) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    } // outer Box
}'''

# Remove the existing last two closing braces ("    }\n}" = outer Box + setContent)
# and replace with the overlay + proper close
if "    } // outer Box\n}" not in s:
    # Replace the last `    }\n}` in the file
    last_close = s.rfind("\n    }\n}")
    if last_close != -1:
        s = s[:last_close] + navbar_overlay
        print("  \u2713 Navbar overlay Box injected")
    else:
        print("  ! Could not find insertion point for overlay")
else:
    print("  ! already patched")

with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(s)
print("  \u2713 MainActivity.kt saved")

# ── BottomNavBar.kt — ensure background is truly transparent outside the shape
# The fix: don't set any background on the outer Box — let the clip handle it.
# Also add windowInsets handling so it doesn't add extra space.

navbar_path = os.path.join(SRC, "ui","components","BottomNavBar.kt")
with open(navbar_path, encoding="utf-8") as f:
    nb = f.read()

# Remove any fillMaxWidth background on the outer Box that could bleed outside clip
# The shadow + clip + background chain is correct — but make sure there's no
# extra Modifier.background() call outside the clip
# Also: remove navigationBarsPadding from the Box itself — handle it inside the shape
# so the shape boundary = the visible area, nothing outside it

# Fix: the navigationBarsPadding should pad the CONTENT inside, not expand the clipped Box
# Replace: .navigationBarsPadding().height(72.dp)
# With:    .height(72.dp) and add navigationBarsPadding inside the Row

nb = nb.replace(
    "            .navigationBarsPadding()\n            .height(72.dp)",
    "            .height(72.dp)"
)

# Add navigationBarsPadding to the Row content instead
nb = nb.replace(
    "        Row(\n            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),",
    "        Row(\n            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 4.dp),"
)

with open(navbar_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(nb)
print("  \u2713 BottomNavBar.kt (nav padding moved inside shape)")

print()
print("Done! Run:")
print("  git add .")
print("  git commit -m \"fix: floating navbar, no dark corners, proper overlay\"")
print("  git push")
print("  cd Sujood && ./gradlew assembleDebug")
