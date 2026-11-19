package com.goldrift.instadm.ui.components

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

// These map to platform haptic constants through HapticFeedbackConstantsCompat,
// which no-ops on API levels without the constant (the newer ones need API 30).
fun HapticFeedback.confirm() = performHapticFeedback(HapticFeedbackType.Confirm)
fun HapticFeedback.reject() = performHapticFeedback(HapticFeedbackType.Reject)
fun HapticFeedback.toggle(on: Boolean) =
    performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
fun HapticFeedback.thresholdActivated() = performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
fun HapticFeedback.segmentTick() = performHapticFeedback(HapticFeedbackType.SegmentTick)
fun HapticFeedback.segmentFrequentTick() = performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
fun HapticFeedback.longPress() = performHapticFeedback(HapticFeedbackType.LongPress)
fun HapticFeedback.contextClick() = performHapticFeedback(HapticFeedbackType.ContextClick)
