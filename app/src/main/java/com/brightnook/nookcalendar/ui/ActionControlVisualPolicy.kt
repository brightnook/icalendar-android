package com.brightnook.nookcalendar.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class LabeledActionPresentation(
    val usesTransparentContainer: Boolean,
    val minimumTouchTarget: Dp,
    val iconTextSpacing: Dp,
)

internal fun transparentLabeledActionPresentation() = LabeledActionPresentation(
    usesTransparentContainer = true,
    minimumTouchTarget = 48.dp,
    iconTextSpacing = 6.dp,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrimaryControlRippleScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
}
