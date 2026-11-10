package com.rcapture

import com.rcapture.intelligence.EntityCategory
import com.rcapture.intelligence.TextIntelligenceEngine
import com.rcapture.service.RcaptureAccessibilityService
import org.junit.Assert.*
import org.junit.Test

class Phase3TextIntelligenceTest {

    private val engine = TextIntelligenceEngine()

    @Test
    fun `url extraction detects http, https, and clean domain urls`() {
        val text = "Visit our portal at https://rcapture.io/api and http://example.org/test?id=1 or www.google.com for docs."
        val entities = engine.extractEntities(text)

        val urlEntities = entities.filter { it.category == EntityCategory.URL }
        assertEquals(3, urlEntities.size)

        val urls = urlEntities.map { it.value }
        assertTrue(urls.contains("https://rcapture.io/api"))
        assertTrue(urls.contains("http://example.org/test?id=1"))
        assertTrue(urls.contains("www.google.com"))

        // Verify trailing punctuation was cleaned
        assertFalse(urls.any { it.endsWith(".") })
    }

    @Test
    fun `email extraction extracts valid email addresses`() {
        val text = "Please send audit logs to security@rcapture.io and ops.team+urgent@cloud.net for review."
        val entities = engine.extractEntities(text)

        val emailEntities = entities.filter { it.category == EntityCategory.EMAIL }
        assertEquals(2, emailEntities.size)

        val emails = emailEntities.map { it.value }
        assertTrue(emails.contains("security@rcapture.io"))
        assertTrue(emails.contains("ops.team+urgent@cloud.net"))
    }

    @Test
    fun `phone extraction detects valid domestic and international numbers`() {
        val text = "Support line is +1-800-555-0199 and international desk is (415) 555-2671."
        val entities = engine.extractEntities(text)

        val phoneEntities = entities.filter { it.category == EntityCategory.PHONE }
        assertEquals(2, phoneEntities.size)
        assertTrue(phoneEntities.any { it.value.contains("800-555-0199") })
        assertTrue(phoneEntities.any { it.value.contains("555-2671") })
    }

    @Test
    fun `date extraction detects ISO, standard and word format dates`() {
        val text = "Incident occurred on 2026-11-09 at 14:30 PM. Follow-up meeting scheduled on November 15, 2026."
        val entities = engine.extractEntities(text)

        val dateEntities = entities.filter { it.category == EntityCategory.DATETIME }
        assertTrue(dateEntities.isNotEmpty())

        val values = dateEntities.map { it.value }
        assertTrue(values.any { it.contains("2026-11-09") })
        assertTrue(values.any { it.contains("November 15, 2026") })
        assertTrue(values.any { it.contains("14:30 PM") })
    }

    @Test
    fun `financial extraction validates cards with Luhn and masks securely`() {
        // 4111 1111 1111 1111 is the canonical valid Luhn test credit card
        val text = "Payment card 4111-1111-1111-1111 was charged $249.50. IBAN: DE89370400440532013000."
        val entities = engine.extractEntities(text)

        val financialEntities = entities.filter { it.category == EntityCategory.FINANCIAL }
        assertTrue(financialEntities.size >= 2)

        val cardEntity = financialEntities.find { it.value.contains("4111") }
        assertNotNull(cardEntity)
        assertTrue(cardEntity!!.isSensitive)
        assertEquals("•••• •••• •••• 1111", cardEntity.maskedValue)

        val ibanEntity = financialEntities.find { it.value.contains("DE89") }
        assertNotNull(ibanEntity)
        assertTrue(ibanEntity!!.isSensitive)
        assertTrue(ibanEntity.maskedValue.startsWith("DE••"))
        assertTrue(ibanEntity.maskedValue.endsWith("3000"))

        val currencyEntity = financialEntities.find { it.value.contains("$249.50") }
        assertNotNull(currencyEntity)
        assertFalse(currencyEntity!!.isSensitive)
    }

    @Test
    fun `false positive handling prevents invalid numbers and isolated years from becoming phone numbers`() {
        val text = "In the year 2026, version 3.2.1 was released. Code was 12345 and test ID 99."
        val entities = engine.extractEntities(text)

        val phoneEntities = entities.filter { it.category == EntityCategory.PHONE }
        val financialCards = entities.filter { it.category == EntityCategory.FINANCIAL && it.isSensitive }

        // Neither 2026, 3.2.1, 12345, nor 99 should match as a phone or valid card
        assertTrue(phoneEntities.isEmpty())
        assertTrue(financialCards.isEmpty())
    }

    @Test
    fun `sensitive data masking functions correctly mask card and iban numbers`() {
        val rawCard = "4111111111111111"
        val maskedCard = TextIntelligenceEngine.maskCardNumber(rawCard)
        assertEquals("•••• •••• •••• 1111", maskedCard)

        val rawIban = "DE89370400440532013000"
        val maskedIban = TextIntelligenceEngine.maskIban(rawIban)
        assertEquals("DE•• •••• •••• 3000", maskedIban)
    }

    @Test
    fun `empty and malformed text returns empty entity list without exceptions`() {
        assertEquals(0, engine.extractEntities("").size)
        assertEquals(0, engine.extractEntities("   \n\t  ").size)
        assertEquals(0, engine.extractEntities("Lorem ipsum dolor sit amet non-entity text").size)
    }

    @Test
    fun `configurable category extraction filters only requested types`() {
        val text = "Contact john@rcapture.io or visit https://rcapture.io for info."
        val emailOnly = engine.extractEntities(text, allowedCategories = setOf(EntityCategory.EMAIL))

        assertEquals(1, emailOnly.size)
        assertEquals(EntityCategory.EMAIL, emailOnly.first().category)
        assertEquals("john@rcapture.io", emailOnly.first().value)
    }

    @Test
    fun `accessibility multi-window aggregation handles null inputs safely`() {
        val emptyResult = RcaptureAccessibilityService.extractMultiWindowText(
            windows = null,
            selfPackageName = "com.rcapture",
            fallbackRoot = null
        )
        assertEquals("", emptyResult)

        val singleEmpty = RcaptureAccessibilityService.extractTextFromNodeTree(null)
        assertEquals("", singleEmpty)
    }
}
