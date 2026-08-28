package com.brightnook.nookcalendar.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRowPresentationTest {
    @Test
    fun timedEventKeepsTitleOnTheFirstLineAndMovesTimeToMetadata() {
        val presentation = widgetRowPresentation(
            title = "团队例会",
            timeText = "09:30",
            calendarName = "工作",
        )

        assertEquals("团队例会", presentation.title)
        assertEquals("09:30 · 工作", presentation.metadata)
    }

    @Test
    fun allDayEventUsesAllDayTextInTheSecondLine() {
        val presentation = widgetRowPresentation(
            title = "生日",
            timeText = "全天",
            calendarName = "家庭",
        )

        assertEquals("全天 · 家庭", presentation.metadata)
    }
}
