package com.chinchin.ads.callback

import com.google.android.gms.ads.rewarded.RewardItem

interface RewardCallback {
    fun onEarnedReward(rewardItem: RewardItem?)

    fun onAdClosed()

    fun onAdFailedToShow(codeError: Int)

    fun onAdImpression()
}
