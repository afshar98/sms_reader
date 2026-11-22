package com.example.smsdiscountorganizer.engine.expiration

import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

class PersianDateResolver(
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val jalaliDateParser: JalaliDateParser = JalaliDateParser(),
    private val relativeDateParser: RelativeDateParser = RelativeDateParser(zoneId),
) {
    private val gregorianDateParser = GregorianDateParser()
    private val calculator = ExpirationCalculator(clock, zoneId)

    fun resolve(message: String?, receivedAtMillis: Long?): ExpirationResolution {
        if (message.isNullOrBlank()) return unresolved(UnresolvedDateReason.NO_EXPIRATION)
        when (val jalaliResult = jalaliDateParser.parse(message)) {
            is JalaliDateParseResult.Resolved -> return resolveAbsoluteOrAmbiguous(message, receivedAtMillis, jalaliResult.date)
            JalaliDateParseResult.MissingYear -> return unresolved(UnresolvedDateReason.MISSING_YEAR)
            JalaliDateParseResult.InvalidDate -> return unresolved(UnresolvedDateReason.INVALID_DATE)
            JalaliDateParseResult.AmbiguousCalendar -> return unresolved(UnresolvedDateReason.AMBIGUOUS_DATE)
            JalaliDateParseResult.NoMatch -> Unit
        }
        when (val gregorianResult = gregorianDateParser.parse(message)) {
            is GregorianDateParseResult.Resolved -> return resolveAbsoluteOrAmbiguous(message, receivedAtMillis, gregorianResult.date)
            GregorianDateParseResult.MissingYear -> return unresolved(UnresolvedDateReason.MISSING_YEAR)
            GregorianDateParseResult.InvalidDate -> return unresolved(UnresolvedDateReason.INVALID_DATE)
            GregorianDateParseResult.Ambiguous -> return unresolved(UnresolvedDateReason.AMBIGUOUS_DATE)
            GregorianDateParseResult.NoMatch -> Unit
        }
        return relativeDateParser.parse(message, receivedAtMillis)
            ?: unresolved(UnresolvedDateReason.NO_EXPIRATION)
    }

    fun status(resolution: ExpirationResolution?): ExpirationStatus = calculator.status(resolution)

    private fun endOfDay(date: LocalDate, confidence: Double): ExpirationResolution = try {
        ExpirationResolution.Resolved(
            expiresAt = date.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1),
            precision = DatePrecision.DATE,
            confidence = confidence,
        )
    } catch (_: DateTimeException) {
        unresolved(UnresolvedDateReason.INVALID_DATE)
    }

    private fun resolveAbsoluteOrAmbiguous(
        message: String,
        receivedAtMillis: Long?,
        date: LocalDate,
    ): ExpirationResolution {
        val absolute = endOfDay(date, 0.99)
        val relative = relativeDateParser.parse(message, receivedAtMillis) ?: return absolute
        if (absolute !is ExpirationResolution.Resolved || relative !is ExpirationResolution.Resolved) {
            return unresolved(UnresolvedDateReason.AMBIGUOUS_DATE)
        }
        return if (absolute.expiresAt == relative.expiresAt) absolute else unresolved(UnresolvedDateReason.AMBIGUOUS_DATE)
    }

    private fun unresolved(reason: UnresolvedDateReason) = ExpirationResolution.Unresolved(reason)
}
