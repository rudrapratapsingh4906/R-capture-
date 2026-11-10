package com.rcapture.diagnostics

data class DeviceEnvironmentInfo(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val hardware: String,
    val board: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildDisplay: String,
    val fingerprint: String
)

data class EmulatorDiagnosis(
    val isEmulator: Boolean,
    val confidenceScore: Int,
    val detectedSignals: List<String>
)

data class RootDiagnosis(
    val isRooted: Boolean,
    val detectedIndicators: List<String>,
    val testKeysPresent: Boolean,
    val suBinaryFound: Boolean
)

data class AdbDiagnosis(
    val isAdbEnabled: Boolean,
    val isDevelopmentSettingsEnabled: Boolean
)

data class MediaProjectionDiagnosis(
    val isServiceAvailable: Boolean,
    val isSdkSupported: Boolean,
    val apiLevel: Int,
    val foregroundServiceRequired: Boolean,
    val description: String
)

data class FlagSecureDiagnosis(
    val isWindowSecureActive: Boolean,
    val osPolicySummary: String,
    val protectionDescription: String
)

data class SystemDiagnosticsReport(
    val timestamp: Long,
    val environment: DeviceEnvironmentInfo,
    val emulator: EmulatorDiagnosis,
    val root: RootDiagnosis,
    val adb: AdbDiagnosis,
    val mediaProjection: MediaProjectionDiagnosis,
    val flagSecure: FlagSecureDiagnosis
)
