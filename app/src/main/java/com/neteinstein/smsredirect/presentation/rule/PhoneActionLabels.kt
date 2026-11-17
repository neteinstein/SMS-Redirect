package com.neteinstein.smsredirect.presentation.rule

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.neteinstein.smsredirect.R
import com.neteinstein.smsredirect.domain.model.PhoneAction
import com.neteinstein.smsredirect.domain.model.SoundMode
import com.neteinstein.smsredirect.domain.model.VibrationPattern

/** The phone actions a rule can pick, in menu order. */
val PHONE_ACTION_KINDS = listOf(PhoneAction.KIND_RING, PhoneAction.KIND_VIBRATE, PhoneAction.KIND_SOUND_MODE)

@Composable
fun phoneActionKindLabel(kind: String): String =
    when (kind) {
        PhoneAction.KIND_VIBRATE -> stringResource(R.string.phone_action_vibrate)
        PhoneAction.KIND_SOUND_MODE -> stringResource(R.string.phone_action_sound_mode)
        else -> stringResource(R.string.phone_action_ring)
    }

@Composable
fun vibrationPatternLabel(pattern: VibrationPattern): String =
    when (pattern) {
        VibrationPattern.SHORT -> stringResource(R.string.vibration_pattern_short)
        VibrationPattern.LONG -> stringResource(R.string.vibration_pattern_long)
        VibrationPattern.DOUBLE -> stringResource(R.string.vibration_pattern_double)
        VibrationPattern.HEARTBEAT -> stringResource(R.string.vibration_pattern_heartbeat)
        VibrationPattern.SOS -> stringResource(R.string.vibration_pattern_sos)
        VibrationPattern.ALARM -> stringResource(R.string.vibration_pattern_alarm)
    }

@Composable
fun soundModeLabel(mode: SoundMode): String =
    when (mode) {
        SoundMode.RING -> stringResource(R.string.sound_mode_ring)
        SoundMode.VIBRATE -> stringResource(R.string.sound_mode_vibrate)
        SoundMode.SILENT -> stringResource(R.string.sound_mode_silent)
        SoundMode.TOGGLE_RING_SILENT -> stringResource(R.string.sound_mode_toggle)
    }

/** One line describing [action], e.g. "Vibrate · Heartbeat", for the rule list. */
@Composable
fun phoneActionSummary(action: PhoneAction): String {
    val detail =
        when (action) {
            is PhoneAction.Ring -> action.soundName.ifBlank { stringResource(R.string.rule_sound_default) }
            is PhoneAction.Vibrate -> vibrationPatternLabel(action.pattern)
            is PhoneAction.SetSoundMode -> soundModeLabel(action.mode)
        }
    return "${phoneActionKindLabel(action.kind)} · $detail"
}

/** The icon for a rule: its phone action's, or forward for a redirect. */
fun ruleIcon(action: PhoneAction?): ImageVector =
    when (action) {
        null -> Icons.AutoMirrored.Filled.Forward
        is PhoneAction.Ring -> Icons.Default.NotificationsActive
        is PhoneAction.Vibrate -> Icons.Default.Vibration
        is PhoneAction.SetSoundMode -> Icons.AutoMirrored.Filled.VolumeUp
    }
