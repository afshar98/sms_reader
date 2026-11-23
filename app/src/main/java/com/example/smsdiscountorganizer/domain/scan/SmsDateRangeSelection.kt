package com.example.smsdiscountorganizer.domain.scan

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** User-selected Inbox window. The range is inclusive at the UI date level. */
data class SmsDateRangeSelection(
    val preset: SmsDateRangePreset = SmsDateRangePreset.LAST_2_CALENDAR_MONTHS,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
) {
    fun resolve(clock: Clock, zoneId: ZoneId): SmsDateRangeResolution {
        if (preset == SmsDateRangePreset.ALL_MESSAGES) {
            return SmsDateRangeResolution.Valid(range = null)
        }
        val today = LocalDate.now(clock.withZone(zoneId))
        val (start, end) = when (preset) {
            SmsDateRangePreset.LAST_7_DAYS -> today.minusDays(6) to today
            SmsDateRangePreset.LAST_30_DAYS -> today.minusDays(29) to today
            SmsDateRangePreset.LAST_2_CALENDAR_MONTHS -> today.minusMonths(2) to today
            SmsDateRangePreset.LAST_3_CALENDAR_MONTHS -> today.minusMonths(3) to today
            SmsDateRangePreset.CUSTOM -> (customStartDate ?: return SmsDateRangeResolution.Invalid) to
                (customEndDate ?: return SmsDateRangeResolution.Invalid)
            SmsDateRangePreset.ALL_MESSAGES -> error("Handled above")
        }
        if (end.isBefore(start)) return SmsDateRangeResolution.Invalid
        val startInstant = start.atStartOfDay(zoneId).toInstant()
        val endExclusive = end.plusDays(1).atStartOfDay(zoneId).toInstant()
        return SmsDateRangeResolution.Valid(
            SmsDateRange(
                startInclusiveMillis = startInstant.toEpochMilli(),
                endExclusiveMillis = endExclusive.toEpochMilli(),
            ),
        )
    }
}

enum class SmsDateRangePreset {
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_2_CALENDAR_MONTHS,
    LAST_3_CALENDAR_MONTHS,
    CUSTOM,
    ALL_MESSAGES,
}

data class SmsDateRange(
    val startInclusiveMillis: Long,
    val endExclusiveMillis: Long,
) {
    init {
        require(startInclusiveMillis < endExclusiveMillis)
    }
}

sealed interface SmsDateRangeResolution {
    data class Valid(val range: SmsDateRange?) : SmsDateRangeResolution
    data object Invalid : SmsDateRangeResolution
}
