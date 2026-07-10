package com.neteinstein.smsredirect.domain.model

/**
 * How often a [ScheduledSms] should repeat after its initial send.
 * [NONE] means the message is sent exactly once, on its scheduled date.
 */
enum class RepeatType {
    NONE,
    DAILY,
    WORK_WEEK_DAYS,
    BIWEEKLY,
    MONTHLY,
    YEARLY,
}
