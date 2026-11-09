package com.p0lymatic.stegoslop

import com.p0lymatic.stegoslop.core.AudioCodecBridge
import com.p0lymatic.stegoslop.core.AudioLsbEngine
import com.p0lymatic.stegoslop.core.CryptoUtils
import com.p0lymatic.stegoslop.core.ImageLsbEngine
import com.p0lymatic.stegoslop.core.PayloadType
import com.p0lymatic.stegoslop.core.StegoProtocol
import com.p0lymatic.stegoslop.core.CamouflagePreset
import com.p0lymatic.stegoslop.core.CamouflageUtils
import com.p0lymatic.stegoslop.core.ZeroWidthStego
import com.p0lymatic.stegoslop.core.generateDecoyName
import com.p0lymatic.stegoslop.core.randomizeDecoyName
import com.p0lymatic.stegoslop.core.OpusCodecBridge
import com.p0lymatic.stegoslop.core.OpusProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class StegoEngineTest {

    @Test
    fun testProtocolPackingAndUnpackingText() {
        val originalText = "StegoSlop: Lossless LSB steganography protocol verification"
        val textBytes = originalText.toByteArray(Charsets.UTF_8)

        for (bpu in listOf(1, 2, 4)) {
            val packed = StegoProtocol.packPayload(
                type = PayloadType.TEXT,
                data = textBytes,
                password = null,
                bitsPerUnit = bpu
            )

            assertNotNull(packed)
            assertTrue(packed.size >= StegoProtocol.HEADER_MIN_LENGTH + textBytes.size)

            val unpacked = StegoProtocol.unpackPayload(packed)
            assertEquals(PayloadType.TEXT, unpacked.type)
            assertFalse(unpacked.isEncrypted)
            assertEquals(bpu, unpacked.bitsPerUnit)
            assertArrayEquals(textBytes, unpacked.data)
            assertEquals(originalText, String(unpacked.data, Charsets.UTF_8))
        }
    }

    @Test
    fun testProtocolPackingAndUnpackingWithFileName() {
        val testData = "Sample file content for testing".toByteArray(Charsets.UTF_8)
        val fileName = "document.pdf"

        val packed = StegoProtocol.packPayload(
            type = PayloadType.FILE,
            data = testData,
            fileName = fileName
        )

        val unpacked = StegoProtocol.unpackPayload(packed)
        assertEquals(PayloadType.FILE, unpacked.type)
        assertEquals(fileName, unpacked.fileName)
        assertArrayEquals(testData, unpacked.data)

        // Also test with encryption
        val password = "Secr3tPassword!"
        val encryptedPacked = StegoProtocol.packPayload(
            type = PayloadType.FILE,
            data = testData,
            password = password,
            fileName = fileName
        )
        val decryptedUnpacked = StegoProtocol.unpackPayload(encryptedPacked, password = password)
        assertEquals(PayloadType.FILE, decryptedUnpacked.type)
        assertEquals(fileName, decryptedUnpacked.fileName)
        assertArrayEquals(testData, decryptedUnpacked.data)
        assertTrue(decryptedUnpacked.isEncrypted)
    }


    @Test
    fun testZeroWidthStegoPlainRoundtrip() {
        val coverText = "Привет, как твои дела? Встретимся завтра в кафе."
        val secretMessage = "Top Secret Zero-Width Message 12345! @#$%"
        val secretBytes = secretMessage.toByteArray(Charsets.UTF_8)

        val stegoText = ZeroWidthStego.encode(coverText, secretBytes)
        assertTrue(ZeroWidthStego.hasHiddenData(stegoText))

        val decodedBytes = ZeroWidthStego.decode(stegoText)
        val decodedMessage = String(decodedBytes, Charsets.UTF_8)
        assertEquals(secretMessage, decodedMessage)
    }

    @Test
    fun testZeroWidthStegoEncryptedRoundtrip() {
        val coverText = "Short cover"
        val secretMessage = "AES-256-GCM Encrypted Zero-Width Payload"
        val secretBytes = secretMessage.toByteArray(Charsets.UTF_8)
        val password = "SuperSecretPassword2026"

        val stegoText = ZeroWidthStego.encode(coverText, secretBytes, password = password)
        assertTrue(ZeroWidthStego.hasHiddenData(stegoText))

        // Without password must throw
        assertThrows(IllegalArgumentException::class.java) {
            ZeroWidthStego.decode(stegoText, password = null)
        }

        // With wrong password must throw
        assertThrows(IllegalArgumentException::class.java) {
            ZeroWidthStego.decode(stegoText, password = "WrongPassword")
        }

        // With correct password must decrypt
        val decodedBytes = ZeroWidthStego.decode(stegoText, password = password)
        assertEquals(secretMessage, String(decodedBytes, Charsets.UTF_8))
    }
    @Test
    fun testProtocolEncryptionDecryption() {
        val secretMessage = "Confidential payload secured with AES-256-GCM"
        val messageBytes = secretMessage.toByteArray(Charsets.UTF_8)
        val password = "StrongPassword@2026!"

        val packed = StegoProtocol.packPayload(
            type = PayloadType.TEXT,
            data = messageBytes,
            password = password,
            bitsPerUnit = 1
        )

        // Attempting to unpack without password must throw
        val noPassEx = assertThrows(IllegalArgumentException::class.java) {
            StegoProtocol.unpackPayload(packed, password = null)
        }
        assertTrue(noPassEx.message?.contains("Password required", ignoreCase = true) == true)

        // Attempting to unpack with incorrect password must throw
        val wrongPassEx = assertThrows(IllegalArgumentException::class.java) {
            StegoProtocol.unpackPayload(packed, password = "WrongPassword")
        }
        assertTrue(wrongPassEx.message?.contains("failed", ignoreCase = true) == true)

        // Unpacking with correct password succeeds
        val unpacked = StegoProtocol.unpackPayload(packed, password = password)
        assertEquals(PayloadType.TEXT, unpacked.type)
        assertTrue(unpacked.isEncrypted)
        assertEquals(1, unpacked.bitsPerUnit)
        assertArrayEquals(messageBytes, unpacked.data)
        assertEquals(secretMessage, String(unpacked.data, Charsets.UTF_8))

        // Also test binary file encryption/decryption
        val binaryData = ByteArray(512).also { Random(42).nextBytes(it) }
        val packedBinary = StegoProtocol.packPayload(
            type = PayloadType.FILE,
            data = binaryData,
            password = password,
            bitsPerUnit = 2
        )
        val unpackedBinary = StegoProtocol.unpackPayload(packedBinary, password = password)
        assertEquals(PayloadType.FILE, unpackedBinary.type)
        assertTrue(unpackedBinary.isEncrypted)
        assertEquals(2, unpackedBinary.bitsPerUnit)
        assertArrayEquals(binaryData, unpackedBinary.data)
    }

    @Test
    fun testAudioLsbEmbedAndExtract() {
        val sampleCount = 16000 // 16,000 samples @ 16-bit = 32,000 bytes data chunk
        val wavBytes = AudioLsbEngine.createSyntheticWav(sampleCount = sampleCount)

        val capacity1 = AudioLsbEngine.computeCapacity(wavBytes, bitsPerSample = 1)
        assertEquals(2000, capacity1) // (32000 / 2 * 1) / 8 = 2000 bytes

        val secretText = "Audio steganography watermark embedded in 16-bit PCM RIFF samples"
        val textBytes = secretText.toByteArray(Charsets.UTF_8)

        for (bpu in listOf(1, 2, 4)) {
            val container = StegoProtocol.packPayload(
                type = PayloadType.TEXT,
                data = textBytes,
                password = "audioSecretPassword",
                bitsPerUnit = bpu
            )

            // Embed into synthetic WAV
            val embeddedWav = AudioLsbEngine.embed(wavBytes, container, bitsPerSample = bpu)
            assertEquals(wavBytes.size, embeddedWav.size)

            // Extract container
            val extractedContainer = AudioLsbEngine.extract(embeddedWav, bitsPerSample = bpu)
            assertArrayEquals(container, extractedContainer)

            // Unpack payload
            val unpacked = StegoProtocol.unpackPayload(extractedContainer, password = "audioSecretPassword")
            assertEquals(PayloadType.TEXT, unpacked.type)
            assertTrue(unpacked.isEncrypted)
            assertEquals(bpu, unpacked.bitsPerUnit)
            assertEquals(secretText, String(unpacked.data, Charsets.UTF_8))
        }

        // Exceeding capacity throws IllegalArgumentException
        val oversizedData = ByteArray(capacity1 + 10)
        val oversizedContainer = StegoProtocol.packPayload(PayloadType.FILE, oversizedData, bitsPerUnit = 1)
        assertThrows(IllegalArgumentException::class.java) {
            AudioLsbEngine.embed(wavBytes, oversizedContainer, bitsPerSample = 1)
        }
    }

    @Test
    fun testImageLsbLogic() {
        val pixelCount = 2000
        val random = Random(12345)
        val pixels = IntArray(pixelCount) {
            val r = random.nextInt(256)
            val g = random.nextInt(256)
            val b = random.nextInt(256)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val secretMessage = "Image LSB embedded across RGB channels with alpha preserved"
        val messageBytes = secretMessage.toByteArray(Charsets.UTF_8)

        for (bpu in listOf(1, 2, 4)) {
            val container = StegoProtocol.packPayload(
                type = PayloadType.TEXT,
                data = messageBytes,
                password = null,
                bitsPerUnit = bpu
            )

            val modifiedPixels = ImageLsbEngine.embedInPixels(pixels, container, bitsPerChannel = bpu)
            assertEquals(pixels.size, modifiedPixels.size)

            // Verify Alpha is intact 0xFF on all pixels
            for (p in modifiedPixels) {
                val alpha = (p ushr 24) and 0xFF
                assertEquals(0xFF, alpha)
            }

            // Extract and verify
            val extractedContainer = ImageLsbEngine.extractFromPixels(modifiedPixels, bitsPerChannel = bpu)
            assertArrayEquals(container, extractedContainer)

            val unpacked = StegoProtocol.unpackPayload(extractedContainer)
            assertEquals(PayloadType.TEXT, unpacked.type)
            assertEquals(bpu, unpacked.bitsPerUnit)
            assertEquals(secretMessage, String(unpacked.data, Charsets.UTF_8))
        }

        // Test bit plane extraction logic
        val planePixels = ImageLsbEngine.generateBitPlanePixels(pixels, bitIndex = 0)
        assertEquals(pixels.size, planePixels.size)
        for (p in planePixels) {
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val a = (p ushr 24) and 0xFF
            assertEquals(0xFF, a)
            assertTrue(r == 0 || r == 255)
            assertEquals(r, g)
            assertEquals(g, b)
        }
    }

    @Test
    fun testCorruptedDataThrows() {
        val payloadData = "Important integrity payload for CRC32 testing".toByteArray(Charsets.UTF_8)
        val packed = StegoProtocol.packPayload(
            type = PayloadType.TEXT,
            data = payloadData,
            password = null,
            bitsPerUnit = 1
        )

        // Corrupt one byte of the payload data
        val corrupted = packed.copyOf()
        val corruptIndex = corrupted.size - 1
        corrupted[corruptIndex] = (corrupted[corruptIndex].toInt() xor 0x01).toByte()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            StegoProtocol.unpackPayload(corrupted)
        }
        assertTrue(
            "Expected CRC32 error message but got: ${ex.message}",
            ex.message?.contains("CRC32", ignoreCase = true) == true
        )

        // Corrupt magic bytes
        val badMagic = packed.copyOf()
        badMagic[0] = 'X'.code.toByte()
        val magicEx = assertThrows(IllegalArgumentException::class.java) {
            StegoProtocol.unpackPayload(badMagic)
        }
        assertTrue(magicEx.message?.contains("magic", ignoreCase = true) == true)
    }

    @Test
    fun testRawBitExtractionImageAndAudio() {
        val sampleCount = 4000
        val wavBytes = AudioLsbEngine.createSyntheticWav(sampleCount = sampleCount)
        val secretText = "Raw bit extraction test payload"
        val container = StegoProtocol.packPayload(PayloadType.TEXT, secretText.toByteArray(Charsets.UTF_8))
        val embeddedWav = AudioLsbEngine.embed(wavBytes, container, bitsPerSample = 1)

        val rawAudioBits = AudioLsbEngine.extractRawBits(embeddedWav, bitsPerSample = 1, maxBytes = container.size)
        assertArrayEquals(container, rawAudioBits)

        val pixelCount = 2000
        val pixels = IntArray(pixelCount) { 0xFF112233.toInt() }
        val embeddedPixels = ImageLsbEngine.embedInPixels(pixels, container, bitsPerChannel = 1)
        val rawImageBits = ImageLsbEngine.extractRawBitsFromPixels(embeddedPixels, bitsPerChannel = 1, maxBytes = container.size)
        assertArrayEquals(container, rawImageBits)
    }

    @Test
    fun testForcedUnpackOfCorruptedPayload() {
        val originalText = "Original confidential text before bit-rot"
        val payloadData = originalText.toByteArray(Charsets.UTF_8)
        val packed = StegoProtocol.packPayload(
            type = PayloadType.TEXT,
            data = payloadData,
            password = null,
            bitsPerUnit = 1
        )

        val corrupted = packed.copyOf()
        val corruptIndex = corrupted.size - 1
        corrupted[corruptIndex] = (corrupted[corruptIndex].toInt() xor 0x01).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            StegoProtocol.unpackPayload(corrupted)
        }

        val forcedUnpack = StegoProtocol.unpackPayloadForced(corrupted)
        assertEquals(PayloadType.TEXT, forcedUnpack.type)
        assertTrue("Expected isCorrupted to be true", forcedUnpack.isCorrupted)
        assertTrue("Expected crcMismatch to be true", forcedUnpack.crcMismatch)
        assertEquals(payloadData.size, forcedUnpack.data.size)
    }

    @Test
    fun testAudioCodecBridgeIsFlac() {
        val flacHeader = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(), 0x00, 0x01)
        assertTrue(AudioCodecBridge.isFlac(flacHeader))

        val wavHeader = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
        assertFalse(AudioCodecBridge.isFlac(wavHeader))

        assertFalse(AudioCodecBridge.isFlac(byteArrayOf('f'.code.toByte(), 'L'.code.toByte())))
        assertFalse(AudioCodecBridge.isFlac(ByteArray(0)))
    }

    @Test
    fun testCamouflagePresetsOutputsNonEmptyWithCorrectExtension() {
        val extensions = listOf("png", ".png", "wav", ".wav")
        for (preset in CamouflagePreset.entries) {
            for (ext in extensions) {
                val clean = ext.trimStart('.')
                val name = generateDecoyName(preset, ext)
                assertNotNull("Preset $preset generated null name", name)
                assertTrue("Preset $preset name should not be blank", name.isNotBlank())
                assertTrue(
                    "Preset $preset name '$name' should end with '.$clean'",
                    name.endsWith(".$clean")
                )
                assertFalse(
                    "Preset $preset name '$name' should not contain double dots",
                    name.contains("..")
                )

                val objName = CamouflageUtils.generateDecoyName(preset, ext)
                assertTrue(objName.endsWith(".$clean"))
            }
        }
    }

    @Test
    fun testCamouflagePresetSpecificFormats() {
        val screenshot = generateDecoyName(CamouflagePreset.SCREENSHOT, "png")
        assertTrue(screenshot.startsWith("Screenshot_"))
        assertTrue(screenshot.endsWith(".png"))

        val camera = generateDecoyName(CamouflagePreset.CAMERA_DCIM, "png")
        assertTrue(camera.matches(Regex("""^IMG_\d{4}\.png$""")))

        val youtube = generateDecoyName(CamouflagePreset.YOUTUBE, "png")
        assertEquals("maxresdefault.png", youtube)

        val telegram = generateDecoyName(CamouflagePreset.TELEGRAM, "png")
        assertTrue(telegram.startsWith("photo_"))
        assertTrue(telegram.endsWith(".png"))

        val vk = generateDecoyName(CamouflagePreset.VK_HASH, "png")
        assertTrue(vk.matches(Regex("""^vk_photo_[0-9a-f]{8}\.png$""")))

        val waImage = generateDecoyName(CamouflagePreset.WHATSAPP, "png")
        assertTrue(waImage.matches(Regex("""^IMG-\d{8}-WA\d{4}\.png$""")))

        val waAudio = generateDecoyName(CamouflagePreset.WHATSAPP, "wav")
        assertTrue(waAudio.matches(Regex("""^AUD-\d{8}-WA\d{4}\.wav$""")))

        val voice = generateDecoyName(CamouflagePreset.VOICE, "wav")
        assertTrue(voice.startsWith("Voice_"))
        assertTrue(voice.endsWith(".wav"))

        val track = generateDecoyName(CamouflagePreset.TRACK, "wav")
        assertTrue(track.matches(Regex("""^track_\d{2}\.wav$""")))

        val genericImage = generateDecoyName(CamouflagePreset.GENERIC, "png")
        assertEquals("image.png", genericImage)

        val genericAudio = generateDecoyName(CamouflagePreset.GENERIC, "wav")
        assertEquals("audio.wav", genericAudio)
    }

    @Test
    fun testRandomizeDecoyName() {
        for (i in 0 until 20) {
            val imgDecoy = randomizeDecoyName(isAudio = false, extension = "png")
            assertTrue("Decoy image name should not be blank", imgDecoy.isNotBlank())
            assertTrue("Decoy image name should end with .png", imgDecoy.endsWith(".png"))

            val audDecoy = randomizeDecoyName(isAudio = true, extension = "wav")
            assertTrue("Decoy audio name should not be blank", audDecoy.isNotBlank())
            assertTrue("Decoy audio name should end with .wav", audDecoy.endsWith(".wav"))

            val objAudDecoy = CamouflageUtils.randomizeDecoyName(isAudio = true, extension = "flac")
            assertTrue("Decoy flac name should end with .flac", objAudDecoy.endsWith(".flac"))
        }
    }

    @Test
    fun testCalculateTargetBitrateVoiceProfile() {
        // Small capacity: raw = 1000 * 0.70 * 8 / 10.0 = 560 bps -> clamps to 8_000
        val lowBitrate = OpusCodecBridge.calculateTargetBitrate(
            durationSeconds = 10.0,
            availableCapacityBytes = 1_000,
            profile = OpusProfile.VOICE
        )
        assertEquals(8_000, lowBitrate)

        // Medium capacity: raw = 10_000 * 0.85 * 8 / 5.0 = 13_600 bps -> 13_600
        val midBitrate = OpusCodecBridge.calculateTargetBitrate(
            durationSeconds = 5.0,
            availableCapacityBytes = 10_000,
            profile = OpusProfile.VOICE
        )
        assertEquals(13_600, midBitrate)

        // High capacity: raw = 100_000 * 0.70 * 8 / 5.0 = 112_000 bps -> clamps to 24_000
        val highBitrate = OpusCodecBridge.calculateTargetBitrate(
            durationSeconds = 5.0,
            availableCapacityBytes = 100_000,
            profile = OpusProfile.VOICE
        )
        assertEquals(24_000, highBitrate)

        // Zero / negative duration: default safe clamp
        val zeroDurBitrate = OpusCodecBridge.calculateTargetBitrate(
            durationSeconds = 0.0,
            availableCapacityBytes = 50_000,
            profile = OpusProfile.VOICE
        )
        assertEquals(8_000, zeroDurBitrate)
    }

    @Test
    fun testCalculateTargetBitrateMusicProfile() {
        // Small capacity: raw = 1000 * 0.70 * 8 / 10.0 = 560 bps -> clamps to 16_000
        val lowBitrate = OpusCodecBridge.calculateTargetBitrate(
            durationSeconds = 10.0,
            availableCapacityBytes = 1_000,
            profile = OpusProfile.MUSIC
        )
        assertEquals(16_000, lowBitrate)

        // Medium capacity: raw = 30_000 * 0.85 * 8 / 5.0 = 40_800 bps -> 40_800
        val midBitrate = OpusCodecBridge.calculateTargetBitrate(
            durationSeconds = 5.0,
            availableCapacityBytes = 30_000,
            profile = OpusProfile.MUSIC
        )
        assertEquals(40_800, midBitrate)

        // High capacity: raw = 200_000 * 0.70 * 8 / 5.0 = 224_000 bps -> clamps to 96_000
        val highBitrate = OpusCodecBridge.calculateTargetBitrate(
            durationSeconds = 5.0,
            availableCapacityBytes = 200_000,
            profile = OpusProfile.MUSIC
        )
        assertEquals(96_000, highBitrate)

        // Zero duration: default safe clamp
        val zeroDurBitrate = OpusCodecBridge.calculateTargetBitrate(
            durationSeconds = 0.0,
            availableCapacityBytes = 50_000,
            profile = OpusProfile.MUSIC
        )
        assertEquals(16_000, zeroDurBitrate)
    }

    @Test
    fun testTrimPcmMonoAccuracy() {
        val sampleRate = 1000
        val channels = 1
        val totalSeconds = 10.0
        val totalSamples = (sampleRate * totalSeconds).toInt()
        // 16-bit mono = 2 bytes per sample
        val pcm = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val sampleValue = (i % 256).toByte()
            pcm[i * 2] = sampleValue
            pcm[i * 2 + 1] = (sampleValue + 1).toByte()
        }

        // Trim 2.0 to 4.5 seconds (2.5 seconds = 2500 samples = 5000 bytes)
        val trimmed = OpusCodecBridge.trimPcm(
            pcmBytes = pcm,
            sampleRate = sampleRate,
            channels = channels,
            startSeconds = 2.0,
            endSeconds = 4.5
        )

        val expectedStartByte = (2.0 * sampleRate * 2).toInt() // 4000
        val expectedEndByte = (4.5 * sampleRate * 2).toInt()   // 9000
        val expectedLength = expectedEndByte - expectedStartByte // 5000

        assertEquals(expectedLength, trimmed.size)
        for (i in 0 until expectedLength) {
            assertEquals(pcm[expectedStartByte + i], trimmed[i])
        }
    }

    @Test
    fun testTrimPcmStereoAccuracyAndBoundaries() {
        val sampleRate = 48000
        val channels = 2
        val totalFrames = 48000 * 3 // 3 seconds
        val bytesPerFrame = channels * 2 // 4 bytes
        val pcm = ByteArray(totalFrames * bytesPerFrame)
        for (i in pcm.indices) {
            pcm[i] = (i and 0xFF).toByte()
        }

        // 1. Full range (0.0 to 3.0)
        val fullTrimmed = OpusCodecBridge.trimPcm(pcm, sampleRate, channels, 0.0, 3.0)
        assertArrayEquals(pcm, fullTrimmed)

        // 2. Sub-slice (1.0 to 2.0 = 1 sec = 48000 frames = 192000 bytes)
        val oneSec = OpusCodecBridge.trimPcm(pcm, sampleRate, channels, 1.0, 2.0)
        assertEquals(48000 * 4, oneSec.size)
        assertEquals(0, oneSec.size % bytesPerFrame)
        val expectedStart = 48000 * 4
        for (i in 0 until 100) {
            assertEquals(pcm[expectedStart + i], oneSec[i])
        }

        // 3. Inverted range (2.0 to 1.0) -> empty array
        val emptyInverted = OpusCodecBridge.trimPcm(pcm, sampleRate, channels, 2.0, 1.0)
        assertEquals(0, emptyInverted.size)

        // 4. Equal range (1.5 to 1.5) -> empty array
        val emptyEqual = OpusCodecBridge.trimPcm(pcm, sampleRate, channels, 1.5, 1.5)
        assertEquals(0, emptyEqual.size)

        // 5. Out of bounds start & end clamp
        val outOfBounds = OpusCodecBridge.trimPcm(pcm, sampleRate, channels, -2.0, 10.0)
        assertArrayEquals(pcm, outOfBounds)
    }

    @Test
    fun testVariableLengthHeaderWithFilenameInImageAndAudio() {
        val testFilename = "06. noma - Brain Power.opus"
        val payloadData = "Brain power secret payload for variable header test".toByteArray(Charsets.UTF_8)

        val container = StegoProtocol.packPayload(
            type = PayloadType.AUDIO,
            data = payloadData,
            password = null,
            bitsPerUnit = 2,
            fileName = testFilename
        )

        // 1. Test Image LSB extraction with filename
        val pixelCount = 10000
        val pixels = IntArray(pixelCount) { (0xFF shl 24) or (it and 0x00FFFFFF) }
        val embeddedPixels = ImageLsbEngine.embedInPixels(pixels, container, bitsPerChannel = 2)
        val extractedFromImage = ImageLsbEngine.extractFromPixels(embeddedPixels, bitsPerChannel = 2)

        val unpackedFromImage = StegoProtocol.unpackPayload(extractedFromImage)
        assertEquals(PayloadType.AUDIO, unpackedFromImage.type)
        assertEquals(testFilename, unpackedFromImage.fileName)
        assertArrayEquals(payloadData, unpackedFromImage.data)

        // 2. Test Audio LSB extraction with filename
        val sampleCount = 20000
        val wavBytes = AudioLsbEngine.createSyntheticWav(sampleCount = sampleCount)
        val embeddedWav = AudioLsbEngine.embed(wavBytes, container, bitsPerSample = 2)
        val extractedFromAudio = AudioLsbEngine.extract(embeddedWav, bitsPerSample = 2)

        val unpackedFromAudio = StegoProtocol.unpackPayload(extractedFromAudio)
        assertEquals(PayloadType.AUDIO, unpackedFromAudio.type)
        assertEquals(testFilename, unpackedFromAudio.fileName)
        assertArrayEquals(payloadData, unpackedFromAudio.data)
    }

    @Test
    fun testResamplePcm16() {
        // 44100 Hz 1-sec stereo = 44100 frames * 4 bytes = 176400 bytes
        val inRate = 44100
        val outRate = 48000
        val channels = 2
        val inFrames = 44100
        val pcm = ByteArray(inFrames * channels * 2)

        val resampled = OpusCodecBridge.resamplePcm16(pcm, inRate, outRate, channels)
        val expectedOutFrames = 48000
        assertEquals(expectedOutFrames * channels * 2, resampled.size)
    }
}
