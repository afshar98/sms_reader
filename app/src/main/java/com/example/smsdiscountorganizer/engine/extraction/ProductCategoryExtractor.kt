package com.example.smsdiscountorganizer.engine.extraction

import com.example.smsdiscountorganizer.domain.offer.ProductCategory
import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer

class ProductCategoryExtractor(
    private val normalizer: PersianTextNormalizer = PersianTextNormalizer(),
) {
    private val terms = linkedMapOf(
        ProductCategory.Apparel to listOf("پوشاک", "لباس", "کفش", "کیف"),
        ProductCategory.Grocery to listOf("مواد غذایی", "سوپرمارکت", "خواربار", "میوه"),
        ProductCategory.Electronics to listOf("گوشی", "موبایل", "لپتاپ", "لپ تاپ", "لوازم الکترونیکی"),
        ProductCategory.Beauty to listOf("آرایشی", "بهداشتی", "زیبایی", "عطر"),
        ProductCategory.Food to listOf("رستوران", "فست فود", "کافه"),
        ProductCategory.Home to listOf("لوازم خانگی", "مبلمان", "دکوراسیون", "خانه"),
        ProductCategory.Travel to listOf("هتل", "سفر", "پرواز", "بلیط هواپیما"),
        ProductCategory.Entertainment to listOf("سینما", "کنسرت", "تئاتر"),
    )

    fun extract(text: String): ProductCategory? {
        val normalized = normalizer.normalize(text)
        val matches = terms.filterValues { words -> words.any(normalized::contains) }.keys
        return matches.singleOrNull()
    }
}
