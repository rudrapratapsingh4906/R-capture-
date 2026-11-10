package com.rcapture.intelligence

import java.util.regex.Pattern

class TextIntelligenceEngine {

    companion object {
        // 1. Email Pattern
        private val EMAIL_PATTERN = Pattern.compile(
            """\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"""
        )

        // 2. URL Pattern
        private val URL_PATTERN = Pattern.compile(
            """(?i)\b(?:https?://|ftp://|www\.)[^\s/$.?#].[^\s]*|(?<![@A-Za-z0-9.-])(?:[a-zA-Z0-9-]+\.)+(?:com|org|net|io|edu|gov|co|app|dev|ai|xyz)(?:/[^\s]*)?"""
        )

        // 3. Phone Pattern (7 to 15 digits with standard delimiters)
        private val PHONE_PATTERN = Pattern.compile(
            """(?:\+?\d{1,3}[\s.-]?)?\(?\d{2,4}\)?[\s.-]?\d{3,4}[\s.-]?\d{3,4}\b"""
        )

        // 4. Date & Time Patterns
        private val DATE_ISO_PATTERN = Pattern.compile(
            """\b\d{4}-\d{2}-\d{2}(?:[T\s]\d{2}:\d{2}(?::\d{2})?(?:Z|[+-]\d{2}:?\d{2})?)?\b"""
        )
        private val DATE_STANDARD_PATTERN = Pattern.compile(
            """\b\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}\b"""
        )
        private val DATE_WORDS_PATTERN = Pattern.compile(
            """(?i)\b(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+\d{1,2}(?:st|nd|rd|th)?,?\s+\d{4}\b"""
        )
        private val TIME_PATTERN = Pattern.compile(
            """\b(?:[01]?\d|2[0-3]):[0-5]\d(?::[0-5]\d)?(?:\s*[AaPp][Mm])\b"""
        )

        // 5. Physical Address Patterns
        private val ADDRESS_PATTERN = Pattern.compile(
            """(?i)\b\d{1,5}\s+[A-Za-z0-9.\s]+(?:Street|St\.|St|Avenue|Ave\.|Ave|Road|Rd\.|Rd|Boulevard|Blvd\.|Blvd|Lane|Ln\.|Ln|Drive|Dr\.|Dr|Court|Ct\.|Ct|Way|Plaza|Parkway|Pkwy)\b(?:,?\s+[A-Za-z\s]+,?\s+[A-Z]{2}\s+\d{5}(?:-\d{4})?)?"""
        )
        private val PO_BOX_PATTERN = Pattern.compile(
            """(?i)\b(?:P\.?O\.?\s*Box)\s+\d+\b"""
        )

        // 6. Financial Identifiers
        private val CARD_NUMBER_PATTERN = Pattern.compile(
            """\b(?:\d{4}[-\s]?){3}\d{4}\b|\b\d{13,19}\b"""
        )
        private val IBAN_PATTERN = Pattern.compile(
            """\b[A-Z]{2}\d{2}[A-Z0-9]{4}\d{7}([A-Z0-9]?){0,16}\b"""
        )
        private val CURRENCY_PATTERN = Pattern.compile(
            """(?:[$€£¥₹]\s*\d+(?:,\d{3})*(?:\.\d{2})?|\b\d+(?:,\d{3})*(?:\.\d{2})?\s*(?:USD|EUR|GBP|INR|CAD|AUD)\b)"""
        )

        fun isValidLuhn(number: String): Boolean {
            val digits = number.filter { it.isDigit() }
            if (digits.length !in 13..19) return false
            var sum = 0
            var alternate = false
            for (i in digits.length - 1 downTo 0) {
                var n = digits[i].digitToInt()
                if (alternate) {
                    n *= 2
                    if (n > 9) n = (n % 10) + 1
                }
                sum += n
                alternate = !alternate
            }
            return (sum % 10 == 0)
        }

        fun maskCardNumber(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            return if (digits.length >= 4) {
                val last4 = digits.takeLast(4)
                "•••• •••• •••• $last4"
            } else {
                "•••• •••• •••• ••••"
            }
        }

        fun maskIban(raw: String): String {
            val cleaned = raw.filter { it.isLetterOrDigit() }
            return if (cleaned.length >= 6) {
                val prefix = cleaned.take(2)
                val last4 = cleaned.takeLast(4)
                "$prefix•• •••• •••• $last4"
            } else {
                "•••• •••• ••••"
            }
        }
    }

    fun extractEntities(
        text: String,
        allowedCategories: Set<EntityCategory> = EntityCategory.entries.toSet()
    ): List<ExtractedEntity> {
        if (text.isBlank()) return emptyList()

        val results = mutableListOf<ExtractedEntity>()
        val matchedRanges = mutableListOf<IntRange>()

        fun isOverlapping(range: IntRange): Boolean {
            return matchedRanges.any { it.first <= range.last && range.first <= it.last }
        }

        // 1. Financial Identifiers (Check first to avoid phone or number collision)
        if (allowedCategories.contains(EntityCategory.FINANCIAL)) {
            // Check Credit/Debit cards with Luhn validation
            val cardMatcher = CARD_NUMBER_PATTERN.matcher(text)
            while (cardMatcher.find()) {
                val match = cardMatcher.group().trim()
                val digitsOnly = match.filter { it.isDigit() }
                if (digitsOnly.length in 13..19 && isValidLuhn(digitsOnly)) {
                    val range = cardMatcher.start()..cardMatcher.end()
                    if (!isOverlapping(range)) {
                        matchedRanges.add(range)
                        results.add(
                            ExtractedEntity(
                                category = EntityCategory.FINANCIAL,
                                value = match,
                                maskedValue = maskCardNumber(match),
                                startIndex = cardMatcher.start(),
                                endIndex = cardMatcher.end(),
                                isSensitive = true
                            )
                        )
                    }
                }
            }

            // Check IBAN numbers
            val ibanMatcher = IBAN_PATTERN.matcher(text)
            while (ibanMatcher.find()) {
                val match = ibanMatcher.group().trim()
                val range = ibanMatcher.start()..ibanMatcher.end()
                if (!isOverlapping(range)) {
                    matchedRanges.add(range)
                    results.add(
                        ExtractedEntity(
                            category = EntityCategory.FINANCIAL,
                            value = match,
                            maskedValue = maskIban(match),
                            startIndex = ibanMatcher.start(),
                            endIndex = ibanMatcher.end(),
                            isSensitive = true
                        )
                    )
                }
            }

            // Check Currency amounts
            val currencyMatcher = CURRENCY_PATTERN.matcher(text)
            while (currencyMatcher.find()) {
                val match = currencyMatcher.group().trim()
                val range = currencyMatcher.start()..currencyMatcher.end()
                if (!isOverlapping(range)) {
                    matchedRanges.add(range)
                    results.add(
                        ExtractedEntity(
                            category = EntityCategory.FINANCIAL,
                            value = match,
                            maskedValue = match,
                            startIndex = currencyMatcher.start(),
                            endIndex = currencyMatcher.end(),
                            isSensitive = false
                        )
                    )
                }
            }
        }

        // 2. Email Addresses
        if (allowedCategories.contains(EntityCategory.EMAIL)) {
            val emailMatcher = EMAIL_PATTERN.matcher(text)
            while (emailMatcher.find()) {
                val match = emailMatcher.group().trim()
                val range = emailMatcher.start()..emailMatcher.end()
                if (!isOverlapping(range)) {
                    matchedRanges.add(range)
                    results.add(
                        ExtractedEntity(
                            category = EntityCategory.EMAIL,
                            value = match,
                            maskedValue = match,
                            startIndex = emailMatcher.start(),
                            endIndex = emailMatcher.end(),
                            isSensitive = false
                        )
                    )
                }
            }
        }

        // 3. URLs
        if (allowedCategories.contains(EntityCategory.URL)) {
            val urlMatcher = URL_PATTERN.matcher(text)
            while (urlMatcher.find()) {
                var match = urlMatcher.group().trim()
                // Trim trailing punctuation attached to URLs
                while (match.endsWith(".") || match.endsWith(",") || match.endsWith(")") || match.endsWith(">")) {
                    match = match.dropLast(1)
                }
                val range = urlMatcher.start()..(urlMatcher.start() + match.length)
                if (!isOverlapping(range)) {
                    matchedRanges.add(range)
                    results.add(
                        ExtractedEntity(
                            category = EntityCategory.URL,
                            value = match,
                            maskedValue = match,
                            startIndex = urlMatcher.start(),
                            endIndex = urlMatcher.start() + match.length,
                            isSensitive = false
                        )
                    )
                }
            }
        }

        // 4. Dates & Times
        if (allowedCategories.contains(EntityCategory.DATETIME)) {
            // ISO dates
            val isoMatcher = DATE_ISO_PATTERN.matcher(text)
            while (isoMatcher.find()) {
                val match = isoMatcher.group().trim()
                val range = isoMatcher.start()..isoMatcher.end()
                if (!isOverlapping(range)) {
                    matchedRanges.add(range)
                    results.add(
                        ExtractedEntity(
                            category = EntityCategory.DATETIME,
                            value = match,
                            maskedValue = match,
                            startIndex = isoMatcher.start(),
                            endIndex = isoMatcher.end(),
                            isSensitive = false
                        )
                    )
                }
            }

            // Word month dates (e.g. November 9, 2026)
            val wordMatcher = DATE_WORDS_PATTERN.matcher(text)
            while (wordMatcher.find()) {
                val match = wordMatcher.group().trim()
                val range = wordMatcher.start()..wordMatcher.end()
                if (!isOverlapping(range)) {
                    matchedRanges.add(range)
                    results.add(
                        ExtractedEntity(
                            category = EntityCategory.DATETIME,
                            value = match,
                            maskedValue = match,
                            startIndex = wordMatcher.start(),
                            endIndex = wordMatcher.end(),
                            isSensitive = false
                        )
                    )
                }
            }

            // Standard slash/dash dates
            val standardMatcher = DATE_STANDARD_PATTERN.matcher(text)
            while (standardMatcher.find()) {
                val match = standardMatcher.group().trim()
                val range = standardMatcher.start()..standardMatcher.end()
                if (!isOverlapping(range)) {
                    matchedRanges.add(range)
                    results.add(
                        ExtractedEntity(
                            category = EntityCategory.DATETIME,
                            value = match,
                            maskedValue = match,
                            startIndex = standardMatcher.start(),
                            endIndex = standardMatcher.end(),
                            isSensitive = false
                        )
                    )
                }
            }

            // Time pattern
            val timeMatcher = TIME_PATTERN.matcher(text)
            while (timeMatcher.find()) {
                val match = timeMatcher.group().trim()
                val range = timeMatcher.start()..timeMatcher.end()
                if (!isOverlapping(range)) {
                    matchedRanges.add(range)
                    results.add(
                        ExtractedEntity(
                            category = EntityCategory.DATETIME,
                            value = match,
                            maskedValue = match,
                            startIndex = timeMatcher.start(),
                            endIndex = timeMatcher.end(),
                            isSensitive = false
                        )
                    )
                }
            }
        }

        // 5. Physical Addresses
        if (allowedCategories.contains(EntityCategory.ADDRESS)) {
            val addrMatcher = ADDRESS_PATTERN.matcher(text)
            while (addrMatcher.find()) {
                val match = addrMatcher.group().trim()
                val range = addrMatcher.start()..addrMatcher.end()
                if (!isOverlapping(range)) {
                    matchedRanges.add(range)
                    results.add(
                        ExtractedEntity(
                            category = EntityCategory.ADDRESS,
                            value = match,
                            maskedValue = match,
                            startIndex = addrMatcher.start(),
                            endIndex = addrMatcher.end(),
                            isSensitive = false
                        )
                    )
                }
            }

            val poBoxMatcher = PO_BOX_PATTERN.matcher(text)
            while (poBoxMatcher.find()) {
                val match = poBoxMatcher.group().trim()
                val range = poBoxMatcher.start()..poBoxMatcher.end()
                if (!isOverlapping(range)) {
                    matchedRanges.add(range)
                    results.add(
                        ExtractedEntity(
                            category = EntityCategory.ADDRESS,
                            value = match,
                            maskedValue = match,
                            startIndex = poBoxMatcher.start(),
                            endIndex = poBoxMatcher.end(),
                            isSensitive = false
                        )
                    )
                }
            }
        }

        // 6. Phone Numbers (Evaluated after dates and financial numbers to avoid false positives)
        if (allowedCategories.contains(EntityCategory.PHONE)) {
            val phoneMatcher = PHONE_PATTERN.matcher(text)
            while (phoneMatcher.find()) {
                val match = phoneMatcher.group().trim()
                val digits = match.filter { it.isDigit() }
                // Avoid false positives: must have 7-15 digits, and not look like pure year or standard date
                if (digits.length in 7..15) {
                    val range = phoneMatcher.start()..phoneMatcher.end()
                    if (!isOverlapping(range)) {
                        matchedRanges.add(range)
                        results.add(
                            ExtractedEntity(
                                category = EntityCategory.PHONE,
                                value = match,
                                maskedValue = match,
                                startIndex = phoneMatcher.start(),
                                endIndex = phoneMatcher.end(),
                                isSensitive = false
                            )
                        )
                    }
                }
            }
        }

        return results.sortedBy { it.startIndex }
    }
}
