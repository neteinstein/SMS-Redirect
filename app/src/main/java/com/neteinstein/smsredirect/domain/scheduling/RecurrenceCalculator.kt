package com.neteinstein.smsredirect.domain.scheduling

import com.neteinstein.smsredirect.domain.model.RepeatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure-Kotlin recurrence math for scheduled SMS. Every occurrence is computed directly from the
 * original anchor date/time (the date the user picked), never from the previously computed
 * occurrence - this avoids clamping drift (e.g. a monthly reminder on the 31st collapsing to the
 * 28th forever after the first short month) and means that if the device was offline for a long
 * time, missed occurrences are skipped silently and only a single, up-to-date occurrence is
 * scheduled next instead of firing a burst of catch-up sends.
 */
object RecurrenceCalculator {
    // Safety cap so a pathological input (e.g. a daily reminder missed for decades) can never spin forever.
    private const val MAX_ITERATIONS = 10_000

    /**
     * Returns the smallest occurrence timestamp that is strictly after [afterEpochMs], or null when
     * [repeatType] is [RepeatType.NONE] (a single, non-repeating send), when [stopAtEpochMs] has been
     * reached, or when no occurrence could be found within [MAX_ITERATIONS] steps.
     */
    fun nextOccurrence(
        anchorEpochMs: Long,
        repeatType: RepeatType,
        afterEpochMs: Long,
        stopAtEpochMs: Long? = null,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (repeatType == RepeatType.NONE) return null

        val anchor = ZonedDateTime.ofInstant(Instant.ofEpochMilli(anchorEpochMs), zoneId)

        var step = 1
        var candidate = addInterval(anchor, repeatType, step)
        while (candidate.toInstant().toEpochMilli() <= afterEpochMs) {
            step++
            if (step > MAX_ITERATIONS) return null
            candidate = addInterval(anchor, repeatType, step)
        }

        val nextEpochMs = candidate.toInstant().toEpochMilli()
        if (stopAtEpochMs != null && nextEpochMs > stopAtEpochMs) return null
        return nextEpochMs
    }

    /** Computes the anchor's occurrence [count] intervals of [repeatType] ahead, directly from the anchor. */
    private fun addInterval(
        anchor: ZonedDateTime,
        repeatType: RepeatType,
        count: Int,
    ): ZonedDateTime =
        when (repeatType) {
            RepeatType.NONE -> anchor
            RepeatType.DAILY -> anchor.plusDays(count.toLong())
            RepeatType.WORK_WEEK_DAYS -> nthWorkDayAfter(anchor, count)
            RepeatType.BIWEEKLY -> anchor.plusWeeks(2L * count)
            RepeatType.MONTHLY -> anchor.plusMonths(count.toLong())
            RepeatType.YEARLY -> anchor.plusYears(count.toLong())
        }

    private fun nthWorkDayAfter(
        anchor: ZonedDateTime,
        count: Int,
    ): ZonedDateTime {
        var result = anchor
        var remaining = count
        while (remaining > 0) {
            result = result.plusDays(1)
            if (result.dayOfWeek != DayOfWeek.SATURDAY && result.dayOfWeek != DayOfWeek.SUNDAY) {
                remaining--
            }
        }
        return result
    }
}
