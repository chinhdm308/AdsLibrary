package com.chinchin.ads.util

import android.content.Context
import android.content.SharedPreferences

object SharePreferenceUtils {
    private const val PREF_NAME = "ad_pref"
    private const val KEY_CURRENT_TOTAL_REVENUE_001_AD = "KEY_CURRENT_TOTAL_REVENUE_001_AD"
    private const val KEY_CURRENT_TOTAL_REVENUE_AD = "KEY_CURRENT_TOTAL_REVENUE_AD"
    private const val KEY_INSTALL_TIME = "KEY_INSTALL_TIME"
    private const val KEY_PUSH_EVENT_REVENUE_3_DAY = "KEY_PUSH_EVENT_REVENUE_3_DAY"
    private const val KEY_PUSH_EVENT_REVENUE_7_DAY = "KEY_PUSH_EVENT_REVENUE_7_DAY"
    private const val KEY_LAST_IMPRESSION_INTERSTITIAL_TIME = "KEY_LAST_IMPRESSION_INTERSTITIAL_TIME"

    private var pre: SharedPreferences? = null

    fun init(context: Context) {
        pre = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    val installTime: Long
        get() = pre!!.getLong(KEY_INSTALL_TIME, 0)

    @JvmStatic
    fun setInstallTime() {
        pre!!.edit().putLong(KEY_INSTALL_TIME, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    val currentTotalRevenueAd: Float
        get() = pre!!.getFloat(KEY_CURRENT_TOTAL_REVENUE_AD, 0f)

    @JvmStatic
    fun updateCurrentTotalRevenue001Ad(revenue: Float) {
        pre!!.edit().putFloat(KEY_CURRENT_TOTAL_REVENUE_001_AD, revenue).apply()
    }

    @JvmStatic
    fun updateCurrentTotalRevenueAd(revenue: Float) {
        var currentTotalRevenue = pre!!.getFloat(KEY_CURRENT_TOTAL_REVENUE_AD, 0f)
        currentTotalRevenue += (revenue / 1000000.0).toFloat()
        pre!!.edit().putFloat(KEY_CURRENT_TOTAL_REVENUE_AD, currentTotalRevenue).apply()
    }

    @JvmStatic
    val currentTotalRevenue001Ad: Float
        get() = pre!!.getFloat(KEY_CURRENT_TOTAL_REVENUE_001_AD, 0f)

    @JvmStatic
    val isPushRevenue3Day: Boolean
        get() = pre!!.getBoolean(KEY_PUSH_EVENT_REVENUE_3_DAY, false)

    @JvmStatic
    fun setPushedRevenue3Day() {
        pre!!.edit().putBoolean(KEY_PUSH_EVENT_REVENUE_3_DAY, true).apply()
    }

    @JvmStatic
    val isPushRevenue7Day: Boolean
        get() = pre!!.getBoolean(KEY_PUSH_EVENT_REVENUE_7_DAY, false)

    @JvmStatic
    fun setPushedRevenue7Day() {
        pre!!.edit().putBoolean(KEY_PUSH_EVENT_REVENUE_7_DAY, true).apply()
    }
}
