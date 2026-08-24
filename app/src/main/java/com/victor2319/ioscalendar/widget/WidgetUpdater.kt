package com.victor2319.ioscalendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.victor2319.ioscalendar.CalendarApplication
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.theme.createWidgetThemePreview
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun interface WidgetRefreshNotifier {
    fun refreshAll()
}

class WidgetUpdater(private val context: Context) : WidgetRefreshNotifier {
    override fun refreshAll() {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, AgendaWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(provider)
        if (ids.isEmpty()) return
        val theme = renderedTheme()
        ids.forEach { appWidgetId -> manager.updateAppWidget(appWidgetId, views(manager, appWidgetId, theme)) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_events)
    }

    fun refresh(appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val theme = renderedTheme()
        appWidgetIds.forEach { appWidgetId -> manager.updateAppWidget(appWidgetId, views(manager, appWidgetId, theme)) }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_events)
    }

    private fun views(
        manager: AppWidgetManager,
        appWidgetId: Int,
        renderedTheme: RenderedWidgetTheme,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.agenda_widget).apply {
        val widthDp = manager.getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val presentation = widgetPresentationForWidth(widthDp)
        val theme = renderedTheme.presentation
        setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (theme.mode == WidgetThemeMode.LIGHT) R.drawable.widget_background_rounded else R.drawable.widget_background_black_rounded,
        )
        setInt(R.id.widget_content, "setBackgroundResource", theme.contentBackgroundResource())
        setViewVisibility(
            R.id.widget_background_image,
            if (renderedTheme.backgroundBitmap == null) View.GONE else View.VISIBLE,
        )
        renderedTheme.backgroundBitmap?.let { bitmap -> setImageViewBitmap(R.id.widget_background_image, bitmap) }
        setTextColor(R.id.widget_title, theme.primaryTextColor)
        setTextColor(R.id.widget_header_date, theme.secondaryTextColor)
        setTextColor(R.id.widget_empty, theme.secondaryTextColor)
        setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, presentation.titleTextSizeSp)
        setViewVisibility(R.id.widget_header_date, if (presentation.showHeaderDate) View.VISIBLE else View.GONE)
        val app = context.applicationContext as CalendarApplication
        val settings = runBlocking { app.container.settingsStore.settings.first() }
        val localizedContext = WidgetLocaleContext.resolve(context, settings.language)
        setTextViewText(R.id.widget_title, localizedContext.getString(R.string.widget_title))
        setTextViewText(R.id.widget_empty, localizedContext.getString(R.string.widget_empty))
        setContentDescription(R.id.widget_refresh, localizedContext.getString(R.string.widget_refresh))
        setTextViewText(R.id.widget_header_date, WidgetDateTimeFormatter.formatHeaderDate(
            LocalDate.now(),
            localizedContext.resources.configuration.locales[0],
        ))
        setTextViewTextSize(R.id.widget_header_date, TypedValue.COMPLEX_UNIT_SP, if (presentation.isCompact) 10f else 12f)
        val serviceIntent = Intent(context, AgendaWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(EXTRA_WIDGET_WIDTH_DP, widthDp)
            .putExtra(EXTRA_WIDGET_THEME_MODE, theme.mode.name)
            .setData(Uri.parse("ioscalendar-widget://agenda/$appWidgetId/$widthDp/${theme.mode.name}/${theme.imageSource?.name?.hashCode() ?: 0}"))
        setRemoteAdapter(R.id.widget_events, serviceIntent)
        setEmptyView(R.id.widget_events, R.id.widget_empty)
        setPendingIntentTemplate(R.id.widget_events, WidgetIntentFactory.eventPendingIntent(context, appWidgetId))
        setOnClickPendingIntent(R.id.widget_title, WidgetIntentFactory.todayPendingIntent(context, appWidgetId))
        setOnClickPendingIntent(R.id.widget_refresh, WidgetIntentFactory.refreshPendingIntent(context, appWidgetId))
    }

    private fun renderedTheme(): RenderedWidgetTheme {
        val application = context.applicationContext as CalendarApplication
        val settings = runBlocking { application.container.settingsStore.settings.first() }.theme
        val imageSource = settings.assetKey?.let(application.container.themeAssetStore::fileFor)
        val selected = widgetThemePresentation(settings, imageSource)
        val bitmap = selected.imageSource?.let(::loadStaticPreview)
        return RenderedWidgetTheme(
            presentation = renderableWidgetTheme(selected, imagePreviewAvailable = bitmap != null),
            backgroundBitmap = bitmap,
        )
    }

    private fun loadStaticPreview(source: File): Bitmap? {
        val directory = File(context.cacheDir, PREVIEW_DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) return null
        val destination = File(directory, "${source.name.hashCode().toUInt().toString(16)}.png")
        if (!destination.isFile && !createWidgetThemePreview(
                source = source,
                destination = destination,
                maxWidthPx = PREVIEW_MAX_WIDTH_PX,
                maxHeightPx = PREVIEW_MAX_HEIGHT_PX,
            )
        ) {
            return null
        }
        return BitmapFactory.decodeFile(destination.path)
    }

    companion object {
        const val EXTRA_WIDGET_WIDTH_DP = "com.victor2319.ioscalendar.widget.WIDTH_DP"
        const val EXTRA_WIDGET_THEME_MODE = "com.victor2319.ioscalendar.widget.THEME_MODE"
        private const val PREVIEW_DIRECTORY = "widget-theme-previews"
        private const val PREVIEW_MAX_WIDTH_PX = 384
        private const val PREVIEW_MAX_HEIGHT_PX = 576
    }
}

private data class RenderedWidgetTheme(
    val presentation: WidgetThemePresentation,
    val backgroundBitmap: Bitmap?,
)

private fun WidgetThemePresentation.contentBackgroundResource(): Int = when (mode) {
    WidgetThemeMode.LIGHT -> R.drawable.widget_content_light_rounded
    WidgetThemeMode.BLACK -> R.drawable.widget_content_black_rounded
    WidgetThemeMode.IMAGE -> R.drawable.widget_content_image_rounded
}
