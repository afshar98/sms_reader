package com.example.smsdiscountorganizer.engine.extraction

class OfferTitleExtractor {
    private val titlePattern = Regex("عنوان\\s*پیشنهاد\\s*[:：]\\s*([^؛;،,\\n.!؟]+)")

    fun extract(text: String): String? = titlePattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)
}
