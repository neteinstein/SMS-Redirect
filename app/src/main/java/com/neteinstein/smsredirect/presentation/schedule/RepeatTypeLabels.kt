package com.neteinstein.smsredirect.presentation.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.RepeatType

/** Shared human-readable label for a [RepeatType], used by both the list and the form screens. */
@Composable
fun repeatTypeLabel(repeatType: RepeatType): String =
    when (repeatType) {
        RepeatType.NONE -> stringResource(R.string.repeat_type_none)
        RepeatType.MINUTELY -> stringResource(R.string.repeat_type_minutely)
        RepeatType.QUARTER_HOURLY -> stringResource(R.string.repeat_type_quarter_hourly)
        RepeatType.HALF_HOURLY -> stringResource(R.string.repeat_type_half_hourly)
        RepeatType.HOURLY -> stringResource(R.string.repeat_type_hourly)
        RepeatType.DAILY -> stringResource(R.string.repeat_type_daily)
        RepeatType.WORK_WEEK_DAYS -> stringResource(R.string.repeat_type_work_week_days)
        RepeatType.BIWEEKLY -> stringResource(R.string.repeat_type_biweekly)
        RepeatType.MONTHLY -> stringResource(R.string.repeat_type_monthly)
        RepeatType.YEARLY -> stringResource(R.string.repeat_type_yearly)
    }

/**
 * Repeat frequencies fast enough that they are only meant to be reachable through the hidden
 * "Berserk mode" toggle on the create-schedule form, not through everyday use.
 */
val BERSERK_ONLY_REPEAT_TYPES: Set<RepeatType> =
    setOf(RepeatType.MINUTELY, RepeatType.QUARTER_HOURLY, RepeatType.HALF_HOURLY, RepeatType.HOURLY)
