package com.example.smsdiscountorganizer.domain.scan

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsDateRangeSelectionTest {
    @Test
    fun defaultsToLastTwoCalendarMonths() {
        val selection = SmsDateRangeSelection()

        assertEquals(SmsDateRangePreset.LAST_2_CALENDAR_MONTHS, selection.preset)
    }

    @Test
    fun twoMonthPresetUsesCalendarArithmeticAtMonthEnd() {
        val result = SmsDateRangeSelection(SmsDateRangePreset.LAST_2_CALENDAR_MONTHS)
            .resolve(fixedClock("2026-03-31T18:00:00Z"), ZoneId.of("UTC"))

        assertTrue(result is SmsDateRangeResolution.Valid)
        val range = (result as SmsDateRangeResolution.Valid).range!!
        assertEquals(Instant.parse("2026-01-31T00:00:00Z").toEpochMilli(), range.startInclusiveMillis)
        assertEquals(Instant.parse("2026-04-01T00:00:00Z").toEpochMilli(), range.endExclusiveMillis)
    }

    @Test
    fun customRangeIncludesTheWholeLocalEndDateAcrossDaylightSavingChange() {
        val zone = ZoneId.of("America/New_York")
        val selection = SmsDateRangeSelection(
            preset = SmsDateRangePreset.CUSTOM,
            customStartDate = LocalDate.parse("2026-03-07"),
            customEndDate = LocalDate.parse("2026-03-08"),
        )

        val range = (selection.resolve(fixedClock("2026-03-10T12:00:00Z"), zone) as SmsDateRangeResolution.Valid)
            .range!!

        assertEquals(Instant.parse("2026-03-07T05:00:00Z").toEpochMilli(), range.startInclusiveMillis)
        assertEquals(Instant.parse("2026-03-09T04:00:00Z").toEpochMilli(), range.endExclusiveMillis)
    }

    @Test
    fun reversedOrIncompleteCustomRangesAreRejected() {
        val clock = fixedClock("2026-03-10T12:00:00Z")
        val reversed = SmsDateRangeSelection(
            SmsDateRangePreset.CUSTOM,
            LocalDate.parse("2026-03-09"),
            LocalDate.parse("2026-03-08"),
        )
        val incomplete = SmsDateRangeSelection(SmsDateRangePreset.CUSTOM, LocalDate.parse("2026-03-08"), null)

        assertEquals(SmsDateRangeResolution.Invalid, reversed.resolve(clock, ZoneId.of("UTC")))
        assertEquals(SmsDateRangeResolution.Invalid, incomplete.resolve(clock, ZoneId.of("UTC")))
    }

    @Test
    fun allMessagesHasNoDateBounds() {
        val result = SmsDateRangeSelection(SmsDateRangePreset.ALL_MESSAGES)
            .resolve(fixedClock("2026-03-10T12:00:00Z"), ZoneId.of("UTC"))

        assertEquals(SmsDateRangeResolution.Valid(range = null), result)
        assertNull((result as SmsDateRangeResolution.Valid).range)
    }

    private fun fixedClock(instant: String): Clock =
        Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"))
}
