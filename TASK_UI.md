# TASK: Refactor StegoSlop UI to match FlashPoint design

## Objectives
Take direct inspiration from `/home/aleph/FlashPoint/app/src/main/java/com/p0lymatic/flashpoint/ui/MainScreen.kt`.

1. **Remove Boilerplate Header:**
   - In `MainScreen.kt`, remove the large title banner ("STEGOSLOP - Lossless LSB Steganography Engine", description, etc.).
   - Use a standard `TopAppBar` in `Scaffold`:
     - Title: `Text("StegoSlop", fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)`
     - Actions:
       - Status badge chip (green circle + "Ready" or "No Carrier")
       - Info icon (`Icons.Default.Info`) opening `AboutScreen`

2. **Integrate AboutScreen:**
   - Use `showAboutScreen` state with `BackHandler(enabled = showAboutScreen) { showAboutScreen = false }`.
   - When true, render `AboutScreen(onBack = { showAboutScreen = false })`.

3. **Spring-Sliding Pill Mode Switcher:**
   - In `MainScreen.kt`, replace the basic tab bar with FlashPoint's animated sliding pill:
     - `Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth())`
     - Inside `BoxWithConstraints`:
       - `pillOffset by animateDpAsState(targetValue = ..., animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow))`
       - Sliding box: `height(44.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer)`
       - Labels: "Inject" and "Extract" with `labelLarge`.

4. **Smooth Directional Animations:**
   - Use `AnimatedContent` for Mode switching with directional horizontal slides (`slideInHorizontally` + `slideOutHorizontally` + `fadeIn` / `fadeOut`).

5. **FlashPoint-Style Card Aesthetics:**
   - Cards: `RoundedCornerShape(24.dp)`, `containerColor = surfaceContainerHigh`.
   - Header labels: `labelMedium`, `SemiBold`, `color = onSurfaceVariant`.
   - Segmented pill chips: `RoundedCornerShape(14.dp)`.
   - Big bold metrics: `headlineLarge`, bold, `primary`.
   - Action buttons: `RoundedCornerShape(28.dp)`, `height(56.dp)`, bold text.
   - Clean linear capacity bar with percentage and byte counts.
   - Expandable password field with `Switch`.

6. **Version Bump:**
   - Bump `versionCode = 4` and `versionName = "1.0.3"` in `app/build.gradle.kts`.

7. **Verification:**
   - Run `./gradlew test` to ensure zero compilation errors and all tests pass.
