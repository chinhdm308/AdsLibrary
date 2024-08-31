package com.chinchin.ads.event

import android.app.Application
import android.util.Log
import com.appsflyer.AppsFlyerLib
import com.appsflyer.adrevenue.AppsFlyerAdRevenue
import com.appsflyer.adrevenue.adnetworks.generic.MediationNetwork
import com.appsflyer.adrevenue.adnetworks.generic.Scheme
import com.google.android.gms.ads.AdValue
import java.util.Currency
import java.util.Locale

object AppsflyerEvent{
    private const val TAG = "AppsflyerEvent"

    private var enableTrackingRevenue = false

    fun init(context: Application, devKey: String, enableTrackingRevenue: Boolean) {
        initDebug(context, devKey, false)
        this.enableTrackingRevenue = enableTrackingRevenue
    }

    fun initDebug(context: Application, devKey: String, enableDebugLog: Boolean) {
        AppsFlyerLib.getInstance().init(devKey, null, context)
        AppsFlyerLib.getInstance().start(context)

        val afRevenueBuilder = AppsFlyerAdRevenue.Builder(context)
        AppsFlyerAdRevenue.initialize(afRevenueBuilder.build())
        AppsFlyerLib.getInstance().setDebugLog(enableDebugLog)
    }

    fun pushTrackEventAdmob(adValue: AdValue, idAd: String, adType: AdType) {
        Log.e(
            TAG,
            "logPaidAdImpression  enableAppsflyer:" + this.enableTrackingRevenue + " --- value: " + adValue.valueMicros / 1000000.0 + " -- adType: " + adType.toString()
        )
        if (enableTrackingRevenue) {
            val customParams: MutableMap<String, String> = HashMap()
            customParams[Scheme.AD_UNIT] = idAd
            customParams[Scheme.AD_TYPE] = adType.toString()
            AppsFlyerAdRevenue.logAdRevenue(
                "Admob",
                MediationNetwork.googleadmob,
                Currency.getInstance(Locale.US),
                adValue.valueMicros / 1000000.0,
                customParams
            )
        }
    }
}
