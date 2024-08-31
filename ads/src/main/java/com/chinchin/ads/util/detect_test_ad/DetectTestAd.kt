package com.chinchin.ads.util.detect_test_ad

import android.content.Context

class DetectTestAd {
    private var showAllAds = false

    fun setShowAds() {
        showAllAds = true
    }

    fun detectedTestAd(showAds: Boolean, context: Context) {
        val editor = context.getSharedPreferences("MY_PRE", Context.MODE_PRIVATE).edit()
        editor.putBoolean(testAd, showAds)
        editor.apply()
    }

    fun isTestAd(context: Context): Boolean {
        return context.getSharedPreferences("MY_PRE", Context.MODE_PRIVATE).getBoolean(testAd, false) && !showAllAds
    }

    companion object {
        var testAd: String = "Test Ad"

        private var INSTANCE: DetectTestAd? = null

        @JvmStatic
        val instance: DetectTestAd?
            get() {
                if (INSTANCE == null) {
                    INSTANCE = DetectTestAd()
                }
                return INSTANCE
            }
    }
}
