package com.chinchin.ads.util.reward

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem

open class RewardAdCallback {
    open fun onAdFailedToLoad(loadAdError: LoadAdError) {}

    open fun onAdLoaded(isSuccessful: Boolean?) {}

    open fun onAdDismissed() {}

    open fun onAdFailedToShow(adError: AdError) {}

    open fun onAdShowed() {}

    open fun onAdClicked() {}

    open fun onNextAction() {}

    open fun onAdImpression() {}

    open fun onUserEarnedReward(rewardItem: RewardItem) {}
}
