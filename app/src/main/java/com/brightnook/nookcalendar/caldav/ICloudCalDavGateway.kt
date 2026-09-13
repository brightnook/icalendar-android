package com.brightnook.nookcalendar.caldav

import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.domain.TimeRange
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

class ICloudCalDavGateway private constructor(
    client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val urlPolicy: CalDavUrlPolicy,
) : CalDavGateway {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val sessionMutex = Mutex()
    private var activeSession: Session? = null

    constructor() : this(
        productionClientForTesting(),
        PRODUCTION_URL,
        CalDavUrlPolicy.production(),
    )

    override suspend fun authenticate(credentials: Credentials): Principal = sessionMutex.withLock {
        val pending = StoredCredentials(
            appleId = credentials.appleId,
            password = credentials.appSpecificPassword.copyOf(),
        )
        var installed = false
        try {
            val result = propfind(
                url = baseUrl,
                depth = "0",
                body = CURRENT_USER_PRINCIPAL,
                credentials = pending,
            )
            val href = result.responses.firstNotNullOfOrNull {
                it.properties["current-user-principal"]
            }?.takeIf(String::isNotBlank) ?: invalid()
            urlPolicy.resolve(baseUrl, href)
            val sessionId = UUID.randomUUID().toString()
            val previous = activeSession
            activeSession = Session(sessionId, pending)
            installed = true
            previous?.credentials?.clear()
            return Principal(href, sessionId)
        } catch (failure: Throwable) {
            if (!installed) pending.clear()
            throw failure
        }
    }

    override suspend fun discoverCalendars(principal: Principal): List<RemoteCalendar> = sessionMutex.withLock {
        val session = session(principal.sessionId)
        val credentials = session.credentials
        val principalUrl = urlPolicy.resolve(baseUrl, principal.href)
        val homeResult = propfind(principalUrl, "0", CALENDAR_HOME, credentials)
        val homeHref = homeResult.responses.firstNotNullOfOrNull {
            it.properties["calendar-home-set"]
        }?.takeIf(String::isNotBlank) ?: invalid()
        val homeUrl = urlPolicy.resolve(principalUrl, homeHref)
        val listing = propfind(homeUrl, "1", CALENDAR_LISTING, credentials)
        return listing.responses.asSequence()
            .filter { it.effectiveStatus in 200..299 }
            .filter { it.properties["resourcetype.calendar"] == "true" }
            .map { response ->
                val resolved = urlPolicy.resolve(homeUrl, response.href)
                val href = normalizedHref(resolved)
                RemoteCalendar(
                    id = resolved.pathSegments.lastOrNull { it.isNotBlank() } ?: invalid(),
                    href = href,
                    displayName = response.properties["displayname"].orEmpty().ifBlank { "Calendar" },
                    color = response.properties["calendar-color"],
                    syncToken = response.properties["sync-token"],
                    sessionId = principal.sessionId,
                )
            }
            .toList()
    }

    override suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange): RemoteSnapshot = sessionMutex.withLock {
        val session = session(calendar.sessionId)
        if (Duration.between(range.start, range.endExclusive) > MAX_FETCH_RANGE) invalid()
        val startYear = range.start.atOffset(ZoneOffset.UTC).year
        val endYear = range.endExclusive.atOffset(ZoneOffset.UTC).year
        if (startYear !in BASIC_YEAR_RANGE || endYear !in BASIC_YEAR_RANGE) invalid()
        val target = urlPolicy.resolve(baseUrl, calendar.href)
        val start = UTC_BASIC.format(range.start)
        val end = UTC_BASIC.format(range.endExclusive)
        val result = report(
            target,
            "1",
            CALENDAR_QUERY.format(start, end),
            session.credentials,
        )
        val resources = result.responses.mapNotNull { response ->
            if (response.effectiveStatus !in 200..299) return@mapNotNull null
            val data = response.properties["calendar-data"] ?: return@mapNotNull null
            val href = safeHref(target, response.href)
            validateCalendarData(data)
            RemoteResource(href, response.properties["getetag"], data)
        }
        return RemoteSnapshot(resources, result.syncToken ?: calendar.syncToken)
    }

    override suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String): RemoteDelta = sessionMutex.withLock {
        val session = session(calendar.sessionId)
        val target = urlPolicy.resolve(baseUrl, calendar.href)
        val result = report(
            target,
            "1",
            SYNC_COLLECTION.format(xmlEscape(syncToken)),
            session.credentials,
        )
        val newSyncToken = result.syncToken?.takeIf(String::isNotBlank) ?: invalid()
        val upserts = mutableListOf<RemoteResource>()
        val tombstones = mutableListOf<RemoteTombstone>()
        val missingData = mutableListOf<String>()
        result.responses.forEach { response ->
            val href = safeHref(target, response.href)
            when (response.effectiveStatus) {
                404, 410 -> tombstones += RemoteTombstone(href)
                in 200..299 -> {
                    val data = response.properties["calendar-data"]
                    if (data == null) {
                        missingData += href
                    } else {
                        validateCalendarData(data)
                        upserts += RemoteResource(href, response.properties["getetag"], data)
                    }
                }
                else -> invalid()
            }
        }
        if (missingData.size > MAX_DELTA_HREFS) invalid()
        missingData.chunked(MULTIGET_CHUNK_SIZE).forEach { chunk ->
            val requested = chunk.toSet()
            if (requested.size != chunk.size) invalid()
            val hrefs = chunk.joinToString("") { "<d:href>${xmlEscape(it)}</d:href>" }
            val multiget = report(
                target,
                "1",
                CALENDAR_MULTIGET.format(hrefs),
                session.credentials,
            )
            val accounted = mutableSetOf<String>()
            multiget.responses.forEach { response ->
                val href = safeHref(target, response.href)
                if (href !in requested || !accounted.add(href)) invalid()
                when (response.effectiveStatus) {
                    404, 410 -> tombstones += RemoteTombstone(href)
                    in 200..299 -> {
                        val data = response.properties["calendar-data"] ?: invalid()
                        validateCalendarData(data)
                        upserts += RemoteResource(href, response.properties["getetag"], data)
                    }
                    else -> invalid()
                }
            }
            if (accounted != requested) invalid()
        }
        return RemoteDelta(
            upserts = upserts.distinctBy(RemoteResource::href),
            tombstones = tombstones.distinctBy(RemoteTombstone::href),
            syncToken = newSyncToken,
        )
    }

    override suspend fun createResource(
        calendar: RemoteCalendar,
        href: String,
        calendarData: String,
    ): RemoteResource = sessionMutex.withLock {
        val session = session(calendar.sessionId)
        validateCalendarData(calendarData)
        val calendarUrl = urlPolicy.resolve(baseUrl, calendar.href)
        val resourceUrl = urlPolicy.resolve(calendarUrl, href)
        val normalizedHref = normalizedHref(resourceUrl)
        val request = Request.Builder()
            .url(resourceUrl)
            .header("Authorization", basicAuthorization(session.credentials))
            .header("If-None-Match", "*")
            .put(calendarData.toRequestBody(CALENDAR_MEDIA_TYPE))
            .build()
        RemoteResource(normalizedHref, executeWrite(request), calendarData)
    }

    override suspend fun updateResource(
        calendar: RemoteCalendar,
        resource: RemoteResource,
    ): RemoteResource = sessionMutex.withLock {
        val session = session(calendar.sessionId)
        val etag = resource.etag?.takeIf(String::isNotBlank) ?: invalid()
        validateCalendarData(resource.calendarData)
        val calendarUrl = urlPolicy.resolve(baseUrl, calendar.href)
        val resourceUrl = urlPolicy.resolve(calendarUrl, resource.href)
        val request = Request.Builder()
            .url(resourceUrl)
            .header("Authorization", basicAuthorization(session.credentials))
            .header("If-Match", etag)
            .put(resource.calendarData.toRequestBody(CALENDAR_MEDIA_TYPE))
            .build()
        resource.copy(href = normalizedHref(resourceUrl), etag = executeWrite(request) ?: etag)
    }

    override suspend fun deleteResource(
        calendar: RemoteCalendar,
        href: String,
        etag: String,
    ): Unit = sessionMutex.withLock {
        if (etag.isBlank()) invalid()
        val session = session(calendar.sessionId)
        val calendarUrl = urlPolicy.resolve(baseUrl, calendar.href)
        val resourceUrl = urlPolicy.resolve(calendarUrl, href)
        val request = Request.Builder()
            .url(resourceUrl)
            .header("Authorization", basicAuthorization(session.credentials))
            .header("If-Match", etag)
            .delete()
            .build()
        executeWrite(request)
    }

    private suspend fun propfind(
        url: HttpUrl,
        depth: String,
        body: String,
        credentials: StoredCredentials,
    ): DavMultistatus = request("PROPFIND", url, depth, body, credentials)

    private suspend fun report(
        url: HttpUrl,
        depth: String,
        body: String,
        credentials: StoredCredentials,
    ): DavMultistatus = request("REPORT", url, depth, body, credentials)

    private suspend fun request(
        method: String,
        url: HttpUrl,
        depth: String,
        xml: String,
        credentials: StoredCredentials,
    ): DavMultistatus {
        val trustedUrl = urlPolicy.requireTrusted(url)
        val authorization = basicAuthorization(credentials)
        val request = Request.Builder()
            .url(trustedUrl)
            .header("Authorization", authorization)
            .header("Depth", depth)
            .method(method, xml.toRequestBody(XML_MEDIA_TYPE))
            .build()
        val responseText = execute(request)
        if (responseText.length > MAX_XML_CHARS) invalid()
        return CalDavXml.parse(responseText)
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isActive) return
                val cancellation = e.causeSequence().filterIsInstance<CancellationException>().firstOrNull()
                continuation.resumeWith(
                    Result.failure(cancellation ?: CalDavFailure.NetworkUnavailable(NetworkFailureDetail.from(e))),
                )
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                try {
                    val result = response.use(::readResponse)
                    continuation.resume(result) { _, _, _ -> }
                } catch (failure: Throwable) {
                    if (!continuation.isActive) return
                    val mapped = when (failure) {
                        is CalDavFailure -> failure
                        is IOException -> CalDavFailure.NetworkUnavailable(NetworkFailureDetail.from(failure))
                        else -> CalDavFailure.InvalidServerData()
                    }
                    continuation.resumeWith(Result.failure(mapped))
                }
            }
        })
    }

    private suspend fun executeWrite(request: Request): String? = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isActive) return
                val cancellation = e.causeSequence().filterIsInstance<CancellationException>().firstOrNull()
                continuation.resumeWith(
                    Result.failure(cancellation ?: CalDavFailure.NetworkUnavailable(NetworkFailureDetail.from(e))),
                )
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                try {
                    val result = response.use {
                        when (it.code) {
                            401, 403 -> throw CalDavFailure.AuthenticationRejected()
                            409, 412 -> throw CalDavFailure.Conflict()
                            408, 429 -> throw CalDavFailure.Temporary(retryAfter(it.header("Retry-After")))
                            in 500..599 -> throw CalDavFailure.Temporary(retryAfter(it.header("Retry-After")))
                            !in 200..299 -> invalid()
                        }
                        if (it.body.contentLength() > MAX_RESPONSE_BYTES) invalid()
                        it.header("ETag")
                    }
                    continuation.resume(result) { _, _, _ -> }
                } catch (failure: Throwable) {
                    if (!continuation.isActive) return
                    continuation.resumeWith(Result.failure(failure))
                }
            }
        })
    }

    private fun readResponse(response: Response): String {
        when (response.code) {
            401, 403 -> throw CalDavFailure.AuthenticationRejected()
            408, 429 -> throw CalDavFailure.Temporary(retryAfter(response.header("Retry-After")))
            in 500..599 -> throw CalDavFailure.Temporary(retryAfter(response.header("Retry-After")))
            !in 200..299 -> invalid()
        }
        val body = response.body
        if (body.contentLength() > MAX_RESPONSE_BYTES) invalid()
        val source = body.source()
        val buffer = Buffer()
        var total = 0L
        while (total <= MAX_RESPONSE_BYTES) {
            val read = source.read(
                buffer,
                minOf(8_192L, MAX_RESPONSE_BYTES + 1L - total),
            )
            if (read == -1L) break
            total += read
        }
        val bytes = buffer.readByteArray()
        try {
            if (bytes.size > MAX_RESPONSE_BYTES) invalid()
            return String(bytes, StandardCharsets.UTF_8)
        } finally {
            bytes.fill(0)
        }
    }

    private fun Throwable.causeSequence(): Sequence<Throwable> =
        generateSequence(this) { current -> current.cause?.takeUnless { it === current } }

    private fun safeHref(context: HttpUrl, href: String): String =
        normalizedHref(urlPolicy.resolve(context, href))

    private fun normalizedHref(url: HttpUrl): String =
        if (url.scheme == baseUrl.scheme && url.host == baseUrl.host && url.port == baseUrl.port) {
            buildString {
                append(url.encodedPath)
                url.encodedQuery?.let { append('?').append(it) }
            }
        } else {
            url.toString()
        }

    private fun validateCalendarData(data: String) {
        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        try {
            if (bytes.size > MAX_CALENDAR_DATA_BYTES) invalid()
        } finally {
            bytes.fill(0)
        }
        // Parsing is deliberately deferred to Ical4jIcsParser. Re-parsing here registered iCal4j's
        // Java ZoneRulesProvider for every VTIMEZONE, which Android does not permit. Keep the cheap
        // type guard here; the parser remains the authoritative structural and event validation step.
        val components = data.lineSequence().map(String::trim)
        var calendarStarted = false
        var calendarEnded = false
        var eventStarted = false
        for (component in components) {
            when (component.uppercase()) {
                "BEGIN:VCALENDAR" -> calendarStarted = true
                "END:VCALENDAR" -> calendarEnded = true
                "BEGIN:VEVENT" -> eventStarted = true
            }
        }
        if (!calendarStarted || !calendarEnded || !eventStarted) invalid()
    }

    private fun session(expectedId: String): Session =
        activeSession?.takeIf { it.id == expectedId } ?: invalid()

    private fun retryAfter(value: String?): Long? =
        value?.toLongOrNull()?.takeIf { it in 0..MAX_RETRY_AFTER_SECONDS }

    companion object {
        internal fun productionClientForTesting(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(SensitiveLoggingInterceptor())
            .connectTimeout(CALDAV_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(CALDAV_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(CALDAV_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALDAV_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        internal fun forTesting(client: OkHttpClient, baseUrl: HttpUrl): ICloudCalDavGateway =
            ICloudCalDavGateway(client, baseUrl, CalDavUrlPolicy.testing(baseUrl))

        private val PRODUCTION_URL = HttpUrl.Builder()
            .scheme("https")
            .host("caldav.icloud.com")
            .addPathSegment("")
            .build()
        private const val CALDAV_CONNECT_TIMEOUT_SECONDS = 30L
        private const val CALDAV_READ_TIMEOUT_SECONDS = 30L
        private const val CALDAV_WRITE_TIMEOUT_SECONDS = 30L
        private const val CALDAV_CALL_TIMEOUT_SECONDS = 45L
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val CALENDAR_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()
        private val UTC_BASIC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        private const val MAX_RESPONSE_BYTES = 1_048_576
        private const val MAX_XML_CHARS = 1_048_576
        private const val MAX_CALENDAR_DATA_BYTES = 524_288
        private const val MULTIGET_CHUNK_SIZE = 50
        private const val MAX_DELTA_HREFS = 500
        private const val MAX_RETRY_AFTER_SECONDS = 86_400L
        private val MAX_FETCH_RANGE = Duration.ofDays(400)
        private val BASIC_YEAR_RANGE = 1..9_999

        private val CURRENT_USER_PRINCIPAL = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>
        """.trimIndent()
        private val CALENDAR_HOME = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><c:calendar-home-set/></d:prop>
            </d:propfind>
        """.trimIndent()
        private val CALENDAR_LISTING = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:x="http://apple.com/ns/ical/">
              <d:prop><d:resourcetype/><d:displayname/><x:calendar-color/><d:sync-token/></d:prop>
            </d:propfind>
        """.trimIndent()
        private val CALENDAR_QUERY = """
            <?xml version="1.0" encoding="utf-8"?>
            <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:getetag/><c:calendar-data/></d:prop>
              <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT">
                <c:time-range start="%s" end="%s"/>
              </c:comp-filter></c:comp-filter></c:filter>
            </c:calendar-query>
        """.trimIndent()
        private val SYNC_COLLECTION = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:sync-collection xmlns:d="DAV:">
              <d:sync-token>%s</d:sync-token><d:sync-level>1</d:sync-level>
              <d:prop><d:getetag/></d:prop>
            </d:sync-collection>
        """.trimIndent()
        private val CALENDAR_MULTIGET = """
            <?xml version="1.0" encoding="utf-8"?>
            <c:calendar-multiget xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:getetag/><c:calendar-data/></d:prop>%s
            </c:calendar-multiget>
        """.trimIndent()

        private fun basicAuthorization(credentials: StoredCredentials): String {
            val appleId = credentials.appleId.toByteArray(StandardCharsets.UTF_8)
            val passwordBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(credentials.password))
            val password = ByteArray(passwordBuffer.remaining()).also { passwordBuffer.get(it) }
            val material = ByteArray(appleId.size + 1 + password.size)
            val encoded: ByteArray
            try {
                appleId.copyInto(material)
                material[appleId.size] = ':'.code.toByte()
                password.copyInto(material, appleId.size + 1)
                encoded = Base64.getEncoder().encode(material)
                try {
                    return "Basic " + String(encoded, StandardCharsets.ISO_8859_1)
                } finally {
                    encoded.fill(0)
                }
            } finally {
                appleId.fill(0)
                password.fill(0)
                material.fill(0)
                if (passwordBuffer.hasArray()) passwordBuffer.array().fill(0)
            }
        }

        private fun xmlEscape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        private fun invalid(): Nothing = throw CalDavFailure.InvalidServerData()
    }

    private data class StoredCredentials(val appleId: String, val password: CharArray) {
        fun clear() = password.fill('\u0000')
        override fun toString(): String = "StoredCredentials(<redacted>)"
    }

    private data class Session(val id: String, val credentials: StoredCredentials)
}

internal class CalDavUrlPolicy private constructor(
    private val testOrigin: Origin?,
) {
    fun resolve(context: HttpUrl, reference: String): HttpUrl {
        if (reference.length > MAX_REFERENCE_LENGTH) invalid()
        val resolved = context.resolve(reference) ?: invalid()
        return requireTrusted(resolved)
    }

    fun requireTrusted(url: HttpUrl): HttpUrl {
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null) invalid()
        val allowed = testOrigin?.matches(url) ?: (
            url.scheme == "https" && (url.host == "icloud.com" || url.host.endsWith(".icloud.com"))
        )
        if (!allowed) invalid()
        return url
    }

    companion object {
        fun production(): CalDavUrlPolicy = CalDavUrlPolicy(null)
        fun testing(baseUrl: HttpUrl): CalDavUrlPolicy = CalDavUrlPolicy(Origin.from(baseUrl))
        private const val MAX_REFERENCE_LENGTH = 8_192
        private fun invalid(): Nothing = throw CalDavFailure.InvalidServerData()
    }

    private data class Origin(val scheme: String, val host: String, val port: Int) {
        fun matches(url: HttpUrl): Boolean =
            url.scheme == scheme && url.host == host && url.port == port

        companion object {
            fun from(url: HttpUrl) = Origin(url.scheme, url.host, url.port)
        }
    }
}
