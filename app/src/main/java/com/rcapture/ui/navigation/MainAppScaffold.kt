package com.rcapture.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rcapture.data.CaptureEntity
import com.rcapture.diagnostics.SystemDiagnosticsReport
import com.rcapture.service.RcaptureAccessibilityService
import com.rcapture.ui.HomeScreenContent
import com.rcapture.ui.RcaptureUiState
import com.rcapture.ui.RcaptureViewModel
import com.rcapture.ui.RecordingConfig
import com.rcapture.ui.dashboard.DashboardScreen
import com.rcapture.ui.library.LibraryScreen
import com.rcapture.ui.recorder.RecorderScreen
import com.rcapture.ui.security.SecurityLabScreen
import com.rcapture.ui.settings.SettingsScreen

@Composable
fun MainAppScaffold(
    viewModel: RcaptureViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val diagnosticsReport by viewModel.diagnosticsReport.collectAsState()
    val isDiagnosticsLoading by viewModel.isDiagnosticsLoading.collectAsState()
    val isFlagSecureEnabled by viewModel.isFlagSecureEnabled.collectAsState()
    val recordingConfig by viewModel.recordingConfig.collectAsState()
    val isRecordingConsentGranted by viewModel.isRecordingConsentGranted.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onImageSelected(uri)
    }
    
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportToJson(it) }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportToCsv(it) }
    }

    MainAppScaffoldContent(
        uiState = uiState,
        diagnosticsReport = diagnosticsReport,
        isDiagnosticsLoading = isDiagnosticsLoading,
        isFlagSecureEnabled = isFlagSecureEnabled,
        recordingConfig = recordingConfig,
        isRecordingConsentGranted = isRecordingConsentGranted,
        isAccessibilityRunning = RcaptureAccessibilityService.isServiceRunning,
        onPickImage = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onClearAll = { viewModel.clearAll() },
        onSaveCurrentText = { viewModel.saveCurrentText() },
        onDeleteCapture = { viewModel.deleteCapture(it) },
        onClearAllHistory = { viewModel.clearAllHistory() },
        onSetStatusMessage = { viewModel.setStatusMessage(it) },
        onQuickScreenCapture = { viewModel.captureActiveScreenText() },
        onRunDiagnostics = { viewModel.runDiagnostics() },
        onToggleFlagSecure = { viewModel.setFlagSecureState(it) },
        onUpdateRecordingConfig = { res, fps, audio ->
            viewModel.updateRecordingConfig(res, fps, audio)
        },
        onRecordingConsentResult = { viewModel.setRecordingConsentGranted(it) },
        onStartRecording = { resultCode, data -> viewModel.startRecording(resultCode, data) },
        onStopRecording = { viewModel.stopRecording() },
        onPauseRecording = { viewModel.pauseRecording() },
        onResumeRecording = { viewModel.resumeRecording() },
        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
        onExportToJsonRequest = { jsonExportLauncher.launch("rcapture_export_${System.currentTimeMillis()}.json") },
        onExportToCsvRequest = { csvExportLauncher.launch("rcapture_export_${System.currentTimeMillis()}.csv") },
        modifier = modifier
    )
}

@Composable
fun MainAppScaffoldContent(
    uiState: RcaptureUiState,
    diagnosticsReport: SystemDiagnosticsReport? = null,
    isDiagnosticsLoading: Boolean = false,
    isFlagSecureEnabled: Boolean = false,
    recordingConfig: RecordingConfig = RecordingConfig(),
    isRecordingConsentGranted: Boolean = false,
    isAccessibilityRunning: Boolean = false,
    onPickImage: () -> Unit = {},
    onClearAll: () -> Unit = {},
    onSaveCurrentText: () -> Unit = {},
    onDeleteCapture: (CaptureEntity) -> Unit = {},
    onClearAllHistory: () -> Unit = {},
    onSetStatusMessage: (String?) -> Unit = {},
    onQuickScreenCapture: () -> Unit = {},
    onRunDiagnostics: () -> Unit = {},
    onToggleFlagSecure: (Boolean) -> Unit = {},
    onUpdateRecordingConfig: (String, Int, String) -> Unit = { _, _, _ -> },
    onRecordingConsentResult: (Boolean) -> Unit = {},
    onStartRecording: (Int, Intent) -> Unit = { _, _ -> },
    onStopRecording: () -> Unit = {},
    onPauseRecording: () -> Unit = {},
    onResumeRecording: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onExportToJsonRequest: () -> Unit = {},
    onExportToCsvRequest: () -> Unit = {},
    initialDestination: NavigationDestination = NavigationDestination.DASHBOARD,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentDestination by rememberSaveable { mutableStateOf(initialDestination) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        if (isExpanded) {
            // Adaptive Expanded Screen: NavigationRail on side
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("expanded_navigation_rail")
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NavigationDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = currentDestination == destination,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = if (currentDestination == destination) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.title
                                )
                            },
                            label = { Text(destination.title, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("nav_${destination.route}")
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    DestinationContent(
                        destination = currentDestination,
                        uiState = uiState,
                        diagnosticsReport = diagnosticsReport,
                        isDiagnosticsLoading = isDiagnosticsLoading,
                        isFlagSecureEnabled = isFlagSecureEnabled,
                        recordingConfig = recordingConfig,
                        isRecordingConsentGranted = isRecordingConsentGranted,
                        isAccessibilityRunning = isAccessibilityRunning,
                        onNavigate = { currentDestination = it },
                        onPickImage = onPickImage,
                        onClearAll = onClearAll,
                        onSaveCurrentText = onSaveCurrentText,
                        onDeleteCapture = onDeleteCapture,
                        onClearAllHistory = onClearAllHistory,
                        onSetStatusMessage = onSetStatusMessage,
                        onQuickScreenCapture = onQuickScreenCapture,
                        onRunDiagnostics = onRunDiagnostics,
                        onToggleFlagSecure = onToggleFlagSecure,
                        onUpdateRecordingConfig = onUpdateRecordingConfig,
                        onRecordingConsentResult = onRecordingConsentResult,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        onPauseRecording = onPauseRecording,
                        onResumeRecording = onResumeRecording,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onExportToJsonRequest = onExportToJsonRequest,
                        onExportToCsvRequest = onExportToCsvRequest,
                        onCopyCapture = { capture ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("R-Capture", capture.text)
                            clipboard.setPrimaryClip(clip)
                            onSetStatusMessage("Copied capture to clipboard")
                        }
                    )
                }
            }
        } else {
            // Adaptive Compact Screen: NavigationBar at bottom
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("compact_navigation_bar")
                    ) {
                        NavigationDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentDestination == destination,
                                onClick = { currentDestination = destination },
                                icon = {
                                    Icon(
                                        imageVector = if (currentDestination == destination) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = destination.title
                                    )
                                },
                                label = { Text(destination.title, style = MaterialTheme.typography.labelSmall) },
                                alwaysShowLabel = false,
                                modifier = Modifier.testTag("nav_${destination.route}")
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    DestinationContent(
                        destination = currentDestination,
                        uiState = uiState,
                        diagnosticsReport = diagnosticsReport,
                        isDiagnosticsLoading = isDiagnosticsLoading,
                        isFlagSecureEnabled = isFlagSecureEnabled,
                        recordingConfig = recordingConfig,
                        isRecordingConsentGranted = isRecordingConsentGranted,
                        isAccessibilityRunning = isAccessibilityRunning,
                        onNavigate = { currentDestination = it },
                        onPickImage = onPickImage,
                        onClearAll = onClearAll,
                        onSaveCurrentText = onSaveCurrentText,
                        onDeleteCapture = onDeleteCapture,
                        onClearAllHistory = onClearAllHistory,
                        onSetStatusMessage = onSetStatusMessage,
                        onQuickScreenCapture = onQuickScreenCapture,
                        onRunDiagnostics = onRunDiagnostics,
                        onToggleFlagSecure = onToggleFlagSecure,
                        onUpdateRecordingConfig = onUpdateRecordingConfig,
                        onRecordingConsentResult = onRecordingConsentResult,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        onPauseRecording = onPauseRecording,
                        onResumeRecording = onResumeRecording,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onExportToJsonRequest = onExportToJsonRequest,
                        onExportToCsvRequest = onExportToCsvRequest,
                        onCopyCapture = { capture ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("R-Capture", capture.text)
                            clipboard.setPrimaryClip(clip)
                            onSetStatusMessage("Copied capture to clipboard")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DestinationContent(
    destination: NavigationDestination,
    uiState: RcaptureUiState,
    diagnosticsReport: SystemDiagnosticsReport?,
    isDiagnosticsLoading: Boolean,
    isFlagSecureEnabled: Boolean,
    recordingConfig: RecordingConfig,
    isRecordingConsentGranted: Boolean,
    isAccessibilityRunning: Boolean,
    onNavigate: (NavigationDestination) -> Unit,
    onPickImage: () -> Unit,
    onClearAll: () -> Unit,
    onSaveCurrentText: () -> Unit,
    onDeleteCapture: (CaptureEntity) -> Unit,
    onClearAllHistory: () -> Unit,
    onSetStatusMessage: (String?) -> Unit,
    onQuickScreenCapture: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onToggleFlagSecure: (Boolean) -> Unit,
    onUpdateRecordingConfig: (String, Int, String) -> Unit,
    onRecordingConsentResult: (Boolean) -> Unit,
    onStartRecording: (Int, Intent) -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onCopyCapture: (CaptureEntity) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onExportToJsonRequest: () -> Unit,
    onExportToCsvRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (destination) {
        NavigationDestination.DASHBOARD -> {
            DashboardScreen(
                uiState = uiState,
                isAccessibilityRunning = isAccessibilityRunning,
                diagnosticsReport = diagnosticsReport,
                onNavigateToCapture = { onNavigate(NavigationDestination.CAPTURE) },
                onNavigateToRecorder = { onNavigate(NavigationDestination.RECORDER) },
                onNavigateToLibrary = { onNavigate(NavigationDestination.LIBRARY) },
                onNavigateToTestLab = { onNavigate(NavigationDestination.SECURITY_LAB) },
                onQuickScreenCapture = onQuickScreenCapture,
                onDeleteCapture = onDeleteCapture,
                onCopyCapture = onCopyCapture,
                modifier = modifier
            )
        }
        NavigationDestination.CAPTURE -> {
            HomeScreenContent(
                uiState = uiState,
                isAccessibilityServiceRunning = isAccessibilityRunning,
                onPickImage = onPickImage,
                onClearAll = onClearAll,
                onSaveCurrentText = onSaveCurrentText,
                onDeleteCapture = onDeleteCapture,
                onClearAllHistory = onClearAllHistory,
                onSetStatusMessage = onSetStatusMessage,
                modifier = modifier
            )
        }
        NavigationDestination.RECORDER -> {
            RecorderScreen(
                recordingConfig = recordingConfig,
                isConsentGranted = isRecordingConsentGranted,
                projectionDiagnosis = diagnosticsReport?.mediaProjection,
                recordingSessionState = uiState.recordingSessionState,
                onUpdateConfig = onUpdateRecordingConfig,
                onConsentResult = onRecordingConsentResult,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onPauseRecording = onPauseRecording,
                onResumeRecording = onResumeRecording,
                onSetStatusMessage = onSetStatusMessage,
                modifier = modifier
            )
        }
        NavigationDestination.LIBRARY -> {
            LibraryScreen(
                uiState = uiState,
                onDeleteCapture = onDeleteCapture,
                onClearAllHistory = onClearAllHistory,
                onSetStatusMessage = onSetStatusMessage,
                onSearchQueryChanged = onSearchQueryChanged,
                onExportToJsonRequest = onExportToJsonRequest,
                onExportToCsvRequest = onExportToCsvRequest,
                modifier = modifier
            )
        }
        NavigationDestination.SECURITY_LAB -> {
            SecurityLabScreen(
                report = diagnosticsReport,
                isLoading = isDiagnosticsLoading,
                isFlagSecureEnabled = isFlagSecureEnabled,
                onRunDiagnostics = onRunDiagnostics,
                onToggleFlagSecure = onToggleFlagSecure,
                modifier = modifier
            )
        }
        NavigationDestination.SETTINGS -> {
            SettingsScreen(
                captures = uiState.savedCaptures,
                onClearAllHistory = onClearAllHistory,
                onSetStatusMessage = onSetStatusMessage,
                onExportToJsonRequest = onExportToJsonRequest,
                onExportToCsvRequest = onExportToCsvRequest,
                modifier = modifier
            )
        }
    }
}
