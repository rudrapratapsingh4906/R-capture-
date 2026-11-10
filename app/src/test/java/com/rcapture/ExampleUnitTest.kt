package com.rcapture

import com.rcapture.service.RcaptureAccessibilityService
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `extractTextFromNodeTree handles null root safely`() {
        val result = RcaptureAccessibilityService.extractTextFromNodeTree(null)
        assertEquals("", result)
    }

    @Test
    fun `text trimming and formatting logic is correct`() {
        val raw = "   Hello World \n Accessibility Reader   "
        assertEquals("Hello World \n Accessibility Reader", raw.trim())
    }
}
