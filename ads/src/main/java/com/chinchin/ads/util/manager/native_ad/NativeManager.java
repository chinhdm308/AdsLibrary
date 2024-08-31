package com.chinchin.ads.util.manager.native_ad;

import android.app.Activity;
import android.os.CountDownTimer;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustAdRevenue;
import com.adjust.sdk.AdjustConfig;
import com.chinchin.ads.adsconsent.AdsConsentManager;
import com.chinchin.ads.callback.NativeCallback;
import com.chinchin.ads.util.Admob;
import com.chinchin.ads.util.NetworkUtil;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.AdapterResponseInfo;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.common.base.Strings;

public class NativeManager implements LifecycleEventObserver {
    enum State {LOADING, LOADED}

    private static final String TAG = "NativeManager";
    private final NativeBuilder builder;
    private final Activity currentActivity;
    private final LifecycleOwner lifecycleOwner;
    private boolean isReloadAds = false;
    private boolean isAlwaysReloadOnResume = false;
    private boolean isShowLoadingNative = true;
    private State state = State.LOADED;
    private long intervalReloadNative = 0;
    private boolean isStop = false;
    private CountDownTimer countDownTimer;
    private boolean isStopReload = false;

    public void notReloadInNextResume() {
        isStopReload = true;
    }

    public void setIntervalReloadNative(long intervalReloadNative) {
        if (intervalReloadNative > 0)
            this.intervalReloadNative = intervalReloadNative;
        countDownTimer = new CountDownTimer(this.intervalReloadNative, 1000) {
            @Override
            public void onTick(long l) {

            }

            @Override
            public void onFinish() {
                loadNative(true);
            }
        };
    }

    public NativeManager(@NonNull Activity currentActivity, LifecycleOwner lifecycleOwner, NativeBuilder builder) {
        this.builder = builder;
        this.currentActivity = currentActivity;
        this.lifecycleOwner = lifecycleOwner;
        this.lifecycleOwner.getLifecycle().addObserver(this);
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
        switch (event) {
            case ON_CREATE:
                Log.d(TAG, "onStateChanged: ON_CREATE");
                loadNative(true);
                break;
            case ON_RESUME:
                if (countDownTimer != null && isStop) {
                    countDownTimer.start();
                }
                String valueLog = isStop + " && " + (isReloadAds || isAlwaysReloadOnResume) + " && " + !isStopReload;
                Log.d(TAG, "onStateChanged: resume\n" + valueLog);
                if (isStop && (isReloadAds || isAlwaysReloadOnResume) && !isStopReload) {
                    isReloadAds = false;
                    loadNative(isShowLoadingNative);
                }
                isStopReload = false;
                isStop = false;
                break;
            case ON_PAUSE:
                Log.d(TAG, "onStateChanged: ON_PAUSE");
                isStop = true;
                if (countDownTimer != null) {
                    countDownTimer.cancel();
                }
                break;
            case ON_DESTROY:
                Log.d(TAG, "onStateChanged: ON_DESTROY");
                this.lifecycleOwner.getLifecycle().removeObserver(this);
                break;
        }
    }

    private void loadNative(boolean isShowLoading) {
        if (Admob.isShowAllAds) {
            if (isShowLoading) {
                builder.showLoading();
            }
            if (this.state != State.LOADING) {
                this.state = State.LOADING;
                loadNativeFloor(builder.getIdAd());
            }
        } else {
            builder.hideAd();
        }
    }

    private void loadNativeFloor(@NonNull String idAd) {
        if (Admob.isShowAllAds && !Strings.isNullOrEmpty(idAd) && NetworkUtil.isNetworkActive(this.currentActivity) && AdsConsentManager.getConsentResult(this.currentActivity)) {
            Log.d(TAG, "loadNativeFloor: " + idAd);
            NativeCallback callback = this.builder.getCallback();
            AdLoader adLoader = (new AdLoader.Builder(this.currentActivity, idAd)).forNativeAd((nativeAd) -> {
                Log.d(TAG, "showAd:");
                this.state = State.LOADED;
                nativeAd.setOnPaidEventListener(adValue -> {
                    if (nativeAd.getResponseInfo() != null)
                        trackRevenue(nativeAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                });
                callback.onNativeAdLoaded(nativeAd);
                if (nativeAd.getResponseInfo().getMediationAdapterClassName().toString().toLowerCase().contains("facebook")) {
                    this.builder.showAdMeta();
                    Admob.getInstance().pushAdsToViewCustom(nativeAd, this.builder.getNativeMetaAdView());
                } else {
                    this.builder.showAd();
                    Admob.getInstance().pushAdsToViewCustom(nativeAd, this.builder.getNativeAdView());
                }
            }).withAdListener(new AdListener() {
                public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                    Log.d(TAG, "onAdFailedToLoad: " + adError.getMessage());
                    if (!Strings.isNullOrEmpty(idAd)) {
                        NativeManager.this.loadNativeFloor(idAd);
                    } else {
                        NativeManager.this.state = State.LOADED;
                        NativeManager.this.builder.getCallback().onAdFailedToLoad();
                        NativeManager.this.builder.hideAd();
                    }

                }

                @Override
                public void onAdClicked() {
                    super.onAdClicked();
                    callback.onAdClicked();
                }

                public void onAdImpression() {
                    super.onAdImpression();
                    NativeManager.this.state = State.LOADED;
                    Log.d(TAG, "onAdImpression: ");
                    if (countDownTimer != null) {
                        countDownTimer.cancel();
                        countDownTimer.start();
                    }
                }
            }).withNativeAdOptions((new NativeAdOptions.Builder()).setVideoOptions((new VideoOptions.Builder()).setStartMuted(true).build()).build()).build();
            adLoader.loadAd(this.getAdRequest());
        } else {
            this.state = State.LOADED;
            this.builder.getCallback().onAdFailedToLoad();
            this.builder.hideAd();
        }
    }

    public void setReloadAds() {
        isReloadAds = true;
    }

    public void reloadAdNow() {
        loadNative(isShowLoadingNative);
    }

    public void setAlwaysReloadOnResume(boolean isAlwaysReloadOnResume) {
        this.isAlwaysReloadOnResume = isAlwaysReloadOnResume;
    }

    public AdRequest getAdRequest() {
        AdRequest.Builder builder = new AdRequest.Builder();
        return builder.build();
    }

    public void setShowLoadingNative(boolean showLoadingNative) {
        isShowLoadingNative = showLoadingNative;
    }

    //push adjust
    private void trackRevenue(@Nullable AdapterResponseInfo loadedAdapterResponseInfo, AdValue adValue) {
        String adName = "";
        if (loadedAdapterResponseInfo != null) {
            adName = loadedAdapterResponseInfo.getAdSourceName();
        }
        double valueMicros = adValue.getValueMicros() / 1000000d;
        Log.d("AdjustRevenue", "adName: " + adName + " - valueMicros: " + valueMicros);
        // send ad revenue info to Adjust
        AdjustAdRevenue adRevenue = new AdjustAdRevenue(AdjustConfig.AD_REVENUE_ADMOB);
        adRevenue.setRevenue(valueMicros, adValue.getCurrencyCode());
        adRevenue.setAdRevenueNetwork(adName);
        Adjust.trackAdRevenue(adRevenue);
    }
}
