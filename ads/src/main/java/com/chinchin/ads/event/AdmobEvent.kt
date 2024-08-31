package com.chinchin.ads.event

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.chinchin.ads.util.Constants
import com.chinchin.ads.util.SharePreferenceUtils.currentTotalRevenueAd
import com.chinchin.ads.util.SharePreferenceUtils.installTime
import com.chinchin.ads.util.SharePreferenceUtils.isPushRevenue3Day
import com.chinchin.ads.util.SharePreferenceUtils.isPushRevenue7Day
import com.chinchin.ads.util.SharePreferenceUtils.setPushedRevenue3Day
import com.chinchin.ads.util.SharePreferenceUtils.setPushedRevenue7Day
import com.chinchin.ads.util.SharePreferenceUtils.updateCurrentTotalRevenue001Ad
import com.chinchin.ads.util.SharePreferenceUtils.updateCurrentTotalRevenueAd
import com.google.android.gms.ads.AdValue
import com.google.firebase.analytics.FirebaseAnalytics

object AdmobEvent {
    var OPEN_POSITION: Int = 0
    private const val TAG = "AdmobEvent"

    fun logEvent(context: Context, nameEvent: String, params: Bundle?) {
        Log.e(TAG, nameEvent)
        FirebaseAnalytics.getInstance(context).logEvent(nameEvent, params)
    }

    @JvmStatic
    fun logClickAdsEvent(adUnitId: String) {
        Log.d(TAG, String.format("User click ad for ad unit %s.", adUnitId))
        val bundle = Bundle()
        bundle.putString("ad_unit_id", adUnitId)
        FirebaseAnalyticsUtil.logClickAdsEvent(bundle)
        FacebookEventUtils.logClickAdsEvent(bundle)
    }

    private fun logEventWithAds(revenue: Float, precision: Int, adUnitId: String, network: String) {
        Log.d(
            TAG,
            String.format(
                "Paid event of value %.0f microcents in currency USD of precision %s%n occurred for ad unit %s from ad network %s",
                revenue,
                precision,
                adUnitId,
                network
            )
        )

        val params = Bundle() // Log ad value in micros.
        params.putDouble("valuemicros", revenue.toDouble())
        params.putString("currency", "USD")
        // These values below won’t be used in ROAS recipe.
        // But log for purposes of debugging and future reference.
        params.putInt("precision", precision)
        params.putString("adunitid", adUnitId)
        params.putString("network", network)

        // log revenue this ad
        logPaidAdImpressionValue(revenue / 1000000.0, precision, adUnitId, network)
        FirebaseAnalyticsUtil.logEventWithAds(params)
        FacebookEventUtils.logEventWithAds(params)
        // update current total
        // l revenue ads
        updateCurrentTotalRevenueAd(revenue)
        logCurrentTotalRevenueAd("event_current_total_revenue_ad")

        // update current total revenue ads for event paid_ad_impression_value_0.01
        Constants.currentTotalRevenue001Ad += revenue
        updateCurrentTotalRevenue001Ad(Constants.currentTotalRevenue001Ad)
        logTotalRevenue001Ad()
        logTotalRevenueAdIn3DaysIfNeed()
        logTotalRevenueAdIn7DaysIfNeed()
    }

    fun logTotalRevenueAdIn3DaysIfNeed() {
        val installTime = installTime
        if (!isPushRevenue3Day && System.currentTimeMillis() - installTime >= 3L * 24 * 60 * 60 * 1000) {
            Log.d(TAG, "logTotalRevenueAdAt3DaysIfNeed: ")
            logCurrentTotalRevenueAd("event_total_revenue_ad_in_3_days")
            setPushedRevenue3Day()
        }
    }

    fun logTotalRevenueAdIn7DaysIfNeed() {
        val installTime = installTime
        if (!isPushRevenue7Day && System.currentTimeMillis() - installTime >= 7L * 24 * 60 * 60 * 1000) {
            Log.d(TAG, "logTotalRevenueAdAt7DaysIfNeed: ")
            logCurrentTotalRevenueAd("event_total_revenue_ad_in_7_days")
            setPushedRevenue7Day()
        }
    }

    private fun logPaidAdImpressionValue(value: Double, precision: Int, adUnitId: String, network: String) {
        val params = Bundle()
        params.putDouble("value", value)
        params.putString("currency", "USD")
        params.putInt("precision", precision)
        params.putString("adunitid", adUnitId)
        params.putString("network", network)

        FirebaseAnalyticsUtil.logPaidAdImpressionValue(params)
        FacebookEventUtils.logPaidAdImpressionValue(params)
    }

    fun logCurrentTotalRevenueAd(eventName: String) {
        val currentTotalRevenue = currentTotalRevenueAd
        val bundle = Bundle()
        bundle.putFloat("value", currentTotalRevenue)
        FirebaseAnalyticsUtil.logCurrentTotalRevenueAd(eventName, bundle)
        FacebookEventUtils.logCurrentTotalRevenueAd(eventName, bundle)
    }

    fun logTotalRevenue001Ad() {
        val revenue = Constants.currentTotalRevenue001Ad
        if (revenue / 1000000 >= 0.01) {
            Constants.currentTotalRevenue001Ad = 0f
            updateCurrentTotalRevenue001Ad(0f)
            val bundle = Bundle()
            bundle.putFloat("value", revenue / 1000000)
            FirebaseAnalyticsUtil.logTotalRevenue001Ad(bundle)
            FacebookEventUtils.logTotalRevenue001Ad(bundle)
        }
    }

    @JvmStatic
    fun logPaidAdImpression(adValue: AdValue, adUnitId: String, adType: AdType) {
        Log.e("logPaidAdImpression", adValue.currencyCode)
        AppsflyerEvent.getInstance().pushTrackEventAdmob(adValue, adUnitId, adType)
        logEventWithAds(adValue.valueMicros.toFloat(), adValue.precisionType, adUnitId, adType.toString())
    }
}
