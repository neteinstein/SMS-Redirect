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
        RepeatType.DAILY -> stringResource(R.string.repeat_type_daily)
        RepeatType.WORK_WEEK_DAYS -> stringResource(R.string.repeat_type_work_week_days)
        RepeatType.BIWEEKLY -> stringResource(R.string.repeat_type_biweekly)
        RepeatType.MONTHLY -> stringResource(R.string.repeat_type_monthly)
        RepeatType.YEARLY -> stringResource(R.string.repeat_type_yearly)
    }
