package com.chinchin.ads.util

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.LogLevel
import com.chinchin.ads.event.FacebookEventUtils
import com.chinchin.ads.event.FirebaseAnalyticsUtil

abstract class AdsApplication : MultiDexApplication(), ActivityLifecycleCallbacks {

    private val tag = "AdsApplication"

    override fun onCreate() {
        super.onCreate()

        SharePreferenceUtils.init(this)
        FacebookEventUtils.init(this)
        FirebaseAnalyticsUtil.init(this)

        if (SharePreferenceUtils.installTime == 0L) {
            SharePreferenceUtils.setInstallTime()
        }

        Constants.currentTotalRevenue001Ad = SharePreferenceUtils.currentTotalRevenue001Ad

        Constants.BUILD_DEBUG = buildDebug()
        Log.i(tag, " run debug: " + Constants.BUILD_DEBUG)
        registerActivityLifecycleCallbacks(this)
    }

    private fun setUpAdjust() {
        val environment = AdjustConfig.ENVIRONMENT_PRODUCTION
        val config = AdjustConfig(this, appTokenAdjust, environment)
        config.setLogLevel(LogLevel.VERBOSE)
        config.fbAppId = facebookID
        config.defaultTracker = appTokenAdjust
        config.isSendInBackground = true
        Adjust.onCreate(config)
        // Enable the SDK
        Adjust.setEnabled(true)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        Adjust.onResume()
    }

    override fun onActivityPaused(activity: Activity) {
        Adjust.onPause()
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    abstract val appTokenAdjust: String

    abstract val facebookID: String

    abstract fun buildDebug(): Boolean

}