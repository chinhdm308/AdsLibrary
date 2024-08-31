package com.chinchin.ads.event

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

object FirebaseAnalyticsUtil {
    private const val TAG = "FirebaseAnalyticsUtil"

    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }

    fun logEventWithAds(params: Bundle?) {
        firebaseAnalytics?.logEvent("admob_paid_ad_impression", params)
    }

    fun logPaidAdImpressionValue(bundle: Bundle?) {
        firebaseAnalytics?.logEvent("admob_paid_ad_impression_value", bundle)
    }

    fun logClickAdsEvent(bundle: Bundle?) {
        firebaseAnalytics?.logEvent("admob_event_user_click_ads", bundle)
    }

    fun logCurrentTotalRevenueAd(eventName: String?, bundle: Bundle?) {
        firebaseAnalytics?.logEvent(eventName!!, bundle)
    }

    fun logTotalRevenue001Ad(bundle: Bundle?) {
        firebaseAnalytics?.logEvent("admob_daily_ads_revenue", bundle)
        firebaseAnalytics?.logEvent("paid_ad_impression_value_001", bundle)
    }

    @JvmStatic
    fun logTimeLoadAdsSplash(timeLoad: Int) {
        Log.d(TAG, String.format("Time load ads splash %s.", timeLoad))
        val bundle = Bundle()
        bundle.putString("time_load", timeLoad.toString())
        firebaseAnalytics?.logEvent("event_time_load_ads_splash", bundle)
    }

    @JvmStatic
    fun logTimeLoadShowAdsInter(timeLoad: Double) {
        Log.d(TAG, String.format("Time show ads  %s", timeLoad))
        val bundle = Bundle()
        bundle.putString("time_show", timeLoad.toString())
        firebaseAnalytics?.logEvent("event_time_show_ads_inter", bundle)
    }

    fun logConfirmPurchaseGoogle(orderId: String?, purchaseId: String?, purchaseToken: String) {
        val tokenPart1: String
        val tokenPart2: String
        if (purchaseToken.length > 100) {
            tokenPart1 = purchaseToken.substring(0, 100)
            tokenPart2 = purchaseToken.substring(100)
        } else {
            tokenPart1 = purchaseToken
            tokenPart2 = "EMPTY"
        }
        val bundle = Bundle()
        bundle.putString("purchase_order_id", orderId)
        bundle.putString("purchase_package_id", purchaseId)
        bundle.putString("purchase_token_part_1", tokenPart1)
        bundle.putString("purchase_token_part_2", tokenPart2)
        firebaseAnalytics?.logEvent("confirm_purchased_with_google", bundle)
        Log.d(TAG, "logConfirmPurchaseGoogle: tracked")
    }

    fun logRevenuePurchase(value: Double) {
        val bundle = Bundle()
        bundle.putDouble(FirebaseAnalytics.Param.VALUE, value)
        bundle.putString(FirebaseAnalytics.Param.CURRENCY, "USD")
        firebaseAnalytics?.logEvent("user_purchased_value", bundle)
    }
}