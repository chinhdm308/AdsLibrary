package com.chinchin.ads.util.reward

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.IntDef
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustConfig
import com.chinchin.ads.event.AdType
import com.chinchin.ads.event.AdmobEvent.logPaidAdImpression
import com.chinchin.ads.util.Constants
import com.chinchin.ads.util.NetworkUtil.isNetworkActive
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.common.base.Strings

class RewardAdModel(@JvmField val idAd: String) {
    private val TAG = "RewardAdModel"


    @Status
    private var status = Status.IDLE
    private var mRewardedAd: RewardedAd? = null

    fun loadReward(context: Context, callback: RewardAdCallback?) {
        if (status != Status.ON_LOADING) {
            status = Status.ON_LOADING
            loadWithIdAd(context, idAd, callback)
        }
    }

    fun loadAndShowReward(context: Context, callback: RewardAdCallback) {
        if (mRewardedAd == null) {
            loadReward(context, object : RewardAdCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    callback.onAdFailedToLoad(loadAdError)
                }

                override fun onAdLoaded(isSuccessful: Boolean?) {
                    super.onAdLoaded(isSuccessful)
                    callback.onAdLoaded(isSuccessful)
                    if (isSuccessful!!) {
                        showReward(context, callback)
                    } else {
                        callback.onNextAction()
                    }
                }
            })
        } else {
            showReward(context, callback)
        }
    }

    val isCalledSuccessfulAd: Boolean
        get() = mRewardedAd != null

    fun showReward(context: Context, callback: RewardAdCallback?) {
        if (!isNetworkActive(context)) {
            status = Status.ON_DISMISS
            if (callback != null) {
                callback.onAdFailedToShow(Constants.NO_INTERNET_ERROR)
                callback.onNextAction()
            }
            return
        }
        if (mRewardedAd == null) {
            status = Status.ON_DISMISS
            if (callback != null) {
                callback.onAdFailedToShow(Constants.AD_NOT_AVAILABLE_ERROR)
                callback.onNextAction()
            }
            return
        }
        if (status != Status.ON_STARTING_SHOW) {
            status = Status.ON_STARTING_SHOW
            mRewardedAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdImpression() {
                    super.onAdImpression()
                    callback!!.onAdImpression()
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    callback!!.onAdClicked()
                }

                override fun onAdShowedFullScreenContent() {
                    status = Status.ON_SHOWING
                    mRewardedAd = null
                    callback?.onAdShowed()
                    loadReward(context, callback)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "onAdFailedToShowFullScreenContent: $adError")
                    status = Status.ON_DISMISS
                    mRewardedAd = null
                    if (callback != null) {
                        callback.onAdFailedToShow(adError)
                        callback.onNextAction()
                    }
                }

                override fun onAdDismissedFullScreenContent() {
                    status = Status.ON_DISMISS
                    if (callback != null) {
                        callback.onAdDismissed()
                        callback.onNextAction()
                    }
                }
            }
            if (context is Activity) {
                context.runOnUiThread {
                    mRewardedAd!!.show(context) { rewardItem: RewardItem? ->
                        callback!!.onUserEarnedReward(
                            rewardItem!!
                        )
                    }
                }
            } else {
                callback!!.onAdFailedToShow(Constants.CONVERT_ACTIVITY_ERROR)
            }
        }
    }

    private fun loadWithIdAd(context: Context, idAd: String, callback: RewardAdCallback?) {
        if (!isNetworkActive(context)) {
            status = Status.ON_LOADED

            if (callback != null) {
                callback.onAdFailedToLoad(Constants.NO_INTERNET_ERROR)
                callback.onAdLoaded(false)
            }
            return
        }
        if (Strings.isNullOrEmpty(idAd)) {
            status = Status.ON_LOADED
            if (callback != null) {
                callback.onAdFailedToLoad(Constants.AD_NOT_HAVE_ID)
                callback.onAdLoaded(false)
            }
            return
        }
        Log.d(TAG, "loadWithIdAd: $idAd")
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, idAd, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                if (Strings.isNullOrEmpty(idAd)) {
                    status = Status.ON_LOADED
                    if (callback != null) {
                        callback.onAdFailedToLoad(loadAdError)
                        callback.onAdLoaded(false)
                    }
                } else {
                    loadWithIdAd(context, idAd, callback)
                }
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                super.onAdLoaded(rewardedAd)
                status = Status.ON_LOADED
                mRewardedAd = rewardedAd
                rewardedAd.onPaidEventListener = OnPaidEventListener { adValue: AdValue ->
                    logPaidAdImpression(adValue, rewardedAd.adUnitId, AdType.REWARDED)
                    trackRevenue(adValue)
                }
                callback?.onAdLoaded(true)
            }
        })
    }

    //push adjust
    private fun trackRevenue(adValue: AdValue) {
        val adName = mRewardedAd!!.responseInfo.loadedAdapterResponseInfo!!.adSourceName.toString()
        val valueMicros = adValue.valueMicros / 1000000.0
        Log.d("AdjustRevenue", "adName: $adName - valueMicros: $valueMicros")
        // send ad revenue info to Adjust
        val adRevenue = AdjustAdRevenue(AdjustConfig.AD_REVENUE_ADMOB)
        adRevenue.setRevenue(valueMicros, adValue.currencyCode)
        adRevenue.adRevenueNetwork = adName
        Adjust.trackAdRevenue(adRevenue)
    }

    @IntDef(Status.IDLE, Status.ON_LOADING, Status.ON_LOADED, Status.ON_STARTING_SHOW, Status.ON_SHOWING, Status.ON_DISMISS)
    annotation class Status {
        companion object {
            const val IDLE: Int = 0
            const val ON_LOADING: Int = 1
            const val ON_LOADED: Int = 2
            const val ON_STARTING_SHOW: Int = 3
            const val ON_SHOWING: Int = 4
            const val ON_DISMISS: Int = 5
        }
    }
}
