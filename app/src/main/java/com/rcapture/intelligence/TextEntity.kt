package com.rcapture.intelligence

enum class EntityCategory(val displayName: String, val tag: String) {
    URL("URL / Link", "URL"),
    EMAIL("Email Address", "EMAIL"),
    PHONE("Phone Number", "PHONE"),
    DATETIME("Date / Time", "DATE"),
    ADDRESS("Physical Address", "ADDRESS"),
    FINANCIAL("Financial / Account", "FINANCIAL")
}

data class ExtractedEntity(
    val id: String = java.util.UUID.randomUUID().toString(),
    val category: EntityCategory,
    val value: String,
    val maskedValue: String,
    val startIndex: Int,
    val endIndex: Int,
    val isSensitive: Boolean = false
)
