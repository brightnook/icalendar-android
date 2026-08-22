package com.victor2319.ioscalendar.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class LabeledActionPresentation(
    val usesTransparentContainer: Boolean,
    val minimumTouchTarget: Dp,
)

internal fun transparentLabeledActionPresentation() = LabeledActionPresentation(
    usesTransparentContainer = true,
    minimumTouchTarget = 48.dp,
)
