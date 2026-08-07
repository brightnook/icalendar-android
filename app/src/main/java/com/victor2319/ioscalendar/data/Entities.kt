package com.victor2319.ioscalendar.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Int,
    val isVisible: Boolean,
)

@Entity(
    tableName = "event_masters",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("calendarId"),
        Index(value = ["id", "calendarId"], unique = true),
        Index(value = ["calendarId", "uid", "recurrenceKey"], unique = true),
    ],
)
data class EventEntity(
    @PrimaryKey val id: String,
    val calendarId: String,
    val uid: String,
    val recurrenceKey: String,
    val title: String,
    val location: String?,
    val notes: String?,
    val originalTimeZoneId: String?,
    val isAllDay: Boolean,
    val recurrenceRule: String?,
    val status: String,
)

@Entity(
    tableName = "event_instances",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id", "calendarId"],
            childColumns = ["eventId", "calendarId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["eventId", "calendarId"]), Index("calendarId")],
)
data class EventInstanceEntity(
    @PrimaryKey val instanceId: String,
    val eventId: String,
    val calendarId: String,
    val isAllDay: Boolean,
    val startEpochMillis: Long?,
    val endEpochMillis: Long?,
    val startDate: String?,
    val endDateExclusive: String?,
)

@Entity(
    tableName = "sync_state",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SyncStateEntity(
    @PrimaryKey val calendarId: String,
    val syncToken: String?,
)

@Entity(
    tableName = "sync_coverage",
    primaryKeys = ["calendarId", "startEpochMillis", "endEpochMillis", "zoneId"],
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CoverageEntity(
    val calendarId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val zoneId: String,
)

@Entity(tableName = "widget_configuration")
data class WidgetConfigEntity(
    @PrimaryKey val appWidgetId: Int,
    val rangeDays: Int = 7,
) {
    init {
        rangeDays.requireSupportedWidgetRangeDays()
    }
}

@Entity(
    tableName = "widget_calendar",
    primaryKeys = ["appWidgetId", "calendarId"],
    foreignKeys = [
        ForeignKey(
            entity = WidgetConfigEntity::class,
            parentColumns = ["appWidgetId"],
            childColumns = ["appWidgetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("calendarId")],
)
data class WidgetCalendarCrossRef(
    val appWidgetId: Int,
    val calendarId: String,
)

data class EventInstanceRow(
    val instanceId: String,
    val eventId: String,
    val calendarId: String,
    val title: String,
    val calendarColorArgb: Int,
    val isAllDay: Boolean,
    val startEpochMillis: Long?,
    val endEpochMillis: Long?,
    val startDate: String?,
    val endDateExclusive: String?,
    val location: String?,
)

data class EventDetailRow(
    val instanceId: String,
    val eventId: String,
    val calendarId: String,
    val calendarName: String,
    val calendarColorArgb: Int,
    val title: String,
    val location: String?,
    val notes: String?,
    val isAllDay: Boolean,
    val startEpochMillis: Long?,
    val endEpochMillis: Long?,
    val startDate: String?,
    val endDateExclusive: String?,
    val originalTimeZoneId: String?,
    val recurrenceRule: String?,
)
