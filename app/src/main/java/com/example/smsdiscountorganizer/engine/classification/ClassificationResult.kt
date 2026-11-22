package com.example.smsdiscountorganizer.engine.classification

enum class ClassificationCategory {
    DISCOUNT,
    PROMOTIONAL_NON_DISCOUNT,
    TRANSACTIONAL,
    OTP,
    UNKNOWN,
}

data class ClassificationReason(
    val ruleId: String,
    val explanation: String,
)

data class ClassificationResult(
    val category: ClassificationCategory,
    val confidence: Double,
    val reasons: List<ClassificationReason>,
)
