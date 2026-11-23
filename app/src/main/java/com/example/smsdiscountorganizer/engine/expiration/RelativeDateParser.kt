package com.example.smsdiscountorganizer.engine.expiration

import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RelativeDateParser(
    private val zoneId: ZoneId,
    private val normalizer: PersianTextNormalizer = PersianTextNormalizer(),
) {
    private val hoursPattern = Regex("(?<![0-9])([0-9]{1,3})\\s*ساعت\\s*(?:آینده|بعد)")
    private val dayAfterTomorrow = Regex("پس[‌\\s]?فردا")
    private val tonightExpiration = Regex("(?:تا\\s*(?:پایان\\s*)?امشب|مهلت.{0,12}امشب|اعتبار.{0,12}امشب)")

    fun parse(text: String, receivedAtMillis: Long?): ExpirationResolution? {
        val normalized = normalizer.normalize(text)
        val hours = hoursPattern.find(normalized)?.groupValues?.get(1)?.toLongOrNull()
        val isDayAfterTomorrow = normalized.contains("پس فردا") || dayAfterTomorrow.containsMatchIn(normalized)
        val relativeKinds = buildList {
            if (hours != null) add(RelativeKind.Duration(hours))
            if (tonightExpiration.containsMatchIn(normalized)) add(RelativeKind.Days(0))
            if (isDayAfterTomorrow) add(RelativeKind.Days(2))
            else if (normalized.contains("فردا")) add(RelativeKind.Days(1))
            if (normalized.contains("امروز")) add(RelativeKind.Days(0))
            if (normalized.contains("پایان هفته") || normalized.contains("آخر هفته")) add(RelativeKind.EndOfWeek)
            if (normalized.contains("تا جمعه") || normalized.contains("تا روز جمعه")) add(RelativeKind.Friday)
        }
        if (relativeKinds.isEmpty()) return null
        val referenceMillis = receivedAtMillis ?: return ExpirationResolution.Unresolved(
            UnresolvedDateReason.MISSING_REFERENCE_TIME,
        )
        val referenceInstant = Instant.ofEpochMilli(referenceMillis)
        val referenceDate = referenceInstant.atZone(zoneId).toLocalDate()
        val resolutions = relativeKinds.map { relativeKind ->
            when (relativeKind) {
                is RelativeKind.Duration -> ExpirationResolution.Resolved(
                    expiresAt = referenceInstant.plus(Duration.ofHours(relativeKind.hours)),
                    precision = DatePrecision.INSTANT,
                    confidence = 0.98,
                )
                is RelativeKind.Days -> resolveDate(referenceDate.plusDays(relativeKind.days.toLong()), 0.98)
                RelativeKind.EndOfWeek -> resolveDate(referenceDate.plusDays(daysUntilFriday(referenceDate)), 0.95)
                RelativeKind.Friday -> resolveDate(referenceDate.plusDays(daysUntilFriday(referenceDate)), 0.95)
            }
        }
        if (resolutions.map(ExpirationResolution.Resolved::expiresAt).distinct().size > 1) {
            return ExpirationResolution.Unresolved(UnresolvedDateReason.AMBIGUOUS_DATE)
        }
        return resolutions.first()
    }

    private fun resolveDate(date: LocalDate, confidence: Double) = ExpirationResolution.Resolved(
        expiresAt = date.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1),
        precision = DatePrecision.DATE,
        confidence = confidence,
    )

    private fun daysUntilFriday(date: LocalDate): Long =
        (5 - date.dayOfWeek.value + 7) % 7L

    private sealed interface RelativeKind {
        data class Duration(val hours: Long) : RelativeKind
        data class Days(val days: Int) : RelativeKind
        data object EndOfWeek : RelativeKind
        data object Friday : RelativeKind
    }
}
