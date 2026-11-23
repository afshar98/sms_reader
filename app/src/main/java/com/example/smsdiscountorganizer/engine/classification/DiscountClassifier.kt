package com.example.smsdiscountorganizer.engine.classification

import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer
import java.util.Locale

class DiscountClassifier(
    private val ruleRegistry: RuleRegistry = RuleRegistry(),
    private val normalizer: PersianTextNormalizer = PersianTextNormalizer(),
) {
    fun classify(message: String): ClassificationResult {
        val normalizedText = normalizer.normalize(message).lowercase(Locale.ROOT)
        val matchingRules = ruleRegistry.matchingRules(normalizedText).let { rules ->
            // Specific phrases such as "کد تخفیف" already contain the generic keyword.
            // Counting both as independent evidence inflates confidence without adding a signal.
            if (rules.any { it.id in SPECIFIC_DISCOUNT_RULE_IDS }) {
                rules.filterNot { it.id == GENERIC_DISCOUNT_RULE_ID }
            } else {
                rules
            }
        }
        if (matchingRules.isEmpty()) {
            return ClassificationResult(
                category = ClassificationCategory.UNKNOWN,
                confidence = 0.0,
                reasons = emptyList(),
            )
        }

        val highestPriority = matchingRules.asSequence()
            .filter { it.priority > 0 }
            .maxOfOrNull(ClassificationRule::priority)
        val consideredRules = if (highestPriority == null) {
            matchingRules
        } else {
            matchingRules.filter { it.priority == highestPriority }
        }
        val scores = consideredRules.groupBy(ClassificationRule::category)
            .mapValues { (_, rules) -> rules.sumOf(ClassificationRule::weight) }
        val winner = scores.entries.sortedWith(
            compareByDescending<Map.Entry<ClassificationCategory, Int>> { it.value }
                .thenBy { it.key.name },
        ).first()
        val runnerUpScore = scores.entries.asSequence()
            .filter { it.key != winner.key }
            .maxOfOrNull { it.value } ?: 0
        val confidence = (winner.value.toDouble() / (winner.value + runnerUpScore + CONFIDENCE_BASELINE))
            .coerceIn(0.0, MAX_CONFIDENCE)
        val reasons = consideredRules.asSequence()
            .filter { it.category == winner.key }
            .sortedWith(compareByDescending<ClassificationRule> { it.weight }.thenBy { it.id })
            .map { ClassificationReason(ruleId = it.id, explanation = it.explanation) }
            .toList()

        return ClassificationResult(
            category = winner.key,
            confidence = confidence,
            reasons = reasons,
        )
    }

    private companion object {
        const val GENERIC_DISCOUNT_RULE_ID = "discount_keyword"
        val SPECIFIC_DISCOUNT_RULE_IDS = setOf(
            "discount_code",
            "discount_percentage",
            "discounted_purchase",
        )
        const val CONFIDENCE_BASELINE = 20.0
        const val MAX_CONFIDENCE = 0.99
    }
}
