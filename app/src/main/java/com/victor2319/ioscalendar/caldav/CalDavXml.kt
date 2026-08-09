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
        val stack = ArrayDeque<String>()
        val responses = mutableListOf<DavResponse>()
        var current: ResponseBuilder? = null
        var inPropstat = false
        var propstatStatus: Int? = null
        var propstatProperties = mutableMapOf<String, String>()
        var activeProperty: String? = null
        var propertyText: StringBuilder? = null
        var directHrefText: StringBuilder? = null
        var statusText: StringBuilder? = null
        var statusIsPropstat = false
        var rootSyncText: StringBuilder? = null
        var rootSyncToken: String? = null
        var sawMultistatus = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    val parent = stack.peekLast()
                    stack.addLast(name)
                    when {
                        name == "multistatus" -> sawMultistatus = true
                        name == "response" -> current = ResponseBuilder()
                        name == "propstat" && current != null -> {
                            inPropstat = true
                            propstatStatus = null
                            propstatProperties = mutableMapOf()
                        }
                        name == "href" && current != null && parent == "response" ->
                            directHrefText = StringBuilder()
                        name == "status" && current != null -> {
                            statusText = StringBuilder()
                            statusIsPropstat = inPropstat
                        }
                        current != null && inPropstat && name in TEXT_PROPERTIES -> {
                            activeProperty = name
                            propertyText = StringBuilder()
                        }
                        current == null && name == "sync-token" -> rootSyncText = StringBuilder()
                    }
                    if (current != null && inPropstat && activeProperty == "resourcetype") {
                        if (name == "calendar") propstatProperties["resourcetype.calendar"] = "true"
                        if (name == "collection") propstatProperties["resourcetype.collection"] = "true"
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
                    val name = parser.name
                    when {
                        name == "href" && directHrefText != null -> {
                            current?.href = directHrefText.toString().trim()
                            directHrefText = null
                        }
                        name == "status" && statusText != null -> {
                            val parsed = parseStatus(statusText.toString())
                            if (statusIsPropstat) propstatStatus = parsed else current?.status = parsed
                            statusText = null
                        }
                        name == activeProperty && propertyText != null -> {
                            propstatProperties[name] = propertyText.toString().trim()
                            activeProperty = null
                            propertyText = null
                        }
                        name == "propstat" -> {
                            if (propstatStatus in 200..299) {
                                current?.properties?.putAll(propstatProperties)
                            }
                            inPropstat = false
                        }
                        name == "response" -> {
                            val completed = current ?: invalid()
                            val href = completed.href?.takeIf { it.isNotEmpty() } ?: invalid()
                            responses += DavResponse(href, completed.status, completed.properties.toMap())
                            if (responses.size > MAX_RESPONSES) invalid()
                            current = null
                        }
                        name == "sync-token" && current == null && rootSyncText != null -> {
                            rootSyncToken = rootSyncText.toString().trim().takeIf(String::isNotEmpty)
                            rootSyncText = null
                        }
                    }
                    if (stack.isEmpty() || stack.removeLast() != name) invalid()
                }
            }
            parser.nextToken()
        }
        if (!sawMultistatus || stack.isNotEmpty()) invalid()
        return DavMultistatus(responses, rootSyncToken)
    }

    private fun parseStatus(value: String): Int =
        STATUS.find(value)?.groupValues?.get(1)?.toIntOrNull() ?: invalid()

    private class ResponseBuilder {
        var href: String? = null
        var status: Int? = null
        val properties = mutableMapOf<String, String>()
    }

    private val STATUS = Regex("HTTP/\\S+\\s+(\\d{3})(?:\\s|$)")
    private val TEXT_PROPERTIES = setOf(
        "current-user-principal",
        "calendar-home-set",
        "displayname",
        "calendar-color",
        "sync-token",
        "getetag",
        "calendar-data",
        "resourcetype",
    )
    private const val MAX_RESPONSES = 1_000

    private fun invalid(): Nothing = throw CalDavFailure.InvalidServerData()
}
