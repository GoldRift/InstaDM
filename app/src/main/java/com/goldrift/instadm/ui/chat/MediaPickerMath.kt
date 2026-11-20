package com.goldrift.instadm.ui.chat

internal data class MediaPickerDragState(
    val sheetExpanded: Boolean = false,
    val dragOffsetY: Float = 0f,
    val upwardDragPx: Float = 0f,
    val armedDismiss: Boolean = false,
)

internal data class MediaPickerDragResult(
    val state: MediaPickerDragState,
    val expandedNow: Boolean = false,
    val armedDismissNow: Boolean = false,
)

internal fun applyMediaPickerHeaderDrag(
    current: MediaPickerDragState,
    dragDeltaY: Float,
    expandThresholdPx: Float,
    dismissThresholdPx: Float,
): MediaPickerDragResult {
    var nextDragOffsetY = current.dragOffsetY
    var nextUpwardDragPx = current.upwardDragPx

    if (dragDeltaY < 0f) {
        val upward = -dragDeltaY
        if (nextDragOffsetY > 0f) {
            val absorbed = minOf(nextDragOffsetY, upward)
            nextDragOffsetY -= absorbed
            val leftover = upward - absorbed
            if (!current.sheetExpanded && leftover > 0f) {
                nextUpwardDragPx += leftover
            }
        } else if (!current.sheetExpanded) {
            nextUpwardDragPx += upward
        }
    } else if (dragDeltaY > 0f) {
        if (nextUpwardDragPx > 0f) {
            val absorbed = minOf(nextUpwardDragPx, dragDeltaY)
            nextUpwardDragPx -= absorbed
            nextDragOffsetY += dragDeltaY - absorbed
        } else {
            nextDragOffsetY += dragDeltaY
        }
    }

    if (!current.sheetExpanded && expandThresholdPx > 0f && nextUpwardDragPx > expandThresholdPx) {
        return MediaPickerDragResult(
            state = MediaPickerDragState(
                sheetExpanded = true,
                dragOffsetY = 0f,
                upwardDragPx = 0f,
                armedDismiss = false,
            ),
            expandedNow = true,
        )
    }

    val shouldArmDismiss =
        !current.sheetExpanded &&
            !current.armedDismiss &&
            dismissThresholdPx > 0f &&
            nextDragOffsetY >= dismissThresholdPx

    return MediaPickerDragResult(
        state = MediaPickerDragState(
            sheetExpanded = current.sheetExpanded,
            dragOffsetY = nextDragOffsetY.coerceAtLeast(0f),
            upwardDragPx = nextUpwardDragPx,
            armedDismiss = current.armedDismiss || shouldArmDismiss,
        ),
        armedDismissNow = shouldArmDismiss,
    )
}
