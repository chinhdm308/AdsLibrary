package com.chinchin.ads.event

import android.content.Context
import android.os.Bundle

//import com.facebook.appevents.AppEventsLogger;
object FacebookEventUtils {
    //private static AppEventsLogger appEventsLogger;
    fun init(context: Context) {
        //appEventsLogger = AppEventsLogger.newLogger(context);
    }

    fun logEventWithAds(params: Bundle?) {
        //appEventsLogger.logEvent("paid_ad_impression", params);
    }

    fun logPaidAdImpressionValue(bundle: Bundle?) {
        //appEventsLogger.logEvent("paid_ad_impression_value", bundle);
    }

    fun logClickAdsEvent(bundle: Bundle?) {
        //appEventsLogger.logEvent("event_user_click_ads", bundle);
    }

    fun logCurrentTotalRevenueAd(eventName: String, bundle: Bundle?) {
        //appEventsLogger.logEvent(eventName, bundle);
    }

    fun logTotalRevenue001Ad(bundle: Bundle?) {
        //appEventsLogger.logEvent("daily_ads_revenue", bundle);
        //appEventsLogger.logEvent("paid_ad_impression_value_001", bundle);
    }
}
