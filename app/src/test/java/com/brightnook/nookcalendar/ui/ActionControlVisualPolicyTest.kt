package com.brightnook.nookcalendar.ui

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

    @Test
    fun labeledActionsUseACompactSixDpIconTextGap() {
        val presentation = transparentLabeledActionPresentation()
        val iconTextSpacing = presentation.javaClass.declaredFields
            .single { it.name == "iconTextSpacing" }
            .apply { isAccessible = true }
            .get(presentation)

        assertEquals(6f, iconTextSpacing as Float)
    }
}
