package com.chinchin.ads.callback

import com.google.android.gms.ads.LoadAdError

class BannerCallBack {
    fun onEarnRevenue(revenue: Double?) {}

    fun onAdFailedToLoad(loadAdError: LoadAdError?) {}

    fun onAdLoadSuccess() {}

    fun onAdClicked() {}

    fun onAdImpression() {}
}