package com.victor2319.ioscalendar.widget

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
import com.victor2319.ioscalendar.CalendarApplication
import com.victor2319.ioscalendar.R
import kotlinx.coroutines.flow.collectLatest
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
        render()
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state -> bind(state) }
        }
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply { setText(R.string.widget_config_title) })
        calendars = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(calendars)
        ranges = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        WidgetRange.entries.forEach { range ->
            ranges.addView(RadioButton(this).apply { id = range.ordinal + 1; setText(rangeLabel(range)); setOnClickListener { viewModel.setRange(range) } })
        }
        root.addView(ranges)
        root.addView(Button(this).apply {
            setText(R.string.widget_config_save)
            setOnClickListener {
                viewModel.save {
                    WidgetUpdater(applicationContext).refresh(intArrayOf(appWidgetId))
                    setResult(RESULT_OK, intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                    finish()
                }
            }
        })
        val scrollView = ScrollView(this).apply {
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
            calendars.addView(CheckBox(this).apply {
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
}

internal fun widgetConfigurationTopPadding(statusBarInsetPx: Int, contentPaddingPx: Int): Int =
    statusBarInsetPx + contentPaddingPx

private class WidgetConfigViewModelFactory(
    private val appWidgetId: Int,
    private val preferences: WidgetPreferences,
    private val calendars: kotlinx.coroutines.flow.Flow<List<com.victor2319.ioscalendar.domain.CalendarSummary>>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WidgetConfigViewModel(appWidgetId, preferences, calendars) as T
}
