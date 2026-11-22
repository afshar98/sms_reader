package com.example.smsdiscountorganizer.engine.expiration

import com.example.smsdiscountorganizer.engine.text.DigitNormalizer
import com.example.smsdiscountorganizer.engine.text.PersianTextNormalizer
import java.time.DateTimeException
import java.time.LocalDate

sealed interface JalaliDateParseResult {
    data class Resolved(val date: LocalDate) : JalaliDateParseResult
    data object MissingYear : JalaliDateParseResult
    data object InvalidDate : JalaliDateParseResult
    data object AmbiguousCalendar : JalaliDateParseResult
    data object NoMatch : JalaliDateParseResult
}

/** Offline Borkowski Jalali conversion and Persian month-name parser. */
class JalaliDateParser(
    private val normalizer: PersianTextNormalizer = PersianTextNormalizer(),
) {
    private val numericYearFirst = Regex("(?<![0-9])([0-9]{4})[./-]([0-9]{1,2})[./-]([0-9]{1,2})(?![0-9])")
    private val monthPattern = MONTH_NAMES.keys.sortedByDescending(String::length).joinToString("|")
    private val dayFirstMonth = Regex("(?<![0-9])([0-9]{1,2})\\s+($monthPattern)(?:\\s+([0-9]{4}))?(?![0-9])")
    private val monthFirstDay = Regex("(?<![\\p{L}])($monthPattern)\\s+([0-9]{1,2})(?:\\s+([0-9]{4}))?(?![0-9])")

    fun parse(text: String): JalaliDateParseResult {
        val normalized = normalized(text)
        val namedMatches = dayFirstMonth.findAll(normalized).toList() + monthFirstDay.findAll(normalized).toList()
        val numericMatches = numericYearFirst.findAll(normalized).toList()
        if (namedMatches.size + numericMatches.size > 1) return JalaliDateParseResult.AmbiguousCalendar
        val named = dayFirstMonth.find(normalized) ?: monthFirstDay.find(normalized)
        if (named != null) {
            val dayFirst = named.groups[1]?.value?.firstOrNull()?.isDigit() == true
            val day = (if (dayFirst) named.groupValues[1] else named.groupValues[2]).toIntOrNull()
                ?: return JalaliDateParseResult.InvalidDate
            val monthName = if (dayFirst) named.groupValues[2] else named.groupValues[1]
            val month = MONTH_NAMES[monthName] ?: return JalaliDateParseResult.NoMatch
            val year = named.groupValues[3].toIntOrNull() ?: return JalaliDateParseResult.MissingYear
            return convert(year, month, day)
        }

        val numeric = numericMatches.firstOrNull() ?: return JalaliDateParseResult.NoMatch
        val year = numeric.groupValues[1].toIntOrNull() ?: return JalaliDateParseResult.InvalidDate
        val month = numeric.groupValues[2].toIntOrNull() ?: return JalaliDateParseResult.InvalidDate
        val day = numeric.groupValues[3].toIntOrNull() ?: return JalaliDateParseResult.InvalidDate
        return when (year) {
            in 1200..1599 -> convert(year, month, day)
            in MIN_SUPPORTED_YEAR..1199, in 1600..1799 -> JalaliDateParseResult.AmbiguousCalendar
            else -> JalaliDateParseResult.NoMatch
        }
    }

    fun toGregorian(year: Int, month: Int, day: Int): LocalDate? =
        (convert(year, month, day) as? JalaliDateParseResult.Resolved)?.date

    private fun convert(year: Int, month: Int, day: Int): JalaliDateParseResult {
        if (year !in MIN_SUPPORTED_YEAR..MAX_SUPPORTED_YEAR || month !in 1..12) {
            return JalaliDateParseResult.InvalidDate
        }
        val leap = leapData(year)?.leap == 0
        val monthLength = when {
            month <= 6 -> 31
            month <= 11 -> 30
            leap -> 30
            else -> 29
        }
        if (day !in 1..monthLength) return JalaliDateParseResult.InvalidDate
        val yearStart = leapData(year) ?: return JalaliDateParseResult.InvalidDate
        val marchFirst = try {
            LocalDate.of(yearStart.gregorianYear, 3, yearStart.marchDay)
        } catch (_: DateTimeException) {
            return JalaliDateParseResult.InvalidDate
        }
        val daysBeforeMonth = if (month <= 7) {
            (month - 1) * 31
        } else {
            186 + (month - 7) * 30
        }
        return JalaliDateParseResult.Resolved(marchFirst.plusDays((daysBeforeMonth + day - 1).toLong()))
    }

    private fun normalized(text: String): String = normalizer.normalize(DigitNormalizer.normalize(text))

    private data class JalaliYearData(val gregorianYear: Int, val marchDay: Int, val leap: Int)

    private fun leapData(year: Int): JalaliYearData? {
        // The break-year cycle follows Borkowski's Jalali conversion algorithm.
        if (year !in MIN_SUPPORTED_YEAR..MAX_SUPPORTED_YEAR) return null
        val gregorianYear = year + 621
        var leapJalali = -14
        var previousBreak = YEAR_BREAKS.first()
        var jump = 0
        for (nextBreak in YEAR_BREAKS.drop(1)) {
            jump = nextBreak - previousBreak
            if (year < nextBreak) break
            leapJalali += jump / 33 * 8 + (jump % 33) / 4
            previousBreak = nextBreak
        }
        val yearOffset = year - previousBreak
        leapJalali += yearOffset / 33 * 8 + (yearOffset % 33 + 3) / 4
        if (jump % 33 == 4 && jump - yearOffset == 4) leapJalali++
        val leapGregorian = gregorianYear / 4 - ((gregorianYear / 100 + 1) * 3) / 4 - 150
        val marchDay = 20 + leapJalali - leapGregorian
        var adjustedOffset = yearOffset
        if (jump - yearOffset < 6) adjustedOffset = yearOffset - jump + (jump + 4) / 33 * 33
        var leap = Math.floorMod(Math.floorMod(adjustedOffset + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return JalaliYearData(gregorianYear, marchDay, leap)
    }

    private companion object {
        const val MIN_SUPPORTED_YEAR = -61
        const val MAX_SUPPORTED_YEAR = 3177
        val YEAR_BREAKS = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)
        val MONTH_NAMES = mapOf(
            "فروردین" to 1,
            "اردیبهشت" to 2,
            "خرداد" to 3,
            "تیر" to 4,
            "مرداد" to 5,
            "شهریور" to 6,
            "مهر" to 7,
            "آبان" to 8,
            "آذر" to 9,
            "دی" to 10,
            "بهمن" to 11,
            "اسفند" to 12,
        )
    }
}
