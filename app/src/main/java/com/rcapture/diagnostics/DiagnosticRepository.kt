package com.rcapture.diagnostics

import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import java.io.File

class DiagnosticRepository {

    fun getDeviceEnvironmentInfo(): DeviceEnvironmentInfo {
        return DeviceEnvironmentInfo(
            brand = Build.BRAND ?: "Unknown",
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
            device = Build.DEVICE ?: "Unknown",
            hardware = Build.HARDWARE ?: "Unknown",
            board = Build.BOARD ?: "Unknown",
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH ?: "Not Reported"
            } else {
                "Pre-M (Legacy)"
            },
            buildDisplay = Build.DISPLAY ?: "Unknown",
            fingerprint = Build.FINGERPRINT ?: "Unknown"
        )
    }

    fun detectEmulator(): EmulatorDiagnosis {
        val signals = mutableListOf<String>()

        val fingerprint = (Build.FINGERPRINT ?: "").lowercase()
        val model = (Build.MODEL ?: "").lowercase()
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val brand = (Build.BRAND ?: "").lowercase()
        val device = (Build.DEVICE ?: "").lowercase()
        val product = (Build.PRODUCT ?: "").lowercase()
        val hardware = (Build.HARDWARE ?: "").lowercase()
        val board = (Build.BOARD ?: "").lowercase()

        if (fingerprint.startsWith("generic") || fingerprint.startsWith("unknown")) {
            signals.add("Fingerprint starts with generic/unknown")
        }
        if (model.contains("google_sdk") || model.contains("emulator") || model.contains("android sdk built for")) {
            signals.add("Model indicates virtual device ($model)")
        }
        if (manufacturer.contains("genymotion") || manufacturer.contains("unknown")) {
            signals.add("Manufacturer indicates emulator ($manufacturer)")
        }
        if (brand.startsWith("generic") && device.startsWith("generic")) {
            signals.add("Generic brand and device identity")
        }
        if (product.contains("sdk_google") || product.contains("google_sdk") || product.contains("sdk") || product.contains("vbox86p")) {
            signals.add("Product configuration matches Android SDK emulator ($product)")
        }
        if (hardware.contains("goldfish") || hardware.contains("ranchu")) {
            signals.add("Virtual hardware profile detected ($hardware)")
        }
        if (board.contains("goldfish")) {
            signals.add("Goldfish emulator board profile")
        }

        // Additional QEMU driver check
        val qemuDrivers = listOf("/dev/socket/qemud", "/dev/qemu_pipe")
        for (driver in qemuDrivers) {
            try {
                if (File(driver).exists()) {
                    signals.add("QEMU kernel driver pipe present: $driver")
                }
            } catch (_: Exception) {}
        }

        val isEmulator = signals.isNotEmpty()
        val confidenceScore = if (signals.isEmpty()) 0 else (signals.size * 25).coerceAtMost(100)

        return EmulatorDiagnosis(
            isEmulator = isEmulator,
            confidenceScore = confidenceScore,
            detectedSignals = signals
        )
    }

    fun detectRoot(): RootDiagnosis {
        val indicators = mutableListOf<String>()
        var testKeysFound = false
        var suFound = false

        // 1. Check build tags for test-keys (unofficial or custom ROMs)
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            testKeysFound = true
            indicators.add("Build.TAGS contains 'test-keys' (non-release build)")
        }

        // 2. Scan standard su binary paths
        val standardSuPaths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/system/bin/.ext/.su"
        )

        for (path in standardSuPaths) {
            try {
                val file = File(path)
                if (file.exists()) {
                    suFound = true
                    indicators.add("SU binary found at path: $path")
                }
            } catch (_: Exception) {}
        }

        // 3. Check for common root management packages existence
        val rootPackages = listOf(
            "/system/app/SuperSU/SuperSU.apk",
            "/system/app/Magisk/Magisk.apk"
        )
        for (pkg in rootPackages) {
            try {
                if (File(pkg).exists()) {
                    indicators.add("Root manager package file found: $pkg")
                }
            } catch (_: Exception) {}
        }

        val isRooted = indicators.isNotEmpty()
        return RootDiagnosis(
            isRooted = isRooted,
            detectedIndicators = indicators,
            testKeysPresent = testKeysFound,
            suBinaryFound = suFound
        )
    }

    fun detectAdb(context: Context): AdbDiagnosis {
        var adbEnabled = false
        var devSettingsEnabled = false

        try {
            val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
            adbEnabled = (adb == 1)
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                val adb = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ADB_ENABLED, 0)
                adbEnabled = (adb == 1)
            } catch (_: Exception) {}
        }

        try {
            val devSettings = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
            devSettingsEnabled = (devSettings == 1)
        } catch (_: Exception) {}

        return AdbDiagnosis(
            isAdbEnabled = adbEnabled,
            isDevelopmentSettingsEnabled = devSettingsEnabled
        )
    }

    fun detectMediaProjection(context: Context): MediaProjectionDiagnosis {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val isAvailable = (manager != null)
        val sdkLevel = Build.VERSION.SDK_INT
        val isSupported = isAvailable && sdkLevel >= Build.VERSION_CODES.LOLLIPOP

        val description = when {
            !isAvailable -> "MediaProjection service is not available on this platform."
            sdkLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> 
                "Full support with Android 14/15/16 security constraints (requires explicit consent and foregroundServiceType='mediaProjection')."
            sdkLevel >= Build.VERSION_CODES.LOLLIPOP -> 
                "Standard MediaProjection support available."
            else -> "Unsupported legacy Android version."
        }

        return MediaProjectionDiagnosis(
            isServiceAvailable = isAvailable,
            isSdkSupported = isSupported,
            apiLevel = sdkLevel,
            foregroundServiceRequired = sdkLevel >= Build.VERSION_CODES.Q,
            description = description
        )
    }

    fun inspectFlagSecure(isWindowSecure: Boolean): FlagSecureDiagnosis {
        val summary = if (isWindowSecure) {
            "ACTIVE (Window content protected: OS blocks screenshots, screen recording, and task switcher previews)"
        } else {
            "INACTIVE (Standard rendering: screenshots and screen recording permitted by OS)"
        }

        val description = "Android WindowManager.LayoutParams.FLAG_SECURE enforces hardware-level display surface protection. " +
                "When active, the window manager instructs SurfaceFlinger to blank the screen buffer for external capture tools and accessibility overlays. " +
                "R-Capture strictly respects this platform security policy."

        return FlagSecureDiagnosis(
            isWindowSecureActive = isWindowSecure,
            osPolicySummary = summary,
            protectionDescription = description
        )
    }

    fun runFullDiagnostic(context: Context, isWindowSecure: Boolean): SystemDiagnosticsReport {
        return SystemDiagnosticsReport(
            timestamp = System.currentTimeMillis(),
            environment = getDeviceEnvironmentInfo(),
            emulator = detectEmulator(),
            root = detectRoot(),
            adb = detectAdb(context),
            mediaProjection = detectMediaProjection(context),
            flagSecure = inspectFlagSecure(isWindowSecure)
        )
    }
}
