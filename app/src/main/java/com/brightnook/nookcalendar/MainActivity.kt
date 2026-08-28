package com.brightnook.nookcalendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.brightnook.nookcalendar.ui.AppRoot
import com.brightnook.nookcalendar.ui.LocalizedAppContent
import com.brightnook.nookcalendar.domain.AppSettings
import com.brightnook.nookcalendar.ui.main.CalendarLaunchTarget
import com.brightnook.nookcalendar.ui.main.CalendarLaunchTargetDelivery
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private lateinit var launchTargetDelivery: CalendarLaunchTargetDelivery
    private lateinit var launchTarget: MutableStateFlow<CalendarLaunchTarget?>
    private val openSettingsRequest = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchTargetDelivery = CalendarLaunchTargetDelivery.fromSavedState(
            savedInstanceState?.getStringArrayList(STATE_DELIVERED_TARGETS).orEmpty(),
        )
        launchTarget = MutableStateFlow(
            CalendarLaunchTargetDelivery.fromSavedState(
                savedInstanceState?.getStringArrayList(STATE_PENDING_TARGET).orEmpty(),
            ).acceptedTargets().singleOrNull(),
        )
        setContent {
            MaterialTheme {
                val target by launchTarget.collectAsState()
                val calendarApplication = application as CalendarApplication
                val settings by calendarApplication.container.settingsStore.settings.collectAsState(initial = AppSettings())
                LocalizedAppContent(settings.language) {
                    AppRoot(
                        container = calendarApplication.container,
                        zoneIds = calendarApplication.timeZoneIds,
                        launchTarget = target,
                        openSettingsRequest = openSettingsRequest.collectAsState().value,
                        onLaunchTargetConsumed = ::markLaunchTargetConsumed,
                    )
                }
            }
        }
        deliverLaunchTarget(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            STATE_DELIVERED_TARGETS,
            ArrayList(launchTargetDelivery.savedState()),
        )
        val pendingState = launchTarget.value?.let {
            CalendarLaunchTargetDelivery(setOf(it)).savedState()
        }.orEmpty()
        outState.putStringArrayList(STATE_PENDING_TARGET, ArrayList(pendingState))
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        (application as CalendarApplication).refreshTimeZone()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverLaunchTarget(intent)
    }

    private fun deliverLaunchTarget(intent: Intent?) {
        if (intent?.getBooleanExtra(com.brightnook.nookcalendar.widget.WidgetIntentFactory.EXTRA_OPEN_SETTINGS, false) == true) {
            openSettingsRequest.value += 1
        }
        val date = runCatching { intent?.getStringExtra(CalendarLaunchTargetDelivery.EXTRA_DATE) }.getOrNull()
        val instanceId = runCatching {
            intent?.getStringExtra(CalendarLaunchTargetDelivery.EXTRA_INSTANCE_ID)
        }.getOrNull()
        launchTargetDelivery.parseAndAccept(date, instanceId)?.let { launchTarget.value = it }
    }

    private fun markLaunchTargetConsumed(target: CalendarLaunchTarget) {
        launchTargetDelivery.markConsumed(target)
        if (launchTarget.value == target) launchTarget.value = null
    }

    private companion object {
        const val STATE_DELIVERED_TARGETS = "delivered_calendar_launch_targets"
        const val STATE_PENDING_TARGET = "pending_calendar_launch_target"
    }
}
