package com.brightnook.nookcalendar.data

import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.caldav.CalDavFailure
import com.brightnook.nookcalendar.caldav.CalDavGateway
import com.brightnook.nookcalendar.caldav.IcsEventWriter
import com.brightnook.nookcalendar.caldav.RemoteCalendar
import com.brightnook.nookcalendar.caldav.RemoteResource
import com.brightnook.nookcalendar.caldav.stableCalendarId
import com.brightnook.nookcalendar.domain.CalendarRepository
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventEditingRepository
import com.brightnook.nookcalendar.domain.EventEditingResult
import com.brightnook.nookcalendar.domain.EventWriteSource
import com.brightnook.nookcalendar.sync.SyncCoordinator
import com.brightnook.nookcalendar.sync.SyncRequest
import com.brightnook.nookcalendar.sync.SyncResult
import com.brightnook.nookcalendar.widget.WidgetRefreshNotifier
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultEventEditingRepository(
    private val credentialsStore: CredentialsStore,
    private val gateway: CalDavGateway,
    private val calendarRepository: CalendarRepository,
    private val syncCoordinator: SyncCoordinator,
    private val widgetRefreshNotifier: WidgetRefreshNotifier,
    private val writer: IcsEventWriter = IcsEventWriter(),
    private val now: () -> Instant = Instant::now,
    private val uid: () -> String = { UUID.randomUUID().toString() },
) : EventEditingRepository {
    override fun observeWritableCalendars(): Flow<List<CalendarSummary>> =
        calendarRepository.observeCalendars().map { calendars -> calendars.filter(CalendarSummary::isWritable) }

    override fun observeEvent(instanceId: String): Flow<EventDetail?> = calendarRepository.observeEvent(instanceId)

    override suspend fun create(draft: EventDraft): EventEditingResult = perform {
        val calendar = remoteCalendar(draft.calendarId) ?: return@perform EventEditingResult.ReadOnly
        val eventUid = uid()
        val data = writer.write(draft, eventUid, now())
        gateway.createResource(calendar, "$eventUid.ics", data)
        refreshAfterWrite()
    }

    override suspend fun update(instanceId: String, draft: EventDraft): EventEditingResult = perform {
        val source = source(instanceId) ?: return@perform EventEditingResult.NotFound
        if (source.isRecurring) return@perform EventEditingResult.RecurringUnsupported
        if (!source.calendar.isWritable) return@perform EventEditingResult.ReadOnly
        val etag = source.resource.etag?.takeIf(String::isNotBlank) ?: return@perform EventEditingResult.Conflict
        val calendar = remoteCalendar(source.calendar.id) ?: return@perform EventEditingResult.ReadOnly
        val data = writer.write(draft.copy(calendarId = source.calendar.id), source.uid, now())
        gateway.updateResource(calendar, RemoteResource(source.resource.href, etag, data))
        refreshAfterWrite()
    }

    override suspend fun delete(instanceId: String): EventEditingResult = perform {
        val source = source(instanceId) ?: return@perform EventEditingResult.NotFound
        if (source.isRecurring) return@perform EventEditingResult.RecurringUnsupported
        if (!source.calendar.isWritable) return@perform EventEditingResult.ReadOnly
        val etag = source.resource.etag?.takeIf(String::isNotBlank) ?: return@perform EventEditingResult.Conflict
        val calendar = remoteCalendar(source.calendar.id) ?: return@perform EventEditingResult.ReadOnly
        gateway.deleteResource(calendar, source.resource.href, etag)
        refreshAfterWrite()
    }

    private suspend fun source(instanceId: String): EventWriteSource? =
        calendarRepository.findEventWriteSource(instanceId)

    private suspend fun remoteCalendar(calendarId: String): RemoteCalendar? {
        val credentials = credentialsStore.load() ?: throw MissingCredentials
        val principal = gateway.authenticate(credentials)
        return gateway.discoverCalendars(principal).firstOrNull {
            it.isWritable && stableCalendarId(it.href) == calendarId
        }
    }

    private suspend fun refreshAfterWrite(): EventEditingResult = when (syncCoordinator.sync(SyncRequest.Refresh)) {
        is SyncResult.Success, is SyncResult.PartialSuccess -> {
            widgetRefreshNotifier.refreshAll()
            EventEditingResult.Success
        }
        else -> EventEditingResult.SavedRefreshPending
    }

    private suspend fun perform(block: suspend () -> EventEditingResult): EventEditingResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: MissingCredentials) {
        EventEditingResult.AuthenticationRequired
    } catch (_: CalDavFailure.AuthenticationRejected) {
        EventEditingResult.AuthenticationRequired
    } catch (_: CalDavFailure.Conflict) {
        EventEditingResult.Conflict
    } catch (_: CalDavFailure.NetworkUnavailable) {
        EventEditingResult.NetworkFailure
    } catch (_: CalDavFailure.Temporary) {
        EventEditingResult.NetworkFailure
    } catch (_: CalDavFailure.InvalidServerData) {
        EventEditingResult.Invalid
    } catch (_: IllegalArgumentException) {
        EventEditingResult.Invalid
    }

    private data object MissingCredentials : RuntimeException()
}
