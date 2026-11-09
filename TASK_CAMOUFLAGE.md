# TASK: Implement Camouflage Filename Presets & In-Place Overwrite

## Requirements
Add anti-forensic filename camouflage and in-place overwrite to StegoSlop.

### 1. Camouflage Utility (`app/src/main/java/com/p0lymatic/stegoslop/core/CamouflageUtils.kt`)
Create helper enum and functions:
```kotlin
enum class CamouflagePreset {
    SCREENSHOT,      // "Снимок экрана YYYY-MM-DD в HH.MM.SS" or "Screenshot_YYYYMMDD-HHMMSS"
    CAMERA_DCIM,     // "IMG_XXXX" (random 0001..9999) or "IMG_YYYYMMDD_HHMMSS"
    YOUTUBE,         // "maxresdefault"
    TELEGRAM,        // "photo_YYYY-MM-DD_HH-MM-SS"
    VK_HASH,         // "vk_photo_c3f819b2" (random 8-char hex)
    WHATSAPP,        // "IMG-YYYYMMDD-WA0001" or "AUD-YYYYMMDD-WA0001"
    GENERIC          // "image" or "audio" / "track_01"
}
```
Provide:
- `generateDecoyName(preset: CamouflagePreset, extension: String): String`
- `randomizeDecoyName(isAudio: Boolean, extension: String): String`
- Unit tests in `StegoEngineTest.kt` verifying each preset outputs non-empty names with the correct extension.

### 2. UI Integration in `MainScreen.kt` (Inject Result Card)
When carrier is injected (`injectedBitmap != null || injectedWavBytes != null`):
- Show a **File Camouflage Card**:
  - `OutlinedTextField` for custom output filename.
  - Trailing icon: Dice / Casino / Refresh icon to randomize the name with one tap.
  - Horizontal row of `PillChip` preset chips:
    - If Image: `[ Screenshot ]`, `[ Camera ]`, `[ YouTube ]`, `[ Telegram ]`, `[ VK Hash ]`, `[ Generic ]`
    - If Audio: `[ WhatsApp ]`, `[ Voice ]`, `[ Track ]`, `[ Generic ]`
  - Tapping any chip immediately updates the filename field.
- **In-Place Overwrite Option:**
  - Row with `Switch`: "Overwrite Original File".
  - If switched ON: shows warning note: "Will replace the original carrier file in-place without creating new files."
  - Action button updates text to: "Overwrite Original File" and overwrites the source file Uri directly.
  - If switched OFF: standard "Save Stego File" button using `saveInjectedFilePicker.launch(outputFileName)`.

### 3. Strict Development Rules
- Do NOT touch `gradle.properties`.
- Bump `versionCode = 6` and `versionName = "1.0.5"` in `app/build.gradle.kts`.
- Run `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew test` to ensure all tests pass.
- When verified, make a clean local git commit:
  `feat(camouflage): filename presets, dice randomizer, and in-place overwrite toggle`
  with category-prefixed bullet lists (`- added: ...`, `- updated: ...`).
- Do NOT push to remote; the orchestrator handles final push verification.
