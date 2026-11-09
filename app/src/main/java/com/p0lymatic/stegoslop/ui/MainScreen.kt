package com.p0lymatic.stegoslop.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.p0lymatic.stegoslop.core.AudioLsbEngine
import com.p0lymatic.stegoslop.core.ImageLsbEngine
import com.p0lymatic.stegoslop.core.PayloadType
import com.p0lymatic.stegoslop.core.StegoProtocol
import com.p0lymatic.stegoslop.core.UnpackedPayload
import com.p0lymatic.stegoslop.ui.theme.EmeraldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

enum class AppMode {
    INJECT,
    EXTRACT
}

enum class CarrierType {
    IMAGE,
    AUDIO
}

enum class PayloadTab {
    TEXT,
    FILE
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var activeMode by remember { mutableStateOf(AppMode.INJECT) }

    // Carrier state
    var carrierType by remember { mutableStateOf(CarrierType.IMAGE) }
    var carrierBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var carrierWavBytes by remember { mutableStateOf<ByteArray?>(null) }
    var carrierFileName by remember { mutableStateOf<String?>(null) }
    var bitsPerUnit by remember { mutableIntStateOf(1) }

    // Payload state (Inject)
    var payloadTab by remember { mutableStateOf(PayloadTab.TEXT) }
    var secretText by remember { mutableStateOf("") }
    var payloadFileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var payloadFileName by remember { mutableStateOf<String?>(null) }

    // Security state
    var encryptEnabled by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Processing & Output state (Inject)
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var injectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var injectedWavBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Extract state
    var extractCarrierBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractCarrierWavBytes by remember { mutableStateOf<ByteArray?>(null) }
    var extractCarrierFileName by remember { mutableStateOf<String?>(null) }
    var extractCarrierType by remember { mutableStateOf(CarrierType.IMAGE) }
    var extractPassword by remember { mutableStateOf("") }
    var extractPasswordVisible by remember { mutableStateOf(false) }
    var extractedPayload by remember { mutableStateOf<UnpackedPayload?>(null) }
    var extractStatusMessage by remember { mutableStateOf<String?>(null) }
    var extractIsError by remember { mutableStateOf(false) }

    // X-Ray Bit Plane state
    var bitPlaneExpanded by remember { mutableStateOf(false) }
    var bitPlaneIndex by remember { mutableIntStateOf(0) }
    var bitPlaneBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Audio Playback state
    var isPlayingAudio by remember { mutableStateOf(false) }
    var currentAudioTrack by remember { mutableStateOf<AudioTrack?>(null) }

    // File pickers
    val injectCarrierPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val isWav = bytes.size >= 12 &&
                                String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
                        if (isWav) {
                            carrierType = CarrierType.AUDIO
                            carrierWavBytes = bytes
                            carrierBitmap = null
                            carrierFileName = "audio_carrier.wav"
                        } else {
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                val argbBmp = if (bmp.config != Bitmap.Config.ARGB_8888) {
                                    bmp.copy(Bitmap.Config.ARGB_8888, true)
                                } else bmp
                                carrierType = CarrierType.IMAGE
                                carrierBitmap = argbBmp
                                carrierWavBytes = null
                                carrierFileName = "image_carrier.png"
                            }
                        }
                    }
                } catch (e: Exception) {
                    statusMessage = "Failed to load carrier: ${e.message}"
                    isError = true
                }
            }
        }
    }

    val payloadFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        payloadFileBytes = bytes
                        payloadFileName = "secret_file.bin"
                    }
                } catch (e: Exception) {
                    statusMessage = "Failed to load payload file: ${e.message}"
                    isError = true
                }
            }
        }
    }

    val extractCarrierPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val isWav = bytes.size >= 12 &&
                                String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
                        if (isWav) {
                            extractCarrierType = CarrierType.AUDIO
                            extractCarrierWavBytes = bytes
                            extractCarrierBitmap = null
                            extractCarrierFileName = "stego_audio.wav"
                        } else {
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                val argbBmp = if (bmp.config != Bitmap.Config.ARGB_8888) {
                                    bmp.copy(Bitmap.Config.ARGB_8888, true)
                                } else bmp
                                extractCarrierType = CarrierType.IMAGE
                                extractCarrierBitmap = argbBmp
                                extractCarrierWavBytes = null
                                extractCarrierFileName = "stego_image.png"
                                bitPlaneBitmap = ImageLsbEngine.generateBitPlane(argbBmp, bitPlaneIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    extractStatusMessage = "Failed to load carrier: ${e.message}"
                    extractIsError = true
                }
            }
        }
    }

    val saveInjectedFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            if (carrierType == CarrierType.IMAGE) "image/png" else "audio/wav"
        )
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        if (carrierType == CarrierType.IMAGE && injectedBitmap != null) {
                            injectedBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } else if (carrierType == CarrierType.AUDIO && injectedWavBytes != null) {
                            out.write(injectedWavBytes!!)
                        }
                    }
                    statusMessage = "File saved successfully"
                    isError = false
                } catch (e: Exception) {
                    statusMessage = "Failed to save file: ${e.message}"
                    isError = true
                }
            }
        }
    }

    val saveExtractedFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            when (extractedPayload?.type) {
                PayloadType.IMAGE -> "image/png"
                PayloadType.AUDIO -> "audio/wav"
                PayloadType.TEXT -> "text/plain"
                else -> "application/octet-stream"
            }
        )
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    extractedPayload?.data?.let { data ->
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(data)
                        }
                        extractStatusMessage = "Payload saved successfully"
                        extractIsError = false
                    }
                } catch (e: Exception) {
                    extractStatusMessage = "Failed to save payload: ${e.message}"
                    extractIsError = true
                }
            }
        }
    }

    // Capacity calculations
    val availableCapacity = remember(carrierBitmap, carrierWavBytes, carrierType, bitsPerUnit) {
        when (carrierType) {
            CarrierType.IMAGE -> carrierBitmap?.let {
                ImageLsbEngine.computeCapacity(it.width, it.height, bitsPerUnit)
            } ?: 0
            CarrierType.AUDIO -> carrierWavBytes?.let {
                try {
                    AudioLsbEngine.computeCapacity(it, bitsPerUnit)
                } catch (e: Exception) {
                    0
                }
            } ?: 0
        }
    }

    val rawPayloadBytes = remember(payloadTab, secretText, payloadFileBytes) {
        when (payloadTab) {
            PayloadTab.TEXT -> secretText.toByteArray(Charsets.UTF_8)
            PayloadTab.FILE -> payloadFileBytes ?: ByteArray(0)
        }
    }

    // Header size: 14 + (if encrypt 28 else 0) + (if encrypt ciphertext + 16 else raw.size)
    val estimatedContainerSize = remember(rawPayloadBytes, encryptEnabled) {
        if (rawPayloadBytes.isEmpty()) 0
        else {
            val headerSize = StegoProtocol.HEADER_MIN_LENGTH + (if (encryptEnabled) 28 else 0)
            val dataSize = rawPayloadBytes.size + (if (encryptEnabled) 16 else 0) // GCM auth tag 16 bytes
            headerSize + dataSize
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Title
            Text(
                text = "STEGOSLOP",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Lossless LSB Steganography Engine",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Animated Segmented Pill: Inject | Extract
            AnimatedSegmentedPill(
                selectedMode = activeMode,
                onModeSelected = {
                    activeMode = it
                    statusMessage = null
                    isError = false
                    extractStatusMessage = null
                    extractIsError = false
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (activeMode) {
                AppMode.INJECT -> {
                    // Carrier Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Carrier",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CarrierTypeChip(
                                        title = "Image",
                                        selected = carrierType == CarrierType.IMAGE,
                                        onSelect = { carrierType = CarrierType.IMAGE }
                                    )
                                    CarrierTypeChip(
                                        title = "Audio",
                                        selected = carrierType == CarrierType.AUDIO,
                                        onSelect = { carrierType = CarrierType.AUDIO }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (carrierType == CarrierType.IMAGE) {
                                            injectCarrierPicker.launch("image/*")
                                        } else {
                                            injectCarrierPicker.launch("audio/*")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (carrierType == CarrierType.IMAGE) Icons.Default.Image else Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select Carrier")
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (carrierType == CarrierType.IMAGE) {
                                            val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                                            val pixels = IntArray(512 * 512) { i ->
                                                val x = i % 512
                                                val y = i / 512
                                                val r = (x * 255 / 512)
                                                val g = (y * 255 / 512)
                                                val b = ((x + y) * 255 / 1024)
                                                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                            }
                                            bmp.setPixels(pixels, 0, 512, 0, 0, 512, 512)
                                            carrierBitmap = bmp
                                            carrierFileName = "synthetic_512x512.png"
                                        } else {
                                            val wav = AudioLsbEngine.createSyntheticWav(sampleCount = 88200) // 2 sec
                                            carrierWavBytes = wav
                                            carrierFileName = "synthetic_44khz_2s.wav"
                                        }
                                    },
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Text("Sample")
                                }
                            }

                            // Carrier details
                            if (carrierType == CarrierType.IMAGE && carrierBitmap != null) {
                                Text(
                                    text = "Dimensions: ${carrierBitmap!!.width} x ${carrierBitmap!!.height} | Format: ARGB_8888",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (carrierType == CarrierType.AUDIO && carrierWavBytes != null) {
                                val duration = try {
                                    val info = AudioLsbEngine.parseWav(carrierWavBytes!!)
                                    String.format(Locale.US, "%.2fs (%d Hz, 16-bit)", info.durationSeconds, info.sampleRate)
                                } catch (e: Exception) {
                                    "PCM WAV"
                                }
                                Text(
                                    text = "Audio: $duration",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Bits per unit selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bits per Unit",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(1, 2, 4).forEach { bpu ->
                                        CarrierTypeChip(
                                            title = "$bpu bit",
                                            selected = bitsPerUnit == bpu,
                                            onSelect = { bitsPerUnit = bpu }
                                        )
                                    }
                                }
                            }

                            // Capacity summary
                            Text(
                                text = "Carrier Capacity: ${formatBytes(availableCapacity)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Payload Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Payload",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            TabRow(
                                selectedTabIndex = payloadTab.ordinal,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            ) {
                                Tab(
                                    selected = payloadTab == PayloadTab.TEXT,
                                    onClick = { payloadTab = PayloadTab.TEXT },
                                    text = { Text("Text") }
                                )
                                Tab(
                                    selected = payloadTab == PayloadTab.FILE,
                                    onClick = { payloadTab = PayloadTab.FILE },
                                    text = { Text("File") }
                                )
                            }

                            when (payloadTab) {
                                PayloadTab.TEXT -> {
                                    OutlinedTextField(
                                        value = secretText,
                                        onValueChange = { secretText = it },
                                        label = { Text("Secret Message") },
                                        placeholder = { Text("Enter secret text payload") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        maxLines = 6,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                    Text(
                                        text = "${rawPayloadBytes.size} bytes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                PayloadTab.FILE -> {
                                    Button(
                                        onClick = { payloadFilePicker.launch("*/*") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(28.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(payloadFileName ?: "Select Payload File")
                                    }
                                    if (payloadFileBytes != null) {
                                        Text(
                                            text = "Size: ${formatBytes(payloadFileBytes!!.size)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Capacity Gauge
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val ratio = if (availableCapacity > 0) {
                                (estimatedContainerSize.toFloat() / availableCapacity.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val isExceeded = availableCapacity > 0 && estimatedContainerSize > availableCapacity

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Carrier Capacity",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${formatBytes(estimatedContainerSize)} / ${formatBytes(availableCapacity)} (${(ratio * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }

                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )

                            if (isExceeded) {
                                Text(
                                    text = "Payload exceeds carrier capacity",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Security Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Encrypt with Password",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Switch(
                                    checked = encryptEnabled,
                                    onCheckedChange = { encryptEnabled = it }
                                )
                            }

                            AnimatedVisibility(visible = encryptEnabled) {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("AES-256-GCM Password") },
                                    placeholder = { Text("Enter passphrase") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }

                    // Status / Error message
                    if (statusMessage != null) {
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Big 56.dp "Inject" Action Pill
                    Button(
                        onClick = {
                            if (rawPayloadBytes.isEmpty()) {
                                statusMessage = "No payload provided"
                                isError = true
                                return@Button
                            }
                            if (encryptEnabled && password.isEmpty()) {
                                statusMessage = "Password required when encryption is enabled"
                                isError = true
                                return@Button
                            }
                            if (carrierType == CarrierType.IMAGE && carrierBitmap == null) {
                                statusMessage = "Please select or generate an image carrier"
                                isError = true
                                return@Button
                            }
                            if (carrierType == CarrierType.AUDIO && carrierWavBytes == null) {
                                statusMessage = "Please select or generate an audio carrier"
                                isError = true
                                return@Button
                            }

                            isProcessing = true
                            statusMessage = null
                            isError = false

                            coroutineScope.launch {
                                try {
                                    val type = if (payloadTab == PayloadTab.TEXT) PayloadType.TEXT else PayloadType.FILE
                                    val container = withContext(Dispatchers.Default) {
                                        StegoProtocol.packPayload(
                                            type = type,
                                            data = rawPayloadBytes,
                                            password = if (encryptEnabled) password else null,
                                            bitsPerUnit = bitsPerUnit
                                        )
                                    }

                                    if (carrierType == CarrierType.IMAGE) {
                                        val output = withContext(Dispatchers.Default) {
                                            ImageLsbEngine.embed(carrierBitmap!!, container, bitsPerUnit)
                                        }
                                        injectedBitmap = output
                                        injectedWavBytes = null
                                    } else {
                                        val output = withContext(Dispatchers.Default) {
                                            AudioLsbEngine.embed(carrierWavBytes!!, container, bitsPerUnit)
                                        }
                                        injectedWavBytes = output
                                        injectedBitmap = null
                                    }

                                    statusMessage = "Payload injected successfully"
                                    isError = false
                                } catch (e: Exception) {
                                    statusMessage = "Injection error: ${e.message}"
                                    isError = true
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        enabled = !isProcessing && availableCapacity > 0 && estimatedContainerSize <= availableCapacity,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Inject",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Success Card
                    if (injectedBitmap != null || injectedWavBytes != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Injection Completed",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Text(
                                    text = if (carrierType == CarrierType.IMAGE) {
                                        "Image: ${injectedBitmap!!.width} x ${injectedBitmap!!.height} (ARGB_8888)"
                                    } else {
                                        "Audio: ${formatBytes(injectedWavBytes!!.size)} WAV"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        saveInjectedFilePicker.launch(
                                            if (carrierType == CarrierType.IMAGE) "stego_image.png" else "stego_audio.wav"
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save File")
                                }
                            }
                        }
                    }
                }

                AppMode.EXTRACT -> {
                    // Carrier Card (Extract)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Carrier",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { extractCarrierPicker.launch("*/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(extractCarrierFileName ?: "Select Carrier")
                                }

                                if (injectedBitmap != null || injectedWavBytes != null) {
                                    OutlinedButton(
                                        onClick = {
                                            if (carrierType == CarrierType.IMAGE && injectedBitmap != null) {
                                                extractCarrierBitmap = injectedBitmap
                                                extractCarrierWavBytes = null
                                                extractCarrierType = CarrierType.IMAGE
                                                extractCarrierFileName = "injected_image.png"
                                                bitPlaneBitmap = ImageLsbEngine.generateBitPlane(injectedBitmap!!, bitPlaneIndex)
                                            } else if (carrierType == CarrierType.AUDIO && injectedWavBytes != null) {
                                                extractCarrierWavBytes = injectedWavBytes
                                                extractCarrierBitmap = null
                                                extractCarrierType = CarrierType.AUDIO
                                                extractCarrierFileName = "injected_audio.wav"
                                            }
                                        },
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Text("Use Injected")
                                    }
                                }
                            }

                            if (extractCarrierBitmap != null) {
                                Text(
                                    text = "Image Carrier: ${extractCarrierBitmap!!.width} x ${extractCarrierBitmap!!.height}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (extractCarrierWavBytes != null) {
                                Text(
                                    text = "Audio Carrier: ${formatBytes(extractCarrierWavBytes!!.size)} WAV",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Bits per unit
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bits per Unit",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(1, 2, 4).forEach { bpu ->
                                        CarrierTypeChip(
                                            title = "$bpu bit",
                                            selected = bitsPerUnit == bpu,
                                            onSelect = {
                                                bitsPerUnit = bpu
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Password Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Password",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = extractPassword,
                                onValueChange = { extractPassword = it },
                                label = { Text("Decryption Password (if encrypted)") },
                                placeholder = { Text("Leave blank if plaintext") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                visualTransformation = if (extractPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { extractPasswordVisible = !extractPasswordVisible }) {
                                        Icon(
                                            imageVector = if (extractPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }

                    if (extractStatusMessage != null) {
                        Text(
                            text = extractStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (extractIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Action Pill: Big 56.dp "Extract" Button
                    Button(
                        onClick = {
                            if (extractCarrierType == CarrierType.IMAGE && extractCarrierBitmap == null) {
                                extractStatusMessage = "Please select an image carrier"
                                extractIsError = true
                                return@Button
                            }
                            if (extractCarrierType == CarrierType.AUDIO && extractCarrierWavBytes == null) {
                                extractStatusMessage = "Please select an audio carrier"
                                extractIsError = true
                                return@Button
                            }

                            isProcessing = true
                            extractStatusMessage = null
                            extractIsError = false
                            extractedPayload = null

                            coroutineScope.launch {
                                try {
                                    val containerBytes = withContext(Dispatchers.Default) {
                                        if (extractCarrierType == CarrierType.IMAGE) {
                                            ImageLsbEngine.extract(extractCarrierBitmap!!, bitsPerUnit)
                                        } else {
                                            AudioLsbEngine.extract(extractCarrierWavBytes!!, bitsPerUnit)
                                        }
                                    }

                                    val payload = withContext(Dispatchers.Default) {
                                        StegoProtocol.unpackPayload(
                                            containerBytes = containerBytes,
                                            password = extractPassword.ifEmpty { null }
                                        )
                                    }

                                    extractedPayload = payload
                                    extractStatusMessage = "Payload extracted successfully"
                                    extractIsError = false
                                } catch (e: Exception) {
                                    extractStatusMessage = "Extraction error: ${e.message}"
                                    extractIsError = true
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        enabled = !isProcessing && (extractCarrierBitmap != null || extractCarrierWavBytes != null),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Extract",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Result Card
                    if (extractedPayload != null) {
                        val payload = extractedPayload!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Payload (${payload.type.name})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (payload.isEncrypted) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Encrypted",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                when (payload.type) {
                                    PayloadType.TEXT -> {
                                        val text = String(payload.data, Charsets.UTF_8)
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                            )
                                        ) {
                                            Text(
                                                text = text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("StegoSlop Payload", text))
                                                extractStatusMessage = "Message copied to clipboard"
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Copy")
                                        }
                                    }
                                    PayloadType.IMAGE -> {
                                        val bmp = BitmapFactory.decodeByteArray(payload.data, 0, payload.data.size)
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        }

                                        Button(
                                            onClick = { saveExtractedFilePicker.launch("extracted_image.png") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(28.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Save")
                                        }
                                    }
                                    PayloadType.AUDIO -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (isPlayingAudio) {
                                                        currentAudioTrack?.stop()
                                                        currentAudioTrack?.release()
                                                        currentAudioTrack = null
                                                        isPlayingAudio = false
                                                    } else {
                                                        try {
                                                            val info = AudioLsbEngine.parseWav(payload.data)
                                                            val minBuf = AudioTrack.getMinBufferSize(
                                                                info.sampleRate,
                                                                if (info.numChannels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
                                                                AudioFormat.ENCODING_PCM_16BIT
                                                            )
                                                            val track = AudioTrack(
                                                                AudioAttributes.Builder()
                                                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                                    .build(),
                                                                AudioFormat.Builder()
                                                                    .setSampleRate(info.sampleRate)
                                                                    .setChannelMask(if (info.numChannels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
                                                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                                                    .build(),
                                                                maxOf(minBuf, info.dataSize),
                                                                AudioTrack.MODE_STREAM,
                                                                AudioManager.AUDIO_SESSION_ID_GENERATE
                                                            )
                                                            track.play()
                                                            track.write(payload.data, info.dataOffset, info.dataSize)
                                                            currentAudioTrack = track
                                                            isPlayingAudio = true
                                                        } catch (e: Exception) {
                                                            extractStatusMessage = "Playback failed: ${e.message}"
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = null
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(if (isPlayingAudio) "Stop" else "Play")
                                            }

                                            Button(
                                                onClick = { saveExtractedFilePicker.launch("extracted_audio.wav") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(28.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Save")
                                            }
                                        }
                                    }
                                    PayloadType.FILE -> {
                                        Text(
                                            text = "Binary File: ${formatBytes(payload.data.size)}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Button(
                                            onClick = { saveExtractedFilePicker.launch("extracted_file.bin") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(28.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Save")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bit Plane Card (Collapsible X-Ray Bit Plane)
                    if (extractCarrierBitmap != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { bitPlaneExpanded = !bitPlaneExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "X-Ray Bit Plane",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = if (bitPlaneExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null
                                    )
                                }

                                AnimatedVisibility(visible = bitPlaneExpanded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = "Bit Plane Inspector",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            (0..7).forEach { idx ->
                                                CarrierTypeChip(
                                                    title = "$idx",
                                                    selected = bitPlaneIndex == idx,
                                                    onSelect = {
                                                        bitPlaneIndex = idx
                                                        extractCarrierBitmap?.let { bmp ->
                                                            bitPlaneBitmap = ImageLsbEngine.generateBitPlane(bmp, idx)
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        if (bitPlaneBitmap != null) {
                                            Image(
                                                bitmap = bitPlaneBitmap!!.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(240.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedSegmentedPill(
    selectedMode: AppMode,
    onModeSelected: (AppMode) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                val background by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "pillBackground"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "pillTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(background)
                        .clickable { onModeSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (mode == AppMode.INJECT) "Inject" else "Extract",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CarrierTypeChip(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onSelect() },
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun formatBytes(bytes: Int): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}
