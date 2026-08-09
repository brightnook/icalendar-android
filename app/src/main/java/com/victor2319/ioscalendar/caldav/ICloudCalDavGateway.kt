package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.domain.TimeRange
import java.io.IOException
import java.io.StringReader
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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
    @Volatile
    private var activeCredentials: StoredCredentials? = null

    constructor() : this(
        OkHttpClient.Builder()
            .addInterceptor(SensitiveLoggingInterceptor())
            .followRedirects(false)
            .followSslRedirects(false)
            .build(),
        PRODUCTION_URL,
        CalDavUrlPolicy.production(),
    )

    override suspend fun authenticate(credentials: Credentials): Principal {
        val pending = StoredCredentials(
            appleId = credentials.appleId,
            password = credentials.appSpecificPassword.copyOf(),
        )
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
            val previous = activeCredentials
            activeCredentials = pending
            previous?.clear()
            return Principal(href)
        } catch (failure: Throwable) {
            if (activeCredentials !== pending) pending.clear()
            throw failure
        }
    }

    override suspend fun discoverCalendars(principal: Principal): List<RemoteCalendar> {
        val credentials = credentials()
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
                )
            }
            .toList()
    }

    override suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange): RemoteSnapshot {
        val target = urlPolicy.resolve(baseUrl, calendar.href)
        val start = UTC_BASIC.format(range.start)
        val end = UTC_BASIC.format(range.endExclusive)
        val result = report(
            target,
            "1",
            CALENDAR_QUERY.format(start, end),
            credentials(),
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

    override suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String): RemoteDelta {
        val target = urlPolicy.resolve(baseUrl, calendar.href)
        val result = report(
            target,
            "1",
            SYNC_COLLECTION.format(xmlEscape(syncToken)),
            credentials(),
        )
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
            val hrefs = chunk.joinToString("") { "<d:href>${xmlEscape(it)}</d:href>" }
            val multiget = report(
                target,
                "1",
                CALENDAR_MULTIGET.format(hrefs),
                credentials(),
            )
            multiget.responses.forEach { response ->
                if (response.effectiveStatus !in 200..299) invalid()
                val href = safeHref(target, response.href)
                val data = response.properties["calendar-data"] ?: invalid()
                validateCalendarData(data)
                upserts += RemoteResource(href, response.properties["getetag"], data)
            }
        }
        return RemoteDelta(
            upserts = upserts.distinctBy(RemoteResource::href),
            tombstones = tombstones.distinctBy(RemoteTombstone::href),
            syncToken = result.syncToken,
        )
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

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
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
                    String(bytes, StandardCharsets.UTF_8)
                } finally {
                    bytes.fill(0)
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: CalDavFailure) {
            throw failure
        } catch (failure: IOException) {
            throw CalDavFailure.NetworkUnavailable(failure)
        }
    }

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
        try {
            val parsed = CalendarBuilder().build(StringReader(data))
            if (parsed.validate().hasErrors() || parsed.getComponents<VEvent>(Component.VEVENT).isEmpty()) {
                invalid()
            }
        } catch (failure: CalDavFailure.InvalidServerData) {
            throw failure
        } catch (_: Exception) {
            invalid()
        }
    }

    private fun credentials(): StoredCredentials = activeCredentials ?: invalid()

    private fun retryAfter(value: String?): Long? =
        value?.toLongOrNull()?.takeIf { it in 0..MAX_RETRY_AFTER_SECONDS }

    companion object {
        internal fun forTesting(client: OkHttpClient, baseUrl: HttpUrl): ICloudCalDavGateway =
            ICloudCalDavGateway(client, baseUrl, CalDavUrlPolicy.testing(baseUrl))

        private val PRODUCTION_URL = HttpUrl.Builder()
            .scheme("https")
            .host("caldav.icloud.com")
            .addPathSegment("")
            .build()
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val UTC_BASIC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        private const val MAX_RESPONSE_BYTES = 1_048_576
        private const val MAX_XML_CHARS = 1_048_576
        private const val MAX_CALENDAR_DATA_BYTES = 524_288
        private const val MULTIGET_CHUNK_SIZE = 50
        private const val MAX_DELTA_HREFS = 500
        private const val MAX_RETRY_AFTER_SECONDS = 86_400L

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
