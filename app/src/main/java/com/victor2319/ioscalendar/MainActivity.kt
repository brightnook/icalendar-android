package com.victor2319.ioscalendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.victor2319.ioscalendar.ui.AppRoot
import com.victor2319.ioscalendar.ui.main.CalendarLaunchTarget
import com.victor2319.ioscalendar.ui.main.CalendarLaunchTargetDelivery
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val launchTargetDelivery = CalendarLaunchTargetDelivery()
    private val launchTarget = MutableStateFlow<CalendarLaunchTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val target by launchTarget.collectAsState()
                AppRoot(
                    container = (application as CalendarApplication).container,
                    launchTarget = target,
                    onLaunchTargetConsumed = ::markLaunchTargetConsumed,
                )
            }
        }
        deliverLaunchTarget(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverLaunchTarget(intent)
    }

    private fun deliverLaunchTarget(intent: Intent?) {
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
}
