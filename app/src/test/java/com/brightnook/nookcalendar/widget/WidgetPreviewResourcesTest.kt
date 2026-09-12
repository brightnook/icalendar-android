package com.brightnook.nookcalendar.widget

import android.util.Xml
import android.view.LayoutInflater
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.brightnook.nookcalendar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetPreviewResourcesTest {
    @Test
    fun pickerPreviewDeclaresStaticHeaderAndTwoEventTitles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val parser = context.resources.getXml(R.xml.agenda_widget_info)
        parser.use {
            while (parser.eventType != XmlPullParser.START_TAG) parser.next()
            assertEquals(
                R.layout.agenda_widget_preview,
                parser.getAttributeResourceValue(ANDROID_NAMESPACE, "previewLayout", 0),
            )
        }

        val preview = LayoutInflater.from(context).inflate(R.layout.agenda_widget_preview, null)
        assertNotNull(preview.findViewById<TextView>(R.id.widget_preview_header))
        assertNotNull(preview.findViewById<TextView>(R.id.widget_preview_event_title_one))
        assertNotNull(preview.findViewById<TextView>(R.id.widget_preview_event_title_two))
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
