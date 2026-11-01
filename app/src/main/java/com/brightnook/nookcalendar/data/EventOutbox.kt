package com.brightnook.nookcalendar.data

import androidx.room.*
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.domain.*
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/** Independent of calendar cache: deleting a cached snapshot must never delete a draft. */
@Entity(tableName = "event_outbox")
data class EventOutboxEntity(
    @PrimaryKey val instanceId: String,
    val accountKey: String,
    val uid: String,
    val sourceCalendarId: String,
    val sourceHref: String?,
    val baseEtag: String?,
    val draftJson: String,
    val calendarData: String,
    val state: String = "PENDING",
    val phase: String = "READY",
    val resultHref: String? = null,
    val resultEtag: String? = null,
    val remoteJson: String? = null,
    val remoteData: String? = null,
    val remoteEtag: String? = null,
    val remoteHref: String? = null,
    val remoteUid: String? = null,
    val remoteCalendarId: String? = null,
    val remoteMissing: Boolean = false,
    val originalCalendarId: String? = null,
    val originalHref: String? = null,
)

@Dao
interface EventOutboxDao {
    @Query("SELECT * FROM event_outbox") fun observeAll(): Flow<List<EventOutboxEntity>>
    @Query("SELECT * FROM event_outbox") suspend fun all(): List<EventOutboxEntity>
    @Query("SELECT * FROM event_outbox WHERE instanceId = :id") suspend fun find(id: String): EventOutboxEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(row: EventOutboxEntity)
    @Update suspend fun update(row: EventOutboxEntity)
    @Query("DELETE FROM event_outbox WHERE instanceId = :id") suspend fun delete(id: String)
    @Query("UPDATE event_outbox SET state = state") suspend fun invalidateAccountOverlays()
}

internal suspend fun CredentialsStore.outboxAccountKey(): String? {
    val credentials = load() ?: return null
    return try {
        MessageDigest.getInstance("SHA-256").digest(credentials.appleId.trim().lowercase(java.util.Locale.ROOT).toByteArray())
            .joinToString("") { "%02x".format(it) }
    } finally { credentials.appSpecificPassword.fill('\u0000') }
}

internal fun encodeDraft(draft: EventDraft): String = JSONObject().apply {
    put("calendarId", draft.calendarId); put("title", draft.title)
    put("location", draft.location); put("notes", draft.notes); put("allDay", draft.isAllDay)
    put("start", draft.startInstant?.toString()); put("end", draft.endInstant?.toString())
    put("date", draft.startDate?.toString()); put("endDate", draft.endDateExclusive?.toString())
    put("zone", draft.timeZoneId); put("reminders", draft.reminders.encode())
}.toString()

internal fun decodeDraft(json: String): EventDraft = JSONObject(json).let { value ->
    fun optional(key: String) = if (value.has(key) && !value.isNull(key)) value.getString(key) else null
    EventDraft(value.getString("calendarId"), value.getString("title"), optional("location"), optional("notes"),
        value.getBoolean("allDay"), optional("start")?.let(Instant::parse), optional("end")?.let(Instant::parse),
        optional("date")?.let(LocalDate::parse), optional("endDate")?.let(LocalDate::parse), optional("zone"),
        value.optString("reminders", "").decodeReminders())
}

internal fun EventOutboxEntity.instance(color: Int): EventInstance = decodeDraft(draftJson).let { d ->
    EventInstance(instanceId, "local:$uid", d.calendarId, d.title, color, d.isAllDay,
        d.startInstant, d.endInstant, d.startDate, d.endDateExclusive, d.location, PendingEventSyncState.valueOf(state))
}

internal fun EventOutboxEntity.detail(calendar: CalendarSummary): EventDetail = decodeDraft(draftJson).let { d ->
    EventDetail(instanceId, "local:$uid", d.calendarId, calendar.name, calendar.colorArgb, d.title, d.location,
        d.notes, d.isAllDay, d.startInstant, d.endInstant, d.startDate, d.endDateExclusive, d.timeZoneId,
        null, d.reminders, PendingEventSyncState.valueOf(state), remoteJson?.let(::decodeDraft),
        pendingDeletion = phase in setOf("DELETE_READY", "DELETE_UNKNOWN", "DELETE_CONFIRMED"))
}

internal fun EventOutboxEntity.isHiddenDeletion(): Boolean =
    phase in setOf("DELETE_READY", "DELETE_UNKNOWN", "DELETE_CONFIRMED", "REMOTE_DELETED") && state in setOf("PENDING", "SYNCING")

/** UID is only unique within a resource identity, not across imported/shared collections. */
internal fun EventOutboxEntity.shadows(event: EventEntity): Boolean {
    if (event.uid != uid) return false
    val href = event.remoteResourceHref ?: return false
    if (event.calendarId == sourceCalendarId && href == sourceHref) return true
    if (event.calendarId == originalCalendarId && href == originalHref) return true
    val destination = decodeDraft(draftJson).calendarId
    if (event.calendarId == destination && href == resultHref) return true
    return sourceHref == null && event.calendarId == destination && href.substringAfterLast('/') == "$uid.ics"
}
