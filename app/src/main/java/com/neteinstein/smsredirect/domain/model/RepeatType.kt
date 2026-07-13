package com.neteinstein.smsredirect.domain.model

/**
 * How often a [ScheduledSms] should repeat after its initial send.
 * [NONE] means the message is sent exactly once, on its scheduled date.
 *
 * [MINUTELY], [QUARTER_HOURLY], [HALF_HOURLY] and [HOURLY] are very high-frequency options only
 * meant to be reachable through the hidden "Berserk mode" toggle on the create-schedule form.
 */
enum class RepeatType {
    NONE,
    MINUTELY,
    QUARTER_HOURLY,
    HALF_HOURLY,
    HOURLY,
    DAILY,
    WORK_WEEK_DAYS,
    BIWEEKLY,
    MONTHLY,
    YEARLY,
}
