package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.domain.TimeRange
import java.io.IOException
import java.time.Instant
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
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
            val principal = gateway.authenticate(credentials)

            assertSuspendThrows<CalDavFailure.InvalidServerData> {
                gateway.fetchRange(
                    calendar(principal.sessionId),
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
        val principal = gateway.authenticate(credentials)

        val snapshot = gateway.fetchRange(
            calendar(principal.sessionId),
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
    fun fetchRangeAcceptsExactlyFourHundredDays() = runTest {
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
        server.enqueue(xmlResponse(multistatus()))
        val gateway = gateway()
        val principal = gateway.authenticate(credentials)
        val start = Instant.parse("2026-01-01T00:00:00Z")

        gateway.fetchRange(
            calendar(principal.sessionId),
            TimeRange(start, start.plus(Duration.ofDays(400))),
        )

        assertEquals(2, server.requestCount)
    }

    @Test
    fun fetchRangeRejectsMoreThanFourHundredDaysWithoutReport() = runTest {
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
        server.enqueue(xmlResponse(multistatus()))
        val gateway = gateway()
        val principal = gateway.authenticate(credentials)
        val requestsBefore = server.requestCount
        val start = Instant.parse("2026-01-01T00:00:00Z")

        assertSuspendThrows<CalDavFailure.InvalidServerData> {
            gateway.fetchRange(
                calendar(principal.sessionId),
                TimeRange(start, start.plus(Duration.ofDays(400)).plusSeconds(1)),
            )
        }

        assertEquals(requestsBefore, server.requestCount)
    }

    @Test
    fun fetchRangeRejectsUtcYearsOutsideFourDigitBasicFormatWithoutReport() = runTest {
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
        server.enqueue(xmlResponse(multistatus()))
        val gateway = gateway()
        val principal = gateway.authenticate(credentials)
        val requestsBefore = server.requestCount

        assertSuspendThrows<CalDavFailure.InvalidServerData> {
            gateway.fetchRange(
                calendar(principal.sessionId),
                TimeRange(
                    Instant.parse("+10000-01-01T00:00:00Z"),
                    Instant.parse("+10000-01-02T00:00:00Z"),
                ),
            )
        }

        assertEquals(requestsBefore, server.requestCount)
    }

    @Test
    fun deltaProducesTombstonesAndMultigetsLiveHrefsInBoundedChunks() = runTest {
        val hrefs = (1..51).map { "/calendars/owner/work/e$it.ics" }
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
        server.enqueue(xmlResponse(syncResponse(hrefs, "/calendars/owner/work/deleted.ics")))
        server.enqueue(xmlResponse(multigetResponse(hrefs.take(50))))
        server.enqueue(xmlResponse(multigetResponse(hrefs.drop(50))))
        val gateway = gateway()
        val principal = gateway.authenticate(credentials)

        val delta = gateway.fetchDelta(calendar(principal.sessionId), "old-token")

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
        val principal = gateway.authenticate(credentials)

        gateway.fetchDelta(calendar(principal.sessionId), "token&amp;old")

        server.takeRequest()
        val sync = server.takeRequest().body.readUtf8()
        val multiget = server.takeRequest().body.readUtf8()
        assertTrue(sync.contains("token&amp;amp;old"))
        assertTrue(multiget.contains("a&amp;b.ics"))
        assertFalse(multiget.contains("a&b.ics"))
    }

    @Test
    fun deltaRejectsMultigetWithMissingRequestedHref() = runTest {
        val requested = listOf("/calendars/owner/work/a.ics", "/calendars/owner/work/b.ics")
        val (gateway, calendar) = authenticatedGatewayForDelta(requested)
        server.enqueue(xmlResponse(multigetResponse(requested.take(1))))

        assertSuspendThrows<CalDavFailure.InvalidServerData> {
            gateway.fetchDelta(calendar, "old-token")
        }
    }

    @Test
    fun deltaRejectsMultigetWithDuplicateRequestedHref() = runTest {
        val requested = listOf("/calendars/owner/work/a.ics")
        val (gateway, calendar) = authenticatedGatewayForDelta(requested)
        server.enqueue(xmlResponse(multistatus(
            resourceResponse(requested.single()),
            resourceResponse(requested.single()),
        )))

        assertSuspendThrows<CalDavFailure.InvalidServerData> {
            gateway.fetchDelta(calendar, "old-token")
        }
    }

    @Test
    fun deltaRejectsMultigetWithUnrequestedExtraHref() = runTest {
        val requested = listOf("/calendars/owner/work/a.ics")
        val (gateway, calendar) = authenticatedGatewayForDelta(requested)
        server.enqueue(xmlResponse(multistatus(
            resourceResponse(requested.single()),
            resourceResponse("/calendars/owner/work/extra.ics"),
        )))

        assertSuspendThrows<CalDavFailure.InvalidServerData> {
            gateway.fetchDelta(calendar, "old-token")
        }
    }

    @Test
    fun deltaTurnsMultigetDeleteRaceIntoTombstone() = runTest {
        val requested = listOf("/calendars/owner/work/deleted-during-fetch.ics")
        val (gateway, calendar) = authenticatedGatewayForDelta(requested)
        server.enqueue(xmlResponse(multistatus(statusResponse(requested.single(), 404))))

        val delta = gateway.fetchDelta(calendar, "old-token")

        assertTrue(delta.upserts.isEmpty())
        assertEquals(requested, delta.tombstones.map(RemoteTombstone::href))
        assertEquals("token-next", delta.syncToken)
    }

    @Test
    fun deltaRejectsSuccessfulSyncCollectionWithoutNewToken() = runTest {
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
        server.enqueue(xmlResponse(multistatus()))
        val gateway = gateway()
        val principal = gateway.authenticate(credentials)

        assertSuspendThrows<CalDavFailure.InvalidServerData> {
            gateway.fetchDelta(calendar(principal.sessionId), "old-token")
        }
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
    fun cancellingCoroutineCancelsRealInFlightOkHttpCallPromptly() = runTest {
        val failed = CountDownLatch(1)
        val observedCall = AtomicReference<Call>()
        val client = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    observedCall.set(call)
                    failed.countDown()
                }
            })
            .build()
        val gateway = gateway(client)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val authentication = async(Dispatchers.Default) {
            gateway.authenticate(credentials)
        }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })

        authentication.cancel()
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { authentication.join() }
        }

        assertTrue(authentication.isCancelled)
        assertTrue(await(failed))
        assertTrue(observedCall.get().isCanceled())

        server.enqueue(xmlResponse(principalResponse("/principal/")))
        val principal = withContext(Dispatchers.Default) {
            withTimeout(2_000) { gateway.authenticate(credentials) }
        }
        assertEquals("/principal/", principal.href)
    }

    @Test
    fun networkUnavailableDropsSensitiveIOExceptionCauseChain() {
        val secrets = listOf(
            "leaked-owner@example.test",
            "leaked-app-password",
            "Basic dG90YWxseS1mYWtl",
        )
        val logs = mutableListOf<String>()
        val rawFailure = IOException("offline for ${secrets[0]}").apply {
            initCause(IOException("${secrets[1]} ${secrets[2]}"))
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(SensitiveLoggingInterceptor(logs::add))
            .addInterceptor { throw rawFailure }
            .build()

        val failure = assertThrows(CalDavFailure.NetworkUnavailable::class.java) {
            runTest { gateway(client).authenticate(credentials) }
        }
        val rendered = buildString {
            append(failure.toString())
            append(failure.stackTraceToString())
            generateSequence(failure.cause) { it.cause }.forEach { append(it.toString()) }
            append(logs.joinToString("\n"))
        }

        secrets.forEach { secret ->
            assertFalse(rendered.contains(secret), "sensitive failure data leaked")
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
    fun rejectsWrongRootAndNamespaceImpostors() {
        val payloads = listOf(
            """
                <e:multistatus xmlns:e="urn:evil">
                  <e:response><e:href>/</e:href><e:propstat><e:prop>
                    <e:current-user-principal><e:href>/principals/evil/</e:href></e:current-user-principal>
                  </e:prop><e:status>HTTP/1.1 200 OK</e:status></e:propstat></e:response>
                </e:multistatus>
            """.trimIndent(),
            """
                <d:multistatus xmlns:d="DAV:" xmlns:e="urn:evil">
                  <d:response><d:href>/</d:href><d:propstat><d:prop>
                    <e:current-user-principal><d:href>/principals/evil/</d:href></e:current-user-principal>
                  </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                </d:multistatus>
            """.trimIndent(),
            """
                <e:wrapper xmlns:e="urn:evil" xmlns:d="DAV:">
                  <d:multistatus><d:response><d:href>/</d:href><d:propstat><d:prop>
                    <d:current-user-principal><d:href>/principals/evil/</d:href></d:current-user-principal>
                  </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>
                </e:wrapper>
            """.trimIndent(),
            """
                <d:multistatus xmlns:d="DAV:" xmlns:e="urn:evil">
                  <e:wrapper><d:response><d:href>/</d:href><d:propstat><d:prop>
                    <d:current-user-principal><d:href>/principals/evil/</d:href></d:current-user-principal>
                  </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></e:wrapper>
                </d:multistatus>
            """.trimIndent(),
        )

        payloads.forEach { payload ->
            server.enqueue(xmlResponse(payload))
            assertThrows(CalDavFailure.InvalidServerData::class.java) {
                runTest { gateway().authenticate(credentials) }
            }
        }
    }

    @Test
    fun rejectsResponseThatMixesStatusAndPropstatInEitherOrder() {
        val propstat = """
            <d:propstat><d:prop><d:getetag>&quot;live&quot;</d:getetag></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
        """.trimIndent()
        listOf(
            "<d:status>HTTP/1.1 404 Not Found</d:status>$propstat",
            "$propstat<d:status>HTTP/1.1 404 Not Found</d:status>",
        ).forEach { controls ->
            assertInvalidMultistatus(
                "<d:response><d:href>/calendar/event.ics</d:href>$controls</d:response>",
            )
        }
    }

    @Test
    fun rejectsRepeatedPropstatStatusAndPropControls() {
        val repeatedStatus = """
            <d:response><d:href>/calendar/event.ics</d:href><d:propstat>
              <d:prop><d:getetag>&quot;live&quot;</d:getetag></d:prop>
              <d:status>HTTP/1.1 404 Not Found</d:status>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat></d:response>
        """.trimIndent()
        val repeatedProp = """
            <d:response><d:href>/calendar/event.ics</d:href><d:propstat>
              <d:prop><d:getetag>&quot;first&quot;</d:getetag></d:prop>
              <d:prop><d:getetag>&quot;second&quot;</d:getetag></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat></d:response>
        """.trimIndent()

        assertInvalidMultistatus(repeatedStatus)
        assertInvalidMultistatus(repeatedProp)
    }

    @Test
    fun rejectsRepeatedResponseHrefAndStatusControls() {
        val repeatedHref = """
            <d:response>
              <d:href>/calendar/first.ics</d:href><d:href>/calendar/second.ics</d:href>
              <d:status>HTTP/1.1 404 Not Found</d:status>
            </d:response>
        """.trimIndent()
        val repeatedStatus = """
            <d:response><d:href>/calendar/event.ics</d:href>
              <d:status>HTTP/1.1 404 Not Found</d:status>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:response>
        """.trimIndent()

        assertInvalidMultistatus(repeatedHref)
        assertInvalidMultistatus(repeatedStatus)
    }

    @Test
    fun rejectsResponseAndPropstatMissingRequiredControls() {
        listOf(
            "<d:response><d:href>/calendar/event.ics</d:href></d:response>",
            """
                <d:response><d:href>/calendar/event.ics</d:href><d:propstat>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat></d:response>
            """.trimIndent(),
            """
                <d:response><d:href>/calendar/event.ics</d:href><d:propstat>
                  <d:prop><d:getetag>&quot;live&quot;</d:getetag></d:prop>
                </d:propstat></d:response>
            """.trimIndent(),
        ).forEach(::assertInvalidMultistatus)
    }

    @Test
    fun acceptsMultiplePropstatsAndUsesOnlySuccessfulProperties() {
        val result = CalDavXml.parse(
            multistatus(
                """
                    <d:response><d:href>/calendar/event.ics</d:href>
                      <d:propstat><d:prop><d:getetag>&quot;ignored&quot;</d:getetag></d:prop>
                        <d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>
                      <d:propstat><d:prop><d:getetag>&quot;accepted&quot;</d:getetag></d:prop>
                        <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                    </d:response>
                """.trimIndent(),
            ),
        )

        assertEquals("\"accepted\"", result.responses.single().properties["getetag"])
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
            val principal = gateway.authenticate(credentials)
            assertSuspendThrows<CalDavFailure.InvalidServerData> {
                gateway.fetchRange(
                    calendar(principal.sessionId),
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

    @Test
    fun concurrentAuthenticationRequestsAreSerializedIntoDistinctSessions() = runTest {
        val firstReachedServer = CountDownLatch(1)
        val secondReachedServer = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val requestNumber = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (requestNumber.incrementAndGet()) {
                    1 -> {
                        firstReachedServer.countDown()
                        releaseFirst.await(2, TimeUnit.SECONDS)
                        xmlResponse(principalResponse("/principals/users/first/"))
                    }
                    else -> {
                        secondReachedServer.countDown()
                        xmlResponse(principalResponse("/principals/users/second/"))
                    }
                }
            }
        }
        val firstPassword = "first-app-password".toCharArray()
        val secondPassword = "second-app-password".toCharArray()
        try {
            val gateway = gateway()
            val first = async(Dispatchers.Default) {
                gateway.authenticate(Credentials("first@example.test", firstPassword))
            }
            assertTrue(await(firstReachedServer))
            val second = async(Dispatchers.Default) {
                gateway.authenticate(Credentials("second@example.test", secondPassword))
            }

            val secondArrivedWhileFirstWasInFlight = await(secondReachedServer, 200)
            releaseFirst.countDown()
            val firstPrincipal = first.await()
            val secondPrincipal = second.await()

            assertFalse(secondArrivedWhileFirstWasInFlight)
            assertFalse(firstPrincipal.sessionId == secondPrincipal.sessionId)
        } finally {
            releaseFirst.countDown()
            firstPassword.fill('\u0000')
            secondPassword.fill('\u0000')
        }
    }

    @Test
    fun reauthenticationRejectsStalePrincipalAndCalendarWithoutNetworkRequests() = runTest {
        server.enqueue(xmlResponse(principalResponse("/principals/users/first/")))
        server.enqueue(xmlResponse(homeResponse("../../../calendars/owner/")))
        server.enqueue(xmlResponse(calendarListingResponse()))
        server.enqueue(xmlResponse(principalResponse("/principals/users/second/")))
        val gateway = gateway()
        val firstPrincipal = gateway.authenticate(credentials)
        val firstCalendar = gateway.discoverCalendars(firstPrincipal).single()

        val secondPassword = "second-app-password".toCharArray()
        try {
            gateway.authenticate(Credentials("second@example.test", secondPassword))
            val requestsBeforeStaleCalls = server.requestCount

            assertSuspendThrows<CalDavFailure.InvalidServerData> {
                gateway.discoverCalendars(firstPrincipal)
            }
            assertSuspendThrows<CalDavFailure.InvalidServerData> {
                gateway.fetchRange(firstCalendar, oneDayRange())
            }

            assertEquals(requestsBeforeStaleCalls, server.requestCount)
        } finally {
            secondPassword.fill('\u0000')
        }
    }

    private fun gateway(client: OkHttpClient = OkHttpClient()): ICloudCalDavGateway =
        ICloudCalDavGateway.forTesting(client, server.url("/"))

    private fun calendar(sessionId: String) = RemoteCalendar(
        id = "work",
        href = "/calendars/owner/work/",
        displayName = "Work",
        color = "#1a2b3c",
        syncToken = "token-1",
        sessionId = sessionId,
    )

    private fun oneDayRange() = TimeRange(
        Instant.parse("2026-08-10T00:00:00Z"),
        Instant.parse("2026-08-11T00:00:00Z"),
    )

    private suspend fun await(latch: CountDownLatch, timeoutMillis: Long = 2_000): Boolean =
        withContext(Dispatchers.IO) { latch.await(timeoutMillis, TimeUnit.MILLISECONDS) }

    private suspend fun authenticatedGatewayForDelta(
        changedHrefs: List<String>,
    ): Pair<ICloudCalDavGateway, RemoteCalendar> {
        server.enqueue(xmlResponse(principalResponse("/principals/users/owner/")))
        server.enqueue(xmlResponse(syncResponse(changedHrefs, null)))
        val gateway = gateway()
        val principal = gateway.authenticate(credentials)
        return gateway to calendar(principal.sessionId)
    }

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
            resourceResponse(href, index)
        }.toTypedArray(),
    )

    private fun resourceResponse(href: String, index: Int = 0) = response(
        href,
        "<d:getetag>&quot;e$index&quot;</d:getetag><c:calendar-data>${xml(VALID_ICS)}</c:calendar-data>",
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

    private fun assertInvalidMultistatus(response: String) {
        assertThrows(CalDavFailure.InvalidServerData::class.java) {
            CalDavXml.parse(multistatus(response))
        }
    }

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
