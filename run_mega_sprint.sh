#!/bin/bash
set -e
cd /home/aleph/StegoSlop

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
echo "[*] Launching omp worker for Mega Sprint (3 Features, Atomic Commits, Version Bump & Push)..."

/home/aleph/.local/bin/omp -p --auto-approve @TASK_MEGA_SPRINT.md "Read @TASK_MEGA_SPRINT.md.
Execute sequentially:
1. Feature 1 (Payload filename preservation): implement, test with ./gradlew test, create Git Commit 1.
2. Feature 2 (Zero-Width Unicode stego): implement, test with ./gradlew test, create Git Commit 2.
3. Feature 3 (Forensic Force-Decode): implement, test with ./gradlew test, create Git Commit 3.
4. Version Bump: bump app/build.gradle.kts to 1.0.6 (versionCode 7), create Commit 4, verify ./gradlew test, and push to origin main.
Do NOT modify gradle.properties. Follow the exact commit messages in TASK_MEGA_SPRINT.md."

echo "[*] Worker finished. Sending ntfy notification..."
curl -s --retry 2 -d "РАБОЧИЙ: StegoSlop 1.0.6 Mega Sprint завершен и запушен!" https://ntfy.sh/alephtheagentnotifications || true

echo "[*] Complete."
