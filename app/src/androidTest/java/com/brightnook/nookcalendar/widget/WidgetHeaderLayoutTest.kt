package com.brightnook.nookcalendar.widget

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.ImageButton
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brightnook.nookcalendar.R
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetHeaderLayoutTest {
    @Test
    fun widgetHeaderInflatesThroughRemoteViews() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val inflated = RemoteViews(context.packageName, R.layout.agenda_widget)
            .apply(context, FrameLayout(context))

        assertNotNull(inflated.findViewById<TextView>(R.id.widget_title))
        assertNotNull(inflated.findViewById<TextView>(R.id.widget_header_date))
        assertNotNull(inflated.findViewById<TextView>(R.id.widget_add_event))
        assertNotNull(inflated.findViewById<ImageButton>(R.id.widget_refresh))
    }

    @Test
    fun twoColumnHeaderKeepsAddAndRefreshSeparateFromTitle() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val density = context.resources.displayMetrics.density
        val widget = LayoutInflater.from(context).inflate(R.layout.agenda_widget, null)
        val title = widget.findViewById<TextView>(R.id.widget_title)
        val date = widget.findViewById<TextView>(R.id.widget_header_date)
        val add = widget.findViewById<TextView>(R.id.widget_add_event)
        val refresh = widget.findViewById<ImageButton>(R.id.widget_refresh)
        title.text = "Agenda"
        date.text = "Aug 24"
        date.visibility = View.VISIBLE

        val width = (184 * density).toInt()
        widget.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((220 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        widget.layout(0, 0, width, (220 * density).toInt())

        assertTrue("Date must be displayed in two-column widgets", date.visibility == View.VISIBLE)
        assertTrue("Date must be below title", title.bottom <= date.top)
        assertTrue("Title must not cover add", title.right <= add.left)
        assertTrue("Date must not cover add", date.right <= add.left)
        assertTrue("Add and refresh must have separate hit areas", add.right <= refresh.left)
    }
}
