package com.brightnook.nookcalendar.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.brightnook.nookcalendar.CalendarApplication
import com.brightnook.nookcalendar.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var viewModel: WidgetConfigViewModel
    private lateinit var calendars: LinearLayout
    private lateinit var ranges: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val app = application as CalendarApplication
        viewModel = ViewModelProvider(this, WidgetConfigViewModelFactory(appWidgetId, app.container.widgetPreferences, app.container.calendarRepository.observeCalendars()))[WidgetConfigViewModel::class.java]
        lifecycleScope.launch {
            val settings = app.container.settingsStore.settings.first()
            render(WidgetLocaleContext.resolve(this@WidgetConfigActivity, settings.language))
            viewModel.uiState.collectLatest { state -> bind(state) }
        }
    }

    private fun render(localizedContext: android.content.Context) {
        title = localizedContext.getString(R.string.widget_config_title)
        val root = LinearLayout(localizedContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(localizedContext).apply { setText(R.string.widget_config_title) })
        calendars = LinearLayout(localizedContext).apply { orientation = LinearLayout.VERTICAL }
        root.addView(calendars)
        ranges = RadioGroup(localizedContext).apply { orientation = RadioGroup.VERTICAL }
        val thirtyDayRangeMarginPx = (THIRTY_DAY_RANGE_MARGIN_DP * localizedContext.resources.displayMetrics.density).toInt()
        WidgetRange.entries.forEach { range ->
            ranges.addView(
                RadioButton(localizedContext).apply {
                    id = range.ordinal + 1
                    setText(rangeLabel(range))
                    setOnClickListener { viewModel.setRange(range) }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (range == WidgetRange.THIRTY_DAYS) {
                        topMargin = thirtyDayRangeMarginPx
                        bottomMargin = thirtyDayRangeMarginPx
                    }
                },
            )
        }
        root.addView(ranges)
        root.addView(Button(localizedContext).apply {
            setText(R.string.widget_config_save)
            setOnClickListener {
                viewModel.save {
                    WidgetUpdater(applicationContext).refresh(intArrayOf(appWidgetId))
                    setResult(RESULT_OK, intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                    finish()
                }
            }
        })
        val scrollView = ScrollView(localizedContext).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(
                48,
                widgetConfigurationTopPadding(systemBars.top, contentPaddingPx = 48),
                48,
                systemBars.bottom + 48,
            )
            insets
        }
        setContentView(scrollView)
        ViewCompat.requestApplyInsets(scrollView)
    }

    private fun bind(state: WidgetConfigUiState) {
        calendars.removeAllViews()
        state.calendars.forEach { calendar ->
            calendars.addView(CheckBox(calendars.context).apply {
                text = calendar.name
                isChecked = calendar.id in state.selectedCalendarIds
                setOnCheckedChangeListener { _, checked -> viewModel.setCalendarSelected(calendar.id, checked) }
            })
        }
        ranges.check(state.range.ordinal + 1)
    }

    private fun rangeLabel(range: WidgetRange): Int = when (range) {
        WidgetRange.TODAY -> R.string.widget_range_today
        WidgetRange.THREE_DAYS -> R.string.widget_range_three_days
        WidgetRange.SEVEN_DAYS -> R.string.widget_range_seven_days
        WidgetRange.THIRTY_DAYS -> R.string.widget_range_thirty_days
    }

    private companion object {
        const val THIRTY_DAY_RANGE_MARGIN_DP = 16
    }
}

internal fun widgetConfigurationTopPadding(statusBarInsetPx: Int, contentPaddingPx: Int): Int =
    statusBarInsetPx + contentPaddingPx

private class WidgetConfigViewModelFactory(
    private val appWidgetId: Int,
    private val preferences: WidgetPreferences,
    private val calendars: kotlinx.coroutines.flow.Flow<List<com.brightnook.nookcalendar.domain.CalendarSummary>>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WidgetConfigViewModel(appWidgetId, preferences, calendars) as T
}
