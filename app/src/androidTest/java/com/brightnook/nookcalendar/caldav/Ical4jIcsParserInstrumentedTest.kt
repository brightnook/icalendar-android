package com.brightnook.nookcalendar.caldav

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brightnook.nookcalendar.domain.TimeRange
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Ical4jIcsParserInstrumentedTest {
    @Test
    fun validCalendarWithVTimeZoneParsesWithoutPlatformZoneProviderLinkage() {
        // Catches Android rejecting ical4j's java.time ZoneRulesProvider constructor before a valid range can sync.
        val parsed = Ical4jIcsParser().parse(
            SAFE_VTIMEZONE_ICS,
            TimeRange(
                start = Instant.parse("2026-08-01T00:00:00Z"),
                endExclusive = Instant.parse("2026-09-01T00:00:00Z"),
                zoneId = ZoneId.of("Asia/Tokyo"),
            ),
            ZoneId.of("Asia/Tokyo"),
        )

        assertEquals(1, parsed.events.size)
        assertEquals(1, parsed.instances.size)
    }

    private companion object {
        val SAFE_VTIMEZONE_ICS = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Synthetic Android Compatibility Test//EN
            BEGIN:VTIMEZONE
            TZID:Asia/Tokyo
            BEGIN:STANDARD
            DTSTART:19700101T000000
            TZOFFSETFROM:+0900
            TZOFFSETTO:+0900
            TZNAME:JST
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:android-zone-provider@example.test
            DTSTAMP:20260801T000000Z
            DTSTART;TZID=Asia/Tokyo:20260810T090000
            DTEND;TZID=Asia/Tokyo:20260810T100000
            SUMMARY:Compatibility test
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
    }
}
