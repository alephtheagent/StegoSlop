# TASK: Multi-Feature Sprint (3 Atomic Commits + Final Push)

## CRITICAL WORKFLOW RULES
1. Implement Feature 1 -> Verify `./gradlew test` -> Create Git Commit 1.
2. Implement Feature 2 -> Verify `./gradlew test` -> Create Git Commit 2.
3. Implement Feature 3 -> Verify `./gradlew test` -> Create Git Commit 3.
4. Bump version to 1.0.6 (versionCode 7) -> Final commit 4 -> Push to `origin main`.
5. Do NOT touch `gradle.properties`. Use `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.

---

## FEATURE 1: Payload Filename & Extension Preservation
### Protocol (`core/StegoProtocol.kt`):
- Add `FLAG_HAS_FILENAME = 0x08`.
- In `packPayload(...)`, add optional `fileName: String? = null`.
  If non-null: set `FLAG_HAS_FILENAME` in flags, encode `fileName.toByteArray(Charsets.UTF_8)`, store 1 byte `fileNameLength` + `fileNameBytes`.
- In `UnpackedPayload`, add `val fileName: String? = null`.
- In `unpackPayload(...)`, if `flags and FLAG_HAS_FILENAME != 0`, read `fileNameLength` and decode `fileName`.
### UI (`ui/MainScreen.kt`):
- In Inject mode, when Payload Tab == FILE:
  - Add Switch: "Preserve original filename" (default = true).
  - Pass `fileName = if (preserveFileName) payloadFileName else null` to `packPayload`.
- In Extract mode:
  - Show `payload.fileName ?: "extracted_file.bin"`.
  - When saving, launch `saveExtractedFilePicker.launch(payload.fileName ?: "extracted_file.bin")`.
### Tests (`StegoEngineTest.kt`):
- Test packing and unpacking with a filename (e.g. "document.pdf") and assert it matches.
### Commit 1:
```
feat(protocol): preserve original payload filename and extension

- added: FLAG_HAS_FILENAME flag in StegoProtocol header
- added: filename preservation toggle in payload selection UI
- added: automatic filename restoration in extraction save picker
- added: unit tests for filename preservation in StegoEngineTest
```

---

## FEATURE 2: Zero-Width Unicode Steganography (Text-in-Text)
### Engine (`core/ZeroWidthStego.kt`):
Create object `ZeroWidthStego`:
- Characters:
  - `\u200B` = Bit 0 (Zero-Width Space)
  - `\u200C` = Bit 1 (Zero-Width Non-Joiner)
  - `\u200D` = Delimiter / Separator (Zero-Width Joiner)
  - `\uFEFF` = Magic Header (Zero-Width No-Break Space)
- `encode(coverText: String, secretData: ByteArray, password: String? = null): String`:
  - If password provided, encrypt `secretData` with AES-256-GCM.
  - Converts bytes into zero-width bit string with signature and 4-byte length prefix.
  - Inserts zero-width string into `coverText` (after first space or middle of text).
- `decode(text: String, password: String? = null): ByteArray`:
  - Extracts zero-width character sequence from `text`.
  - Parses bits, verifies signature and length.
  - Decrypts with password if encrypted, returns original bytes.
- `hasHiddenData(text: String): Boolean`: checks if text contains `\uFEFF` followed by zero-width characters.
### UI (`ui/MainScreen.kt`):
- Add `CarrierType.TEXT` to `CarrierType` enum: `[ Image ]` | `[ Audio ]` | `[ Text ]`.
- When Carrier == `TEXT`:
  - Inject Mode: Show Cover Text field (innocent message e.g. "Привет, как дела?") and Secret Message field.
  - Injected Output: Card with the visible text and a "Copy to Clipboard" button.
  - Extract Mode: Paste Text input field. Tapping "Extract" recovers the hidden secret from zero-width characters.
### Tests (`StegoEngineTest.kt`):
- Test `ZeroWidthStego.encode` and `decode` roundtrip with plain text.
- Test with password encryption.
### Commit 2:
```
feat(zerowidth): zero-width unicode steganography for covert text-in-text transport

- added: ZeroWidthStego core engine encoding bits as invisible unicode characters
- added: Text carrier option in MainScreen for embedding invisible secrets into chat messages
- added: text extraction and clipboard integration in extract mode
- added: unit tests for zero-width encoding, decoding, and encryption
```

---

## FEATURE 3: Forensic Force-Decode & Raw LSB Dump
### Engine (`core/ImageLsbEngine.kt` and `core/AudioLsbEngine.kt`):
- In `ImageLsbEngine`: add `extractRawBits(bitmap: Bitmap, bitsPerChannel: Int = 1, maxBytes: Int = 262144): ByteArray`: dumps raw LSB bits sequentially without header checks.
- In `AudioLsbEngine`: add `extractRawBits(wavBytes: ByteArray, bitsPerSample: Int = 1, maxBytes: Int = 262144): ByteArray`: dumps raw LSB bits from PCM data.
- In `StegoProtocol`: add `unpackPayloadForced(containerBytes: ByteArray, password: String? = null): UnpackedPayload`: attempts unpacking, but ignores CRC32 mismatch errors (returns data with a warning flag instead of throwing).
### UI (`ui/MainScreen.kt`):
- In Extract mode, add Switch: "Force Decode (Bypass Header & CRC)".
- When enabled, runs raw dump or forced unpack.
- Displays raw ASCII / Hex preview and "Save Raw Dump" button.
### Tests (`StegoEngineTest.kt`):
- Test raw bit extraction from image and audio.
- Test forced unpack of corrupted payload.
### Commit 3:
```
feat(forensics): force-decode mode bypassing SLOP header and CRC32 checks

- added: extractRawBits methods in ImageLsbEngine and AudioLsbEngine
- added: unpackPayloadForced in StegoProtocol ignoring CRC32 validation failures
- added: Force Decode toggle in Extract UI with raw hex/text preview
- added: unit tests for forensic bit dumps and corrupted payload recovery
```

---

## FINAL STEP: Version Bump & Dual Push
- In `app/build.gradle.kts`: bump to `versionCode = 7` and `versionName = "1.0.6"`.
- Verify `./gradlew test` and `./gradlew assembleDebug` succeed.
- Commit 4:
```
chore(release): bump version to 1.0.6 (versionCode 7)
```
- Push to remotes: `git push origin main`.
