package com.p0lymatic.stegoslop.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.p0lymatic.stegoslop.core.AudioLsbEngine
import com.p0lymatic.stegoslop.core.OpusCodecBridge
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerSheet(
    audioBytes: ByteArray,
    audioTitle: String,
    onDismiss: () -> Unit,
    onSaveWav: (() -> Unit)? = null,
    onSaveOpus: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isWav = remember(audioBytes) { OpusCodecBridge.isWav(audioBytes) }
    val formatBadge = remember(audioBytes, isWav) {
        if (isWav) {
            try {
                val info = AudioLsbEngine.parseWav(audioBytes)
                "%.1f kHz • 16-bit • WAV".format(info.sampleRate / 1000.0)
            } catch (_: Throwable) {
                "WAV Audio"
            }
        } else {
            "48.0 kHz • OPUS"
        }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf(0) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    // Prepare MediaPlayer safely
    DisposableEffect(audioBytes) {
        val tempSuffix = if (isWav) ".wav" else ".opus"
        val tempFile = File.createTempFile("stego_player_", tempSuffix, context.cacheDir)
        try {
            tempFile.writeBytes(audioBytes)
            val mp = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                totalDurationMs = duration.coerceAtLeast(0)
                setOnCompletionListener {
                    isPlaying = false
                    currentPositionMs = 0
                }
            }
            mediaPlayer = mp
            mp.start()
            isPlaying = true
        } catch (_: Throwable) {
            // If direct playback fails, attempt WAV decode
            try {
                val pcmWav = OpusCodecBridge.decodeOpusToPcmWav(context, audioBytes)
                val wavFile = File.createTempFile("stego_player_wav_", ".wav", context.cacheDir)
                wavFile.writeBytes(pcmWav)
                val mp = MediaPlayer().apply {
                    setDataSource(wavFile.absolutePath)
                    prepare()
                    totalDurationMs = duration.coerceAtLeast(0)
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = 0
                    }
                }
                mediaPlayer = mp
                mp.start()
                isPlaying = true
            } catch (_: Throwable) {}
        }

        onDispose {
            try { mediaPlayer?.stop() } catch (_: Throwable) {}
            try { mediaPlayer?.release() } catch (_: Throwable) {}
            mediaPlayer = null
            tempFile.delete()
        }
    }

    // Progress ticker
    LaunchedEffect(isPlaying, isSeeking) {
        while (isPlaying && !isSeeking) {
            mediaPlayer?.let { mp ->
                try {
                    currentPositionMs = mp.currentPosition
                } catch (_: Throwable) {}
            }
            delay(50L)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Track Title (Pixel Player OSS style)
            Text(
                text = audioTitle.ifBlank { "Audio Payload" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Audio Format Pill Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = formatBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Wavy / Pixel Player Slider
            val progress = if (totalDurationMs > 0) {
                if (isSeeking) seekProgress else (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
            } else 0f

            WavyProgressBar(
                progress = progress,
                isPlaying = isPlaying,
                onSeekStarted = {
                    isSeeking = true
                    seekProgress = it
                },
                onSeekProgress = {
                    seekProgress = it
                },
                onSeekFinished = {
                    isSeeking = false
                    val targetMs = (it * totalDurationMs).toInt()
                    currentPositionMs = targetMs
                    try {
                        mediaPlayer?.seekTo(targetMs)
                    } catch (_: Throwable) {}
                },
                waveColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            )

            // Timestamps (Current on left, Duration on right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(totalDurationMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Big Center MD3 Controls (Pixel Player style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // -10 sec button
                IconButton(
                    onClick = {
                        mediaPlayer?.let { mp ->
                            val target = (mp.currentPosition - 10_000).coerceAtLeast(0)
                            currentPositionMs = target
                            try { mp.seekTo(target) } catch (_: Throwable) {}
                        }
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Rewind 10 seconds",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Big MD3 Center Play/Pause Pill Button
                Surface(
                    onClick = {
                        mediaPlayer?.let { mp ->
                            if (isPlaying) {
                                try { mp.pause() } catch (_: Throwable) {}
                                isPlaying = false
                            } else {
                                try { mp.start() } catch (_: Throwable) {}
                                isPlaying = true
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(width = 88.dp, height = 72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // +10 sec button
                IconButton(
                    onClick = {
                        mediaPlayer?.let { mp ->
                            val target = (mp.currentPosition + 10_000).coerceAtMost(totalDurationMs)
                            currentPositionMs = target
                            try { mp.seekTo(target) } catch (_: Throwable) {}
                        }
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Forward 10 seconds",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Save Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onSaveOpus != null) {
                    OutlinedButton(
                        onClick = onSaveOpus,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Opus", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                if (onSaveWav != null) {
                    OutlinedButton(
                        onClick = onSaveWav,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save WAV", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Pixel Player OSS / Android 13-style Wavy Progress Bar with interactive scrubbing.
 */
@Composable
private fun WavyProgressBar(
    progress: Float,
    isPlaying: Boolean,
    onSeekStarted: (Float) -> Unit,
    onSeekProgress: (Float) -> Unit,
    onSeekFinished: (Float) -> Unit,
    waveColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeekStarted(newProgress)
                    onSeekFinished(newProgress)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekStarted(newProgress)
                    },
                    onDragEnd = {
                        onSeekFinished(progress)
                    },
                    onDragCancel = {
                        onSeekFinished(progress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeekProgress(newProgress)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val width = size.width
            val midY = size.height / 2f
            val playedWidth = width * progress.coerceIn(0f, 1f)

            // Draw unplayed background track (flat line)
            drawLine(
                color = trackColor,
                start = Offset(playedWidth, midY),
                end = Offset(width, midY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Draw played wavy track
            if (playedWidth > 0f) {
                val wavePath = Path()
                wavePath.moveTo(0f, midY)

                val waveAmplitude = if (isPlaying) 4.dp.toPx() else 0f
                val waveLength = 28.dp.toPx()
                val stepPx = 4f
                var x = 0f

                while (x <= playedWidth) {
                    val relativeX = x / waveLength
                    val y = midY + waveAmplitude * sin(relativeX * 2 * PI.toFloat() + wavePhase)
                    wavePath.lineTo(x, y)
                    x += stepPx
                }
                wavePath.lineTo(playedWidth, midY)

                drawPath(
                    path = wavePath,
                    color = waveColor,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Thumb handle
                drawCircle(
                    color = waveColor,
                    radius = 8.dp.toPx(),
                    center = Offset(playedWidth, midY)
                )
            }
        }
    }
}

private fun formatDuration(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
