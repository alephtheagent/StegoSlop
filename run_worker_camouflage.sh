#!/bin/bash
set -e
cd /home/aleph/StegoSlop

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
echo "[*] Launching omp worker for Camouflage slice..."
/home/aleph/.local/bin/omp -p --auto-approve @TASK_CAMOUFLAGE.md "Read @TASK_CAMOUFLAGE.md and implement the Camouflage slice:
1. Create core/CamouflageUtils.kt with presets, randomizer, and tests in StegoEngineTest.kt.
2. In MainScreen.kt, add the Camouflage card when injected container is ready with filename field, dice randomizer, preset chips, and in-place overwrite toggle.
3. Bump version to 1.0.5 (versionCode 6) in app/build.gradle.kts.
4. Run ./gradlew test to verify.
5. Create a clean local git commit adhering strictly to Conventional Commits:
feat(camouflage): filename presets, dice randomizer, and in-place overwrite toggle
Do NOT modify gradle.properties. Do NOT push to remote."

echo "[*] Worker finished. Sending ntfy notification..."
curl -s --retry 2 -d "РАБОЧИЙ: StegoSlop Camouflage готов!" https://ntfy.sh/alephtheagentnotifications || true

echo "[*] Complete."
