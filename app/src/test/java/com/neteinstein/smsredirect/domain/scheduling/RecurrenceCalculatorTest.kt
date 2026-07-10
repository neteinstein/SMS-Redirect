package com.neteinstein.smsredirect.domain.scheduling

import com.neteinstein.smsredirect.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class RecurrenceCalculatorTest {
    private fun epoch(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 10,
        minute: Int = 0,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `NONE never repeats`() {
        val anchor = epoch(2023, 1, 1)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.NONE,
                afterEpochMs = anchor,
                zoneId = ZoneOffset.UTC,
            )

        assertNull(result)
    }

    @Test
    fun `DAILY returns anchor plus one day when after equals anchor`() {
        val anchor = epoch(2023, 3, 10)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.DAILY,
                afterEpochMs = anchor,
                zoneId = ZoneOffset.UTC,
            )

        assertEquals(epoch(2023, 3, 11), result)
    }

    @Test
    fun `DAILY skips ahead to the first day strictly after the given instant`() {
        val anchor = epoch(2023, 3, 10)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.DAILY,
                afterEpochMs = epoch(2023, 3, 13),
                zoneId = ZoneOffset.UTC,
            )

        assertEquals(epoch(2023, 3, 14), result)
    }

    @Test
    fun `BIWEEKLY advances by fourteen days`() {
        val anchor = epoch(2023, 1, 1)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.BIWEEKLY,
                afterEpochMs = anchor,
                zoneId = ZoneOffset.UTC,
            )

        assertEquals(epoch(2023, 1, 15), result)
    }

    @Test
    fun `MONTHLY advances by one month on a regular month`() {
        val anchor = epoch(2023, 3, 15)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.MONTHLY,
                afterEpochMs = anchor,
                zoneId = ZoneOffset.UTC,
            )

        assertEquals(epoch(2023, 4, 15), result)
    }

    @Test
    fun `YEARLY advances by one year`() {
        val anchor = epoch(2023, 6, 1)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.YEARLY,
                afterEpochMs = anchor,
                zoneId = ZoneOffset.UTC,
            )

        assertEquals(epoch(2024, 6, 1), result)
    }

    @Test
    fun `WORK_WEEK_DAYS from a Friday skips the weekend to Monday`() {
        // 2023-03-10 is a Friday.
        val anchor = epoch(2023, 3, 10)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.WORK_WEEK_DAYS,
                afterEpochMs = anchor,
                zoneId = ZoneOffset.UTC,
            )

        // 2023-03-13 is the following Monday.
        assertEquals(epoch(2023, 3, 13), result)
    }

    @Test
    fun `WORK_WEEK_DAYS from a midweek day advances to the next day`() {
        // 2023-03-08 is a Wednesday.
        val anchor = epoch(2023, 3, 8)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.WORK_WEEK_DAYS,
                afterEpochMs = anchor,
                zoneId = ZoneOffset.UTC,
            )

        assertEquals(epoch(2023, 3, 9), result)
    }

    @Test
    fun `MONTHLY clamped short month does not drift when computing the following occurrence`() {
        // Anchored on the 31st: the first occurrence clamps into February (28 days in 2023),
        // but the second occurrence must be computed from the original anchor (31st), landing
        // on March 31st - not March 28th, which naive chaining off the clamped result would produce.
        val anchor = epoch(2023, 1, 31)
        val firstOccurrence = epoch(2023, 2, 28)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.MONTHLY,
                afterEpochMs = firstOccurrence,
                zoneId = ZoneOffset.UTC,
            )

        assertEquals(epoch(2023, 3, 31), result)
    }

    @Test
    fun `YEARLY from a leap day clamps to Feb 28 in non-leap years but restores Feb 29 once a leap year recurs`() {
        // Anchored on Feb 29, 2024 (leap): 2025, 2026 and 2027 are not leap years, so those three
        // occurrences all clamp to Feb 28. Naive chaining off the previous *result* would get stuck
        // on Feb 28 forever, but computing every occurrence from the original anchor correctly
        // restores Feb 29 once 2028 (a leap year) comes around.
        val anchor = epoch(2024, 2, 29)
        val thirdOccurrence = epoch(2027, 2, 28)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.YEARLY,
                afterEpochMs = thirdOccurrence,
                zoneId = ZoneOffset.UTC,
            )

        assertEquals(epoch(2028, 2, 29), result)
    }

    @Test
    fun `stopAt exactly on the next occurrence still allows it`() {
        val anchor = epoch(2023, 1, 1)
        val nextOccurrence = epoch(2023, 1, 2)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.DAILY,
                afterEpochMs = anchor,
                stopAtEpochMs = nextOccurrence,
                zoneId = ZoneOffset.UTC,
            )

        assertEquals(nextOccurrence, result)
    }

    @Test
    fun `stopAt before the next occurrence returns null`() {
        val anchor = epoch(2023, 1, 1)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.DAILY,
                afterEpochMs = anchor,
                stopAtEpochMs = epoch(2023, 1, 2) - 1,
                zoneId = ZoneOffset.UTC,
            )

        assertNull(result)
    }

    @Test
    fun `stopAt far in the future does not block the next occurrence`() {
        val anchor = epoch(2023, 1, 1)

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.MONTHLY,
                afterEpochMs = anchor,
                stopAtEpochMs = epoch(2030, 1, 1),
                zoneId = ZoneOffset.UTC,
            )

        assertNotNull(result)
    }

    @Test
    fun `returns null when no occurrence is found within the iteration cap`() {
        val anchor = epoch(2023, 1, 1)
        // Far beyond MAX_ITERATIONS (10,000) daily steps ahead of the anchor.
        val wayInTheFuture = anchor + 10_005L * 24 * 60 * 60 * 1000

        val result =
            RecurrenceCalculator.nextOccurrence(
                anchorEpochMs = anchor,
                repeatType = RepeatType.DAILY,
                afterEpochMs = wayInTheFuture,
                zoneId = ZoneOffset.UTC,
            )

        assertNull(result)
    }
}
