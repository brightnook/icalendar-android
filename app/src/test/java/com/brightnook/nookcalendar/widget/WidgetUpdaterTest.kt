package com.brightnook.nookcalendar.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetUpdaterTest {
    @Test
    fun refreshAllUpdatesAndInvalidatesEveryActiveWidget() {
        val gateway = RecordingWidgetUpdateGateway(activeIds = intArrayOf(41, 42))

        WidgetUpdater(gateway).refreshAll()

        assertEquals(listOf(intArrayOf(41, 42).asList()), gateway.updatedIds)
        assertEquals(listOf(intArrayOf(41, 42).asList()), gateway.invalidatedIds)
    }

    @Test
    fun refreshAllSkipsUpdateRequestsWhenThereAreNoActiveWidgets() {
        val gateway = RecordingWidgetUpdateGateway(activeIds = intArrayOf())

        WidgetUpdater(gateway).refreshAll()

        assertEquals(emptyList<List<Int>>(), gateway.updatedIds)
        assertEquals(emptyList<List<Int>>(), gateway.invalidatedIds)
    }
}

private class RecordingWidgetUpdateGateway(
    private val activeIds: IntArray,
) : WidgetUpdateGateway {
    val updatedIds = mutableListOf<List<Int>>()
    val invalidatedIds = mutableListOf<List<Int>>()

    override fun activeWidgetIds(): IntArray = activeIds

    override fun updateAppWidgets(appWidgetIds: IntArray) {
        updatedIds += appWidgetIds.asList()
    }

    override fun notifyEventsChanged(appWidgetIds: IntArray) {
        invalidatedIds += appWidgetIds.asList()
    }
}
