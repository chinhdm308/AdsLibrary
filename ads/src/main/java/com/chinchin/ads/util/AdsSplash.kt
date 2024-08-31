package com.chinchin.ads.util

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.chinchin.ads.callback.AdCallback
import com.chinchin.ads.callback.InterCallback
import java.util.Random

class AdsSplash {
    private enum class STATE {
        INTER, OPEN, NO_ADS
    }

    private var state = STATE.NO_ADS

    private var interAdId: String? = null
    private var openAdId: String? = null

    fun init(interAdId: String, openAdId: String, showInter: Boolean, showOpen: Boolean, rate: IntArray) {
        Log.d(TAG, "AdsSplash init")
        Log.d(TAG, "AdsSplash ==> interAdId: $interAdId")
        Log.d(TAG, "AdsSplash ==> openAdId: $openAdId")
        this.interAdId = interAdId
        this.openAdId = openAdId

        if (!Admob.isShowAllAds) {
            state = STATE.NO_ADS
        } else if (showInter && showOpen) {
            checkShowInterOpenSplash(rate)
        } else if (showInter) {
            state = STATE.INTER
        } else if (showOpen) {
            state = STATE.OPEN
        } else {
            state = STATE.NO_ADS
        }
    }

    private fun checkShowInterOpenSplash(rate: IntArray) {
        var rateInter = 0
        var rateOpen = 0
        try {
            rateInter = rate[0]
            rateOpen = rate[1]
        } catch (e: Exception) {
            Log.d(TAG, "checkShowInterOpenSplash: " + e.message)
        }
        Log.d(TAG, "rateInter: $rateInter - rateOpen: $rateOpen")
        if (rateInter >= 0 && rateOpen >= 0 && rateInter + rateOpen == 100) {
            val isShowOpenSplash = Random().nextInt(100) + 1 < rateOpen
            state = if (isShowOpenSplash) STATE.OPEN else STATE.INTER
        } else {
            state = STATE.NO_ADS
        }
    }


    fun showAdsSplashApi(activity: AppCompatActivity, openCallback: AdCallback?, interCallback: InterCallback) {
        Log.d(TAG, "state show: $state")
        when (state) {
            STATE.OPEN -> AppOpenManager.getInstance().loadOpenAppAdSplashFloor(activity, openAdId!!, true, openCallback!!)
            STATE.INTER -> Admob.getInstance().loadSplashInterAds3(activity, interAdId, 3000, 20000, interCallback, true)
            else -> interCallback.onNextAction()
        }
    }

    fun onCheckShowSplashWhenFail(activity: AppCompatActivity, openCallback: AdCallback?, interCallback: InterCallback?) {
        if (state == STATE.OPEN) AppOpenManager.getInstance().onCheckShowSplashWhenFailNew(activity, openCallback!!, 1000)
        else if (state == STATE.INTER) Admob.getInstance().onCheckShowSplashWhenFail(activity, interCallback, 1000)
    }

    companion object {
        private const val TAG = "AdsSplash"

        @Volatile
        private var INSTANCE: AdsSplash? = null

        @JvmStatic
        fun getInstance(): AdsSplash {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = AdsSplash()
                }
            }
            return INSTANCE!!
        }
    }
}