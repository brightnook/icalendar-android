package com.brightnook.nookcalendar.caldav

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal fun stableCalendarId(href: String): String = stableHash("icloud-calendar", canonicalHref(href))

internal fun canonicalHref(href: String): String {
    val normalized = URI(href).normalize()
    if (!normalized.isAbsolute) return normalized.rawPath ?: href
    val scheme = normalized.scheme.lowercase()
    val host = normalized.host?.lowercase() ?: return normalized.toASCIIString()
    val port = normalized.port.takeUnless {
        it == -1 || scheme == "https" && it == 443 || scheme == "http" && it == 80
    }
    return buildString {
        append(scheme).append("://").append(host)
        port?.let { append(':').append(it) }
        append(normalized.rawPath.orEmpty().ifEmpty { "/" })
        normalized.rawQuery?.let { append('?').append(it) }
    }
}

private fun stableHash(namespace: String, value: String): String {
    val material = "$namespace:${value.length}:$value"
    return MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
