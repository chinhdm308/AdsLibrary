package com.chinchin.ads.callback

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd

open class InterCallback {
    fun onAdClosed() {}

    open fun onAdFailedToLoad(i: LoadAdError?) {}

    fun onAdFailedToShow(adError: AdError?) {}

    fun onAdLeftApplication() {}

    fun onAdLoaded() {}

    open fun onAdLoadSuccess(interstitialAd: InterstitialAd?) {}

    open fun onAdClicked() {}

    fun onAdImpression() {}

    fun onAdClosedByUser() {}

    fun onNextAction() {}

    fun onEarnRevenue(revenue: Double?) {}

    fun onLoadInter() {}

    fun onInterDismiss() {}
}
