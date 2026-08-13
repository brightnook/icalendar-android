package com.victor2319.ioscalendar.caldav

import java.io.StringReader
import java.util.ArrayDeque
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

internal data class DavMultistatus(
    val responses: List<DavResponse>,
    val syncToken: String?,
)

internal data class DavResponse(
    val href: String,
    val status: Int?,
    val properties: Map<String, String>,
) {
    val effectiveStatus: Int? get() = status ?: if (properties.isNotEmpty()) 200 else null
}

internal object CalDavXml {
    fun parse(xml: String): DavMultistatus {
        val upper = xml.uppercase(Locale.ROOT)
        if ("<!DOCTYPE" in upper || "<!ENTITY" in upper) invalid()
        val input = xml.trimStart().let { trimmed ->
            if (trimmed.startsWith("<?xml", ignoreCase = true)) {
                val end = trimmed.indexOf("?>")
                if (end < 0) invalid()
                trimmed.substring(end + 2)
            } else {
                trimmed
            }
        }
        try {
            val parser = newParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
            parser.setInput(StringReader(input))
            return readMultistatus(parser)
        } catch (failure: CalDavFailure.InvalidServerData) {
            throw failure
        } catch (_: Exception) {
            invalid()
        }
    }

    private fun newParser(): XmlPullParser = try {
        Class.forName("org.kxml2.io.KXmlParser").getDeclaredConstructor().newInstance() as XmlPullParser
    } catch (_: ReflectiveOperationException) {
        android.util.Xml.newPullParser()
    }

    private fun readMultistatus(parser: XmlPullParser): DavMultistatus {
        val stack = ArrayDeque<XmlName>()
        val responses = mutableListOf<DavResponse>()
        var current: ResponseBuilder? = null
        var inPropstat = false
        var propstatStatus: Int? = null
        var propstatProperties = mutableMapOf<String, String>()
        var activeProperty: XmlName? = null
        var propertyText: StringBuilder? = null
        var directHrefText: StringBuilder? = null
        var statusText: StringBuilder? = null
        var statusIsPropstat = false
        var rootSyncText: StringBuilder? = null
        var rootSyncToken: String? = null
        var rootClosed = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val name = XmlName(parser.namespace.orEmpty(), parser.name)
                    rejectNamespaceImpostor(name)
                    val parent = stack.peekLast()
                    if (stack.isEmpty()) {
                        if (rootClosed || name != MULTISTATUS) invalid()
                    } else {
                        when (name) {
                            MULTISTATUS -> invalid()
                            RESPONSE -> if (parent != MULTISTATUS || current != null) invalid()
                            PROPSTAT -> if (parent != RESPONSE || current == null || inPropstat) invalid()
                            PROP -> if (parent != PROPSTAT || !inPropstat) invalid()
                            STATUS -> if (parent != RESPONSE && parent != PROPSTAT) invalid()
                            HREF -> if (parent != RESPONSE && parent != CURRENT_USER_PRINCIPAL && parent != CALENDAR_HOME_SET) invalid()
                            SYNC_TOKEN -> if (parent != PROP && parent != MULTISTATUS) invalid()
                        }
                    }
                    stack.addLast(name)
                    when {
                        name == RESPONSE -> current = ResponseBuilder()
                        name == PROPSTAT -> {
                            inPropstat = true
                            propstatStatus = null
                            propstatProperties = mutableMapOf()
                        }
                        name == HREF && parent == RESPONSE -> directHrefText = StringBuilder()
                        name == STATUS -> {
                            statusText = StringBuilder()
                            statusIsPropstat = parent == PROPSTAT
                        }
                        parent == PROP && name in TEXT_PROPERTIES -> {
                            activeProperty = name
                            propertyText = StringBuilder()
                        }
                        name == SYNC_TOKEN && parent == MULTISTATUS -> rootSyncText = StringBuilder()
                    }
                    if (current != null && inPropstat && activeProperty == RESOURCE_TYPE) {
                        if (name == CALENDAR) propstatProperties["resourcetype.calendar"] = "true"
                        if (name == COLLECTION) propstatProperties["resourcetype.collection"] = "true"
                    }
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                    val text = parser.text
                    directHrefText?.append(text)
                    statusText?.append(text)
                    propertyText?.append(text)
                    rootSyncText?.append(text)
                }

                XmlPullParser.END_TAG -> {
                    val name = XmlName(parser.namespace.orEmpty(), parser.name)
                    when {
                        name == HREF && directHrefText != null -> {
                            current?.href = directHrefText.toString().trim()
                            directHrefText = null
                        }
                        name == STATUS && statusText != null -> {
                            val parsed = parseStatus(statusText.toString())
                            if (statusIsPropstat) propstatStatus = parsed else current?.status = parsed
                            statusText = null
                        }
                        name == activeProperty && propertyText != null -> {
                            propstatProperties[propertyKey(name)] = propertyText.toString().trim()
                            activeProperty = null
                            propertyText = null
                        }
                        name == PROPSTAT -> {
                            if (propstatStatus in 200..299) current?.properties?.putAll(propstatProperties)
                            inPropstat = false
                        }
                        name == RESPONSE -> {
                            val completed = current ?: invalid()
                            val href = completed.href?.takeIf { it.isNotEmpty() } ?: invalid()
                            responses += DavResponse(href, completed.status, completed.properties.toMap())
                            if (responses.size > MAX_RESPONSES) invalid()
                            current = null
                        }
                        name == SYNC_TOKEN && stack.size == 2 && rootSyncText != null -> {
                            rootSyncToken = rootSyncText.toString().trim().takeIf(String::isNotEmpty)
                            rootSyncText = null
                        }
                    }
                    if (stack.isEmpty() || stack.removeLast() != name) invalid()
                    if (name == MULTISTATUS) rootClosed = true
                }
            }
            parser.nextToken()
        }
        if (!rootClosed || stack.isNotEmpty() || current != null || inPropstat) invalid()
        return DavMultistatus(responses, rootSyncToken)
    }

    private fun rejectNamespaceImpostor(name: XmlName) {
        val expectedNamespaces = EXPECTED_NAMESPACES[name.local] ?: return
        if (name.namespace !in expectedNamespaces) invalid()
    }

    private fun propertyKey(name: XmlName): String = when (name) {
        CURRENT_USER_PRINCIPAL -> "current-user-principal"
        CALENDAR_HOME_SET -> "calendar-home-set"
        DISPLAY_NAME -> "displayname"
        CALENDAR_COLOR -> "calendar-color"
        SYNC_TOKEN -> "sync-token"
        GET_ETAG -> "getetag"
        CALENDAR_DATA -> "calendar-data"
        RESOURCE_TYPE -> "resourcetype"
        else -> invalid()
    }

    private fun parseStatus(value: String): Int =
        STATUS_PATTERN.find(value)?.groupValues?.get(1)?.toIntOrNull() ?: invalid()

    private class ResponseBuilder {
        var href: String? = null
        var status: Int? = null
        val properties = mutableMapOf<String, String>()
    }

    private data class XmlName(val namespace: String, val local: String)

    private const val DAV = "DAV:"
    private const val CALDAV = "urn:ietf:params:xml:ns:caldav"
    private const val APPLE_ICAL = "http://apple.com/ns/ical/"
    private val MULTISTATUS = XmlName(DAV, "multistatus")
    private val RESPONSE = XmlName(DAV, "response")
    private val HREF = XmlName(DAV, "href")
    private val STATUS = XmlName(DAV, "status")
    private val PROPSTAT = XmlName(DAV, "propstat")
    private val PROP = XmlName(DAV, "prop")
    private val CURRENT_USER_PRINCIPAL = XmlName(DAV, "current-user-principal")
    private val CALENDAR_HOME_SET = XmlName(CALDAV, "calendar-home-set")
    private val DISPLAY_NAME = XmlName(DAV, "displayname")
    private val CALENDAR_COLOR = XmlName(APPLE_ICAL, "calendar-color")
    private val SYNC_TOKEN = XmlName(DAV, "sync-token")
    private val GET_ETAG = XmlName(DAV, "getetag")
    private val CALENDAR_DATA = XmlName(CALDAV, "calendar-data")
    private val RESOURCE_TYPE = XmlName(DAV, "resourcetype")
    private val COLLECTION = XmlName(DAV, "collection")
    private val CALENDAR = XmlName(CALDAV, "calendar")
    private val TEXT_PROPERTIES = setOf(
        CURRENT_USER_PRINCIPAL,
        CALENDAR_HOME_SET,
        DISPLAY_NAME,
        CALENDAR_COLOR,
        SYNC_TOKEN,
        GET_ETAG,
        CALENDAR_DATA,
        RESOURCE_TYPE,
    )
    private val EXPECTED_NAMESPACES = mapOf(
        "multistatus" to setOf(DAV),
        "response" to setOf(DAV),
        "href" to setOf(DAV),
        "status" to setOf(DAV),
        "propstat" to setOf(DAV),
        "prop" to setOf(DAV),
        "current-user-principal" to setOf(DAV),
        "calendar-home-set" to setOf(CALDAV),
        "displayname" to setOf(DAV),
        "calendar-color" to setOf(APPLE_ICAL),
        "sync-token" to setOf(DAV),
        "getetag" to setOf(DAV),
        "calendar-data" to setOf(CALDAV),
        "resourcetype" to setOf(DAV),
        "collection" to setOf(DAV),
        "calendar" to setOf(CALDAV),
    )
    private val STATUS_PATTERN = Regex("HTTP/\\S+\\s+(\\d{3})(?:\\s|$)")
    private const val MAX_RESPONSES = 1_000

    private fun invalid(): Nothing = throw CalDavFailure.InvalidServerData()
}
