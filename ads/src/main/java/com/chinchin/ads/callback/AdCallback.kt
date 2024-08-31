package com.chinchin.ads.callback

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd

class AdCallback {
    fun onNextAction() {}

    fun onAdClosed() {}

    fun onAdFailedToLoad(i: LoadAdError?) {}

    fun onAdFailedToShow(adError: AdError?) {}

    fun onAdLeftApplication() {}

    fun onAdLoaded() {}

    fun onAdSplashReady() {}

    fun onInterstitialLoad(interstitialAd: InterstitialAd?) {}

    fun onAdClicked() {}

    fun onAdImpression() {}

    fun onRewardAdLoaded(rewardedAd: RewardedAd?) {}

    fun onRewardAdLoaded(rewardedAd: RewardedInterstitialAd?) {}

    fun onUnifiedNativeAdLoaded(unifiedNativeAd: NativeAd) {}

    fun onInterstitialShow() {}
}
