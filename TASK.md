# TASK: Implement StegoSlop Android Application

## Project Overview
StegoSlop is a modern Android application for LSB steganography in lossless audio (WAV) and images (PNG/Bitmap). Built with Kotlin, Jetpack Compose, and Material 3 Expressive.

Package namespace: `com.p0lymatic.stegoslop`
Compile SDK: 35, Min SDK: 26, Target SDK: 35.

---

## 1. Steganography Protocol (`app/src/main/java/com/p0lymatic/stegoslop/core/StegoProtocol.kt`)
Implement binary container serialization and deserialization:
- **Magic Bytes (4 bytes):** `SLOP` (`0x53, 0x4C, 0x4F, 0x50`)
- **Payload Type (1 byte):**
  - `0x01` = `TEXT` (UTF-8 string)
  - `0x02` = `IMAGE` (PNG / WebP lossless bytes)
  - `0x03` = `AUDIO` (WAV PCM bytes)
  - `0x04` = `FILE` (Arbitrary binary file)
- **Flags (1 byte):**
  - Bit 0: Encrypted flag (`0` = plaintext, `1` = AES-256-GCM)
  - Bits 1-2: Bits per unit (`0` = 1 bit, `1` = 2 bits, `2` = 4 bits)
- **Payload Length (4 bytes):** Big-Endian unsigned int length of the payload
- **CRC32 (4 bytes):** Big-Endian CRC32 checksum of the payload bytes
- **Salt (16 bytes):** Only present if encrypted flag is set.
- **IV / Nonce (12 bytes):** Only present if encrypted flag is set.
- **Payload Data:** The raw or encrypted payload bytes.

Provide helper methods:
- `packPayload(type: PayloadType, data: ByteArray, password: String? = null, bitsPerUnit: Int = 1): ByteArray`
- `unpackPayload(containerBytes: ByteArray, password: String? = null): UnpackedPayload`
- `CryptoUtils.kt`: AES-256-GCM encryption/decryption using `PBKDF2WithHmacSHA256` key derivation (10,000 iterations).

---

## 2. Image LSB Engine (`app/src/main/java/com/p0lymatic/stegoslop/core/ImageLsbEngine.kt`)
Operate on `android.graphics.Bitmap` (must be `Bitmap.Config.ARGB_8888`):
- `computeCapacity(width: Int, height: Int, bitsPerChannel: Int = 1): Int`: `(width * height * 3 * bitsPerChannel) / 8` bytes.
- `embed(carrier: Bitmap, payloadContainer: ByteArray, bitsPerChannel: Int = 1): Bitmap`:
  - Modifies lowest bits of Red, Green, and Blue channels for each pixel sequentially.
  - Leaves Alpha channel intact (`0xFF`).
  - Throws `IllegalArgumentException` if payload exceeds capacity.
- `extract(carrier: Bitmap, bitsPerChannel: Int = 1): ByteArray`:
  - Reads lowest bits of R, G, B channels.
  - Reads 4 bytes magic, validates `SLOP`.
  - Reads header to determine total length, then extracts exactly that many bytes.
- `generateBitPlane(carrier: Bitmap, bitIndex: Int = 0): Bitmap`:
  - Generates a visual grayscale Bitmap showing the 0-th bit plane (amplified to 0 or 255) for X-Ray inspection.

---

## 3. Audio LSB Engine (`app/src/main/java/com/p0lymatic/stegoslop/core/AudioLsbEngine.kt`)
Operate on 16-bit PCM RIFF WAV audio (`ByteArray` or stream):
- Parse WAV header: verify `"RIFF"`, format `"WAVE"`, find `"fmt "` subchunk and `"data"` subchunk.
- Audio samples are 16-bit signed integers (little-endian, 2 bytes per sample).
- `computeCapacity(wavBytes: ByteArray, bitsPerSample: Int = 1): Int`: `(dataChunkSize / 2 * bitsPerSample) / 8` bytes.
- `embed(wavBytes: ByteArray, payloadContainer: ByteArray, bitsPerSample: Int = 1): ByteArray`:
  - Replaces lowest bit of each 16-bit sample in the `"data"` chunk with payload bits.
  - Preserves exact RIFF header, channels, sample rate.
  - Throws `IllegalArgumentException` if payload exceeds capacity.
- `extract(wavBytes: ByteArray, bitsPerSample: Int = 1): ByteArray`:
  - Extracts lowest bit of each 16-bit sample from `"data"` chunk.
  - Parses `SLOP` header and extracts full container.

---

## 4. Unit Tests (`app/src/test/java/com/p0lymatic/stegoslop/StegoEngineTest.kt`)
Create comprehensive JUnit tests:
1. `testProtocolPackingAndUnpackingText()`
2. `testProtocolEncryptionDecryption()`
3. `testAudioLsbEmbedAndExtract()`: Generate synthetic 16-bit PCM WAV in memory, embed text/data, extract and assert equality.
4. `testImageLsbLogic()`: Test byte bit-packing / bit-unpacking logic.
5. `testCorruptedDataThrows()`: Validate CRC32 check fails when payload is altered.

---

## 5. UI Implementation (`app/src/main/java/com/p0lymatic/stegoslop/ui/`)
Design according to Material 3 Expressive guidelines:
- **Palette & Theme:** Deep dark theme (`surfaceContainerLowest = #08090C`, `surfaceContainer = #171B22`, `primary = #00E599` electric emerald). Dynamic color fallback.
- **Shapes:** Cards with `RoundedCornerShape(24.dp)` to `28.dp`. Action buttons with `RoundedCornerShape(28.dp)` (full pill).
- **Strict Copy Minimalism:** Zero emojis in UI labels, titles, or button text. Concise technical copy ("Select Carrier", "Extract Payload", "Inject", "Carrier Capacity", "Bit Plane Inspector").
- **Main Layout (`MainScreen.kt`):**
  - Single scrollable column (`Modifier.verticalScroll(rememberScrollState())`).
  - Animated Segmented Pill at the top: `Inject` | `Extract`.
  - **Inject Mode:**
    - Carrier Card: Choose Image or Audio file. Shows dimensions / audio duration, and calculated maximum capacity.
    - Payload Card: Tabs for `Text` or `File`. Text field for secret message or file picker.
    - Capacity Gauge: Clean linear progress bar showing used vs available bytes and percentage.
    - Security Card: Switch for "Encrypt with Password" + password input.
    - Action Pill: Big 56.dp "Inject" button.
    - Success Card: shows completion info and "Save File" button.
  - **Extract Mode:**
    - Carrier Card: Choose file to inspect.
    - Password Card: Prompt for password if encrypted flag detected.
    - Action Pill: "Extract" button.
    - Result Card:
      - Text: display message + "Copy" button.
      - Image: preview thumbnail + "Save" button.
      - Audio: playback trigger / save button.
    - Bit Plane Card: Collapsible "X-Ray Bit Plane" view to visualize LSB distribution.

---

## 6. Verification
Ensure:
- `./gradlew test` passes cleanly.
- Code has zero compilation warnings or broken imports.
