package com.chinchin.ads.util.manager.banner

import android.app.Activity
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.chinchin.ads.util.Admob
import com.google.android.gms.ads.AdRequest

class BannerManager(
    private val currentActivity: Activity,
    private val lifecycleOwner: LifecycleOwner,
    private val builder: BannerBuilder
) : LifecycleEventObserver {

    private val tag = "BannerManager"

    internal enum class State {
        LOADING, LOADED
    }

    private val state = State.LOADED

    private var isReloadAds = false
    private var isAlwaysReloadOnResume = false
    private val isShowLoadingBanner = true
    private var intervalReloadBanner: Long = 0
    private var isStop = false
    private var countDownTimer: CountDownTimer? = null
    private var isStopReload = false

    private val adRequest: AdRequest = AdRequest.Builder().build()

    fun notReloadInNextResume() {
        isStopReload = true
    }

    fun setIntervalReloadBanner(intervalReloadBanner: Long) {
        if (intervalReloadBanner > 0) {
            this.intervalReloadBanner = intervalReloadBanner
        }
        countDownTimer = object : CountDownTimer(this.intervalReloadBanner, 1000) {
            override fun onTick(l: Long) {
            }

            override fun onFinish() {
                loadBanner()
            }
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                Log.d(tag, "onStateChanged: ON_CREATE")
                loadBanner()
            }

            Lifecycle.Event.ON_RESUME -> {
                if (countDownTimer != null && isStop) {
                    countDownTimer!!.start()
                }
                val valueLog = isStop.toString() + " && " + (isReloadAds || isAlwaysReloadOnResume) + " && " + !isStopReload
                Log.d(tag, "onStateChanged: resume\n$valueLog")
                if (isStop && (isReloadAds || isAlwaysReloadOnResume) && !isStopReload) {
                    isReloadAds = false
                    loadBanner()
                }
                isStopReload = false
                isStop = false
            }

            Lifecycle.Event.ON_PAUSE -> {
                Log.d(tag, "onStateChanged: ON_PAUSE")
                isStop = true
                if (countDownTimer != null) {
                    countDownTimer!!.cancel()
                }
            }

            Lifecycle.Event.ON_DESTROY -> {
                Log.d(tag, "onStateChanged: ON_DESTROY")
                lifecycleOwner.lifecycle.removeObserver(this)
            }

            else -> {}
        }
    }

    private fun loadBanner() {
        Log.d(tag, "loadBanner: " + builder.idAd)
        if (Admob.isShowAllAds) {
            Admob.getInstance().loadBannerFloor(currentActivity, builder.idAd)
        } else {
            Admob.getInstance().hideBanner(currentActivity)
        }
    }


    fun setReloadAds() {
        isReloadAds = true
    }

    fun reloadAdNow() {
        loadBanner()
    }

    fun setAlwaysReloadOnResume(isAlwaysReloadOnResume: Boolean) {
        this.isAlwaysReloadOnResume = isAlwaysReloadOnResume
    }
}
