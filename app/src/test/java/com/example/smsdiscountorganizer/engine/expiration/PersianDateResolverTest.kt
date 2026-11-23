package com.example.smsdiscountorganizer.engine.expiration

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianDateResolverTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val clock = Clock.fixed(Instant.parse("2025-03-19T12:00:00Z"), zone)
    private val resolver = PersianDateResolver(clock, zone)

    @Test
    fun resolvesJalaliNumericDateWithKnownBorkowskiConversion() {
        val result = resolver.resolve("تا ۱۳۹۵/۰۱/۲۳", receivedAt("2025-03-19T12:00:00Z"))

        assertResolvedDate(result, LocalDate.of(2016, 4, 11))
    }

    @Test
    fun acceptsPersianArabicAndLatinDigitsForDatesAndRelativeTimes() {
        val expectedDate = LocalDate.of(2016, 4, 11)
        assertResolvedDate(resolver.resolve("تا ۱۳۹۵/۰۱/۲۳", receivedAt("2025-03-19T12:00:00Z")), expectedDate)
        assertResolvedDate(resolver.resolve("تا ١٣٩٥/٠١/٢٣", receivedAt("2025-03-19T12:00:00Z")), expectedDate)
        assertResolvedDate(resolver.resolve("تا 1395/01/23", receivedAt("2025-03-19T12:00:00Z")), expectedDate)

        val reference = receivedAt("2025-03-20T12:00:00Z")
        val expectedInstant = Instant.ofEpochMilli(reference).plus(Duration.ofHours(48))
        listOf("۴۸ ساعت آینده", "٤٨ ساعت آینده", "48 ساعت آینده").forEach { input ->
            val resolved = resolver.resolve(input, reference) as ExpirationResolution.Resolved
            assertEquals(expectedInstant, resolved.expiresAt)
        }
    }

    @Test
    fun resolvesPersianMonthNameAndExplicitYear() {
        val result = resolver.resolve("تا ۲۳ فروردین ۱۳۹۵", receivedAt("2025-03-19T12:00:00Z"))

        assertResolvedDate(result, LocalDate.of(2016, 4, 11))
    }

    @Test
    fun resolvesExplicitGregorianIsoDate() {
        val result = resolver.resolve("2025-12-31", receivedAt("2025-03-19T12:00:00Z"))
        val slashDate = resolver.resolve("۲۰۲۵/۱۲/۳۱", receivedAt("2025-03-19T12:00:00Z"))

        assertResolvedDate(result, LocalDate.of(2025, 12, 31))
        assertResolvedDate(slashDate, LocalDate.of(2025, 12, 31))
    }

    @Test
    fun resolvesUnambiguousGregorianSlashOrderAndRejectsInvalidDate() {
        assertResolvedDate(
            resolver.resolve("31/12/2025", receivedAt("2025-03-19T12:00:00Z")),
            LocalDate.of(2025, 12, 31),
        )
        assertEquals(
            UnresolvedDateReason.INVALID_DATE,
            (resolver.resolve("2025-02-29", receivedAt("2025-03-19T12:00:00Z")) as ExpirationResolution.Unresolved).reason,
        )
    }

    @Test
    fun resolvesJalaliLeapDayAndRejectsNonLeapDay() {
        assertResolvedDate(
            resolver.resolve("۱۳۹۵/۱۲/۳۰", receivedAt("2025-03-19T12:00:00Z")),
            LocalDate.of(2017, 3, 20),
        )

        assertEquals(
            UnresolvedDateReason.INVALID_DATE,
            (resolver.resolve("۱۳۹۴/۱۲/۳۰", receivedAt("2025-03-19T12:00:00Z")) as ExpirationResolution.Unresolved).reason,
        )
    }

    @Test
    fun resolvesJalaliYearTransition() {
        assertResolvedDate(
            resolver.resolve("۱۴۰۴/۱۲/۲۹", receivedAt("2026-03-19T10:00:00Z")),
            LocalDate.of(2026, 3, 20),
        )
        assertResolvedDate(
            resolver.resolve("۱۴۰۵/۰۱/۰۱", receivedAt("2026-03-19T10:00:00Z")),
            LocalDate.of(2026, 3, 21),
        )
    }

    @Test
    fun doesNotInventMissingJalaliYear() {
        val result = resolver.resolve("تا ۲۵ خرداد", receivedAt("2025-03-19T12:00:00Z"))

        assertEquals(UnresolvedDateReason.MISSING_YEAR, (result as ExpirationResolution.Unresolved).reason)
    }

    @Test
    fun missingYearStaysUnresolvedEvenWhenTheMessageDateSuggestsAConvenientYear() {
        val result = resolver.resolve("تا ۱ فروردین", receivedAt("2025-03-19T12:00:00Z"))

        assertEquals(UnresolvedDateReason.MISSING_YEAR, (result as ExpirationResolution.Unresolved).reason)
        assertEquals(ExpirationStatus.UNKNOWN, ExpirationCalculator(clock, zone).status(result))
    }

    @Test
    fun todayAndTomorrowUseReceivedTimestampInConfiguredZone() {
        val received = receivedAt("2025-03-20T20:30:00Z") // 00:00 on March 21 in Tehran.

        assertResolvedDate(resolver.resolve("تا پایان امروز", received), LocalDate.of(2025, 3, 21))
        assertResolvedDate(resolver.resolve("فردا", received), LocalDate.of(2025, 3, 22))
        assertResolvedDate(resolver.resolve("پس فردا", received), LocalDate.of(2025, 3, 23))
    }

    @Test
    fun tonightUsesEndOfReceivedLocalDayButAnEventTonightIsNotAnExpiration() {
        val received = receivedAt("2025-03-20T10:00:00Z")
        val tonight = resolver.resolve("کد تخفیف تا امشب اعتبار دارد", received)

        assertResolvedDate(tonight, LocalDate.of(2025, 3, 20))
        assertEquals(
            UnresolvedDateReason.NO_EXPIRATION,
            (resolver.resolve("فروش ویژه امشب", received) as ExpirationResolution.Unresolved).reason,
        )
    }

    @Test
    fun resolvesEndOfWeekAsFridayInSaturdayFirstWeek() {
        val saturday = receivedAt("2025-03-22T08:00:00Z") // Saturday in Tehran.
        val friday = receivedAt("2025-03-28T08:00:00Z") // Friday in Tehran.

        assertResolvedDate(resolver.resolve("تا پایان هفته", saturday), LocalDate.of(2025, 3, 28))
        assertResolvedDate(resolver.resolve("فقط تا جمعه", saturday), LocalDate.of(2025, 3, 28))
        assertResolvedDate(resolver.resolve("فقط تا جمعه", friday), LocalDate.of(2025, 3, 28))
    }

    @Test
    fun resolvesFortyEightHoursAsAnExactDurationAcrossLocalDateChange() {
        val received = receivedAt("2025-03-20T20:30:00Z")
        val result = resolver.resolve("۴۸ ساعت آینده", received) as ExpirationResolution.Resolved

        assertEquals(Instant.ofEpochMilli(received).plus(Duration.ofHours(48)), result.expiresAt)
        assertEquals(DatePrecision.INSTANT, result.precision)
    }

    @Test
    fun missingReferenceTimeMakesRelativeDateUnresolved() {
        val result = resolver.resolve("فردا", receivedAtMillis = null)

        assertEquals(UnresolvedDateReason.MISSING_REFERENCE_TIME, (result as ExpirationResolution.Unresolved).reason)
    }

    @Test
    fun conflictingRelativeAndAbsoluteDatesRemainUnresolved() {
        val result = resolver.resolve(
            "تا پایان امروز یا تا 2025-03-25",
            receivedAt("2025-03-20T12:00:00Z"),
        )

        assertEquals(UnresolvedDateReason.AMBIGUOUS_DATE, (result as ExpirationResolution.Unresolved).reason)
    }

    @Test
    fun conflictingExplicitDatesRemainUnresolved() {
        val result = resolver.resolve("از ۱۴۰۵/۰۱/۰۱ تا ۱۴۰۵/۰۱/۱۵", receivedAt("2025-03-20T12:00:00Z"))

        assertEquals(UnresolvedDateReason.AMBIGUOUS_DATE, (result as ExpirationResolution.Unresolved).reason)
    }

    @Test
    fun conflictingRelativeDatesRemainUnresolved() {
        val result = resolver.resolve("تا امروز یا در ۴۸ ساعت آینده", receivedAt("2025-03-20T12:00:00Z"))

        assertEquals(UnresolvedDateReason.AMBIGUOUS_DATE, (result as ExpirationResolution.Unresolved).reason)
    }

    @Test
    fun missingExpirationIsUnknownNotExpired() {
        val result = resolver.resolve("پیشنهاد ویژه برای شما", receivedAt("2025-03-19T12:00:00Z"))

        assertEquals(UnresolvedDateReason.NO_EXPIRATION, (result as ExpirationResolution.Unresolved).reason)
        assertEquals(ExpirationStatus.UNKNOWN, ExpirationCalculator(clock, zone).status(result))
    }

    @Test
    fun datePrecisionAndConfidenceAreIndependentValues() {
        val result = resolver.resolve("تا ۱۴۰۴/۱۲/۲۹", receivedAt("2026-03-19T10:00:00Z")) as ExpirationResolution.Resolved

        assertEquals(DatePrecision.DATE, result.precision)
        assertTrue(result.confidence > 0.0)
        assertTrue(result.confidence <= 1.0)
    }

    @Test
    fun calculatorResolvesAllStatusesAtBoundaries() {
        val now = Instant.parse("2025-03-20T12:00:00Z")
        val calculator = ExpirationCalculator(Clock.fixed(now, ZoneOffset.UTC), ZoneOffset.UTC, Duration.ofHours(24))

        assertEquals(ExpirationStatus.EXPIRED, calculator.status(resolved(now.minusMillis(1))))
        assertEquals(ExpirationStatus.EXPIRED, calculator.status(resolved(now)))
        assertEquals(ExpirationStatus.EXPIRING_SOON, calculator.status(resolved(now.plus(Duration.ofHours(24)))))
        assertEquals(ExpirationStatus.ACTIVE, calculator.status(resolved(now.plus(Duration.ofHours(24)).plusMillis(1))))
        assertEquals(
            ExpirationStatus.UNKNOWN,
            calculator.status(ExpirationResolution.Unresolved(UnresolvedDateReason.MISSING_YEAR)),
        )
    }

    @Test
    fun calculatorUsesInjectedClockInsteadOfWallClock() {
        val expiration = Instant.parse("2025-03-20T12:00:00Z")
        val calculator = ExpirationCalculator(Clock.fixed(expiration.minusSeconds(3600), ZoneOffset.UTC), ZoneOffset.UTC)

        assertEquals(ExpirationStatus.EXPIRING_SOON, calculator.status(resolved(expiration)))
    }

    @Test
    fun exactDurationIsMeasuredOnTimelineAcrossDaylightSavingChange() {
        val berlin = ZoneId.of("Europe/Berlin")
        val received = Instant.parse("2025-03-29T11:00:00Z")
        val result = PersianDateResolver(Clock.fixed(received, berlin), berlin)
            .resolve("۴۸ ساعت آینده", received.toEpochMilli()) as ExpirationResolution.Resolved

        assertEquals(Instant.parse("2025-03-31T11:00:00Z"), result.expiresAt)
        assertEquals(13, result.expiresAt.atZone(berlin).hour)
    }

    @Test
    fun endOfDayUsesTheLocalDateAcrossDaylightSavingChange() {
        val berlin = ZoneId.of("Europe/Berlin")
        val received = Instant.parse("2025-03-30T10:00:00Z")
        val result = PersianDateResolver(Clock.fixed(received, berlin), berlin)
            .resolve("امروز", received.toEpochMilli()) as ExpirationResolution.Resolved

        assertEquals(LocalDate.of(2025, 3, 30), result.expiresAt.atZone(berlin).toLocalDate())
        assertEquals(23, result.expiresAt.atZone(berlin).hour)
        assertEquals(
            Duration.ofHours(23),
            Duration.between(
                LocalDate.of(2025, 3, 30).atStartOfDay(berlin),
                LocalDate.of(2025, 3, 31).atStartOfDay(berlin),
            ),
        )
    }

    @Test
    fun dateOnlyExpiryUsesEndOfLocalDayAcrossOffset() {
        val result = resolver.resolve("2025-03-21", receivedAt("2025-03-20T10:00:00Z")) as ExpirationResolution.Resolved
        val localExpiry = result.expiresAt.atZone(zone)

        assertEquals(LocalDate.of(2025, 3, 21), localExpiry.toLocalDate())
        assertEquals(23, localExpiry.hour)
        assertEquals(59, localExpiry.minute)
        assertEquals(59, localExpiry.second)
        assertEquals(DatePrecision.DATE, result.precision)
    }

    @Test
    fun ambiguousSlashDateIsUnresolved() {
        val result = resolver.resolve("03/04/2025", receivedAt("2025-03-19T12:00:00Z"))

        assertEquals(UnresolvedDateReason.AMBIGUOUS_DATE, (result as ExpirationResolution.Unresolved).reason)
    }

    @Test
    fun supportsGregorianEnglishMonthDateAndYearlessMonthDateExplicitly() {
        assertResolvedDate(
            resolver.resolve("December 31, 2025", receivedAt("2025-03-19T12:00:00Z")),
            LocalDate.of(2025, 12, 31),
        )
        assertEquals(
            UnresolvedDateReason.MISSING_YEAR,
            (resolver.resolve("تا 31 December", receivedAt("2025-03-19T12:00:00Z")) as ExpirationResolution.Unresolved).reason,
        )
    }

    private fun receivedAt(isoInstant: String): Long = Instant.parse(isoInstant).toEpochMilli()

    private fun assertResolvedDate(result: ExpirationResolution, expectedDate: LocalDate) {
        assertTrue("Expected resolved date, got $result", result is ExpirationResolution.Resolved)
        val resolved = result as ExpirationResolution.Resolved
        assertEquals(expectedDate, resolved.expiresAt.atZone(zone).toLocalDate())
        assertEquals(DatePrecision.DATE, resolved.precision)
    }

    private fun resolved(instant: Instant) = ExpirationResolution.Resolved(
        expiresAt = instant,
        precision = DatePrecision.INSTANT,
        confidence = 1.0,
    )
}
