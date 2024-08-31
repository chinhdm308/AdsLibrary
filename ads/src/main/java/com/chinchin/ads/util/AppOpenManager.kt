package com.chinchin.ads.util

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustConfig
import com.chinchin.ads.adsconsent.AdsConsentManager
import com.chinchin.ads.callback.AdCallback
import com.chinchin.ads.dialog.LoadingAdsDialog
import com.chinchin.ads.dialog.ResumeLoadingDialog
import com.chinchin.ads.event.AdType
import com.chinchin.ads.event.AdmobEvent
import com.google.android.gms.ads.AdActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdapterResponseInfo
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.google.common.base.Strings
import java.lang.ref.WeakReference
import java.util.Date
import kotlin.concurrent.Volatile

class AppOpenManager private constructor() : ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private var appResumeAd: AppOpenAd? = null
    private var splashAd: AppOpenAd? = null
    private var fullScreenContentCallback: FullScreenContentCallback? = null
    private var isShowLoadingSplash = false //kiểm tra trạng thái ad splash, ko cho load, show khi đang show loading ads splash
    private var appResumeAdId: String? = null
    private var splashAdId: String? = null

    private var currentActivity = WeakReference<Activity>(null)
    private var myApplication: Application? = null

    private var appResumeLoadTime: Long = 0
    private var splashLoadTime: Long = 0
    private var splashTimeout = 0

    private var isShowingAd = false
    private var checkLoadResume = false

    @JvmField
    var isInitialized: Boolean = false // on  - off ad resume on app
    private var isAppResumeEnabled = true
    private var isInterstitialShowing: Boolean = false
    private var enableScreenContentCallback = false // default =  true when use splash & false after show splash
    private var disableAdResumeByClickAction = false
    private val disabledAppOpenList: MutableList<Class<*>> = ArrayList()
    private var splashActivity: Class<*>? = null
    private var welcomeBackClass: Class<*>? = null
    private var isTimeout = false
    private var dialog: Dialog? = null

    fun init(application: Application, resumeAdId: String) {
        isInitialized = true
        disableAdResumeByClickAction = false
        myApplication = application
        myApplication!!.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        appResumeAdId = resumeAdId
    }

    fun initWelcomeBackActivity(application: Application, welcomeBackClass: Class<*>?) {
        isInitialized = true
        disableAdResumeByClickAction = false
        this.welcomeBackClass = welcomeBackClass
        myApplication = application
        myApplication!!.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * DefaultLifecycleObserver method that shows the app open ad when the app moves to foreground.
     */
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        if (!isAppResumeEnabled) {
            Log.d(TAG, "onResume: app resume is disabled")
            return
        }

        if (isInterstitialShowing) {
            Log.d(TAG, "onResume: interstitial is showing")
            return
        }

        if (disableAdResumeByClickAction) {
            Log.d(TAG, "onResume: ad resume disable ad by action")
            disableAdResumeByClickAction = false
            return
        }

        if (currentActivity.get() != null) {
            for (activity in disabledAppOpenList) {
                if (activity.name == currentActivity.get()?.javaClass?.name) {
                    Log.d(TAG, "onStart: activity is disabled")
                    return
                }
            }
        }

        if (splashActivity != null && splashActivity!!.name == currentActivity.get()?.javaClass?.name) {
            Log.d(TAG, "onStart: load and show splash ads")
            loadAndShowSplashAds()
            return
        }

        showAdIfAvailable(false)
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Log.d(TAG, "onPause: app pause")
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "onStop: app stop")
    }

    /**
     * ActivityLifecycleCallback methods.
     */
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        Log.d(TAG, "onActivityStarted: $activity")
        currentActivity = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        Log.d(TAG, "onActivityResumed: $activity")
        currentActivity = WeakReference(activity)
        if (Admob.isShowAllAds) {
            if (splashActivity == null) {
                if (activity.javaClass.name != AdActivity::class.java.name) {
                    Log.d(TAG, "onActivityResumed 1: with " + activity.javaClass.name)
                    fetchAd(false)
                }
            } else {
                if (activity.javaClass.name != splashActivity!!.name && activity.javaClass.name != AdActivity::class.java.name) {
                    Log.d(TAG, "onActivityResumed 2: with " + activity.javaClass.name)
                    fetchAd(false)
                }
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        Log.d(TAG, "onActivityDestroyed: " + activity.javaClass.simpleName)
        currentActivity.clear()
    }

    fun setSplashAdId(splashAdId: String) {
        this.splashAdId = splashAdId
    }

    fun setEnableScreenContentCallback(enableScreenContentCallback: Boolean) {
        this.enableScreenContentCallback = enableScreenContentCallback
    }

    /**
     * Call disable ad resume when click a button, auto enable ad resume in next start
     */
    fun disableAdResumeByClickAction() {
        disableAdResumeByClickAction = true
    }

    fun setDisableAdResumeByClickAction(disableAdResumeByClickAction: Boolean) {
        this.disableAdResumeByClickAction = disableAdResumeByClickAction
    }

    /**
     * Check app open ads is showing
     *
     * @return isShowingAd
     */
    fun isShowingAd(): Boolean = isShowingAd

    /**
     * Disable app open app on specific activity
     *
     * @param activityClass: activity class
     */
    fun disableAppResumeWithActivity(activityClass: Class<*>) {
        Log.d(TAG, "disableAppResumeWithActivity: " + activityClass.name)
        disabledAppOpenList.add(activityClass)
    }

    fun enableAppResumeWithActivity(activityClass: Class<*>) {
        Log.d(TAG, "enableAppResumeWithActivity: " + activityClass.name)
        disabledAppOpenList.remove(activityClass)
    }

    fun disableAppResume() {
        isAppResumeEnabled = false
    }

    fun enableAppResume() {
        isAppResumeEnabled = true
    }

    fun setSplashActivity(splashActivity: Class<*>?, splashAdId: String, timeoutInMillis: Int) {
        this.splashActivity = splashActivity
        this.splashAdId = splashAdId
        this.splashTimeout = timeoutInMillis
    }

    fun setAppResumeAdId(resumeAdId: String) {
        this.appResumeAdId = resumeAdId
    }

    fun setFullScreenContentCallback(callback: FullScreenContentCallback?) {
        this.fullScreenContentCallback = callback
    }

    fun removeFullScreenContentCallback() {
        this.fullScreenContentCallback = null
    }

    /**
     * Request an ad
     */
    fun fetchAd(isSplash: Boolean) {
        Log.d(TAG, "fetchAd: isSplash = $isSplash")
        // Do not load ad if there is an unused ad or ...
        if (isAdAvailable(isSplash) || checkLoadResume) {
            return
        }
        if (!isSplash) {
            // Log event admob
            AdmobEvent.OPEN_POSITION++
            val bundle = Bundle()
            bundle.putInt("ad_open_position", AdmobEvent.OPEN_POSITION)
            AdmobEvent.logEvent(myApplication!!, "ad_open_load", bundle)
            //end log
        }
        checkLoadResume = true
        if (Admob.isShowAllAds) {
            if (!appResumeAdId.isNullOrEmpty()) {
                callResumeWithId(isSplash)
            }
        }
    }

    private fun callResumeWithId(isSplash: Boolean) {
        val loadCallback: AppOpenAdLoadCallback = object : AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                onAdAppOpenLoaded(ad, isSplash)
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                onAdAppOpenFailed(loadAdError, isSplash)
            }
        }
        AppOpenAd.load(myApplication!!, (if (isSplash) splashAdId else appResumeAdId)!!, adRequest, loadCallback)
    }

    private fun onAdAppOpenFailed(loadAdError: LoadAdError, isSplash: Boolean) {
        checkLoadResume = false
        Log.d(TAG, "onAppOpenAdFailedToLoad: isSplash" + isSplash + " message " + loadAdError.message)
        dismissDialogLoading()
        // Log event admob
        if (!isSplash) {
            val bundle = Bundle()
            bundle.putString("ad_open_position", AdmobEvent.OPEN_POSITION.toString())
            bundle.putString("ad_error_domain", loadAdError.domain)
            bundle.putString("ad_error_code", loadAdError.code.toString())
            bundle.putString("ad_error_message", loadAdError.message)
            bundle.putString("ad_error_response", loadAdError.responseInfo.toString())
            bundle.putString("internet_status", isNetworkConnected(myApplication).toString())
            AdmobEvent.logEvent(myApplication!!, "ad_open_load_failed", bundle)
        }
    }

    private fun onAdAppOpenLoaded(ad: AppOpenAd, isSplash: Boolean) {
        checkLoadResume = false
        Log.d(TAG, "onAppOpenAdLoaded: isSplash = $isSplash")
        if (!isSplash) {
            // Log event admob
            AdmobEvent.OPEN_POSITION++
            val bundle = Bundle()
            bundle.putInt("ad_open_position", AdmobEvent.OPEN_POSITION)
            AdmobEvent.logEvent(myApplication!!, "ad_open_load_success", bundle)

            //end log
            appResumeAd = ad
            appResumeAd?.onPaidEventListener = OnPaidEventListener { adValue: AdValue ->
                trackRevenue(appResumeAd?.responseInfo?.loadedAdapterResponseInfo, adValue)
            }
            this.appResumeLoadTime = Date().time
        } else {
            splashAd = ad
            splashAd?.onPaidEventListener = OnPaidEventListener { adValue: AdValue ->
                trackRevenue(splashAd?.responseInfo?.loadedAdapterResponseInfo, adValue)
                AdmobEvent.logPaidAdImpression(adValue, ad.adUnitId, AdType.APP_OPEN)
            }
            splashLoadTime = Date().time
        }
    }

    /**
     * Creates and returns ad request.
     */
    private val adRequest: AdRequest
        get() = AdRequest.Builder().build()

    /**
     * Check if ad was loaded more than n hours ago.
     */
    private fun wasLoadTimeLessThanNHoursAgo(loadTime: Long, numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < (numMilliSecondsPerHour * numHours)
    }

    /**
     * Check if ad exists and can be shown.
     */
    private fun isAdAvailable(isSplash: Boolean): Boolean {
        // Ad references in the app open beta will time out after four hours, but this time limit
        // may change in future beta versions. For details, see:
        // https://support.google.com/admob/answer/9341964?hl=en
        val loadTime = if (isSplash) splashLoadTime else appResumeLoadTime
        val wasLoadTimeLessThanNHoursAgo = wasLoadTimeLessThanNHoursAgo(loadTime, 4)
        Log.d(TAG, "isAdAvailable: $wasLoadTimeLessThanNHoursAgo")
        return (if (isSplash) splashAd != null else appResumeAd != null) && wasLoadTimeLessThanNHoursAgo
    }

    /**
     * Show the ad if one isn't already showing.
     */
    private fun showAdIfAvailable(isSplash: Boolean) {
        // Only show ad if there is not already an app open ad currently showing
        // and an ad is available.
        if (currentActivity.get() == null) {
            if (fullScreenContentCallback != null && enableScreenContentCallback) {
                fullScreenContentCallback!!.onAdDismissedFullScreenContent()
            }
            return
        }

        Log.d(TAG, "showAdIfAvailable: " + ProcessLifecycleOwner.get().lifecycle.currentState)
        Log.d(TAG, "showAd isSplash: $isSplash")
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            Log.d(TAG, "showAdIfAvailable: return")
            if (fullScreenContentCallback != null && enableScreenContentCallback) {
                fullScreenContentCallback!!.onAdDismissedFullScreenContent()
            }
            return
        }
        if (!isShowingAd && currentActivity.get() != null && welcomeBackClass != null && currentActivity.get()!!.javaClass != welcomeBackClass) {
            currentActivity.get()!!.startActivity(Intent(currentActivity.get(), welcomeBackClass))
            return
        }
        if (!isShowingAd && isAdAvailable(isSplash)) {
            Log.d(TAG, "Will show ad isSplash: $isSplash")
            if (isSplash) {
                showAdsWithLoading()
            } else if (welcomeBackClass == null) {
                showResumeAds()
            }
        } else {
            Log.d(TAG, "Ad is not ready")
            if (!isSplash) {
                fetchAd(false)
            }
        }
    }

    private fun showAdsWithLoading() {
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            try {
                dismissDialogLoading()
                dialog = ResumeLoadingDialog(currentActivity.get()!!)
                dialog?.show()
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
                if (fullScreenContentCallback != null && enableScreenContentCallback) {
                    fullScreenContentCallback!!.onAdDismissedFullScreenContent()
                }
                return
            }
            val finalDialog = dialog
            Handler(Looper.getMainLooper()).postDelayed({
                if (splashAd != null) {
                    splashAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            // Set the reference to null so isAdAvailable() returns false.
                            appResumeAd = null
                            if (fullScreenContentCallback != null && enableScreenContentCallback) {
                                fullScreenContentCallback!!.onAdDismissedFullScreenContent()
                                enableScreenContentCallback = false
                            }
                            isShowingAd = false
                            fetchAd(true)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e(TAG, "onAdFailedToShowFullScreenContent: $adError")
                            appResumeAd = null
                            if (fullScreenContentCallback != null && enableScreenContentCallback) {
                                fullScreenContentCallback!!.onAdFailedToShowFullScreenContent(adError)
                            }
                        }

                        override fun onAdShowedFullScreenContent() {
                            // Log event admob
                            AdmobEvent.OPEN_POSITION++
                            val bundle = Bundle()
                            bundle.putInt("ad_open_position", AdmobEvent.OPEN_POSITION)
                            AdmobEvent.logEvent(myApplication!!, "ad_open_show", bundle)
                            //end log
                            if (fullScreenContentCallback != null && enableScreenContentCallback) {
                                fullScreenContentCallback!!.onAdShowedFullScreenContent()
                            }
                            isShowingAd = true
                        }


                        override fun onAdClicked() {
                            super.onAdClicked()
                            if (currentActivity.get() != null) {
                                splashAdId?.let { AdmobEvent.logClickAdsEvent(it) }
                                if (fullScreenContentCallback != null) {
                                    fullScreenContentCallback!!.onAdClicked()
                                }
                            }
                        }
                    }
                    if (Admob.isShowAllAds) splashAd!!.show(currentActivity.get()!!)
                    else fullScreenContentCallback!!.onAdDismissedFullScreenContent()
                }
                if (currentActivity.get() != null && !currentActivity.get()!!.isDestroyed && finalDialog != null && finalDialog.isShowing) {
                    Log.d(TAG, "dismiss dialog loading ad open: ")
                    try {
                        finalDialog.dismiss()
                    } catch (e: Exception) {
                        Log.e(TAG, e.message, e)
                    }
                }
            }, 800)
        }
    }

    private fun showResumeAds() {
        if (appResumeAd == null || currentActivity.get() == null) {
            return
        }
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            try {
                dismissDialogLoading()
                dialog = ResumeLoadingDialog(currentActivity.get()!!)
                dialog?.show()

            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
                if (fullScreenContentCallback != null && enableScreenContentCallback) {
                    fullScreenContentCallback!!.onAdDismissedFullScreenContent()
                }
                return
            }
            if (appResumeAd != null && AdsConsentManager.getConsentResult(currentActivity.get()) && Admob.isShowAllAds) {
                appResumeAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        // Set the reference to null so isAdAvailable() returns false.
                        appResumeAd = null
                        if (fullScreenContentCallback != null && enableScreenContentCallback) {
                            fullScreenContentCallback!!.onAdDismissedFullScreenContent()
                        }
                        isShowingAd = false
                        fetchAd(false)
                        dismissDialogLoading()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.e(TAG, "onAdFailedToShowFullScreenContent: " + adError.message)
                        if (fullScreenContentCallback != null && enableScreenContentCallback) {
                            fullScreenContentCallback!!.onAdFailedToShowFullScreenContent(adError)
                        }

                        if (currentActivity.get() != null && !currentActivity.get()!!.isDestroyed && dialog != null && dialog!!.isShowing) {
                            Log.d(TAG, "dismiss dialog loading ad open: ")
                            try {
                                dialog!!.dismiss()
                            } catch (e: Exception) {
                                Log.e(TAG, e.message, e)
                            }
                        }
                        appResumeAd = null
                        isShowingAd = false
                        fetchAd(false)
                    }

                    override fun onAdShowedFullScreenContent() {
                        if (currentActivity.get() != null) {
                            AdmobEvent.logEvent(currentActivity.get()!!, "resume_appopen_view", Bundle())
                        }
                        if (fullScreenContentCallback != null && enableScreenContentCallback) {
                            fullScreenContentCallback!!.onAdShowedFullScreenContent()
                        }
                        isShowingAd = true
                    }

                    override fun onAdClicked() {
                        super.onAdClicked()
                        if (currentActivity.get() != null) {
                            AdmobEvent.logEvent(currentActivity.get()!!, "resume_appopen_click", Bundle())
                            AdmobEvent.logClickAdsEvent(appResumeAdId!!)
                            if (fullScreenContentCallback != null) {
                                fullScreenContentCallback!!.onAdClicked()
                            }
                        }
                    }

                    override fun onAdImpression() {
                        super.onAdImpression()
                        if (currentActivity.get() != null) {
                            if (fullScreenContentCallback != null) {
                                fullScreenContentCallback!!.onAdImpression()
                            }
                        }
                    }
                }

                if (currentActivity.get() != null) {
                    appResumeAd!!.show(currentActivity.get()!!)
                }
            } else {
                dismissDialogLoading()
            }
        }
    }

    private fun loadAndShowSplashAds() {
        isTimeout = false
        enableScreenContentCallback = true
        if (currentActivity.get() != null) {
            if (fullScreenContentCallback != null) {
                fullScreenContentCallback!!.onAdDismissedFullScreenContent()
            }
            return
        }

        if (Admob.isShowAllAds) {
            if (appResumeAdId != null) callResumeWithId(true)
        }

        if (splashTimeout > 0) {
            val timeoutRunnable = Runnable {
                Log.e(TAG, "timeout load ad ")
                isTimeout = true
                enableScreenContentCallback = false
                if (fullScreenContentCallback != null) {
                    fullScreenContentCallback!!.onAdDismissedFullScreenContent()
                }
            }
            val timeoutHandler = Handler(Looper.getMainLooper())
            timeoutHandler.postDelayed(timeoutRunnable, splashTimeout.toLong())
        }
    }

    private fun dismissDialogLoading() {
        dialog?.dismiss()
    }

    fun showAppOpenSplash(context: Context, adCallback: AdCallback) {
        if (this.splashAd == null) {
            adCallback.onNextAction()
            adCallback.onAdFailedToLoad(null)
        } else {
            try {
                dialog = LoadingAdsDialog(context)
                dialog?.show()
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
            }

            Handler(Looper.getMainLooper()).postDelayed({
                if (splashAd != null) {
                    splashAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            adCallback.onNextAction()
                            adCallback.onAdClosed()
                            splashAd = null
                            isShowingAd = false
                            isShowLoadingSplash = false
                            if ((dialog != null) && currentActivity.get() != null && !currentActivity.get()!!.isDestroyed) {
                                try {
                                    dialog!!.dismiss()
                                } catch (e: Exception) {
                                    Log.e(TAG, e.message, e)
                                }
                            }
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e(TAG, "onAdFailedToShowFullScreenContent: $adError")
                            isShowLoadingSplash = true
                            splashAd = null
                            adCallback.onAdFailedToShow(adError)
                            isShowingAd = false
                            dismissDialogLoading()
                        }

                        override fun onAdShowedFullScreenContent() {
                            adCallback.onAdImpression()
                            AdmobEvent.logEvent(currentActivity.get()!!, "splash_appopen_view", Bundle())
                            isShowingAd = true
                        }

                        override fun onAdClicked() {
                            super.onAdClicked()
                            adCallback.onAdClicked()
                            AdmobEvent.logEvent(currentActivity.get()!!, "splash_appopen_click", Bundle())
                        }
                    }

                    if (currentActivity.get() != null) {
                        splashAd!!.show(currentActivity.get()!!)
                    }
                }
            }, 800L)
        }
    }

    private fun showAppOpenSplashNew(context: Context, adCallback: AdCallback) {
        if (this.splashAd == null) {
            adCallback.onNextAction()
            adCallback.onAdFailedToLoad(null)
        } else {
            try {
                dialog = null
                dialog = LoadingAdsDialog(context)
                dialog?.show()
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
            }

            Handler(Looper.getMainLooper()).postDelayed({
                if (this@AppOpenManager.splashAd != null) {
                    splashAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            adCallback.onNextAction()
                            adCallback.onAdClosed()
                            splashAd = null
                            isShowingAd = false
                            isShowLoadingSplash = false
                            if (dialog != null && currentActivity.get() != null && !currentActivity.get()!!.isDestroyed) {
                                try {
                                    dialog!!.dismiss()
                                } catch (e: Exception) {
                                    Log.e(TAG, e.message, e)
                                }
                            }
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e(TAG, "onAdFailedToShowFullScreenContent: $adError")
                            isShowLoadingSplash = true
                            adCallback.onNextAction()
                            adCallback.onAdFailedToShow(adError)
                            isShowingAd = false
                            splashAd = null
                            dismissDialogLoading()
                        }

                        override fun onAdShowedFullScreenContent() {
                            adCallback.onAdImpression()
                            AdmobEvent.logEvent(currentActivity.get()!!, "splash_appopen_view", Bundle())
                            isShowingAd = true
                        }

                        override fun onAdClicked() {
                            super.onAdClicked()
                            adCallback.onAdClicked()
                            AdmobEvent.logEvent(currentActivity.get()!!, "splash_appopen_click", Bundle())
                        }
                    }

                    if (currentActivity.get() != null) {
                        splashAd!!.show(currentActivity.get()!!)
                    }
                }
            }, 800L)
        }
    }

    fun loadOpenAppAdSplash(
        context: Context,
        idResumeSplash: String?,
        timeDelay: Long,
        timeOut: Long,
        isShowAdIfReady: Boolean,
        adCallback: AdCallback,
    ) {
        this.splashAdId = idResumeSplash
        if (!isNetworkConnected(context) || !Admob.isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            Handler(Looper.getMainLooper()).postDelayed({
                adCallback.onAdFailedToLoad(null)
                adCallback.onNextAction()
            }, timeDelay)
        } else {
            val currentTimeMillis = System.currentTimeMillis()
            val timeOutRunnable = Runnable {
                Log.d("AppOpenManager", "getAdSplash time out")
                adCallback.onNextAction()
                isShowingAd = false
            }
            val timeoutHandler = Handler(Looper.getMainLooper())
            timeoutHandler.postDelayed(timeOutRunnable, timeOut)
            val adRequest = adRequest
            val adUnitId = this.splashAdId
            val appOpenAdLoadCallback: AppOpenAdLoadCallback = object : AppOpenAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    timeoutHandler.removeCallbacks(timeOutRunnable)
                    adCallback.onAdFailedToLoad(null)
                    adCallback.onNextAction()
                }

                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    super.onAdLoaded(appOpenAd)
                    timeoutHandler.removeCallbacks(timeOutRunnable)
                    splashAd = appOpenAd
                    splashAd!!.onPaidEventListener = OnPaidEventListener { adValue: AdValue ->
                        trackRevenue(
                            splashAd!!.responseInfo.loadedAdapterResponseInfo, adValue
                        )
                    }
                    appOpenAd.onPaidEventListener = OnPaidEventListener { adValue: AdValue ->
                        trackRevenue(appOpenAd.responseInfo.loadedAdapterResponseInfo, adValue)
                        AdmobEvent.logPaidAdImpression(adValue, appOpenAd.adUnitId, AdType.APP_OPEN)
                    }
                    if (isShowAdIfReady) {
                        var elapsedTime = System.currentTimeMillis() - currentTimeMillis
                        if (elapsedTime >= timeDelay) {
                            elapsedTime = 0L
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            showAppOpenSplash(context, adCallback)
                        }, elapsedTime)
                    } else {
                        adCallback.onAdSplashReady()
                    }
                }
            }
            //AppOpenAd.load(context, adUnitId, adRequest, AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT, appOpenAdLoadCallback);
            AppOpenAd.load(context, adUnitId!!, adRequest, appOpenAdLoadCallback)
        }
    }

    fun loadOpenAppAdSplashFloor(context: Context, idAd: String, isShowAdIfReady: Boolean, adCallback: AdCallback) {
        if (!isNetworkConnected(context) || !Admob.isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            Handler(Looper.getMainLooper()).postDelayed({
                adCallback.onAdFailedToLoad(null)
                adCallback.onNextAction()
            }, 3000)
        } else {
            if (Strings.isNullOrEmpty(idAd)) {
                adCallback.onAdFailedToLoad(null)
                adCallback.onNextAction()
                return
            }
            Log.e("AppOpenManager", "load ID:$idAd")

            val appOpenAdLoadCallback: AppOpenAdLoadCallback = object : AppOpenAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    adCallback.onAdFailedToLoad(loadAdError)
                    adCallback.onNextAction()
                }

                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    super.onAdLoaded(appOpenAd)
                    splashAd = appOpenAd
                    splashAd!!.onPaidEventListener = OnPaidEventListener { adValue: AdValue ->
                        trackRevenue(splashAd!!.responseInfo.loadedAdapterResponseInfo, adValue)
                        AdmobEvent.logPaidAdImpression(adValue, appOpenAd.adUnitId, AdType.APP_OPEN)
                    }
                    if (isShowAdIfReady) {
                        showAppOpenSplash(context, adCallback)
                    } else {
                        adCallback.onAdSplashReady()
                    }
                }
            }
            //AppOpenAd.load(context, listIDResume.get(0), adRequest, AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT, appOpenAdLoadCallback);
            AppOpenAd.load(context!!, idAd, adRequest, appOpenAdLoadCallback)
        }
    }

    fun onCheckShowSplashWhenFail(activity: AppCompatActivity, callback: AdCallback, timeDelay: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (splashAd != null && !isShowingAd) {
                Log.e("AppOpenManager", "show ad splash when show fail in background")
                showAppOpenSplash(activity, callback)
            }
        }, timeDelay)
    }

    fun onCheckShowSplashWhenFailNew(activity: AppCompatActivity, callback: AdCallback, timeDelay: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (splashAd != null && !isShowingAd) {
                Log.e("AppOpenManager", "show ad splash when show fail in background")
                showAppOpenSplashNew(activity, callback)
            }
        }, timeDelay)
    }

    private fun isNetworkConnected(context: Context?): Boolean {
        val cm = context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo != null && cm.activeNetworkInfo!!.isConnected
    }

    //push adjust
    private fun trackRevenue(loadedAdapterResponseInfo: AdapterResponseInfo?, adValue: AdValue) {
        var adName = ""
        if (loadedAdapterResponseInfo != null) adName = loadedAdapterResponseInfo.adSourceName
        val valueMicros = adValue.valueMicros / 1000000.0
        Log.d("AdjustRevenue", "adName: $adName - valueMicros: $valueMicros")
        // send ad revenue info to Adjust
        val adRevenue = AdjustAdRevenue(AdjustConfig.AD_REVENUE_ADMOB)
        adRevenue.setRevenue(valueMicros, adValue.currencyCode)
        adRevenue.adRevenueNetwork = adName
        Adjust.trackAdRevenue(adRevenue)
    }

    companion object {
        private const val TAG = "AppOpenManager"

        @Volatile
        private var INSTANCE: AppOpenManager? = null

        @JvmStatic
        fun getInstance(): AppOpenManager {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = AppOpenManager()
                }
            }
            return INSTANCE!!
        }
    }
}

