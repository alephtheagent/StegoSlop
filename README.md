# StegoSlop 🍋

Material 3 Expressive steganography app for Android.  
Embed and extract text, images, and audio in lossless media (PNG/Bitmap and 16-bit PCM WAV) via Least Significant Bit (LSB) steganography.

---

## Features

- **Lossless Carrier Support:**
  - **Images:** PNG, BMP, lossless WebP (`Bitmap.Config.ARGB_8888`). RGB channels LSB embedding with preserved alpha.
  - **Audio:** 16-bit PCM RIFF WAV audio. Sample-level LSB embedding in `data` chunks.
- **Payload Types:**
  - `TEXT` (UTF-8 strings)
  - `IMAGE` (Lossless PNG/WebP blobs)
  - `AUDIO` (WAV audio blobs)
  - `FILE` (Arbitrary binary files with metadata)
- **Security & Integrity:**
  - Optional **AES-256-GCM** encryption with PBKDF2-derived keys (10,000 iterations).
  - Hardware-accelerated CRC32 checksums for container integrity validation.
  - Variable LSB bit depth (1-bit stealth, 2-bit balanced, 4-bit greedy).
- **Material 3 Expressive UI:**
  - Single scrollable dashboard layout (`Modifier.verticalScroll`).
  - Dynamic Color theming (Monet / Android 12+) with high-contrast fallback (`#08090C`, `#171B22`, `#00E599`).
  - Real-time carrier capacity calculator and live progress gauge.
  - **X-Ray Bit-Plane Inspector:** Visualizes the 0-th bit plane on a grayscale canvas.
  - Adaptive Android launcher icon with full monochrome themed icon support (Android 13+).

---

## Technical Specifications

### Binary Container Protocol (`SLOP`)
```
+---------------+-------------------+--------------------+----------------------+
| Magic (4B)    | Payload Type (1B) | Flags (1B)         | Payload Length (4B)  |
| "SLOP"        | 0x01..0x04        | Bit0: AES, Bit1-2  | Big-Endian UInt32    |
+---------------+-------------------+--------------------+----------------------+
| CRC32 (4B)    | [Salt 16B]        | [IV 12B]           | Payload Data (N B)   |
| Checksum      | If encrypted      | If encrypted       | Raw / Ciphertext     |
+---------------+-------------------+--------------------+----------------------+
```

### Carrier Capacities (1-bit LSB)
- **1080×1920 FHD PNG Image:** ~777 KB
- **44.1 kHz 16-bit Stereo WAV Audio:** ~11 KB per second (~2 MB per 3-minute song)

---

## Building

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test
```

Direct `.apk` releases are built automatically via GitHub Actions CI/CD on every tag or push to `main`.

---

## License

MIT License. Icons based on Twemoji (CC-BY 4.0).
