package com.brightnook.nookcalendar.sync

import android.util.Log
import com.brightnook.nookcalendar.caldav.NetworkFailureDetail
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/** Count-only, redacted diagnostics for incremental synchronization investigations. */
internal fun interface SyncDiagnostics {
    fun record(event: SyncDiagnosticsEvent)
}

internal object AndroidSyncDiagnostics : SyncDiagnostics {
    private const val tag = "NookCalendarSync"

    override fun record(event: SyncDiagnosticsEvent) {
        Log.i(tag, event.toLogLine())
    }
}

internal object NoOpSyncDiagnostics : SyncDiagnostics {
    override fun record(event: SyncDiagnosticsEvent) = Unit
}

internal fun newSyncDiagnosticAttemptId(): String = UUID.randomUUID().toString().take(8)

internal class SyncDiagnosticsEvent private constructor(
    private val phase: String,
    private val attemptId: String,
    private val calendarId: String,
    private val coverageChunks: Int? = null,
    private val upserts: Int? = null,
    private val tombstones: Int? = null,
    private val events: Int? = null,
    private val instances: Int? = null,
    private val failure: SyncFailureCategory? = null,
    private val network: String? = null,
) {
    fun toLogLine(): String = buildList {
        add("phase=$phase")
        add("attempt=$attemptId")
        add("calendar=${redact(calendarId)}")
        coverageChunks?.let { add("coverageChunks=$it") }
        upserts?.let { add("upserts=$it") }
        tombstones?.let { add("tombstones=$it") }
        events?.let { add("events=$it") }
        instances?.let { add("instances=$it") }
        failure?.let { add("failure=$it") }
        network?.let { add("network=$it") }
    }.joinToString(" ")

    companion object {
        fun deltaStarted(attemptId: String, calendarId: String, coverageChunks: Int) =
            SyncDiagnosticsEvent("delta_started", attemptId, calendarId, coverageChunks = coverageChunks)

        fun deltaReceived(attemptId: String, calendarId: String, upserts: Int, tombstones: Int) =
            SyncDiagnosticsEvent("delta_received", attemptId, calendarId, upserts = upserts, tombstones = tombstones)

        fun deltaParsed(attemptId: String, calendarId: String, events: Int, instances: Int) =
            SyncDiagnosticsEvent("delta_parsed", attemptId, calendarId, events = events, instances = instances)

        fun deltaApplied(attemptId: String, calendarId: String, events: Int, instances: Int) =
            SyncDiagnosticsEvent("delta_applied", attemptId, calendarId, events = events, instances = instances)

        fun rangeFallbackStarted(attemptId: String, calendarId: String, coverageChunks: Int) =
            SyncDiagnosticsEvent("range_fallback_started", attemptId, calendarId, coverageChunks = coverageChunks)

        fun failed(
            attemptId: String,
            calendarId: String,
            category: SyncFailureCategory,
            networkDetail: NetworkFailureDetail? = null,
        ) =
            SyncDiagnosticsEvent(
                "failed",
                attemptId,
                calendarId,
                failure = category,
                network = networkDetail?.name ?: if (category == SyncFailureCategory.NETWORK) "UNKNOWN" else null,
            )
    }
}

private fun redact(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
    return digest.take(6).joinToString("") { byte -> "%02x".format(byte) }
}
