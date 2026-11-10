package com.rcapture.data

import kotlinx.coroutines.flow.Flow

class CaptureRepository(private val captureDao: CaptureDao) {
    val allCaptures: Flow<List<CaptureEntity>> = captureDao.getAllCaptures()

    fun search(query: String): Flow<List<CaptureEntity>> {
        return captureDao.searchCaptures(query)
    }

    fun getFiltered(
        source: String? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): Flow<List<CaptureEntity>> {
        return captureDao.getCapturesFiltered(source, startTime, endTime)
    }

    suspend fun insert(text: String, source: String = "Accessibility", entitiesJson: String? = null): Long {
        return captureDao.insertCapture(
            CaptureEntity(
                text = text,
                source = source,
                entitiesJson = entitiesJson
            )
        )
    }

    suspend fun delete(capture: CaptureEntity) {
        captureDao.deleteCapture(capture)
    }

    suspend fun clearAll() {
        captureDao.clearAll()
    }
}
