package com.victor2319.ioscalendar.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionControlVisualPolicyTest {
    @Test
    fun labeledAccountActionsUseTransparentIconPresentationWithAccessibleTargets() {
        val presentation = transparentLabeledActionPresentation()

        assertTrue(presentation.usesTransparentContainer)
        assertEquals(48.dp, presentation.minimumTouchTarget)
    }
}
