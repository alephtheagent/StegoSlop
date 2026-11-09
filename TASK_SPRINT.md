# TASK: StegoSlop 1.0.4 - Smooth Transitions, Monet, FLAC & Lossless Carriers

## 1. Smooth Transition to AboutScreen
In `app/src/main/java/com/p0lymatic/stegoslop/ui/MainScreen.kt`:
- Replace the abrupt `if (showAboutScreen) { AboutScreen(...); return }` with `AnimatedContent(targetState = showAboutScreen, ...)`:
  - Transition forward (Main -> About):
    `(slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(animationSpec = tween(300)))`
    `  .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> -width / 3 } + fadeOut(animationSpec = tween(300)))`
  - Transition back (About -> Main):
    `(slideInHorizontally(animationSpec = tween(300)) { width -> -width / 3 } + fadeIn(animationSpec = tween(300)))`
    `  .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut(animationSpec = tween(300)))`
- Ensure `BackHandler(enabled = showAboutScreen) { showAboutScreen = false }` triggers the smooth slide back.

## 2. TopAppBar Polish
- If `!hasCarrier`: do not display any status chip at all. Zero "No Carrier" text. Just clean `StegoSlop` title on the left and the Info icon on the right.
- If `hasCarrier`: display a compact `Ready` pill with a green dot and carrier format badge (e.g. "PNG" or "WAV" / "FLAC").

## 3. Dynamic Color (Monet Theming)
- In `app/src/main/java/com/p0lymatic/stegoslop/ui/theme/Theme.kt`:
  Ensure `dynamicColor: Boolean = true` is default so on Android 12+ it uses `dynamicDarkColorScheme(context)`.

## 4. FLAC & Lossless Carriers Support
- Create `app/src/main/java/com/p0lymatic/stegoslop/core/AudioCodecBridge.kt`:
  - `isFlac(bytes: ByteArray): Boolean`: checks for `"fLaC"` magic bytes.
  - `decodeFlacToPcmWav(context: Context, flacBytes: ByteArray): ByteArray`: Decodes FLAC bytes into standard 16-bit PCM WAV using Android's native `MediaExtractor` and `MediaCodec.createDecoderByType("audio/flac")`.
  - `encodePcmWavToFlac(context: Context, wavBytes: ByteArray): ByteArray`: Encodes 16-bit PCM WAV into FLAC using Android's native `MediaCodec.createEncoderByType("audio/flac")`.
- In `MainScreen.kt`:
  - When picking an audio carrier, accept both `.wav` and `.flac`. If FLAC is picked, decode to PCM in memory and process via `AudioLsbEngine`.
  - When saving an injected audio carrier, allow saving as `.wav` or `.flac`.
  - When picking images, accept `.png`, `.webp`, `.bmp`.

## 5. Tests & Verification
- In `app/src/test/java/com/p0lymatic/stegoslop/StegoEngineTest.kt`:
  Ensure all unit tests continue to pass.
- In `app/build.gradle.kts`:
  Bump `versionCode = 5`, `versionName = "1.0.4"`.
- Run `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew test` to ensure clean pass.
- Remember: do NOT add `org.gradle.java.home` to `gradle.properties` (keep it clean for GitHub Actions).
