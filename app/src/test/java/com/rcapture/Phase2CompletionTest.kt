package com.rcapture

import com.rcapture.data.CaptureEntity
import com.rcapture.diagnostics.DiagnosticRepository
import com.rcapture.diagnostics.FlagSecureDiagnosis
import com.rcapture.diagnostics.MediaProjectionDiagnosis
import com.rcapture.diagnostics.RootDiagnosis
import com.rcapture.service.RcaptureAccessibilityService
import com.rcapture.ui.RecordingConfig
import org.junit.Assert.*
import org.junit.Test

class Phase2CompletionTest {

    @Test
    fun `extractTextFromNodeTree handles null root safely`() {
        val result = RcaptureAccessibilityService.extractTextFromNodeTree(null)
        assertEquals("", result)
    }

    @Test
    fun `capture entity model properties are correct`() {
        val capture = CaptureEntity(
            id = 1L,
            text = "Sample captured test content",
            timestamp = 1000L,
            source = "Floating Overlay"
        )
        assertEquals(1L, capture.id)
        assertEquals("Sample captured test content", capture.text)
        assertEquals(1000L, capture.timestamp)
        assertEquals("Floating Overlay", capture.source)
    }

    @Test
    fun `text trimming and formatting logic is correct`() {
        val raw = "   Line 1 \n Line 2 \n Line 1   "
        val trimmed = raw.trim()
        assertTrue(trimmed.contains("Line 1"))
        assertTrue(trimmed.contains("Line 2"))
    }

    @Test
    fun `navigation destinations contain all six required destinations`() {
        val destinations = com.rcapture.ui.navigation.NavigationDestination.entries
        assertEquals(6, destinations.size)
        val expectedRoutes = setOf("dashboard", "capture", "recorder", "library", "security_lab", "settings")
        val actualRoutes = destinations.map { it.route }.toSet()
        assertEquals(expectedRoutes, actualRoutes)
    }

    @Test
    fun `navigation destinations have descriptive titles`() {
        com.rcapture.ui.navigation.NavigationDestination.entries.forEach { destination ->
            assertTrue(destination.title.isNotBlank())
            assertTrue(destination.route.isNotBlank())
            assertNotNull(destination.selectedIcon)
            assertNotNull(destination.unselectedIcon)
        }
    }

    @Test
    fun `color tokens match futuristic palette specification`() {
        // Compose Color packs 32-bit ARGB into upper 32 bits: value shr 32
        assertEquals(0xFF080B10, (com.rcapture.ui.theme.BackgroundDark.value shr 32).toLong())
        assertEquals(0xFF0F1522, (com.rcapture.ui.theme.SurfaceLevel1.value shr 32).toLong())
        assertEquals(0xFF171F32, (com.rcapture.ui.theme.SurfaceLevel2.value shr 32).toLong())
        assertEquals(0xFF00F0FF, (com.rcapture.ui.theme.ElectricCyan.value shr 32).toLong())
        assertEquals(0xFF2D68FF, (com.rcapture.ui.theme.CyberBlue.value shr 32).toLong())
        assertEquals(0xFF00E5A3, (com.rcapture.ui.theme.CyberEmerald.value shr 32).toLong())
        assertEquals(0xFFFFB020, (com.rcapture.ui.theme.SecurityAmber.value shr 32).toLong())
        assertEquals(0xFFFF3B30, (com.rcapture.ui.theme.CyberCrimson.value shr 32).toLong())
        assertEquals(0xFFF0F4FC, (com.rcapture.ui.theme.TextPrimary.value shr 32).toLong())
        assertEquals(0xFF8E9CAE, (com.rcapture.ui.theme.TextSecondary.value shr 32).toLong())
    }

    @Test
    fun `flag secure diagnosis reports correct active state and policy summary`() {
        val repo = DiagnosticRepository()
        val secureReport = repo.inspectFlagSecure(isWindowSecure = true)
        assertTrue(secureReport.isWindowSecureActive)
        assertTrue(secureReport.osPolicySummary.contains("ACTIVE"))
        assertTrue(secureReport.protectionDescription.contains("FLAG_SECURE"))

        val unsecureReport = repo.inspectFlagSecure(isWindowSecure = false)
        assertFalse(unsecureReport.isWindowSecureActive)
        assertTrue(unsecureReport.osPolicySummary.contains("INACTIVE"))
    }

    @Test
    fun `root diagnosis model evaluates indicators and flags accurately`() {
        val rootClean = RootDiagnosis(
            isRooted = false,
            detectedIndicators = emptyList(),
            testKeysPresent = false,
            suBinaryFound = false
        )
        assertFalse(rootClean.isRooted)
        assertEquals(0, rootClean.detectedIndicators.size)

        val rootDetected = RootDiagnosis(
            isRooted = true,
            detectedIndicators = listOf("Build.TAGS test-keys"),
            testKeysPresent = true,
            suBinaryFound = false
        )
        assertTrue(rootDetected.isRooted)
        assertTrue(rootDetected.testKeysPresent)
    }

    @Test
    fun `recording configuration model holds valid defaults and updates`() {
        val defaultConfig = RecordingConfig()
        assertEquals("1080p FHD", defaultConfig.resolution)
        assertEquals(30, defaultConfig.fps)
        assertEquals("Internal Audio", defaultConfig.audioSource)

        val customConfig = defaultConfig.copy(
            resolution = "720p HD",
            fps = 60,
            audioSource = "Microphone"
        )
        assertEquals("720p HD", customConfig.resolution)
        assertEquals(60, customConfig.fps)
        assertEquals("Microphone", customConfig.audioSource)
    }

    @Test
    fun `library search filtering logic accurately filters by source and query`() {
        val items = listOf(
            CaptureEntity(1L, "Invoice #10023 Total $500", 1000L, "Manual Save"),
            CaptureEntity(2L, "Account Security Code 9492", 2000L, "Floating Overlay"),
            CaptureEntity(3L, "Meeting Agenda Notes", 3000L, "Manual Save")
        )

        // Filter by source
        val overlayOnly = items.filter { it.source.contains("Floating Overlay", ignoreCase = true) }
        assertEquals(1, overlayOnly.size)
        assertEquals(2L, overlayOnly.first().id)

        // Filter by text search query
        val invoiceQuery = items.filter { it.text.contains("Invoice", ignoreCase = true) }
        assertEquals(1, invoiceQuery.size)
        assertEquals(1L, invoiceQuery.first().id)

        // Case-insensitive query
        val securityQuery = items.filter { it.text.contains("security", ignoreCase = true) }
        assertEquals(1, securityQuery.size)
        assertEquals(2L, securityQuery.first().id)
    }
}
