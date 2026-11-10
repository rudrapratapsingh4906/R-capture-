package com.rcapture.service

enum class RecordingStatus {
    IDLE, STARTING, RECORDING, PAUSED, STOPPING, ERROR
}

data class RecordingSessionState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val durationMillis: Long = 0,
    val filePath: String? = null,
    val fileSize: Long = 0,
    val errorMessage: String? = null
)
