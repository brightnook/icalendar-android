package com.brightnook.nookcalendar.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.brightnook.nookcalendar.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

private const val LOG_TAG = "NookCalendarAds"

internal fun debugConsentGeographyFor(isDebugBuild: Boolean): Int? =
    if (isDebugBuild) ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA else null

class AdConsentManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private val readyCallbacks = mutableListOf<() -> Unit>()
    private var adsInitializationStarted = false
    private var adsInitializationCompleted = false

    fun initialize(activity: Activity, onReady: () -> Unit) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(applicationContext)
        val debugGeography = debugConsentGeographyFor(BuildConfig.DEBUG)
        val request = ConsentRequestParameters.Builder()
            .apply {
                debugGeography?.let { geography ->
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(applicationContext)
                            .setDebugGeography(geography)
                            .build(),
                    )
                    Log.i(LOG_TAG, "UMP debug geography forced to EEA")
                }
            }
            .build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            request,
            {
                Log.i(
                    LOG_TAG,
                    "UMP info updated: canRequestAds=${consentInformation.canRequestAds()}, " +
                        "privacyOptions=${consentInformation.privacyOptionsRequirementStatus}",
                )
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(LOG_TAG, "UMP consent form did not load: ${formError.message}")
                    }
                    initializeAdsWhenPermitted(consentInformation, onReady)
                }
            },
            { requestError ->
                Log.w(LOG_TAG, "UMP info update failed: ${requestError.message}")
                initializeAdsWhenPermitted(consentInformation, onReady)
            },
        )
    }

    fun privacyOptionsRequired(): Boolean =
        UserMessagingPlatform.getConsentInformation(applicationContext)
            .privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            onDismissed()
        }
    }

    private fun initializeAdsWhenPermitted(
        consentInformation: ConsentInformation,
        onReady: () -> Unit,
    ) {
        if (!consentInformation.canRequestAds()) {
            Log.w(LOG_TAG, "Ads unavailable because UMP cannot request ads")
            return
        }

        var callNow = false
        var startInitialization = false
        synchronized(lock) {
            when {
                adsInitializationCompleted -> callNow = true
                adsInitializationStarted -> readyCallbacks += onReady
                else -> {
                    adsInitializationStarted = true
                    readyCallbacks += onReady
                    startInitialization = true
                }
            }
        }
        if (callNow) {
            Log.i(LOG_TAG, "Ads already initialized")
            onReady()
        } else if (startInitialization) {
            Log.i(LOG_TAG, "Starting Mobile Ads initialization")
            MobileAds.initialize(applicationContext) {
                Log.i(LOG_TAG, "Mobile Ads initialization completed")
                val callbacks = synchronized(lock) {
                    adsInitializationCompleted = true
                    readyCallbacks.toList().also { readyCallbacks.clear() }
                }
                callbacks.forEach { it() }
            }
        }
    }
}
