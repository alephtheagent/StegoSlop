# StegoSlop Feature Roadmap 🍋

Strategic engineering plan for StegoSlop steganography expansion.

---

## 1. Text-in-Text Covert Transport
- **Zero-Width Unicode Engine (`ZW-Stego`):**
  - Uses invisible Unicode control characters (`\u200B` = 0, `\u200C` = 1, `\u200D` = delimiter, `\uFEFF` = magic).
  - Embeds arbitrary secret text or AES ciphertext invisibly inside normal messaging text (Telegram, Discord, Notes).
- **Homoglyph Engine (`Homoglyph-Stego`):**
  - Exploits identical Cyrillic and Latin glyph pairs: `а/a`, `е/e`, `о/o`, `р/p`, `с/c`, `у/y`, `х/x`, `і/i`.
  - Binary encoding: Cyrillic glyph = bit 0, Latin lookalike = bit 1.
  - 100% resilient against zero-width filters, SMS gateways, and content sanitizers. Survives any plain-text copy-paste.

---

## 2. Visual & Image Innovations
- **Ghost Alpha Channel & Interactive Scanner (`Ghost-Alpha`):**
  - Encodes monochrome images/drawings into the least significant bit of the Alpha channel (`alpha = 254` vs `255`).
  - Visually indistinguishable (0.39% opacity delta) in standard image viewers.
  - In-app interactive "Reveal Slider": dynamically amplifies alpha delta to 0 / 255 for real-time visual discovery.
- **Polyglot Files (`ZIP-in-PNG`):**
  - Constructs dual-valid hybrid files: renders as a legitimate PNG image in galleries, but unpacks as a full ZIP archive when opened in archive managers.

---

## 3. Advanced Audio Covert Channels
- **Audio Spectrogram Art (`Spectrogram-Stego`):**
  - Renders monochrome images directly into the audio frequency domain via Inverse Fast Fourier Transform (IFFT).
  - Sounds like raw electronic glitch/noise, but visually reveals secret images when inspected via spectrogram analyzers (Aphex Twin / Doom style).
- **Echo Hiding (`Echo-Stego`):**
  - Embeds data into high-frequency audio components via sub-millisecond acoustic delays (< 1 ms).
  - Perceived by the human ear as natural room reverberation / ambience.
  - Higher survivability against mild lossy re-encoding compared to raw LSB.
- **FLAC Metadata & Album Art Preservation:**
  - Extracts and re-grafts `VORBIS_COMMENT` (title, artist, album) and `PICTURE` (cover art) metadata blocks back into re-encoded FLAC containers.
  - Keeps carrier tracks visually and acoustically identical in music players.

---

## 4. Cryptographic Defenses
- **Plausible Deniability / Duress Decoy:**
  - Multi-tiered container supporting two independent passwords:
    - Decoy Password: unlocks an innocent decoy payload (meme, recipe, innocuous note).
    - True Password: unlocks the confidential encrypted payload.
  - Cryptographically indistinguishable from uniform pseudorandom noise.

---

## 5. Offline Acoustic Air-Gap
- **Ultrasound Data Transfer (`Acoustic-AirGap`):**
  - Near-ultrasound acoustic modulation (~18–20 kHz FSK) between devices via speaker and microphone.
  - Enables zero-network, zero-Bluetooth, air-gapped short payload exchanges.

---

## 6. Forensic & Raw Analysis Tools
- **Force-Decode & Raw Dump Engine (`Forensic-Dump`):**
  - **Bypass Headers:** Disables mandatory `SLOP` magic signature check, enabling extraction of raw LSB bitstreams injected by third-party tools (Stegsolve, OpenStego, or custom scripts).
  - **Bypass Integrity Verification:** Ignores CRC32 checksum failures to salvage readable text or data from partially damaged, clipped, or corrupted carriers.
  - **Channel & Plane Slicing:** Extract raw bits from specific individual channels (R, G, B, Alpha, or combined RGB).
  - **Built-in Hex & ASCII Viewer:** Direct hex dump viewer with string extraction for reverse engineering unknown carriers.

---

## 7. Video Steganography (`Video-Stego`)
- **Frame-by-Frame Lossless LSB:**
  - Treats sequential video frames (in lossless MP4, MKV, or Motion-JPEG) as an array of high-speed image carriers.
  - Colossal payload capacity: a 10-second 1080p 60fps video delivers up to **~460 MB** of encrypted payload capacity.
- **Audio-Track Multiplexing:**
  - Embeds payloads directly into the video's lossless/PCM audio soundtrack while preserving video stream playback.
- **Subliminal 1/60s Frame Insertion:**
  - Injects a single encrypted/visual frame at 60 FPS (imperceptible during normal playback, extractable deterministically by frame index).
- **Subtitle Stream Stego:**
  - Injects Zero-Width Unicode or homoglyph payloads inside embedded subtitle tracks (`.srt`, `.vtt`, SSA/ASS).

---

## 8. Anti-Forensic Camouflage & Filename Masquerading (`Decoy-Naming`)
- **Realistic OS & Social Media Preset Chips:**
  - One-tap preset chips below the save filename field to masquerade carriers as mundane daily files:
    - **Android/iOS Screenshot:** `Снимок экрана ГГГГ-ММ-ДД в ЧЧ.ММ.СС.png` or `Screenshot_YYYYMMDD-HHMMSS.png`
    - **Camera DCIM:** `IMG_YYYYMMDD_HHMMSS.png` or `IMG_0001..9999.png`
    - **YouTube Thumbnail:** `maxresdefault.png` / `hqdefault.png`
    - **Telegram Photo:** `photo_YYYY-MM-DD_HH-MM-SS.png`
    - **VK / Social Hash:** `vk_photo_c3f819b2.png` or CDN hash style
    - **WhatsApp Media:** `IMG-YYYYMMDD-WA0001.png` / `AUD-YYYYMMDD-WA0001.wav`
    - **Generic Defaults:** `image.png`, `audio.wav`, `track_01.flac`
- **Dynamic Randomizer & Timestamp Synthesis:**
  - Dice icon button to randomize timestamps and sequential counter IDs (`0001..9999`).
- **File System Last-Modified Timestamp Spoofing (mtime):**
  - Synchronizes the saved file's OS last-modified timestamp with the fake date generated in the filename, placing it seamlessly into historical positions in the user's gallery / downloads list.
- **Custom Editable Text Field:**
  - Free-form manual editing with live extension guard.
