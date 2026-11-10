package com.rcapture.ui

import android.app.Application
import android.content.*
import android.net.Uri
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rcapture.data.CaptureEntity
import com.rcapture.data.CaptureRepository
import com.rcapture.data.OcrRepository
import com.rcapture.data.RcaptureDatabase
import com.rcapture.data.ExportManager
import com.rcapture.diagnostics.DiagnosticRepository
import com.rcapture.diagnostics.SystemDiagnosticsReport
import com.rcapture.intelligence.ExtractedEntity
import com.rcapture.intelligence.TextIntelligenceEngine
import com.rcapture.service.RcaptureAccessibilityService
import com.rcapture.service.RecordingService
import com.rcapture.service.RecordingSessionState
import com.rcapture.service.RecordingStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RcaptureUiState(
    val selectedImageUri: Uri? = null,
    val extractedText: String = "",
    val detectedEntities: List<ExtractedEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val savedCaptures: List<CaptureEntity> = emptyList(),
    val recordingSessionState: RecordingSessionState = RecordingSessionState(),
    val searchQuery: String = "",
    val isExporting: Boolean = false
)

data class RecordingConfig(
    val resolution: String = "1080p FHD",
    val fps: Int = 30,
    val audioSource: String = "Internal Audio"
)

class RcaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val ocrRepository = OcrRepository(application)
    private val captureRepository = CaptureRepository(RcaptureDatabase.getDatabase(application).captureDao())
    private val diagnosticRepository = DiagnosticRepository()
    private val textIntelligenceEngine = TextIntelligenceEngine()
    private val exportManager = ExportManager(application)

    private val _uiState = MutableStateFlow(RcaptureUiState())
    val uiState: StateFlow<RcaptureUiState> = _uiState.asStateFlow()

    private val _diagnosticsReport = MutableStateFlow<SystemDiagnosticsReport?>(null)
    val diagnosticsReport: StateFlow<SystemDiagnosticsReport?> = _diagnosticsReport.asStateFlow()

    private val _isDiagnosticsLoading = MutableStateFlow(false)
    val isDiagnosticsLoading: StateFlow<Boolean> = _isDiagnosticsLoading.asStateFlow()

    private val _isFlagSecureEnabled = MutableStateFlow(false)
    val isFlagSecureEnabled: StateFlow<Boolean> = _isFlagSecureEnabled.asStateFlow()

    private val _recordingConfig = MutableStateFlow(RecordingConfig())
    val recordingConfig: StateFlow<RecordingConfig> = _recordingConfig.asStateFlow()

    private val _isRecordingConsentGranted = MutableStateFlow(false)
    val isRecordingConsentGranted: StateFlow<Boolean> = _isRecordingConsentGranted.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.RecordingBinder
            val serviceInstance = binder.getService()
            _recordingService.value = serviceInstance
            isServiceBound = true
            
            // Collect recording state from service
            viewModelScope.launch {
                serviceInstance.recordingState.collect { state ->
                    _uiState.update { it.copy(recordingSessionState = state) }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _recordingService.value = null
            isServiceBound = false
        }
    }

    private val _recordingService = MutableStateFlow<RecordingService?>(null)

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300L)
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        captureRepository.allCaptures
                    } else {
                        captureRepository.search(query)
                    }
                }
                .collect { captures ->
                    _uiState.update { it.copy(savedCaptures = captures) }
                }
        }
        runDiagnostics()
        
        // Bind to recording service if it's already running or when needed
        val intent = Intent(application, RecordingService::class.java)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun runDiagnostics(context: Context = getApplication()) {
        viewModelScope.launch {
            _isDiagnosticsLoading.value = true
            try {
                val report = diagnosticRepository.runFullDiagnostic(
                    context = context,
                    isWindowSecure = _isFlagSecureEnabled.value
                )
                _diagnosticsReport.value = report
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Diagnostics error: ${e.localizedMessage}") }
            } finally {
                _isDiagnosticsLoading.value = false
            }
        }
    }

    fun setFlagSecureState(enabled: Boolean, context: Context = getApplication()) {
        _isFlagSecureEnabled.value = enabled
        runDiagnostics(context)
    }

    fun updateRecordingConfig(resolution: String, fps: Int, audioSource: String) {
        _recordingConfig.value = RecordingConfig(
            resolution = resolution,
            fps = fps,
            audioSource = audioSource
        )
    }

    fun startRecording(resultCode: Int, data: Intent) {
        val context = getApplication<Application>()
        val config = _recordingConfig.value
        
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        
        val (width, height) = when (config.resolution) {
            "1080p FHD" -> 1080 to 1920
            "720p HD" -> 720 to 1280
            else -> metrics.widthPixels to metrics.heightPixels
        }
        
        val bitRate = when (config.resolution) {
            "1080p FHD" -> 6000000
            "720p HD" -> 3000000
            else -> 4000000
        }

        _recordingService.value?.startRecording(
            resultCode = resultCode,
            data = data,
            width = width,
            height = height,
            dpi = metrics.densityDpi,
            fps = config.fps,
            bitRate = bitRate,
            useAudio = config.audioSource != "Muted"
        )
    }

    fun stopRecording() {
        _recordingService.value?.stopRecording()
    }

    fun pauseRecording() {
        _recordingService.value?.pauseRecording()
    }

    fun resumeRecording() {
        _recordingService.value?.resumeRecording()
    }

    fun setRecordingConsentGranted(granted: Boolean) {
        _isRecordingConsentGranted.value = granted
        if (granted) {
            _uiState.update { it.copy(statusMessage = "MediaProjection consent approved for current session.") }
        }
    }

    fun onImageSelected(uri: Uri?) {
        if (uri == null) return
        _uiState.update {
            it.copy(
                selectedImageUri = uri,
                extractedText = "",
                detectedEntities = emptyList(),
                errorMessage = null,
                statusMessage = "Image selected. Extracting text..."
            )
        }
        processImage(uri)
    }

    fun processImage(uri: Uri? = _uiState.value.selectedImageUri) {
        if (uri == null) {
            _uiState.update { it.copy(errorMessage = "Please select an image first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Recognizing text on-device...") }
            val result = ocrRepository.recognizeTextFromUri(uri)
            result.fold(
                onSuccess = { text ->
                    val entities = textIntelligenceEngine.extractEntities(text)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            extractedText = text,
                            detectedEntities = entities,
                            statusMessage = if (text.isBlank()) "No text found in selected image." else "Extracted text and ${entities.size} structured entities."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to extract text.",
                            statusMessage = "Error during text recognition."
                        )
                    }
                }
            )
        }
    }

    fun analyzeText(text: String) {
        val entities = textIntelligenceEngine.extractEntities(text)
        _uiState.update {
            it.copy(
                extractedText = text,
                detectedEntities = entities,
                statusMessage = "Analyzed text: ${entities.size} entities detected."
            )
        }
    }

    fun captureActiveScreenText() {
        if (!RcaptureAccessibilityService.isServiceRunning) {
            _uiState.update {
                it.copy(errorMessage = "Accessibility Service is not enabled. Please enable it in Settings or Capture.")
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "Capturing active screen text...") }

        viewModelScope.launch {
            try {
                val latest = _uiState.value.savedCaptures.firstOrNull()
                if (latest != null && latest.text.isNotBlank()) {
                    val entities = textIntelligenceEngine.extractEntities(latest.text)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            extractedText = latest.text,
                            detectedEntities = entities,
                            statusMessage = "Loaded active screen capture into workspace. ${entities.size} structured entities detected."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Floating '📷 R-Cap' overlay is active. Tap it over any window to extract and store text directly in SQLite."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage)
                }
            }
        }
    }

    fun saveCurrentText() {
        val currentText = _uiState.value.extractedText
        val entities = _uiState.value.detectedEntities
        if (currentText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "No text to save.") }
            return
        }
        viewModelScope.launch {
            val entitiesJson = com.rcapture.data.EntityJsonAdapter.toJson(entities)
            captureRepository.insert(currentText, source = "Manual Save", entitiesJson = entitiesJson)
            _uiState.update {
                it.copy(statusMessage = "Capture saved with ${entities.size} entities to local history.")
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun exportToJson(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, statusMessage = "Packaging history as JSON...") }
            val result = exportManager.exportToJson(uri, _uiState.value.savedCaptures)
            _uiState.update { 
                it.copy(
                    isExporting = false, 
                    statusMessage = if (result.isSuccess) "History exported to JSON successfully." else "Export failed: ${result.exceptionOrNull()?.localizedMessage}"
                ) 
            }
        }
    }

    fun exportToCsv(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, statusMessage = "Packaging history as CSV...") }
            val result = exportManager.exportToCsv(uri, _uiState.value.savedCaptures)
            _uiState.update { 
                it.copy(
                    isExporting = false, 
                    statusMessage = if (result.isSuccess) "History exported to CSV successfully." else "Export failed: ${result.exceptionOrNull()?.localizedMessage}"
                ) 
            }
        }
    }

    fun deleteCapture(capture: CaptureEntity) {
        viewModelScope.launch {
            captureRepository.delete(capture)
            _uiState.update { it.copy(statusMessage = "Capture deleted.") }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            captureRepository.clearAll()
            _uiState.update { it.copy(statusMessage = "All history cleared.") }
        }
    }

    fun clearAll() {
        _uiState.update {
            RcaptureUiState(
                savedCaptures = it.savedCaptures,
                statusMessage = "Cleared current workspace. Saved history preserved."
            )
        }
    }

    fun setStatusMessage(msg: String?) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    override fun onCleared() {
        super.onCleared()
        if (isServiceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
