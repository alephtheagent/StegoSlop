package com.p0lymatic.stegoslop.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class CamouflagePreset {
    SCREENSHOT,      // "Screenshot_YYYYMMDD-HHMMSS"
    CAMERA_DCIM,     // "IMG_XXXX" (random 0001..9999)
    YOUTUBE,         // "maxresdefault"
    TELEGRAM,        // "photo_YYYY-MM-DD_HH-MM-SS"
    VK_HASH,         // "vk_photo_c3f819b2" (random 8-char hex)
    WHATSAPP,        // "IMG-YYYYMMDD-WA0001" or "AUD-YYYYMMDD-WA0001"
    VOICE,           // "Voice_YYYYMMDD_HHMMSS"
    TRACK,           // "track_01"
    GENERIC          // "image" or "audio" / "track_01"
}

object CamouflageUtils {
    fun generateDecoyName(preset: CamouflagePreset, extension: String): String =
        com.p0lymatic.stegoslop.core.generateDecoyName(preset, extension)

    fun randomizeDecoyName(isAudio: Boolean, extension: String): String =
        com.p0lymatic.stegoslop.core.randomizeDecoyName(isAudio, extension)

    fun isAudioExtension(extension: String): Boolean =
        com.p0lymatic.stegoslop.core.isAudioExtension(extension)
}

fun isAudioExtension(extension: String): Boolean {
    val clean = extension.trim().removePrefix(".").lowercase(Locale.US)
    return clean in setOf("wav", "flac", "mp3", "m4a", "ogg", "aac", "opus", "wma")
}

fun generateDecoyName(preset: CamouflagePreset, extension: String): String {
    val isAudio = isAudioExtension(extension)
    val baseName = when (preset) {
        CamouflagePreset.SCREENSHOT -> {
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            "Screenshot_$ts"
        }
        CamouflagePreset.CAMERA_DCIM -> {
            val num = Random.nextInt(1, 10000)
            String.format(Locale.US, "IMG_%04d", num)
        }
        CamouflagePreset.YOUTUBE -> "maxresdefault"
        CamouflagePreset.TELEGRAM -> {
            val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            "photo_$ts"
        }
        CamouflagePreset.VK_HASH -> {
            val hexChars = "0123456789abcdef"
            val hex = (1..8).map { hexChars[Random.nextInt(hexChars.length)] }.joinToString("")
            "vk_photo_$hex"
        }
        CamouflagePreset.WHATSAPP -> {
            val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val num = String.format(Locale.US, "%04d", Random.nextInt(1, 10000))
            val prefix = if (isAudio) "AUD" else "IMG"
            "$prefix-$date-WA$num"
        }
        CamouflagePreset.VOICE -> {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            "Voice_$ts"
        }
        CamouflagePreset.TRACK -> {
            val num = String.format(Locale.US, "%02d", Random.nextInt(1, 30))
            "track_$num"
        }
        CamouflagePreset.GENERIC -> {
            if (isAudio) "audio" else "image"
        }
    }

    val cleanExt = extension.trim().removePrefix(".")
    return if (cleanExt.isNotEmpty()) "$baseName.$cleanExt" else baseName
}

fun randomizeDecoyName(isAudio: Boolean, extension: String): String {
    val audioPresets = listOf(
        CamouflagePreset.WHATSAPP,
        CamouflagePreset.VOICE,
        CamouflagePreset.TRACK,
        CamouflagePreset.GENERIC
    )
    val imagePresets = listOf(
        CamouflagePreset.SCREENSHOT,
        CamouflagePreset.CAMERA_DCIM,
        CamouflagePreset.YOUTUBE,
        CamouflagePreset.TELEGRAM,
        CamouflagePreset.VK_HASH,
        CamouflagePreset.WHATSAPP,
        CamouflagePreset.GENERIC
    )
    val preset = if (isAudio || isAudioExtension(extension)) {
        audioPresets.random()
    } else {
        imagePresets.random()
    }
    return generateDecoyName(preset, extension)
}
