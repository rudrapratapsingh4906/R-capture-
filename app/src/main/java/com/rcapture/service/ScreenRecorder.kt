package com.rcapture.service

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecorder(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val outputFile: File,
    private val width: Int,
    private val height: Int,
    private val dpi: Int,
    private val fps: Int,
    private val bitRate: Int,
    private val useAudio: Boolean = false
) {
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var muxer: MediaMuxer? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var inputSurface: Surface? = null
    
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    
    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioSetupSuccessful = false

    fun start() {
        if (isRecording.get()) return
        isRecording.set(true)
        isPaused.set(false)
        
        recordingJob = scope.launch {
            try {
                prepareVideoEncoder()
                audioSetupSuccessful = if (useAudio) prepareAudioEncoder() else false
                
                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                
                virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenRecorder",
                    width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface, null, null
                )
                
                videoEncoder?.start()
                if (audioSetupSuccessful) {
                    audioEncoder?.start()
                    audioRecord?.startRecording()
                }
                
                recordLoop()
            } catch (e: Exception) {
                Log.e("ScreenRecorder", "Error during recording", e)
                stop()
            }
        }
    }

    private fun prepareVideoEncoder() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        
        videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = createInputSurface()
        }
    }

    private fun prepareAudioEncoder(): Boolean {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate, channelConfig, audioFormat, bufferSize
                )
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("ScreenRecorder", "AudioRecord failed to initialize.")
                    audioRecord = null
                    return false
                }
            } catch (e: SecurityException) {
                Log.e("ScreenRecorder", "SecurityException creating AudioRecord: ${e.message}")
                return false
            }
        } else {
            Log.e("ScreenRecorder", "Missing RECORD_AUDIO permission for microphone recording.")
            return false
        }
        
        return try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 64000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            }
            
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            true
        } catch (e: Exception) {
            Log.e("ScreenRecorder", "Failed to create/configure audio encoder: ${e.message}")
            audioRecord?.release()
            audioRecord = null
            false
        }
    }

    private suspend fun recordLoop() = coroutineScope {
        val videoBufferInfo = MediaCodec.BufferInfo()
        val audioBufferInfo = MediaCodec.BufferInfo()
        
        while (isRecording.get()) {
            if (isPaused.get()) {
                delay(100)
                continue
            }
            
            drainVideoEncoder(videoBufferInfo)
            if (audioSetupSuccessful) drainAudioEncoder(audioBufferInfo)
            
            yield()
        }
    }

    private fun drainVideoEncoder(bufferInfo: MediaCodec.BufferInfo) {
        val encoder = videoEncoder ?: return
        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 1000)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) throw RuntimeException("Format changed twice")
                videoTrackIndex = muxer?.addTrack(encoder.outputFormat) ?: -1
                checkReadyToStartMuxer()
            } else if (outputBufferIndex >= 0) {
                val encodedData = encoder.getOutputBuffer(outputBufferIndex) ?: continue
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0 && muxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                }
                encoder.releaseOutputBuffer(outputBufferIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
    }

    private fun drainAudioEncoder(bufferInfo: MediaCodec.BufferInfo) {
        val encoder = audioEncoder ?: return
        val record = audioRecord ?: return
        
        // Feed PCM data to encoder
        val inputBufferIndex = encoder.dequeueInputBuffer(1000)
        if (inputBufferIndex >= 0) {
            val inputBuffer = encoder.getInputBuffer(inputBufferIndex) ?: return
            val bytesRead = record.read(inputBuffer, inputBuffer.remaining())
            if (bytesRead > 0) {
                encoder.queueInputBuffer(inputBufferIndex, 0, bytesRead, System.nanoTime() / 1000, 0)
            } else {
                encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, 0)
            }
        }
        
        // Drain encoded AAC data
        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 1000)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                audioTrackIndex = muxer?.addTrack(encoder.outputFormat) ?: -1
                checkReadyToStartMuxer()
            } else if (outputBufferIndex >= 0) {
                val encodedData = encoder.getOutputBuffer(outputBufferIndex) ?: continue
                if (bufferInfo.size != 0 && muxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer?.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                }
                encoder.releaseOutputBuffer(outputBufferIndex, false)
            }
        }
    }

    private fun checkReadyToStartMuxer() {
        if (!muxerStarted && videoTrackIndex != -1 && (!audioSetupSuccessful || audioTrackIndex != -1)) {
            muxer?.start()
            muxerStarted = true
        }
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    fun stop() {
        if (!isRecording.get()) return
        isRecording.set(false)
        
        runBlocking {
            recordingJob?.cancelAndJoin()
        }
        
        releaseResources()
    }

    private fun releaseResources() {
        try {
            virtualDisplay?.release()
            videoEncoder?.stop()
            videoEncoder?.release()
            audioEncoder?.stop()
            audioEncoder?.release()
            audioRecord?.stop()
            audioRecord?.release()
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
        } catch (e: Exception) {
            Log.e("ScreenRecorder", "Error releasing resources", e)
        } finally {
            virtualDisplay = null
            videoEncoder = null
            audioEncoder = null
            audioRecord = null
            muxer = null
            inputSurface = null
            muxerStarted = false
        }
    }
}
