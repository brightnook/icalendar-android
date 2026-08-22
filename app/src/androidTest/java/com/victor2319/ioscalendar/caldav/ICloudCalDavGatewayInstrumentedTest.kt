package com.victor2319.ioscalendar.caldav

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.domain.TimeRange
import java.net.InetAddress
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ICloudCalDavGatewayInstrumentedTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun validRangeWithVTimeZoneDoesNotRunPlatformIncompatiblePreparse() = runTest {
        // Catches gateway validation invoking ical4j's Android-incompatible ZoneRulesProvider before parsing.
        server.enqueue(xmlResponse(principalResponse()))
        server.enqueue(xmlResponse(queryResponse()))
        val gateway = ICloudCalDavGateway.forTesting(OkHttpClient(), server.url("/"))
        val password = "synthetic-test-password".toCharArray()
        val principal = try {
            gateway.authenticate(Credentials("synthetic@example.test", password))
        } finally {
            password.fill('\u0000')
        }

        val snapshot = gateway.fetchRange(
            RemoteCalendar("calendar", "/calendar/", "Synthetic", null, null, principal.sessionId),
            TimeRange(Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")),
        )

        assertEquals(1, snapshot.resources.size)
    }

    private fun xmlResponse(body: String) = MockResponse()
        .setResponseCode(207)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(body)

    private fun principalResponse() = """
        <d:multistatus xmlns:d="DAV:"><d:response><d:href>/</d:href><d:propstat><d:prop>
        <d:current-user-principal><d:href>/principal/</d:href></d:current-user-principal>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>
    """.trimIndent()

    private fun queryResponse() = """
        <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:response>
        <d:href>/calendar/event.ics</d:href><d:propstat><d:prop><d:getetag>"etag"</d:getetag>
        <c:calendar-data>${xml(SAFE_VTIMEZONE_ICS)}</c:calendar-data>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>
    """.trimIndent()

    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private companion object {
        val SAFE_VTIMEZONE_ICS = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTIMEZONE
            TZID:Asia/Tokyo
            BEGIN:STANDARD
            DTSTART:19700101T000000
            TZOFFSETFROM:+0900
            TZOFFSETTO:+0900
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:synthetic@example.test
            DTSTAMP:20260801T000000Z
            DTSTART;TZID=Asia/Tokyo:20260810T090000
            DTEND;TZID=Asia/Tokyo:20260810T100000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
    }
}
