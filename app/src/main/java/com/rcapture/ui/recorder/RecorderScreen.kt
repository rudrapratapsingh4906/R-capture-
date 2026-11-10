package com.rcapture.ui.recorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rcapture.diagnostics.MediaProjectionDiagnosis
import com.rcapture.service.RecordingSessionState
import com.rcapture.service.RecordingStatus
import com.rcapture.ui.RecordingConfig
import com.rcapture.ui.theme.*
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderScreen(
    recordingConfig: RecordingConfig,
    isConsentGranted: Boolean,
    projectionDiagnosis: MediaProjectionDiagnosis?,
    recordingSessionState: RecordingSessionState,
    onUpdateConfig: (resolution: String, fps: Int, audioSource: String) -> Unit,
    onConsentResult: (Boolean) -> Unit,
    onStartRecording: (Int, Intent) -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onSetStatusMessage: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val audioGranted = perms[android.Manifest.permission.RECORD_AUDIO] ?: false
        if (!audioGranted && recordingConfig.audioSource != "Muted") {
            onSetStatusMessage("Microphone permission required for audio recording.")
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            onConsentResult(true)
            onStartRecording(result.resultCode, result.data!!)
        } else {
            onConsentResult(false)
            onSetStatusMessage("MediaProjection authorization denied.")
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsLauncher.launch(
                arrayOf(
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            )
        } else {
            permissionsLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "RECORDER STUDIO",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "MEDIAPROJECTION ENGINE ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ENGINE STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when (recordingSessionState.status) {
                                RecordingStatus.RECORDING -> Color.Red
                                RecordingStatus.PAUSED -> SecurityAmber
                                RecordingStatus.IDLE -> CyberEmerald
                                else -> Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = recordingSessionState.status.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = dotColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = formatDuration(recordingSessionState.durationMillis),
                        style = MaterialTheme.typography.displayMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    if (recordingSessionState.status == RecordingStatus.IDLE && recordingSessionState.fileSize > 0) {
                        Text(
                            text = "Last: ${formatFileSize(recordingSessionState.fileSize)} MP4",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (recordingSessionState.status == RecordingStatus.IDLE || recordingSessionState.status == RecordingStatus.ERROR) {
                    Button(
                        onClick = {
                            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            projectionLauncher.launch(manager.createScreenCaptureIntent())
                        },
                        modifier = Modifier.weight(1f).height(56.dp).testTag("start_recording_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Recording", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStopRecording,
                        modifier = Modifier.weight(1f).height(56.dp).testTag("stop_recording_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop", fontWeight = FontWeight.Bold)
                    }
                    
                    if (recordingSessionState.status == RecordingStatus.RECORDING) {
                        FilledTonalButton(
                            onClick = onPauseRecording,
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                        }
                    } else if (recordingSessionState.status == RecordingStatus.PAUSED) {
                        FilledTonalButton(
                            onClick = onResumeRecording,
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        }
                    }
                }
            }

            // Configuration
            Text(
                text = "CONFIGURATIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Resolution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1080p FHD", "720p HD", "Native").forEach { res ->
                            FilterChip(
                                selected = recordingConfig.resolution == res,
                                onClick = { onUpdateConfig(res, recordingConfig.fps, recordingConfig.audioSource) },
                                label = { Text(res) },
                                enabled = recordingSessionState.status == RecordingStatus.IDLE
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Frame Rate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60).forEach { fps ->
                            FilterChip(
                                selected = recordingConfig.fps == fps,
                                onClick = { onUpdateConfig(recordingConfig.resolution, fps, recordingConfig.audioSource) },
                                label = { Text("$fps FPS") },
                                enabled = recordingSessionState.status == RecordingStatus.IDLE
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Audio Source", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Internal Audio", "Muted").forEach { audio ->
                            FilterChip(
                                selected = recordingConfig.audioSource == audio,
                                onClick = { onUpdateConfig(recordingConfig.resolution, recordingConfig.fps, audio) },
                                label = { Text(audio) },
                                enabled = recordingSessionState.status == RecordingStatus.IDLE
                            )
                        }
                    }
                }
            }

            // File result
            if (recordingSessionState.filePath != null && recordingSessionState.status == RecordingStatus.IDLE) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Latest Recording Saved", fontWeight = FontWeight.Bold)
                            Text(File(recordingSessionState.filePath).name, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            val file = File(recordingSessionState.filePath)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.fromFile(file), "video/mp4")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Play Recording"))
                        }) {
                            Icon(Icons.Default.PlayCircle, contentDescription = "Play")
                        }
                        IconButton(onClick = {
                            val file = File(recordingSessionState.filePath)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Recording"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60)) % 24
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", size, units[unitIndex])
}
