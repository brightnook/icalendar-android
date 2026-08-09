package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.domain.TimeRange
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ICloudCalDavGatewayTest {
    private lateinit var server: MockWebServer
    private val password = "abcd-efgh-ijkl-mnop".toCharArray()
    private val credentials = Credentials("owner@example.test", password)

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        password.fill('\u0000')
        server.close()
    }

    @Test
    fun authenticateSendsOnlyBoundedPropfindAndDoesNotMutateCredentials() = runTest {
        val originalPassword = password.copyOf()
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))

        val principal = gateway().authenticate(credentials)
        val request = server.takeRequest()

        assertEquals("/principals/users/owner/", principal.href)
        assertEquals("PROPFIND", request.method)
        assertEquals("0", request.getHeader("Depth"))
        assertEquals("application/xml; charset=utf-8", request.getHeader("Content-Type"))
        assertTrue(request.body.readUtf8().contains("current-user-principal"))
        assertNotNull(request.getHeader("Authorization"))
        assertArrayEquals(originalPassword, password)
        originalPassword.fill('\u0000')
    }

    @Test
    fun discoversHomeThenCalendarsUsingRelativeRfcReferences() = runTest {
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
        server.enqueue(xmlResponse(homeResponse("../../../calendars/owner/")))
        server.enqueue(xmlResponse(calendarListingResponse()))
        val gateway = gateway()

        val principal = gateway.authenticate(credentials)
        val calendars = gateway.discoverCalendars(principal)

        assertEquals(1, calendars.size)
        assertEquals("Work & Focus", calendars.single().displayName)
        assertEquals("#1a2b3c", calendars.single().color)
        assertEquals("token-1", calendars.single().syncToken)
        assertEquals("/calendars/owner/work/", calendars.single().href)
        assertEquals("/", server.takeRequest().path)
        val homeRequest = server.takeRequest()
        assertEquals("/principals/users/owner/", homeRequest.path)
        assertEquals("0", homeRequest.getHeader("Depth"))
        val listingRequest = server.takeRequest()
        assertEquals("/calendars/owner/", listingRequest.path)
        assertEquals("1", listingRequest.getHeader("Depth"))
        assertTrue(listingRequest.body.readUtf8().contains("calendar-color"))
    }

    @Test
    fun productionPolicyAcceptsOnlyHttpsIcloudHostsIncludingShards() {
        val policy = CalDavUrlPolicy.production()
        val base = HttpUrl.Builder().scheme("https").host("caldav.icloud.com").build()

        assertEquals(
            "p123-caldav.icloud.com",
            policy.resolve(base, "https://p123-caldav.icloud.com/123/calendars/").host,
        )
        assertThrows(CalDavFailure.InvalidServerData::class.java) {
            policy.resolve(base, "http://caldav.icloud.com/cleartext")
        }
        assertThrows(CalDavFailure.InvalidServerData::class.java) {
            policy.resolve(base, "https://icloud.com.evil.test/steal")
        }
    }

    @Test
    fun rejectsServerProvidedCrossOriginHomeBeforeSendingCredentials() = runTest {
        val attacker = MockWebServer()
        attacker.start()
        try {
            server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
            server.enqueue(xmlResponse(homeResponse(attacker.url("/steal").toString())))
            val gateway = gateway()
            val principal = gateway.authenticate(credentials)

            assertSuspendThrows<CalDavFailure.InvalidServerData> {
                gateway.discoverCalendars(principal)
            }

            assertNull(attacker.takeRequest(200, TimeUnit.MILLISECONDS))
        } finally {
            attacker.close()
        }
    }

    @Test
    fun doesNotFollowRedirectsOrForwardAuthorization() = runTest {
        val attacker = MockWebServer()
        attacker.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", attacker.url("/capture")),
            )

            assertSuspendThrows<CalDavFailure.InvalidServerData> {
                gateway().authenticate(credentials)
            }
            assertNull(attacker.takeRequest(200, TimeUnit.MILLISECONDS))
        } finally {
            attacker.close()
        }
    }

    @Test
    fun rejectsCrossOriginAbsoluteResourceHrefWithoutContactingThatOrigin() = runTest {
        val attacker = MockWebServer()
        attacker.start()
        try {
            server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
            server.enqueue(xmlResponse(queryResponse(attacker.url("/stolen.ics").toString(), VALID_ICS)))
            val gateway = gateway()
            gateway.authenticate(credentials)

            assertSuspendThrows<CalDavFailure.InvalidServerData> {
                gateway.fetchRange(
                    calendar(),
                    TimeRange(Instant.parse("2026-08-10T00:00:00Z"), Instant.parse("2026-08-11T00:00:00Z")),
                )
            }

            assertNull(attacker.takeRequest(200, TimeUnit.MILLISECONDS))
        } finally {
            attacker.close()
        }
    }

    @Test
    fun fetchRangeUsesUtcHalfOpenCalendarQueryAndReturnsValidatedResources() = runTest {
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
        server.enqueue(xmlResponse(queryResponse("event-1.ics", VALID_ICS)))
        val gateway = gateway()
        gateway.authenticate(credentials)

        val snapshot = gateway.fetchRange(
            calendar(),
            TimeRange(
                Instant.parse("2026-08-10T00:00:00Z"),
                Instant.parse("2026-08-11T00:00:00Z"),
            ),
        )

        server.takeRequest() // authenticate
        val report = server.takeRequest()
        assertEquals("1", report.getHeader("Depth"))
        assertEquals("application/xml; charset=utf-8", report.getHeader("Content-Type"))
        val body = report.body.readUtf8()
        assertTrue(body.contains("calendar-query"))
        assertTrue(body.contains("start=\"20260810T000000Z\""))
        assertTrue(body.contains("end=\"20260811T000000Z\""))
        assertEquals("\"etag-1\"", snapshot.resources.single().etag)
        assertEquals(VALID_ICS.replace("\r\n", "\n").trimEnd(), snapshot.resources.single().calendarData)
    }

    @Test
    fun deltaProducesTombstonesAndMultigetsLiveHrefsInBoundedChunks() = runTest {
        val hrefs = (1..51).map { "/calendars/owner/work/e$it.ics" }
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
        server.enqueue(xmlResponse(syncResponse(hrefs, "/calendars/owner/work/deleted.ics")))
        server.enqueue(xmlResponse(multigetResponse(hrefs.take(50))))
        server.enqueue(xmlResponse(multigetResponse(hrefs.drop(50))))
        val gateway = gateway()
        gateway.authenticate(credentials)

        val delta = gateway.fetchDelta(calendar(), "old-token")

        assertEquals(51, delta.upserts.size)
        assertEquals(listOf("/calendars/owner/work/deleted.ics"), delta.tombstones.map { it.href })
        assertEquals("token-next", delta.syncToken)
        server.takeRequest() // authenticate
        val sync = server.takeRequest()
        assertTrue(sync.body.readUtf8().contains("<d:sync-token>old-token</d:sync-token>"))
        val first = server.takeRequest().body.readUtf8()
        val second = server.takeRequest().body.readUtf8()
        assertEquals(50, "<d:href>".toRegex().findAll(first).count())
        assertEquals(1, "<d:href>".toRegex().findAll(second).count())
    }

    @Test
    fun hrefsAreXmlEscapedInMultigetBodies() = runTest {
        val href = "/calendars/owner/work/a&b.ics"
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
        server.enqueue(xmlResponse(syncResponse(listOf(href), null)))
        server.enqueue(xmlResponse(multigetResponse(listOf("/calendars/owner/work/a&b.ics"))))
        val gateway = gateway()
        gateway.authenticate(credentials)

        gateway.fetchDelta(calendar(), "token&amp;old")

        server.takeRequest()
        val sync = server.takeRequest().body.readUtf8()
        val multiget = server.takeRequest().body.readUtf8()
        assertTrue(sync.contains("token&amp;amp;old"))
        assertTrue(multiget.contains("a&amp;b.ics"))
        assertFalse(multiget.contains("a&b.ics"))
    }

    @Test
    fun statusCodesMapWithoutIncludingResponseBodies() {
        listOf(
            401 to CalDavFailure.AuthenticationRejected::class.java,
            403 to CalDavFailure.AuthenticationRejected::class.java,
            408 to CalDavFailure.Temporary::class.java,
            429 to CalDavFailure.Temporary::class.java,
            503 to CalDavFailure.Temporary::class.java,
            418 to CalDavFailure.InvalidServerData::class.java,
        ).forEach { (code, type) ->
            server.enqueue(
                MockResponse().setResponseCode(code)
                    .addHeader("Retry-After", "120")
                    .setBody("secret response body"),
            )
            val error = assertThrows(type) { runTest { gateway().authenticate(credentials) } }
            assertFalse(error.toString().contains("secret response body"))
        }
    }

    @Test
    fun offlineIoMapsToNetworkUnavailableWhileCancellationIsRethrown() {
        val offlineClient = OkHttpClient.Builder().addInterceptor { throw IOException("synthetic offline") }.build()
        val offline = gateway(offlineClient)
        assertThrows(CalDavFailure.NetworkUnavailable::class.java) {
            runTest { offline.authenticate(credentials) }
        }

        val cancellingClient = OkHttpClient.Builder().addInterceptor { throw CancellationException("cancel") }.build()
        assertThrows(CancellationException::class.java) {
            runTest { gateway(cancellingClient).authenticate(credentials) }
        }
    }

    @Test
    fun rejectsMalformedDtdAndOversizedXmlAsInvalidServerData() {
        val payloads = listOf(
            "<d:multistatus xmlns:d=\"DAV:\"><broken>",
            "<!DOCTYPE x [<!ENTITY leak SYSTEM \"file:///etc/passwd\">]><x>&leak;</x>",
            "x".repeat(1_048_577),
        )
        payloads.forEach { body ->
            server.enqueue(xmlResponse(body))
            assertThrows(CalDavFailure.InvalidServerData::class.java) {
                runTest { gateway().authenticate(credentials) }
            }
        }
    }

    @Test
    fun rejectsMoreThanOneThousandDavResponses() {
        val responses = (1..1_001).map { index ->
            response(
                "/principals/users/$index/",
                "<d:current-user-principal><d:href>/principals/users/$index/</d:href></d:current-user-principal>",
            )
        }.toTypedArray()
        server.enqueue(xmlResponse(multistatus(*responses)))

        assertThrows(CalDavFailure.InvalidServerData::class.java) {
            runTest { gateway().authenticate(credentials) }
        }
    }

    @Test
    fun rejectsMalformedAndOversizedCalendarData() = runTest {
        val invalid = listOf(
            "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n",
            "x".repeat(524_289),
        )
        invalid.forEach { ics ->
            server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
            server.enqueue(xmlResponse(queryResponse("bad.ics", ics)))
            val gateway = gateway()
            gateway.authenticate(credentials)
            assertSuspendThrows<CalDavFailure.InvalidServerData> {
                gateway.fetchRange(
                    calendar(),
                    TimeRange(Instant.parse("2026-08-10T00:00:00Z"), Instant.parse("2026-08-11T00:00:00Z")),
                )
            }
        }
    }

    @Test
    fun installedSensitiveLoggerNeverEmitsCredentialsHeadersPathsQueriesOrBodies() = runTest {
        val logs = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor(SensitiveLoggingInterceptor(logs::add))
            .build()
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("response-secret"),
        )

        assertSuspendThrows<CalDavFailure.AuthenticationRejected> {
            gateway(client).authenticate(credentials)
        }

        val rendered = logs.joinToString("\n")
        assertTrue(rendered.contains("PROPFIND"))
        assertTrue(rendered.contains("http://localhost"))
        listOf(
            credentials.appleId,
            "abcd-efgh-ijkl-mnop",
            "Authorization",
            "Basic",
            "response-secret",
            server.url("/").port.toString(),
            "?",
        ).forEach { secret -> assertFalse(rendered.contains(secret), "log contained forbidden metadata") }
    }

    @Test
    fun publicGatewaySurfaceExposesReadOperationsOnly() {
        assertEquals(
            setOf("authenticate", "discoverCalendars", "fetchRange", "fetchDelta"),
            CalDavGateway::class.java.declaredMethods.map { it.name.substringBefore('-') }.toSet(),
        )
    }

    @Test
    fun remoteResourceToStringNeverIncludesCalendarResponseBody() {
        val rendered = RemoteResource(
            href = "/calendars/owner/work/private.ics",
            etag = "etag-private",
            calendarData = "private calendar response body",
        ).toString()

        assertFalse(rendered.contains("private calendar response body"))
        assertTrue(rendered.contains("<redacted>"))
    }

    private fun gateway(client: OkHttpClient = OkHttpClient()): ICloudCalDavGateway =
        ICloudCalDavGateway.forTesting(client, server.url("/"))

    private fun calendar() = RemoteCalendar(
        id = "work",
        href = "/calendars/owner/work/",
        displayName = "Work",
        color = "#1a2b3c",
        syncToken = "token-1",
    )

    private fun xmlResponse(body: String) = MockResponse()
        .setResponseCode(207)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(body)

    private fun principalResponse(href: String) = multistatus(
        response(
            "/",
            "<d:current-user-principal><d:href>${xml(href)}</d:href></d:current-user-principal>",
        ),
    )

    private fun homeResponse(href: String) = multistatus(
        response(
            "/principals/users/owner/",
            "<c:calendar-home-set><d:href>${xml(href)}</d:href></c:calendar-home-set>",
        ),
    )

    private fun calendarListingResponse() = multistatus(
        response("/calendars/owner/", "<d:resourcetype><d:collection/></d:resourcetype>"),
        response(
            "/calendars/owner/work/",
            """
            <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
            <d:displayname>Work &amp; Focus</d:displayname>
            <x:calendar-color>#1a2b3c</x:calendar-color>
            <d:sync-token>token-1</d:sync-token>
            """.trimIndent(),
        ),
    )

    private fun queryResponse(href: String, ics: String) = multistatus(
        response(
            if (href.startsWith("http://") || href.startsWith("https://")) href else "/calendars/owner/work/$href",
            "<d:getetag>&quot;etag-1&quot;</d:getetag><c:calendar-data>${xml(ics)}</c:calendar-data>",
        ),
    )

    private fun syncResponse(hrefs: List<String>, deleted: String?) = multistatus(
        *(hrefs.map { response(it, "<d:getetag>&quot;changed&quot;</d:getetag>") } +
            listOfNotNull(deleted?.let { statusResponse(it, 404) })).toTypedArray(),
        suffix = "<d:sync-token>token-next</d:sync-token>",
    )

    private fun multigetResponse(hrefs: List<String>) = multistatus(
        *hrefs.mapIndexed { index, href ->
            response(
                href,
                "<d:getetag>&quot;e$index&quot;</d:getetag><c:calendar-data>${xml(VALID_ICS)}</c:calendar-data>",
            )
        }.toTypedArray(),
    )

    private fun response(href: String, props: String) = """
        <d:response>
          <d:href>${xml(href)}</d:href>
          <d:propstat><d:prop>$props</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
        </d:response>
    """.trimIndent()

    private fun statusResponse(href: String, status: Int) = """
        <d:response><d:href>${xml(href)}</d:href><d:status>HTTP/1.1 $status Gone</d:status></d:response>
    """.trimIndent()

    private fun multistatus(vararg responses: String, suffix: String = "") = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:x="http://apple.com/ns/ical/">
          ${responses.joinToString("\n")}
          $suffix
        </d:multistatus>
    """.trimIndent()

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit,
    ): T {
        try {
            block()
        } catch (failure: Throwable) {
            if (failure is T) return failure
            throw AssertionError("Expected ${T::class.java.name}, got ${failure.javaClass.name}", failure)
        }
        throw AssertionError("Expected ${T::class.java.name} to be thrown")
    }

    companion object {
        private val VALID_ICS = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Synthetic Gateway Test//EN
            BEGIN:VEVENT
            UID:gateway-event@example.test
            DTSTAMP:20260801T000000Z
            DTSTART:20260810T090000Z
            DTEND:20260810T100000Z
            SUMMARY:Gateway event
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
    }
}
