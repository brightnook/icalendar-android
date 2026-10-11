package com.brightnook.nookcalendar.widget

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.GradientDrawable
import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import com.brightnook.nookcalendar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetProviderResourcesTest {
    @Test
    fun providerRequestsTwoByThreeInitiallyWhileRetainingBothResizeDirections() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val parser = resources.getXml(R.xml.agenda_widget_info)
        parser.use {
            while (parser.eventType != XmlPullParser.START_TAG) parser.next()
            val attributes = resources.obtainAttributes(
                Xml.asAttributeSet(parser),
                intArrayOf(
                    android.R.attr.targetCellWidth,
                    android.R.attr.targetCellHeight,
                    android.R.attr.minWidth,
                    android.R.attr.minHeight,
                    android.R.attr.resizeMode,
                ),
            )
            attributes.use {
                assertEquals(2, attributes.getInt(0, -1))
                assertEquals(3, attributes.getInt(1, -1))
                assertTrue(parser.getAttributeValue(ANDROID_NAMESPACE, "minWidth") in setOf("110dp", "110.0dip"))
                assertTrue(parser.getAttributeValue(ANDROID_NAMESPACE, "minHeight") in setOf("180dp", "180.0dip"))
                val resizeMode = parser.getAttributeIntValue(ANDROID_NAMESPACE, "resizeMode", 0)
                assertTrue(resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0)
                assertTrue(resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0)
            }
        }
    }

    @Test
    fun outerWidgetBackgroundUsesTwelveDpCorners() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val background = resources.getDrawable(R.drawable.widget_background_rounded, null) as GradientDrawable

        assertEquals(12f * resources.displayMetrics.density, background.cornerRadius, 0.01f)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
