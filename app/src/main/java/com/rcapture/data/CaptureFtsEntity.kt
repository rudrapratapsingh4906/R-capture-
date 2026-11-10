package com.rcapture.data

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = CaptureEntity::class)
@Entity(tableName = "captures_fts")
data class CaptureFtsEntity(
    val text: String
)
