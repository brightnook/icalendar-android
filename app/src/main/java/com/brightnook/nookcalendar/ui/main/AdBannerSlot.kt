package com.brightnook.nookcalendar.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.key
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.brightnook.nookcalendar.monetization.MonetizationUiState
import kotlin.math.roundToInt

const val AD_BANNER_SLOT_TAG = "ad-banner-slot"
const val ANCHORED_ADAPTIVE_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

internal enum class AdBannerLoadState { LOADING, FAILED }

internal fun anchoredAdaptiveBannerWidthDp(width: androidx.compose.ui.unit.Dp): Int =
    width.value.roundToInt()

@Composable
fun AdBannerSlot(
    state: MonetizationUiState,
    adUnitId: String = ANCHORED_ADAPTIVE_TEST_AD_UNIT_ID,
    modifier: Modifier = Modifier,
) {
    if (!state.canShowAds) return

    var loadState by remember(adUnitId) { mutableStateOf(AdBannerLoadState.LOADING) }
    AdBannerSlotContent(
        state = state,
        loadState = loadState,
        modifier = modifier,
    ) {
        AdaptiveBannerAd(
            adUnitId = adUnitId,
            onFailedToLoad = { loadState = AdBannerLoadState.FAILED },
        )
    }
}

@Composable
internal fun AdBannerSlotContent(
    state: MonetizationUiState,
    loadState: AdBannerLoadState,
    modifier: Modifier = Modifier,
    adContent: @Composable () -> Unit = {},
) {
    if (!state.canShowAds) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(AD_BANNER_SLOT_TAG),
    ) {
        if (loadState != AdBannerLoadState.FAILED) adContent()
    }
}

@Composable
private fun AdaptiveBannerAd(
    adUnitId: String,
    onFailedToLoad: () -> Unit,
) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val adWidthDp = anchoredAdaptiveBannerWidthDp(maxWidth)
        key(adWidthDp) {
            AndroidView(
                factory = {
                    AdView(context).apply {
                        this.adUnitId = adUnitId
                        setAdSize(
                            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp),
                        )
                        adListener = object : AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                onFailedToLoad()
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                onRelease = AdView::destroy,
            )
        }
    }
}
