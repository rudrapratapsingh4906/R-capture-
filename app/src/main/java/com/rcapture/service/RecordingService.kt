package com.rcapture.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rcapture.MainActivity
import com.rcapture.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingService : Service() {

    private val binder = RecordingBinder()
    
    private var mediaProjection: MediaProjection? = null
    private var screenRecorder: ScreenRecorder? = null
    
    private val _recordingState = MutableStateFlow(RecordingSessionState())
    val recordingState = _recordingState.asStateFlow()
    
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_STOP = "com.rcapture.action.STOP"
        
        var isServiceRunning = false
            private set
    }

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
        }
        return START_NOT_STICKY
    }

    fun startRecording(
        resultCode: Int,
        data: Intent,
        width: Int,
        height: Int,
        dpi: Int,
        fps: Int,
        bitRate: Int,
        useAudio: Boolean
    ) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)
        
        if (mediaProjection == null) {
            _recordingState.value = RecordingSessionState(status = RecordingStatus.ERROR, errorMessage = "Failed to obtain MediaProjection")
            return
        }

        val outputDir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputFile = File(outputDir, "R-CAP_$timestamp.mp4")

        screenRecorder = ScreenRecorder(
            context = this,
            mediaProjection = mediaProjection!!,
            outputFile = outputFile,
            width = width,
            height = height,
            dpi = dpi,
            fps = fps,
            bitRate = bitRate,
            useAudio = useAudio
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification("Starting recording..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification("Starting recording..."))
            }
            screenRecorder?.start()
            
            _recordingState.value = RecordingSessionState(
                status = RecordingStatus.RECORDING,
                filePath = outputFile.absolutePath
            )
            
            startTimer()
        } catch (e: Exception) {
            Log.e("RecordingService", "Error starting recording", e)
            _recordingState.value = RecordingSessionState(status = RecordingStatus.ERROR, errorMessage = e.message)
            stopRecording()
        }
    }

    fun stopRecording() {
        _recordingState.value = _recordingState.value.copy(status = RecordingStatus.STOPPING)
        
        timerJob?.cancel()
        screenRecorder?.stop()
        mediaProjection?.stop()
        
        val finalFile = _recordingState.value.filePath?.let { File(it) }
        val fileSize = finalFile?.length() ?: 0
        
        _recordingState.value = _recordingState.value.copy(
            status = RecordingStatus.IDLE,
            fileSize = fileSize
        )
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        screenRecorder = null
        mediaProjection = null
        stopSelf()
    }

    fun pauseRecording() {
        screenRecorder?.pause()
        _recordingState.value = _recordingState.value.copy(status = RecordingStatus.PAUSED)
    }

    fun resumeRecording() {
        screenRecorder?.resume()
        _recordingState.value = _recordingState.value.copy(status = RecordingStatus.RECORDING)
    }

    private fun startTimer() {
        timerJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                if (_recordingState.value.status == RecordingStatus.RECORDING) {
                    val duration = System.currentTimeMillis() - startTime
                    _recordingState.value = _recordingState.value.copy(durationMillis = duration)
                    updateNotification("Recording active: ${formatDuration(duration)}")
                }
                delay(1000)
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60)) % 24
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Screen Recording"
            val descriptionText = "Notifications for active screen recording"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("R-Capture Recording")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use default icon if no other
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        stopRecording()
        serviceScope.cancel()
    }
}
