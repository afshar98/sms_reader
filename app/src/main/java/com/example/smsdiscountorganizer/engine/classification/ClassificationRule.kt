package com.example.smsdiscountorganizer.engine.classification

interface ClassificationRule {
    val id: String
    val category: ClassificationCategory
    val weight: Int

    /** Higher-priority matches suppress lower-priority categories, for example OTP over discounts. */
    val priority: Int

    /** Static text only; implementations must not include the input message in a match reason. */
    val explanation: String

    fun matches(normalizedText: String): Boolean
}
