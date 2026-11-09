#!/bin/bash
set -e
cd /home/aleph/StegoSlop

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
echo "[*] Launching omp worker for StegoSlop 1.0.4 sprint..."
/home/aleph/.local/bin/omp -p --auto-approve @TASK_SPRINT.md "Read @TASK_SPRINT.md and implement all specifications for StegoSlop 1.0.4:
1. Wrap MainScreen and AboutScreen in AnimatedContent with horizontal slide + fade transition for smooth screen transitions.
2. Remove 'No Carrier' status pill when no carrier is loaded.
3. Verify dynamicColor is true by default in Theme.kt.
4. Implement AudioCodecBridge.kt for FLAC support using Android MediaCodec/MediaExtractor, wire into MainScreen for audio picking/saving.
5. Bump version to 1.0.4 (versionCode 5) in app/build.gradle.kts.
Do NOT modify gradle.properties.
Ensure ./gradlew test passes cleanly."

echo "[*] Worker finished. Sending ntfy notification..."
curl -s --retry 2 -d "РАБОЧИЙ: StegoSlop 1.0.4 готов!" https://ntfy.sh/alephtheagentnotifications || true

echo "[*] Complete."
