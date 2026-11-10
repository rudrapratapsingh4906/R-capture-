package com.rcapture

import com.rcapture.service.RecordingSessionState
import com.rcapture.service.RecordingStatus
import com.rcapture.ui.RecordingConfig
import org.junit.Assert.*
import org.junit.Test

class Phase4RecordingTest {

    @Test
    fun `recording configuration model holds valid defaults and updates`() {
        val defaultConfig = RecordingConfig()
        assertEquals("1080p FHD", defaultConfig.resolution)
        assertEquals(30, defaultConfig.fps)
        assertEquals("Internal Audio", defaultConfig.audioSource)

        val customConfig = defaultConfig.copy(
            resolution = "720p HD",
            fps = 60,
            audioSource = "Muted"
        )
        assertEquals("720p HD", customConfig.resolution)
        assertEquals(60, customConfig.fps)
        assertEquals("Muted", customConfig.audioSource)
    }

    @Test
    fun `recording session state transitions correctly`() {
        var state = RecordingSessionState()
        assertEquals(RecordingStatus.IDLE, state.status)
        assertEquals(0L, state.durationMillis)

        state = state.copy(status = RecordingStatus.RECORDING, filePath = "/path/to/video.mp4")
        assertEquals(RecordingStatus.RECORDING, state.status)
        assertEquals("/path/to/video.mp4", state.filePath)

        state = state.copy(status = RecordingStatus.PAUSED, durationMillis = 5000L)
        assertEquals(RecordingStatus.PAUSED, state.status)
        assertEquals(5000L, state.durationMillis)

        state = state.copy(status = RecordingStatus.IDLE, fileSize = 1024L)
        assertEquals(RecordingStatus.IDLE, state.status)
        assertEquals(1024L, state.fileSize)
    }

    @Test
    fun `recording error state stores error message`() {
        val errorState = RecordingSessionState(
            status = RecordingStatus.ERROR,
            errorMessage = "Encoder initialization failed"
        )
        assertEquals(RecordingStatus.ERROR, errorState.status)
        assertEquals("Encoder initialization failed", errorState.errorMessage)
    }

    @Test
    fun `duration formatting logic is accurate`() {
        fun format(millis: Long): String {
            val seconds = (millis / 1000) % 60
            val minutes = (millis / (1000 * 60)) % 60
            val hours = (millis / (1000 * 60 * 60)) % 24
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        assertEquals("00:05", format(5000L))
        assertEquals("01:00", format(60000L))
        assertEquals("01:00:00", format(3600000L))
    }
}
