#!/bin/bash
set -e
cd /home/aleph/StegoSlop

echo "[*] Launching omp worker for StegoSlop..."
/home/aleph/.local/bin/omp -p --auto-approve @TASK.md "Read @TASK.md and implement all specifications for StegoSlop:
1. Core steganography protocol (StegoProtocol.kt, CryptoUtils.kt).
2. Image LSB engine (ImageLsbEngine.kt).
3. Audio LSB engine (AudioLsbEngine.kt).
4. Unit tests in app/src/test/java/com/p0lymatic/stegoslop/StegoEngineTest.kt. Verify tests pass with ./gradlew test.
5. Compose UI in app/src/main/java/com/p0lymatic/stegoslop/ui/ (Theme, MainScreen, MainActivity) following Material 3 Expressive guidelines.
Ensure ./gradlew test passes cleanly."

echo "[*] Worker finished. Sending ntfy notification..."
curl -s --retry 2 -d "РАБОЧИЙ: StegoSlop реализация завершена!" https://ntfy.sh/alephtheagentnotifications || true

echo "[*] Complete."
