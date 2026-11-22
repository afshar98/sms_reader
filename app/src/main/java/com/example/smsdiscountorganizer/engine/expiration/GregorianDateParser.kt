package com.example.smsdiscountorganizer.engine.expiration

import com.example.smsdiscountorganizer.engine.text.DigitNormalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.util.Locale

internal sealed interface GregorianDateParseResult {
    data class Resolved(val date: LocalDate) : GregorianDateParseResult
    data object MissingYear : GregorianDateParseResult
    data object InvalidDate : GregorianDateParseResult
    data object Ambiguous : GregorianDateParseResult
    data object NoMatch : GregorianDateParseResult
}

internal class GregorianDateParser {
    private val yearFirstDate = Regex("(?<![0-9])([0-9]{4})([-/.])([0-9]{1,2})\\2([0-9]{1,2})(?![0-9])")
    private val slashDate = Regex("(?<![0-9])([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})(?![0-9])")
    private val numericYearless = Regex("(?<![0-9])([0-9]{1,2})/([0-9]{1,2})(?![/0-9])")
    private val monthNames = Month.entries.associateBy { it.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT) }
    private val abbreviatedMonths = Month.entries.associateBy { it.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).trimEnd('.').lowercase(Locale.ROOT) }
    private val monthPattern = (monthNames.keys + abbreviatedMonths.keys).distinct().sortedByDescending(String::length).joinToString("|")
    private val monthFirst = Regex("(?i)(?<![\\p{L}])($monthPattern)\\s+([0-9]{1,2})(?:,?\\s+([0-9]{4}))?(?![0-9])")
    private val dayFirst = Regex("(?i)(?<![0-9])([0-9]{1,2})\\s+($monthPattern)(?:,?\\s+([0-9]{4}))?(?![0-9])")

    fun parse(text: String): GregorianDateParseResult {
        val normalized = DigitNormalizer.normalize(text)
        val recognizedDateCount = listOf(yearFirstDate, slashDate, monthFirst, dayFirst)
            .sumOf { pattern -> pattern.findAll(normalized).count() }
        if (recognizedDateCount > 1) return GregorianDateParseResult.Ambiguous
        yearFirstDate.find(normalized)?.let { match ->
            return buildDate(match.groupValues[1], match.groupValues[3], match.groupValues[4])
        }
        slashDate.find(normalized)?.let { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return GregorianDateParseResult.InvalidDate
            val second = match.groupValues[2].toIntOrNull() ?: return GregorianDateParseResult.InvalidDate
            val year = match.groupValues[3].toIntOrNull() ?: return GregorianDateParseResult.InvalidDate
            if (first in 1..12 && second in 1..12) return GregorianDateParseResult.Ambiguous
            val month = if (first > 12) second else first
            val day = if (first > 12) first else second
            if (year < 1800) return GregorianDateParseResult.Ambiguous
            return buildDate(year.toString(), month.toString(), day.toString())
        }

        val named = monthFirst.find(normalized) ?: dayFirst.find(normalized)
        if (named != null) {
            val firstIsMonth = named.groups[1]?.value?.firstOrNull()?.isLetter() == true
            val monthName = (if (firstIsMonth) named.groupValues[1] else named.groupValues[2]).lowercase(Locale.ROOT).trimEnd('.')
            val month = monthNames[monthName] ?: abbreviatedMonths[monthName] ?: return GregorianDateParseResult.NoMatch
            val day = (if (firstIsMonth) named.groupValues[2] else named.groupValues[1]).toIntOrNull()
                ?: return GregorianDateParseResult.InvalidDate
            val year = named.groupValues[3].toIntOrNull() ?: return GregorianDateParseResult.MissingYear
            return buildDate(year.toString(), month.value.toString(), day.toString())
        }
        if (numericYearless.containsMatchIn(normalized)) return GregorianDateParseResult.Ambiguous
        return GregorianDateParseResult.NoMatch
    }

    private fun buildDate(yearText: String, monthText: String, dayText: String): GregorianDateParseResult {
        val year = yearText.toIntOrNull() ?: return GregorianDateParseResult.InvalidDate
        val month = monthText.toIntOrNull() ?: return GregorianDateParseResult.InvalidDate
        val day = dayText.toIntOrNull() ?: return GregorianDateParseResult.InvalidDate
        if (year !in 1800..9999) return GregorianDateParseResult.Ambiguous
        return try {
            GregorianDateParseResult.Resolved(LocalDate.of(year, month, day))
        } catch (_: DateTimeException) {
            GregorianDateParseResult.InvalidDate
        }
    }
}
