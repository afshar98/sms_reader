package com.example.smsdiscountorganizer.data.local

import androidx.room.TypeConverter
import com.example.smsdiscountorganizer.domain.offer.MonetaryAmount
import com.example.smsdiscountorganizer.domain.offer.MoneyCurrency
import com.example.smsdiscountorganizer.domain.offer.ProductCategory
import com.example.smsdiscountorganizer.domain.offer.OfferCategory

class OfferConverters {
    @TypeConverter
    fun percentageListToStorage(values: List<Double>?): String? = values?.joinToString(",")

    @TypeConverter
    fun storageToPercentageList(value: String?): List<Double>? = value?.split(',')?.mapNotNull(String::toDoubleOrNull)

    @TypeConverter
    fun amountListToStorage(values: List<MonetaryAmount>?): String? = values?.joinToString(";") {
        "${it.amount}|${it.currency.name}"
    }

    @TypeConverter
    fun storageToAmountList(value: String?): List<MonetaryAmount>? = value?.split(';')?.mapNotNull { entry ->
        val fields = entry.split('|', limit = 2)
        val currency = fields.getOrNull(1)?.let { runCatching { MoneyCurrency.valueOf(it) }.getOrNull() }
        if (fields.size != 2 || currency == null) null else MonetaryAmount(fields[0], currency)
    }

    @TypeConverter
    fun amountToStorage(value: MonetaryAmount?): String? = value?.let { "${it.amount}|${it.currency.name}" }

    @TypeConverter
    fun storageToAmount(value: String?): MonetaryAmount? = value?.let { storageToAmountList(it)?.singleOrNull() }

    @TypeConverter
    fun categoryToStorage(value: ProductCategory?): String? = value?.name

    @TypeConverter
    fun storageToCategory(value: String?): ProductCategory? = value?.let { runCatching { ProductCategory.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun categoriesToStorage(values: List<OfferCategory>): String =
        values.distinctBy(OfferCategory::stableId).joinToString(",") { it.stableId }

    @TypeConverter
    fun storageToCategories(value: String?): List<OfferCategory> = value.orEmpty()
        .split(',')
        .mapNotNull(OfferCategory::fromStableId)
}
