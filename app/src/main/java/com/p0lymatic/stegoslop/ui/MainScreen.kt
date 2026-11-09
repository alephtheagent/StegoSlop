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
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.p0lymatic.stegoslop.core.CamouflagePreset
import com.p0lymatic.stegoslop.core.CamouflageUtils
import com.p0lymatic.stegoslop.core.generateDecoyName
import com.p0lymatic.stegoslop.core.randomizeDecoyName
import androidx.compose.ui.unit.sp
import com.p0lymatic.stegoslop.core.AudioCodecBridge
import com.p0lymatic.stegoslop.core.AudioLsbEngine
import com.p0lymatic.stegoslop.core.ImageLsbEngine
import com.p0lymatic.stegoslop.core.ZeroWidthStego
import com.p0lymatic.stegoslop.core.PayloadType
import com.p0lymatic.stegoslop.core.StegoProtocol
import com.p0lymatic.stegoslop.core.UnpackedPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class AppMode {
    INJECT,
    EXTRACT
}

enum class CarrierType {
    IMAGE,
    AUDIO,
    TEXT
}
enum class PayloadTab {
    TEXT,
    FILE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var showAboutScreen by remember { mutableStateOf(false) }

    BackHandler(enabled = showAboutScreen) {
        showAboutScreen = false
    }

    AnimatedContent(
        targetState = showAboutScreen,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(animationSpec = tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> -width / 3 } + fadeOut(animationSpec = tween(300)))
            } else {
                (slideInHorizontally(animationSpec = tween(300)) { width -> -width / 3 } + fadeIn(animationSpec = tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut(animationSpec = tween(300)))
            }
        },
        label = "MainAboutTransition"
    ) { isAbout ->
        if (isAbout) {
            AboutScreen(onBack = { showAboutScreen = false })
        } else {
            MainScreenContent(onOpenAbout = { showAboutScreen = true })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    onOpenAbout: () -> Unit
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var activeMode by remember { mutableStateOf(AppMode.INJECT) }

    // Carrier state (Inject)
    var carrierType by remember { mutableStateOf(CarrierType.IMAGE) }
    var carrierBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var carrierWavBytes by remember { mutableStateOf<ByteArray?>(null) }
    var carrierFileName by remember { mutableStateOf<String?>(null) }
    var carrierFormatBadge by remember { mutableStateOf<String?>(null) }
    var coverText by remember { mutableStateOf("Привет, как дела?") }
    var injectedText by remember { mutableStateOf<String?>(null) }
    var extractInputText by remember { mutableStateOf("") }
    var bitsPerUnit by remember { mutableIntStateOf(1) }

    // Payload state (Inject)
    var payloadTab by remember { mutableStateOf(PayloadTab.TEXT) }
    var secretText by remember { mutableStateOf("") }
    var payloadFileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var payloadFileName by remember { mutableStateOf<String?>(null) }
    var preserveFileName by remember { mutableStateOf(true) }

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
    var carrierUri by remember { mutableStateOf<Uri?>(null) }
    var outputFileName by remember { mutableStateOf("") }
    var overwriteOriginal by remember { mutableStateOf(false) }
    var extractCarrierFileName by remember { mutableStateOf<String?>(null) }
    var extractCarrierFormatBadge by remember { mutableStateOf<String?>(null) }
    var extractCarrierType by remember { mutableStateOf(CarrierType.IMAGE) }
    var extractPassword by remember { mutableStateOf("") }
    var extractPasswordVisible by remember { mutableStateOf(false) }
    var extractedPayload by remember { mutableStateOf<UnpackedPayload?>(null) }
    var extractStatusMessage by remember { mutableStateOf<String?>(null) }
    var extractIsError by remember { mutableStateOf(false) }
    var forceDecode by remember { mutableStateOf(false) }
    var rawDumpBytes by remember { mutableStateOf<ByteArray?>(null) }

    // X-Ray Bit Plane state
    var bitPlaneExpanded by remember { mutableStateOf(false) }
    var bitPlaneIndex by remember { mutableIntStateOf(0) }
    var bitPlaneBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Audio Playback state
    var isPlayingAudio by remember { mutableStateOf(false) }
    var currentAudioTrack by remember { mutableStateOf<AudioTrack?>(null) }

    // File pickers
    val injectCarrierPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            carrierUri = it
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val isWav = bytes.size >= 12 &&
                                String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
                        val isFlac = AudioCodecBridge.isFlac(bytes)
                        if (isWav) {
                            carrierType = CarrierType.AUDIO
                            carrierWavBytes = bytes
                            carrierBitmap = null
                            carrierFileName = "audio_carrier.wav"
                            carrierFormatBadge = "WAV"
                        } else if (isFlac) {
                            val decodedWav = AudioCodecBridge.decodeFlacToPcmWav(context, bytes)
                            carrierType = CarrierType.AUDIO
                            carrierWavBytes = decodedWav
                            carrierBitmap = null
                            carrierFileName = "audio_carrier.flac"
                            carrierFormatBadge = "FLAC"
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
                                carrierFormatBadge = "PNG"
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
                        val resolvedName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                cursor.getString(nameIndex)
                            } else null
                        } ?: uri.lastPathSegment ?: "secret_file.bin"
                        payloadFileBytes = bytes
                        payloadFileName = resolvedName
                    }
                } catch (e: Exception) {
                    statusMessage = "Failed to load payload file: ${e.message}"
                    isError = true
                }
            }
        }
    }

    val extractCarrierPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val isWav = bytes.size >= 12 &&
                                String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
                        val isFlac = AudioCodecBridge.isFlac(bytes)
                        if (isWav) {
                            extractCarrierType = CarrierType.AUDIO
                            extractCarrierWavBytes = bytes
                            extractCarrierBitmap = null
                            extractCarrierFileName = "stego_audio.wav"
                            extractCarrierFormatBadge = "WAV"
                        } else if (isFlac) {
                            val decodedWav = AudioCodecBridge.decodeFlacToPcmWav(context, bytes)
                            extractCarrierType = CarrierType.AUDIO
                            extractCarrierWavBytes = decodedWav
                            extractCarrierBitmap = null
                            extractCarrierFileName = "stego_audio.flac"
                            extractCarrierFormatBadge = "FLAC"
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
                                extractCarrierFormatBadge = "PNG"
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

    val saveInjectedWavPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        injectedWavBytes?.let { out.write(it) }
                    }
                    statusMessage = "WAV file saved successfully"
                    isError = false
                } catch (e: Exception) {
                    statusMessage = "Failed to save WAV file: ${e.message}"
                    isError = true
                }
            }
        }
    }

    val saveInjectedFlacPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/flac")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val flacBytes = injectedWavBytes?.let { AudioCodecBridge.encodePcmWavToFlac(context, it) }
                    if (flacBytes != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(flacBytes)
                        }
                        statusMessage = "FLAC file saved successfully"
                        isError = false
                    }
                } catch (e: Exception) {
                    statusMessage = "Failed to save FLAC file: ${e.message}"
                    isError = true
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

    val saveRawDumpPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    rawDumpBytes?.let { data ->
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(data)
                        }
                        extractStatusMessage = "Raw dump saved successfully (${formatBytes(data.size)})"
                        extractIsError = false
                    }
                } catch (e: Exception) {
                    extractStatusMessage = "Failed to save raw dump: ${e.message}"
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
            CarrierType.TEXT -> Int.MAX_VALUE
        }
    }

    val rawPayloadBytes = remember(payloadTab, secretText, payloadFileBytes) {
        when (payloadTab) {
            PayloadTab.TEXT -> secretText.toByteArray(Charsets.UTF_8)
            PayloadTab.FILE -> payloadFileBytes ?: ByteArray(0)
        }
    }

    val estimatedContainerSize = remember(rawPayloadBytes, encryptEnabled, preserveFileName, payloadFileName, payloadTab) {
        if (rawPayloadBytes.isEmpty()) 0
        else {
            val fileNameBytesLen = if (payloadTab == PayloadTab.FILE && preserveFileName && payloadFileName != null) {
                1 + payloadFileName!!.toByteArray(Charsets.UTF_8).size
            } else 0
            val headerSize = StegoProtocol.HEADER_MIN_LENGTH + (if (encryptEnabled) 28 else 0) + fileNameBytesLen
            val dataSize = rawPayloadBytes.size + (if (encryptEnabled) 16 else 0)
            headerSize + dataSize
        }
    }

    val hasCarrier = when (activeMode) {
        AppMode.INJECT -> (carrierType == CarrierType.IMAGE && carrierBitmap != null) ||
                (carrierType == CarrierType.AUDIO && carrierWavBytes != null) ||
                (carrierType == CarrierType.TEXT && coverText.isNotBlank())
        AppMode.EXTRACT -> (extractCarrierType == CarrierType.IMAGE && extractCarrierBitmap != null) ||
                (extractCarrierType == CarrierType.AUDIO && extractCarrierWavBytes != null) ||
                (extractCarrierType == CarrierType.TEXT && extractInputText.isNotBlank())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "StegoSlop",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenAbout,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Spring-Sliding Pill Mode Switcher
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    val pillWidth = maxWidth / 2
                    val pillOffset by animateDpAsState(
                        targetValue = if (activeMode == AppMode.INJECT) 0.dp else pillWidth,
                        animationSpec = spring(
                            dampingRatio = 0.76f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "pillSpringAnimation"
                    )

                    Box(
                        modifier = Modifier
                            .offset(x = pillOffset)
                            .width(pillWidth)
                            .height(44.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .clickable {
                                    activeMode = AppMode.INJECT
                                    statusMessage = null
                                    isError = false
                                }
                        ) {
                            Text(
                                text = "Inject",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (activeMode == AppMode.INJECT) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeMode == AppMode.INJECT) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .clickable {
                                    activeMode = AppMode.EXTRACT
                                    extractStatusMessage = null
                                    extractIsError = false
                                }
                        ) {
                            Text(
                                text = "Extract",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (activeMode == AppMode.EXTRACT) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeMode == AppMode.EXTRACT) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Mode Container with Smooth Directional Slide Animations
            AnimatedContent(
                targetState = activeMode,
                transitionSpec = {
                    if (targetState == AppMode.EXTRACT && initialState == AppMode.INJECT) {
                        (slideInHorizontally(animationSpec = tween(240)) { width -> width } + fadeIn(animationSpec = tween(240)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { width -> -width } + fadeOut(animationSpec = tween(240)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(240)) { width -> -width } + fadeIn(animationSpec = tween(240)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { width -> width } + fadeOut(animationSpec = tween(240)))
                    }.using(SizeTransform(clip = false))
                },
                label = "modeAnimatedContent",
                modifier = Modifier.fillMaxWidth()
            ) { mode ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (mode) {
                        AppMode.INJECT -> {
                            // Carrier Card
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
                                            text = "Carrier",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            CarrierTypeChip(
                                                title = "Text",
                                                selected = carrierType == CarrierType.TEXT,
                                                onSelect = { carrierType = CarrierType.TEXT }
                                            )
                                        }
                                    }

                                    if (carrierType == CarrierType.TEXT) {
                                        OutlinedTextField(
                                            value = coverText,
                                            onValueChange = { coverText = it },
                                            label = { Text("Cover Text") },
                                            placeholder = { Text("Привет, как дела?") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 3,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                    } else {
                                        Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (carrierType == CarrierType.IMAGE) {
                                                    injectCarrierPicker.launch(arrayOf("image/png", "image/webp", "image/bmp", "image/*"))
                                                } else {
                                                    injectCarrierPicker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/flac", "audio/x-flac", "audio/*"))
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(28.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme.onSurface
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
                                                    val wav = AudioLsbEngine.createSyntheticWav(sampleCount = 88200)
                                                    carrierWavBytes = wav
                                                    carrierFileName = "synthetic_44khz_2s.wav"
                                                    carrierFormatBadge = "WAV"
                                                }
                                            },
                                            shape = RoundedCornerShape(28.dp)
                                        ) {
                                            Text("Sample")
                                        }
                                    }

                                    if ((carrierType == CarrierType.IMAGE && carrierBitmap != null) ||
                                        (carrierType == CarrierType.AUDIO && carrierWavBytes != null)
                                    ) {
                                        Text(
                                            text = carrierFileName ?: (if (carrierType == CarrierType.IMAGE) "Image Carrier" else "Audio Carrier"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = formatBytes(availableCapacity),
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            val detailText = if (carrierType == CarrierType.IMAGE && carrierBitmap != null) {
                                                "${carrierBitmap!!.width} x ${carrierBitmap!!.height} | ARGB_8888"
                                            } else {
                                                try {
                                                    val info = AudioLsbEngine.parseWav(carrierWavBytes!!)
                                                    String.format(Locale.US, "%.2fs (%d Hz, 16-bit)", info.durationSeconds, info.sampleRate)
                                                } catch (e: Exception) {
                                                    "PCM WAV"
                                                }
                                            }
                                            Text(
                                                text = detailText,
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "No carrier selected",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Choose an image or audio file to calculate capacity",
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
                                }
                                }
                            }

                            // Payload Card
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
                                            text = "Payload",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            CarrierTypeChip(
                                                title = "Text",
                                                selected = payloadTab == PayloadTab.TEXT,
                                                onSelect = { payloadTab = PayloadTab.TEXT }
                                            )
                                            CarrierTypeChip(
                                                title = "File",
                                                selected = payloadTab == PayloadTab.FILE,
                                                onSelect = { payloadTab = PayloadTab.FILE }
                                            )
                                        }
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
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
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
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Preserve original filename",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Switch(
                                                    checked = preserveFileName,
                                                    onCheckedChange = { preserveFileName = it }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (carrierType != CarrierType.TEXT) {
                                // Capacity Usage Card
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
                                    val ratio = if (availableCapacity > 0) {
                                        (estimatedContainerSize.toFloat() / availableCapacity.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    val percent = if (availableCapacity > 0) ((estimatedContainerSize.toDouble() / availableCapacity) * 100).toInt() else 0
                                    val isExceeded = availableCapacity > 0 && estimatedContainerSize > availableCapacity

                                    Text(
                                        text = "Capacity Usage",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = "$percent%",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )

                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Used: ${formatBytes(estimatedContainerSize)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Total: ${formatBytes(availableCapacity)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isExceeded) {
                                        Text(
                                            text = "Payload exceeds carrier capacity",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                            // Security Card
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
                                        Column {
                                            Text(
                                                text = "Security",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Encrypt with Password",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Switch(
                                            checked = encryptEnabled,
                                            onCheckedChange = { encryptEnabled = it }
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = encryptEnabled,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(modifier = Modifier.padding(top = 4.dp)) {
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
                            }

                            // Status / Error message
                            if (statusMessage != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = statusMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            // 56.dp Inject Action Pill
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
                                    if (carrierType == CarrierType.TEXT) {
                                        if (coverText.isBlank()) {
                                            statusMessage = "Please enter cover text"
                                            isError = true
                                            return@Button
                                        }
                                        isProcessing = true
                                        statusMessage = null
                                        isError = false
                                        coroutineScope.launch {
                                            try {
                                                val output = withContext(Dispatchers.Default) {
                                                    ZeroWidthStego.encode(
                                                        coverText = coverText,
                                                        secretData = rawPayloadBytes,
                                                        password = if (encryptEnabled) password else null
                                                    )
                                                }
                                                injectedText = output
                                                injectedBitmap = null
                                                injectedWavBytes = null
                                                statusMessage = "Secret hidden in text successfully"
                                                isError = false
                                            } catch (e: Exception) {
                                                statusMessage = "Injection error: ${e.message}"
                                                isError = true
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
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
                                                    bitsPerUnit = bitsPerUnit,
                                                    fileName = if (type == PayloadType.FILE && preserveFileName) payloadFileName else null
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
                                            val ext = if (carrierType == CarrierType.IMAGE) "png" else "wav"
                                            outputFileName = randomizeDecoyName(carrierType == CarrierType.AUDIO, ext)

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

                            // Injected Text Output Card
                            if (carrierType == CarrierType.TEXT && injectedText != null) {
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
                                                text = "Injected Output",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        OutlinedTextField(
                                            value = injectedText!!,
                                            onValueChange = {},
                                            readOnly = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 3,
                                            shape = RoundedCornerShape(16.dp)
                                        )

                                        Button(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Covert Message", injectedText)
                                                clipboard.setPrimaryClip(clip)
                                                statusMessage = "Copied to clipboard"
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(28.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Copy to Clipboard")
                                        }
                                    }
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
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

                                        // File Camouflage Card
                                        Text(
                                            text = "File Camouflage",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        OutlinedTextField(
                                            value = outputFileName,
                                            onValueChange = { outputFileName = it },
                                            label = { Text("Output Filename") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    val ext = if (carrierType == CarrierType.IMAGE) "png" else "wav"
                                                    outputFileName = randomizeDecoyName(carrierType == CarrierType.AUDIO, ext)
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Casino,
                                                        contentDescription = "Randomize Decoy Name"
                                                    )
                                                }
                                            }
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (carrierType == CarrierType.IMAGE) {
                                                PillChip(title = "Screenshot") {
                                                    outputFileName = generateDecoyName(CamouflagePreset.SCREENSHOT, "png")
                                                }
                                                PillChip(title = "Camera") {
                                                    outputFileName = generateDecoyName(CamouflagePreset.CAMERA_DCIM, "png")
                                                }
                                                PillChip(title = "YouTube") {
                                                    outputFileName = generateDecoyName(CamouflagePreset.YOUTUBE, "png")
                                                }
                                                PillChip(title = "Telegram") {
                                                    outputFileName = generateDecoyName(CamouflagePreset.TELEGRAM, "png")
                                                }
                                                PillChip(title = "VK Hash") {
                                                    outputFileName = generateDecoyName(CamouflagePreset.VK_HASH, "png")
                                                }
                                                PillChip(title = "Generic") {
                                                    outputFileName = generateDecoyName(CamouflagePreset.GENERIC, "png")
                                                }
                                            } else {
                                                PillChip(title = "WhatsApp") {
                                                    outputFileName = generateDecoyName(CamouflagePreset.WHATSAPP, "wav")
                                                }
                                                PillChip(title = "Voice") {
                                                    outputFileName = generateDecoyName(CamouflagePreset.VOICE, "wav")
                                                }
                                                PillChip(title = "Track") {
                                                    outputFileName = generateDecoyName(CamouflagePreset.TRACK, "wav")
                                                }
                                                PillChip(title = "Generic") {
                                                    outputFileName = generateDecoyName(CamouflagePreset.GENERIC, "wav")
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Overwrite Original File",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Switch(
                                                checked = overwriteOriginal,
                                                onCheckedChange = { overwriteOriginal = it }
                                            )
                                        }

                                        if (overwriteOriginal) {
                                            Text(
                                                text = "Will replace the original carrier file in-place without creating new files.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (overwriteOriginal) {
                                                    if (carrierUri == null) {
                                                        statusMessage = "Cannot overwrite: Original file URI not available"
                                                        isError = true
                                                    } else {
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            try {
                                                                context.contentResolver.openOutputStream(carrierUri!!)?.use { out ->
                                                                    if (carrierType == CarrierType.IMAGE && injectedBitmap != null) {
                                                                        injectedBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out)
                                                                    } else if (carrierType == CarrierType.AUDIO && injectedWavBytes != null) {
                                                                        out.write(injectedWavBytes!!)
                                                                    }
                                                                }
                                                                statusMessage = "Original file overwritten successfully"
                                                                isError = false
                                                            } catch (e: Exception) {
                                                                statusMessage = "Failed to overwrite original file: ${e.message}"
                                                                isError = true
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    val nameToSave = if (outputFileName.isNotBlank()) {
                                                        outputFileName
                                                    } else if (carrierType == CarrierType.IMAGE) {
                                                        "stego_image.png"
                                                    } else {
                                                        "stego_audio.wav"
                                                    }
                                                    saveInjectedFilePicker.launch(nameToSave)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(28.dp),
                                            colors = if (overwriteOriginal) {
                                                ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error,
                                                    contentColor = MaterialTheme.colorScheme.onError
                                                )
                                            } else {
                                                ButtonDefaults.buttonColors()
                                            }
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (overwriteOriginal) "Overwrite Original File" else "Save Stego File")
                                        }

                                        if (carrierType == CarrierType.AUDIO && !overwriteOriginal) {
                                            OutlinedButton(
                                                onClick = {
                                                    val flacName = if (outputFileName.isNotBlank()) {
                                                        if (outputFileName.contains('.')) {
                                                            outputFileName.substringBeforeLast('.') + ".flac"
                                                        } else {
                                                            "$outputFileName.flac"
                                                        }
                                                    } else {
                                                        "stego_audio.flac"
                                                    }
                                                    saveInjectedFlacPicker.launch(flacName)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(28.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Save FLAC")
                                            }
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
                                            text = "Carrier",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            CarrierTypeChip(
                                                title = "Image",
                                                selected = extractCarrierType == CarrierType.IMAGE,
                                                onSelect = { extractCarrierType = CarrierType.IMAGE }
                                            )
                                            CarrierTypeChip(
                                                title = "Audio",
                                                selected = extractCarrierType == CarrierType.AUDIO,
                                                onSelect = { extractCarrierType = CarrierType.AUDIO }
                                            )
                                            CarrierTypeChip(
                                                title = "Text",
                                                selected = extractCarrierType == CarrierType.TEXT,
                                                onSelect = { extractCarrierType = CarrierType.TEXT }
                                            )
                                        }
                                    }

                                    if (extractCarrierType == CarrierType.TEXT) {
                                        OutlinedTextField(
                                            value = extractInputText,
                                            onValueChange = { extractInputText = it },
                                            label = { Text("Paste Text with Hidden Secret") },
                                            placeholder = { Text("Paste message here...") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 4,
                                            shape = RoundedCornerShape(16.dp)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clipData = clipboard.primaryClip
                                                    if (clipData != null && clipData.itemCount > 0) {
                                                        extractInputText = clipData.getItemAt(0).text?.toString() ?: ""
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(28.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Paste")
                                            }

                                            if (injectedText != null) {
                                                OutlinedButton(
                                                    onClick = {
                                                        extractInputText = injectedText!!
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(28.dp)
                                                ) {
                                                    Text("Use Injected")
                                                }
                                            }
                                        }
                                    } else {

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = { extractCarrierPicker.launch(arrayOf("*/*")) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(28.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme.onSurface
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
                                                        extractCarrierFormatBadge = "PNG"
                                                        bitPlaneBitmap = ImageLsbEngine.generateBitPlane(injectedBitmap!!, bitPlaneIndex)
                                                    } else if (carrierType == CarrierType.AUDIO && injectedWavBytes != null) {
                                                        extractCarrierWavBytes = injectedWavBytes
                                                        extractCarrierBitmap = null
                                                        extractCarrierType = CarrierType.AUDIO
                                                        extractCarrierFileName = "injected_audio.wav"
                                                        extractCarrierFormatBadge = "WAV"
                                                    }
                                                },
                                                shape = RoundedCornerShape(28.dp)
                                            ) {
                                                Text("Use Injected")
                                            }
                                        }
                                    }

                                    if (extractCarrierBitmap != null || extractCarrierWavBytes != null) {
                                        Text(
                                            text = extractCarrierFileName ?: (if (extractCarrierType == CarrierType.IMAGE) "Image Carrier" else "Audio Carrier"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            val detailText = if (extractCarrierBitmap != null) {
                                                "${extractCarrierBitmap!!.width} x ${extractCarrierBitmap!!.height} | ARGB_8888"
                                            } else {
                                                "${formatBytes(extractCarrierWavBytes!!.size)} | WAV"
                                            }
                                            Text(
                                                text = detailText,
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "No carrier selected",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Select a carrier to extract hidden data",
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
                                }
                                }
                            }

                            // Password Card
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
                                    Text(
                                        text = "Decryption",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                            // Force Decode Switch Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Force Decode (Bypass Header & CRC)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Bypasses SLOP header and CRC32 verification to extract raw/damaged data",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = forceDecode,
                                        onCheckedChange = { forceDecode = it }
                                    )
                                }
                            }

                            if (extractStatusMessage != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (extractIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = extractStatusMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (extractIsError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            // 56.dp Extract Action Pill
                            Button(
                                onClick = {
                                    if (extractCarrierType == CarrierType.TEXT && extractInputText.isBlank()) {
                                        extractStatusMessage = "Please paste text containing hidden data"
                                        extractIsError = true
                                        return@Button
                                    }
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
                                    rawDumpBytes = null

                                    coroutineScope.launch {
                                        try {
                                            if (forceDecode) {
                                                val rawBytes: ByteArray = withContext(Dispatchers.Default) {
                                                    when (extractCarrierType) {
                                                        CarrierType.IMAGE -> ImageLsbEngine.extractRawBits(extractCarrierBitmap!!, bitsPerUnit)
                                                        CarrierType.AUDIO -> AudioLsbEngine.extractRawBits(extractCarrierWavBytes!!, bitsPerUnit)
                                                        CarrierType.TEXT -> {
                                                            try {
                                                                ZeroWidthStego.decode(extractInputText, extractPassword.ifEmpty { null })
                                                            } catch (e: Exception) {
                                                                val headerIdx = extractInputText.indexOf(ZeroWidthStego.CHAR_HEADER)
                                                                val bitBuilder = StringBuilder()
                                                                val startIdx = if (headerIdx != -1) headerIdx + 1 else 0
                                                                for (i in startIdx until extractInputText.length) {
                                                                    val c = extractInputText[i]
                                                                    if (c == ZeroWidthStego.CHAR_ZERO) bitBuilder.append('0')
                                                                    else if (c == ZeroWidthStego.CHAR_ONE) bitBuilder.append('1')
                                                                }
                                                                val bitStr = bitBuilder.toString()
                                                                val numBytes = bitStr.length / 8
                                                                val bytes = ByteArray(numBytes)
                                                                for (bIdx in 0 until numBytes) {
                                                                    var b = 0
                                                                    for (bit in 0 until 8) {
                                                                        b = (b shl 1) or (if (bitStr[bIdx * 8 + bit] == '1') 1 else 0)
                                                                    }
                                                                    bytes[bIdx] = b.toByte()
                                                                }
                                                                bytes
                                                            }
                                                        }
                                                    }
                                                }
                                                rawDumpBytes = rawBytes
                                                val forcedPayload = withContext(Dispatchers.Default) {
                                                    try {
                                                        StegoProtocol.unpackPayloadForced(rawBytes, extractPassword.ifEmpty { null })
                                                    } catch (e: Exception) {
                                                        UnpackedPayload(
                                                            type = PayloadType.FILE,
                                                            data = rawBytes,
                                                            isCorrupted = true
                                                        )
                                                    }
                                                }
                                                extractedPayload = forcedPayload
                                                extractStatusMessage = if (forcedPayload.isCorrupted) {
                                                    "Force decoded (Warning: CRC mismatch or non-SLOP data)"
                                                } else {
                                                    "Force decoded successfully"
                                                }
                                                extractIsError = false
                                                return@launch
                                            }

                                            if (extractCarrierType == CarrierType.TEXT) {
                                                val secretBytes = withContext(Dispatchers.Default) {
                                                    ZeroWidthStego.decode(
                                                        text = extractInputText,
                                                        password = extractPassword.ifEmpty { null }
                                                    )
                                                }
                                                extractedPayload = UnpackedPayload(
                                                    type = PayloadType.TEXT,
                                                    data = secretBytes,
                                                    isEncrypted = extractPassword.isNotEmpty()
                                                )
                                                extractStatusMessage = "Secret extracted from text successfully"
                                                extractIsError = false
                                                return@launch
                                            }

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
                                enabled = !isProcessing && (extractCarrierBitmap != null || extractCarrierWavBytes != null || (extractCarrierType == CarrierType.TEXT && extractInputText.isNotBlank())),
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

                            // Raw Dump Card (Force Decode)
                            if (forceDecode && rawDumpBytes != null) {
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
                                                text = "Raw LSB Dump (${formatBytes(rawDumpBytes!!.size)})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (extractedPayload?.isCorrupted == true) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer
                                                ) {
                                                    Text(
                                                        text = "CRC Mismatch",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        val previewText = remember(rawDumpBytes) {
                                            formatHexDump(rawDumpBytes!!)
                                        }

                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Text(
                                                text = previewText,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier
                                                    .padding(12.dp)
                                                    .horizontalScroll(rememberScrollState())
                                            )
                                        }

                                        Button(
                                            onClick = { saveRawDumpPicker.launch("raw_lsb_dump.bin") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(28.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Save Raw Dump")
                                        }
                                    }
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
                                                text = "Extracted Payload (${payload.type.name})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (payload.isEncrypted) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Lock,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = "Encrypted",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        when (payload.type) {
                                            PayloadType.TEXT -> {
                                                val text = String(payload.data, Charsets.UTF_8)
                                                Card(
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                                    )
                                                ) {
                                                    Text(
                                                        text = text,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(14.dp)
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
                                                            .clip(RoundedCornerShape(16.dp))
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
                                                    text = "Binary File: ${payload.fileName ?: "extracted_file.bin"} (${formatBytes(payload.data.size)})",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )

                                                Button(
                                                    onClick = { saveExtractedFilePicker.launch(payload.fileName ?: "extracted_file.bin") },
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
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                            .clip(RoundedCornerShape(16.dp))
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
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PillChip(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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


private fun formatHexDump(bytes: ByteArray, maxLines: Int = 16): String {
    val totalChunks = (bytes.size + 15) / 16
    val linesToShow = minOf(totalChunks, maxLines)
    val sb = StringBuilder()
    for (i in 0 until linesToShow) {
        val start = i * 16
        val end = minOf(start + 16, bytes.size)
        val slice = bytes.copyOfRange(start, end)
        val hex = slice.joinToString(" ") { "%02X".format(it) }
        val ascii = slice.map { b ->
            val c = b.toInt() and 0xFF
            if (c in 32..126) c.toChar() else '.'
        }.joinToString("")
        sb.append("%04X  %-48s  |%s|\n".format(start, hex, ascii))
    }
    if (totalChunks > maxLines) {
        sb.append("... (${bytes.size - maxLines * 16} more bytes)")
    }
    return sb.toString()
}