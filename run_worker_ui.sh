#!/bin/bash
set -e
cd /home/aleph/StegoSlop

echo "[*] Launching omp worker for UI refactoring..."
/home/aleph/.local/bin/omp -p --auto-approve @TASK_UI.md "Read @TASK_UI.md and inspect /home/aleph/FlashPoint/app/src/main/java/com/p0lymatic/flashpoint/ui/MainScreen.kt.
Refactor app/src/main/java/com/p0lymatic/stegoslop/ui/MainScreen.kt to match FlashPoint style:
1. Remove all top boilerplate/subtitles.
2. Use TopAppBar with StegoSlop title, Ready status pill, and Info icon opening AboutScreen.
3. Spring-sliding pill mode switcher (Inject / Extract) with spring animation.
4. Directional AnimatedContent transitions.
5. Clean cards with RoundedCornerShape(24.dp), surfaceContainerHigh, clean chips and 56dp action pills.
6. Bump version to 1.0.3 (versionCode 4) in app/build.gradle.kts.
Ensure ./gradlew test passes cleanly."

echo "[*] Worker finished. Sending ntfy notification..."
curl -s --retry 2 -d "РАБОЧИЙ: StegoSlop UI рефакторинг завершен!" https://ntfy.sh/alephtheagentnotifications || true

echo "[*] Complete."
