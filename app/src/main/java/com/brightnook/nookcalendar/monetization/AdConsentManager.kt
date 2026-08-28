package com.brightnook.nookcalendar.monetization

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class AdConsentManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private val readyCallbacks = mutableListOf<() -> Unit>()
    private var adsInitializationStarted = false
    private var adsInitializationCompleted = false

    fun initialize(activity: Activity, onReady: () -> Unit) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(applicationContext)
        val request = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            request,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    initializeAdsWhenPermitted(consentInformation, onReady)
                }
            },
            {
                initializeAdsWhenPermitted(consentInformation, onReady)
            },
        )
    }

    private fun initializeAdsWhenPermitted(
        consentInformation: ConsentInformation,
        onReady: () -> Unit,
    ) {
        if (!consentInformation.canRequestAds()) return

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
            onReady()
        } else if (startInitialization) {
            MobileAds.initialize(applicationContext) {
                val callbacks = synchronized(lock) {
                    adsInitializationCompleted = true
                    readyCallbacks.toList().also { readyCallbacks.clear() }
                }
                callbacks.forEach { it() }
            }
        }
    }
}
