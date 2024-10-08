package com.chinchin.ads.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustAdRevenue;
import com.adjust.sdk.AdjustConfig;
import com.adjust.sdk.AdjustEvent;
import com.chinchin.ads.BuildConfig;
import com.chinchin.ads.R;
import com.chinchin.ads.adsconsent.AdsConsentManager;
import com.chinchin.ads.callback.BannerCallBack;
import com.chinchin.ads.callback.InterCallback;
import com.chinchin.ads.callback.NativeCallback;
import com.chinchin.ads.callback.RewardCallback;
import com.chinchin.ads.dialog.LoadingAdsDialog;
import com.chinchin.ads.event.AdType;
import com.chinchin.ads.event.AdmobEvent;
import com.chinchin.ads.event.FirebaseAnalyticsUtil;
import com.chinchin.ads.util.detect_test_ad.DetectTestAd;
import com.chinchin.ads.util.reward.RewardAdCallback;
import com.chinchin.ads.util.reward.RewardAdModel;
import com.facebook.ads.AdSettings;
import com.facebook.ads.AudienceNetworkAds;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.AdapterResponseInfo;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.initialization.AdapterStatus;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.common.base.Strings;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Admob {
    private static final String TAG = "Admob";
    private LoadingAdsDialog dialog;
    private int currentClicked = 0; // số lượt đã click hiện tại
    private int numShowAds = 3;     // số lượt hiển thị ads
    private int maxClickAds = 100;  // số lượt click tối đa
    private Handler handlerTimeout; // Inter Splash
    private Runnable rdTimeout;     // Inter Splash
    private boolean isTimeLimited;
    private boolean isShowLoadingSplash = false; // kiểm tra trạng thái ad splash, ko cho load, show khi đang show loading ads splash
    private boolean checkTimeDelay = false;
    private boolean openActivityAfterShowInterAds = true;
    private boolean isTimeDelay = false; // xử lý delay time show ads, = true mới show ads
    private boolean isTimeout; // xử lý timeout show ads

    private RewardedAd rewardedAd;
    private String rewardedId = "";
    private InterstitialAd mInterstitialSplash;
    private boolean disableAdResumeWhenClickAds = false;
    private static final String BANNER_INLINE_SMALL_STYLE = "BANNER_INLINE_SMALL_STYLE";
    private static final String BANNER_INLINE_LARGE_STYLE = "BANNER_INLINE_LARGE_STYLE";
    private static final int MAX_SMALL_INLINE_BANNER_HEIGHT = 50;

    private static long timeLimitAds = 0; // Set > 1000 nếu cần limit ads click
    private boolean isShowInter = true;
    private boolean isShowBanner = true;
    private boolean isShowNative = true;
    private boolean logTimeLoadAdsSplash = false;
    private boolean logLogTimeShowAds = false;
    public static boolean isShowAllAds = true;
    private long currentTime;
    private long currentTimeShowAds;
    private boolean checkLoadBanner = false;
    private boolean checkLoadBannerCollapsible = false;
    private long timeInterval = 0L;
    private long lastTimeDismissInter = 0L;
    private StateInter stateInter = StateInter.DISMISS;

    enum StateInter {SHOWING, SHOWED, DISMISS}

    private AdView adView;
    private Dialog dialogLoadingLoadAndShowReward;

    private Map<String, AdapterStatus> admobAdapterStatusMap = new HashMap<>();

    private Handler handlerTimeOutSplash = null;
    private long timeStartSplash = 0;

    private static volatile Admob INSTANCE;

    public static Admob getInstance() {
        if (INSTANCE == null) {
            synchronized (Admob.class) {
                if (INSTANCE == null) INSTANCE = new Admob();
            }
        }
        return INSTANCE;
    }

    public void initAdmob(Context context, List<String> testDeviceList) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String processName = Application.getProcessName();
            String packageName = context.getPackageName();
            if (!packageName.equals(processName)) {
                WebView.setDataDirectorySuffix(processName);
            }
        }

        AudienceNetworkAds.initialize(context);
        MobileAds.initialize(context, initializationStatus -> {
            this.admobAdapterStatusMap = initializationStatus.getAdapterStatusMap();
            for (AdapterStatus status : admobAdapterStatusMap.values()) {
                Log.d(TAG, "initAdmob: " + status.getInitializationState());
            }
        });
        MobileAds.setRequestConfiguration(new RequestConfiguration.Builder().setTestDeviceIds(testDeviceList).build());
    }

    public void initAdmob(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String processName = Application.getProcessName();
            String packageName = context.getPackageName();
            if (!packageName.equals(processName)) {
                WebView.setDataDirectorySuffix(processName);
            }
        }

        AudienceNetworkAds.initialize(context);
        MobileAds.initialize(context, initializationStatus -> {
            this.admobAdapterStatusMap = initializationStatus.getAdapterStatusMap();
            for (AdapterStatus status : admobAdapterStatusMap.values()) {
                Log.d(TAG, "initAdmob: " + status.getInitializationState());
            }
        });

        if (BuildConfig.DEBUG) {
            AdSettings.setTestMode(true);
            RequestConfiguration requestConfiguration = new RequestConfiguration.Builder()
                    .setTestDeviceIds(Collections.singletonList(getDeviceId((Activity) context)))
                    .build();
            MobileAds.setRequestConfiguration(requestConfiguration);
        }
    }

    public void initAdmob(Context context, OnInitializationCompleteListener listener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String processName = Application.getProcessName();
            String packageName = context.getPackageName();
            if (!packageName.equals(processName)) {
                WebView.setDataDirectorySuffix(processName);
            }
        }

        AudienceNetworkAds.initialize(context);
        MobileAds.initialize(context, listener);

        if (BuildConfig.DEBUG) {
            AdSettings.setTestMode(true);
            RequestConfiguration requestConfiguration = new RequestConfiguration.Builder()
                    .setTestDeviceIds(Collections.singletonList(getDeviceId((Activity) context)))
                    .build();
            MobileAds.setRequestConfiguration(requestConfiguration);
        }
    }

    public Boolean checkAdmobReady() {
        InitializationStatus initializationStatus = MobileAds.getInitializationStatus();
        if (initializationStatus == null) return false;
        /* get the adapter status */
        Map<String, AdapterStatus> map = initializationStatus.getAdapterStatusMap();
        for (Map.Entry<String, AdapterStatus> entry : map.entrySet()) {
            AdapterStatus adapterStatus = entry.getValue();
            AdapterStatus.State state = adapterStatus.getInitializationState();
            if (state == AdapterStatus.State.READY) return true;
        }
        return false;
    }

    /**
     * Set tắt ads resume khi click ads
     */
    public void setDisableAdResumeWhenClickAds(boolean disableAdResumeWhenClickAds) {
        this.disableAdResumeWhenClickAds = disableAdResumeWhenClickAds;
    }

    /**
     * Set tắt toàn bộ ads trong project
     **/
    public void setOpenShowAllAds(boolean isShowAllAds) {
        Admob.isShowAllAds = isShowAllAds;
    }

    /**
     * Set tắt event log time load splash
     **/
    public void setOpenEventLoadTimeLoadAdsSplash(boolean logTimeLoadAdsSplash) {
        this.logTimeLoadAdsSplash = logTimeLoadAdsSplash;
    }

    /**
     * Set tắt event log time show splash
     **/
    public void setOpenEventLoadTimeShowAdsInter(boolean logLogTimeShowAds) {
        this.logLogTimeShowAds = logLogTimeShowAds;
    }

    /* ============================== START Banner ============================== */

    public void hideBanner(final Activity mActivity) {
        final View viewAds = mActivity.findViewById(R.id.ll_ads);
        if (viewAds != null) {
            viewAds.setVisibility(View.GONE);
        }
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        if (containerShimmer != null) containerShimmer.setVisibility(View.GONE);
        if (adContainer != null) adContainer.setVisibility(View.GONE);
    }

    public void loadBanner(final Activity mActivity, String idAd) {
        Log.e(TAG, "Load Banner");
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            loadBanner(mActivity, idAd, adContainer, containerShimmer, null, false, BANNER_INLINE_LARGE_STYLE);
        }
    }

    public void loadBannerFloor(@NonNull final Activity mActivity, String idAd) {
        Log.e(TAG, "Load Banner Floor");
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            if (Strings.isNullOrEmpty(idAd)) {
                adContainer.setVisibility(View.GONE);
                containerShimmer.setVisibility(View.GONE);
                return;
            }
            checkLoadBanner = false;
            loadBannerFloor(mActivity, idAd, adContainer, containerShimmer, null, false, BANNER_INLINE_LARGE_STYLE);
        }
    }

    public void loadBannerFloor(final Activity mActivity, String idAd, BannerCallBack bannerCallBack) {
        Log.e(TAG, "Load Banner Floor");
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        adContainer.removeAllViews();
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            bannerCallBack.onAdFailedToLoad(new LoadAdError(-1, "not allow", "local", null, null));
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            if (Strings.isNullOrEmpty(idAd)) {
                bannerCallBack.onAdFailedToLoad(new LoadAdError(-1, "not have id", "local", null, null));
                adContainer.setVisibility(View.GONE);
                containerShimmer.setVisibility(View.GONE);
                return;
            }
            checkLoadBanner = false;
            loadBannerFloor(mActivity, idAd, adContainer, containerShimmer, bannerCallBack, false, BANNER_INLINE_LARGE_STYLE);
        }
    }

    public void loadBannerFloorSplash(final Activity mActivity, String idAd, BannerCallBack bannerCallBack) {
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        adContainer.removeAllViews();
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            bannerCallBack.onAdFailedToLoad(new LoadAdError(-1, "not allow", "local", null, null));
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            if (Strings.isNullOrEmpty(idAd)) {
                bannerCallBack.onAdFailedToLoad(new LoadAdError(-1, "not have id", "local", null, null));
                adContainer.setVisibility(View.GONE);
                containerShimmer.setVisibility(View.GONE);
                return;
            }
            checkLoadBanner = false;
            loadBannerFloor(mActivity, idAd, adContainer, containerShimmer, bannerCallBack, false, BANNER_INLINE_LARGE_STYLE);
        }
    }

    private boolean detectTestAd(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View viewChild = viewGroup.getChildAt(i);
            if (viewChild instanceof ViewGroup) {
                if (detectTestAd((ViewGroup) viewChild)) return true;
            }
            if (viewChild instanceof TextView) {
                return true;
            }
        }
        return false;
    }

    /**
     * Load quảng cáo Banner Trong Activity
     */
    public void loadBanner(final Activity mActivity, String idAd, BannerCallBack callback) {
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            loadBanner(mActivity, idAd, adContainer, containerShimmer, callback, false, BANNER_INLINE_LARGE_STYLE);
        }
    }

    /**
     * Load quảng cáo Banner Trong Activity set Inline adaptive banners
     */
    public void loadBanner(final Activity mActivity, String idAd, Boolean useInlineAdaptive) {
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            loadBanner(mActivity, idAd, adContainer, containerShimmer, null, useInlineAdaptive, BANNER_INLINE_LARGE_STYLE);
        }
    }

    /**
     * Load quảng cáo Banner Trong Activity set Inline adaptive banners
     */
    public void loadInlineBanner(final Activity activity, String idAd, String inlineStyle) {
        final FrameLayout adContainer = activity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = activity.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        loadBanner(activity, idAd, adContainer, containerShimmer, null, true, inlineStyle);
    }

    /**
     * Load quảng cáo Banner Trong Activity set Inline adaptive banners
     */
    public void loadBanner(final Activity mActivity, String idAd, final BannerCallBack callback, Boolean useInlineAdaptive) {
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            loadBanner(mActivity, idAd, adContainer, containerShimmer, callback, useInlineAdaptive, BANNER_INLINE_LARGE_STYLE);
        }
    }

    /**
     * Load quảng cáo Banner Trong Activity set Inline adaptive banners
     */
    public void loadInlineBanner(final Activity activity, String idAd, String inlineStyle, final BannerCallBack callback) {
        final FrameLayout adContainer = activity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = activity.findViewById(R.id.shimmer_container_banner);
        if (!isShowAllAds || !isNetworkConnected(activity) || !AdsConsentManager.getConsentResult(activity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            loadBanner(activity, idAd, adContainer, containerShimmer, callback, true, inlineStyle);
        }
    }

    /**
     * Load quảng cáo Collapsible Banner Trong Activity
     */
    public void loadCollapsibleBanner(final Activity mActivity, String idAd, String gravity) {
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            loadCollapsibleBanner(mActivity, idAd, gravity, adContainer, containerShimmer);
        }
    }

    public void loadCollapsibleBannerFloor(final Activity mActivity, String idAd, String gravity) {
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            if (Strings.isNullOrEmpty(idAd)) {
                adContainer.setVisibility(View.GONE);
                containerShimmer.setVisibility(View.GONE);
                return;
            }
            checkLoadBannerCollapsible = false;
            loadCollapsibleBannerFloor(mActivity, idAd, gravity, adContainer, containerShimmer);
        }
    }

    public void loadCollapsibleBannerFloor(final Activity mActivity, String idAd, String gravity, BannerCallBack bannerCallBack) {
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
            bannerCallBack.onAdFailedToLoad(null);
        } else {
            if (Strings.isNullOrEmpty(idAd)) {
                adContainer.setVisibility(View.GONE);
                containerShimmer.setVisibility(View.GONE);
                return;
            }
            checkLoadBannerCollapsible = false;
            loadCollapsibleBannerFloor(mActivity, idAd, gravity, adContainer, containerShimmer, bannerCallBack);
        }
    }

    public AdView loadCollapsibleBannerFloorWithReload(final Activity mActivity, String idAd, String gravity, BannerCallBack bannerCallBack) {
        final FrameLayout adContainer = mActivity.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = mActivity.findViewById(R.id.shimmer_container_banner);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
            bannerCallBack.onAdFailedToLoad(null);
        } else {
            if (Strings.isNullOrEmpty(idAd)) {
                adContainer.setVisibility(View.GONE);
                containerShimmer.setVisibility(View.GONE);
                return null;
            }
            checkLoadBannerCollapsible = false;
            return loadCollapsibleBannerFloor(mActivity, idAd, gravity, adContainer, containerShimmer, bannerCallBack);
        }
        return null;
    }

    public void loadBannerFragmentFloor(final Activity mActivity, String idAd, final View rootView) {
        final FrameLayout adContainer = rootView.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = rootView.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);

        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            if (Strings.isNullOrEmpty(idAd)) {
                adContainer.setVisibility(View.GONE);
                containerShimmer.setVisibility(View.GONE);
                return;
            }
            checkLoadBanner = false;
            loadBannerFloor(mActivity, idAd, adContainer, containerShimmer, null, false, BANNER_INLINE_LARGE_STYLE);
        }
    }

    public void loadBannerFragmentFloor(final Activity mActivity, String idAd, final View rootView, BannerCallBack bannerCallBack) {
        final FrameLayout adContainer = rootView.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = rootView.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);

        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
            bannerCallBack.onAdFailedToLoad(null);
        } else {
            if (Strings.isNullOrEmpty(idAd)) {
                adContainer.setVisibility(View.GONE);
                containerShimmer.setVisibility(View.GONE);
                return;
            }
            checkLoadBanner = false;
            loadBannerFloor(mActivity, idAd, adContainer, containerShimmer, bannerCallBack, false, BANNER_INLINE_LARGE_STYLE);
        }
    }

    /**
     * Load Quảng Cáo Banner Trong Fragment
     */
    public void loadBannerFragment(final Activity mActivity, String idAd, final View rootView) {
        final FrameLayout adContainer = rootView.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = rootView.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            loadBanner(mActivity, idAd, adContainer, containerShimmer, null, false, BANNER_INLINE_LARGE_STYLE);
        }
    }

    /**
     * Load Quảng Cáo Banner Trong Fragment
     */
    public void loadBannerFragment(final Activity mActivity, String idAd, final View rootView, final BannerCallBack callback) {
        final FrameLayout adContainer = rootView.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = rootView.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
            callback.onAdFailedToLoad(null);
        } else {
            loadBanner(mActivity, idAd, adContainer, containerShimmer, callback, false, BANNER_INLINE_LARGE_STYLE);
        }
    }

    /**
     * Load Quảng Cáo Banner Trong Fragment set Inline adaptive banners
     */
    public void loadBannerFragment(final Activity mActivity, String idAd, final View rootView, Boolean useInlineAdaptive) {
        final FrameLayout adContainer = rootView.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = rootView.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            loadBanner(mActivity, idAd, adContainer, containerShimmer, null, useInlineAdaptive, BANNER_INLINE_LARGE_STYLE);
        }
    }

    /**
     * Load Quảng Cáo Banner Trong Fragment set Inline adaptive banners
     */
    public void loadInlineBannerFragment(final Activity activity, String idAd, final View rootView, String inlineStyle) {
        final FrameLayout adContainer = rootView.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = rootView.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(activity) || !AdsConsentManager.getConsentResult(activity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            loadBanner(activity, idAd, adContainer, containerShimmer, null, true, inlineStyle);
        }
    }

    /**
     * Load Quảng Cáo Banner Trong Fragment set Inline adaptive banners
     */
    public void loadBannerFragment(final Activity mActivity, String idAd, final View rootView, final BannerCallBack callback, Boolean useInlineAdaptive) {
        final FrameLayout adContainer = rootView.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = rootView.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
            callback.onAdFailedToLoad(null);
        } else {
            loadBanner(mActivity, idAd, adContainer, containerShimmer, callback, useInlineAdaptive, BANNER_INLINE_LARGE_STYLE);
        }
    }

    /**
     * Load Quảng Cáo Banner Trong Fragment set Inline adaptive banners
     */
    public void loadInlineBannerFragment(final Activity activity, String idAd, final View rootView, String inlineStyle, final BannerCallBack callback) {
        final FrameLayout adContainer = rootView.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = rootView.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(activity) || !AdsConsentManager.getConsentResult(activity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
            callback.onAdFailedToLoad(null);
        } else {
            loadBanner(activity, idAd, adContainer, containerShimmer, callback, true, inlineStyle);
        }
    }

    /**
     * Load quảng cáo Collapsible Banner Trong Fragment
     */
    public void loadCollapsibleBannerFragment(final Activity mActivity, String idAd, final View rootView, String gravity) {
        final FrameLayout adContainer = rootView.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = rootView.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        loadCollapsibleBanner(mActivity, idAd, gravity, adContainer, containerShimmer);
    }

    public void loadCollapsibleBannerFragmentFloor(final Activity mActivity, String idAd, final View rootView, String gravity) {
        final FrameLayout adContainer = rootView.findViewById(R.id.banner_container);
        final ShimmerFrameLayout containerShimmer = rootView.findViewById(R.id.shimmer_container_banner);
        containerShimmer.setVisibility(View.VISIBLE);
        adContainer.setVisibility(View.GONE);
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        } else {
            if (Strings.isNullOrEmpty(idAd)) {
                adContainer.setVisibility(View.GONE);
                containerShimmer.setVisibility(View.GONE);
                return;
            }
            checkLoadBannerCollapsible = false;
            loadCollapsibleBannerFloor(mActivity, idAd, gravity, adContainer, containerShimmer);
        }
    }

    private void loadBanner(final Activity mActivity, String idAd, final FrameLayout adContainer, final ShimmerFrameLayout containerShimmer, final BannerCallBack callback, Boolean useInlineAdaptive, String inlineStyle) {
        containerShimmer.setVisibility(View.VISIBLE);
        containerShimmer.startShimmer();

        try {
            AdView adView = new AdView(mActivity);
            adView.setAdUnitId(idAd);
            adContainer.addView(adView);
            AdSize adSize = getAdSize(mActivity, useInlineAdaptive, inlineStyle);
            int adHeight;
            if (useInlineAdaptive && inlineStyle.equalsIgnoreCase(BANNER_INLINE_SMALL_STYLE)) {
                adHeight = MAX_SMALL_INLINE_BANNER_HEIGHT;
            } else {
                adHeight = adSize.getHeight();
            }
            containerShimmer.getLayoutParams().height = (int) (adHeight * Resources.getSystem().getDisplayMetrics().density + 0.5f);
            adView.setAdSize(adSize);
            adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    containerShimmer.stopShimmer();
                    adContainer.setVisibility(View.GONE);
                    containerShimmer.setVisibility(View.GONE);
                    if (callback != null) {
                        callback.onAdFailedToLoad(loadAdError);
                    }
                }


                @Override
                public void onAdLoaded() {
                    Log.d(TAG, "Banner adapter class name: " + adView.getResponseInfo().getMediationAdapterClassName());
                    containerShimmer.stopShimmer();
                    containerShimmer.setVisibility(View.GONE);
                    adContainer.setVisibility(View.VISIBLE);
                    if (callback != null) {
                        callback.onAdLoadSuccess();
                    }
                    adView.setOnPaidEventListener(adValue -> {
                        trackRevenue(adView.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                        Log.d(TAG, "OnPaidEvent Banner:" + adValue.getValueMicros());
                        AdmobEvent.logPaidAdImpression(adValue, adView.getAdUnitId(), AdType.BANNER);
                    });
                }

                @Override
                public void onAdClicked() {
                    super.onAdClicked();
                    if (callback != null) {
                        callback.onAdClicked();
                    }
                    if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                    AdmobEvent.logClickAdsEvent(idAd);
                }

                @Override
                public void onAdImpression() {
                    super.onAdImpression();
                    if (callback != null) {
                        callback.onAdImpression();
                    }
                }
            });

            adView.loadAd(getAdRequest());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private void loadBannerFloor(final Activity mActivity, String idAd, final FrameLayout adContainer, final ShimmerFrameLayout containerShimmer, final BannerCallBack callback, Boolean useInlineAdaptive, String inlineStyle) {
        if (checkLoadBanner) {
            return;
        }
        if (Strings.isNullOrEmpty(idAd)) {
            containerShimmer.stopShimmer();
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
            return;
        }
        Log.e(TAG, "load banner ID: " + idAd);

        containerShimmer.setVisibility(View.VISIBLE);
        containerShimmer.startShimmer();
        try {
            AdView adView = new AdView(mActivity);
            adView.setAdUnitId(idAd);
            adContainer.addView(adView);
            AdSize adSize = getAdSize(mActivity, useInlineAdaptive, inlineStyle);
            int adHeight;
            if (useInlineAdaptive && inlineStyle.equalsIgnoreCase(BANNER_INLINE_SMALL_STYLE)) {
                adHeight = MAX_SMALL_INLINE_BANNER_HEIGHT;
            } else {
                adHeight = adSize.getHeight();
            }
            containerShimmer.getLayoutParams().height = (int) (adHeight * Resources.getSystem().getDisplayMetrics().density + 0.5f);
            adView.setAdSize(adSize);
            adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    if (callback != null) {
                        callback.onAdFailedToLoad(loadAdError);
                    }

                    if (!Strings.isNullOrEmpty(idAd)) {
                        loadBannerFloor(mActivity, idAd, adContainer, containerShimmer, callback, useInlineAdaptive, inlineStyle);
                    } else {
                        containerShimmer.stopShimmer();
                        adContainer.setVisibility(View.GONE);
                        containerShimmer.setVisibility(View.GONE);
                    }
                }


                @Override
                public void onAdLoaded() {
                    checkLoadBanner = true;
                    //lỗi: chưa kiểm tra null
                    if (!DetectTestAd.getInstance().isTestAd(mActivity))
                        DetectTestAd.getInstance().detectedTestAd(detectTestAd(adView), mActivity);
                    if (callback != null) callback.onAdLoadSuccess();
                    Log.d(TAG, "Banner adapter class name: " + adView.getResponseInfo().getMediationAdapterClassName());
                    containerShimmer.stopShimmer();
                    adContainer.setVisibility(View.VISIBLE);
                    containerShimmer.setVisibility(View.GONE);
                    adView.setOnPaidEventListener(adValue -> {
                        trackRevenue(adView.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                        Log.d(TAG, "OnPaidEvent Banner:" + adValue.getValueMicros());

                        AdmobEvent.logPaidAdImpression(adValue, adView.getAdUnitId(), AdType.BANNER);
                    });
                }

                @Override
                public void onAdClicked() {
                    super.onAdClicked();
                    //lỗi: chưa kiểm tra null
                    if (callback != null) callback.onAdClicked();
                    if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                    AdmobEvent.logClickAdsEvent(idAd);
                }

                @Override
                public void onAdImpression() {
                    super.onAdImpression();
                    //lỗi: chưa kiểm tra null
                    if (callback != null) callback.onAdImpression();
                    //end log
                }
            });

            adView.loadAd(getAdRequest());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            containerShimmer.stopShimmer();
            adContainer.setVisibility(View.GONE);
            containerShimmer.setVisibility(View.GONE);
        }
    }

    private void loadCollapsibleBanner(final Activity mActivity, String idAd, String gravity, final FrameLayout adContainer, final ShimmerFrameLayout containerShimmer) {
        if (!isNetworkConnected(mActivity)) {
            containerShimmer.setVisibility(View.GONE);
            return;
        }

        containerShimmer.setVisibility(View.VISIBLE);
        containerShimmer.startShimmer();
        try {
            AdView adView = new AdView(mActivity);
            adView.setAdUnitId(idAd);
            adContainer.addView(adView);
            AdSize adSize = getAdSize(mActivity, false, "");
            containerShimmer.getLayoutParams().height = (int) (adSize.getHeight() * Resources.getSystem().getDisplayMetrics().density + 0.5f);
            adView.setAdSize(adSize);
            adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            adView.loadAd(getAdRequestForCollapsibleBanner(gravity));
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    containerShimmer.stopShimmer();
                    adContainer.setVisibility(View.GONE);
                    containerShimmer.setVisibility(View.GONE);
                }

                @Override
                public void onAdLoaded() {
                    Log.d(TAG, "Banner adapter class name: " + adView.getResponseInfo().getMediationAdapterClassName());
                    containerShimmer.stopShimmer();
                    adContainer.setVisibility(View.VISIBLE);
                    containerShimmer.setVisibility(View.GONE);
                    adView.setOnPaidEventListener(adValue -> {
                        trackRevenue(adView.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                        Log.d(TAG, "OnPaidEvent Banner:" + adValue.getValueMicros());

                        AdmobEvent.logPaidAdImpression(adValue, adView.getAdUnitId(), AdType.BANNER);
                    });

                }

                @Override
                public void onAdClicked() {
                    super.onAdClicked();
                    if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                    AdmobEvent.logClickAdsEvent(idAd);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private void loadCollapsibleBannerFloor(final Activity mActivity, String idAd, String gravity, final FrameLayout adContainer, final ShimmerFrameLayout containerShimmer) {
        if (checkLoadBannerCollapsible) {
            return;
        }
        containerShimmer.setVisibility(View.VISIBLE);
        containerShimmer.startShimmer();
        try {
            Log.e("Admob", "load collapsible banner ID : " + idAd);
            AdView adView = new AdView(mActivity);
            adView.setAdUnitId(idAd);
            adContainer.addView(adView);
            AdSize adSize = getAdSize(mActivity, false, "");
            containerShimmer.getLayoutParams().height = (int) (adSize.getHeight() * Resources.getSystem().getDisplayMetrics().density + 0.5f);
            adView.setAdSize(adSize);
            adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            adView.loadAd(getAdRequestForCollapsibleBanner(gravity));
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    Log.e("Admob", "load failed collapsible banner ID : " + idAd);
                    if (!Strings.isNullOrEmpty(idAd)) {
                        loadCollapsibleBannerFloor(mActivity, idAd, gravity, adContainer, containerShimmer);
                    } else {
                        containerShimmer.stopShimmer();
                        adContainer.setVisibility(View.GONE);
                        containerShimmer.setVisibility(View.GONE);
                    }

                }

                @Override
                public void onAdLoaded() {
                    checkLoadBannerCollapsible = true;
                    Log.d(TAG, "Banner adapter class name: " + adView.getResponseInfo().getMediationAdapterClassName());
                    containerShimmer.stopShimmer();
                    containerShimmer.setVisibility(View.GONE);
                    adContainer.setVisibility(View.VISIBLE);
                    adView.setOnPaidEventListener(adValue -> {
                        trackRevenue(adView.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                        Log.d(TAG, "OnPaidEvent Banner:" + adValue.getValueMicros());

                        AdmobEvent.logPaidAdImpression(adValue, adView.getAdUnitId(), AdType.BANNER);
                    });

                }

                @Override
                public void onAdClicked() {
                    super.onAdClicked();
                    if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                    AdmobEvent.logClickAdsEvent(idAd);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private AdView loadCollapsibleBannerFloor(final Activity mActivity, String idAd, String gravity, final FrameLayout adContainer, final ShimmerFrameLayout containerShimmer, BannerCallBack bannerCallBack) {
        if (checkLoadBannerCollapsible) {
            return null;
        }
        containerShimmer.setVisibility(View.VISIBLE);
        containerShimmer.startShimmer();
        try {
            Log.e("Admob", "load collapsible banner ID : " + idAd);
            adView = new AdView(mActivity);
            adView.setAdUnitId(idAd);
            adContainer.addView(adView);
            AdSize adSize = getAdSize(mActivity, false, "");
            containerShimmer.getLayoutParams().height = (int) (adSize.getHeight() * Resources.getSystem().getDisplayMetrics().density + 0.5f);
            adView.setAdSize(adSize);
            adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            adView.loadAd(getAdRequestForCollapsibleBanner(gravity));
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    Log.e("Admob", "load failed collapsible banner ID : " + idAd);
                    if (!Strings.isNullOrEmpty(idAd)) {
                        loadCollapsibleBannerFloor(mActivity, idAd, gravity, adContainer, containerShimmer);
                    } else {
                        bannerCallBack.onAdFailedToLoad(loadAdError);
                        containerShimmer.stopShimmer();
                        adContainer.setVisibility(View.GONE);
                        containerShimmer.setVisibility(View.GONE);
                    }

                }

                @Override
                public void onAdLoaded() {
                    checkLoadBannerCollapsible = true;
                    bannerCallBack.onAdLoadSuccess();
                    Log.d(TAG, "Banner adapter class name: " + adView.getResponseInfo().getMediationAdapterClassName());
                    containerShimmer.stopShimmer();
                    containerShimmer.setVisibility(View.GONE);
                    adContainer.setVisibility(View.VISIBLE);
                    adView.setOnPaidEventListener(adValue -> {
                        trackRevenue(adView.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                        Log.d(TAG, "OnPaidEvent Banner:" + adValue.getValueMicros());

                        AdmobEvent.logPaidAdImpression(adValue, adView.getAdUnitId(), AdType.BANNER);
                    });

                }

                @Override
                public void onAdClicked() {
                    super.onAdClicked();
                    bannerCallBack.onAdClicked();
                    if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                    AdmobEvent.logClickAdsEvent(idAd);
                }

                @Override
                public void onAdImpression() {
                    super.onAdImpression();
                    if (bannerCallBack != null) {
                        bannerCallBack.onAdImpression();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return adView;
    }

    private AdSize getAdSize(Activity mActivity, Boolean useInlineAdaptive, String inlineStyle) {
        // Step 2 - Determine the screen width (less decorations) to use for the ad width.
        Display display = mActivity.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        float widthPixels = outMetrics.widthPixels;
        float density = outMetrics.density;

        int adWidth = (int) (widthPixels / density);

        // Step 3 - Get adaptive ad size and return for setting on the ad view.
        if (useInlineAdaptive) {
            if (inlineStyle.equalsIgnoreCase(BANNER_INLINE_LARGE_STYLE)) {
                return AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(mActivity, adWidth);
            } else {
                return AdSize.getInlineAdaptiveBannerAdSize(adWidth, MAX_SMALL_INLINE_BANNER_HEIGHT);
            }
        }
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(mActivity, adWidth);

    }

    private AdRequest getAdRequestForCollapsibleBanner(String gravity) {
        AdRequest.Builder builder = new AdRequest.Builder();
        Bundle admobExtras = new Bundle();
        admobExtras.putString("collapsible", gravity);
        builder.addNetworkExtrasBundle(AdMobAdapter.class, admobExtras);
        return builder.build();
    }

    /* ============================== END Banner ============================== */
    /* ============================== START Inter Splash ============================== */

    public boolean interstitialSplashLoaded() {
        return mInterstitialSplash != null;
    }

    public InterstitialAd getInterstitialSplash() {
        return mInterstitialSplash;
    }

    public void loadSplashInterAds(final Context context, String id, long timeOut, long timeDelay, final InterCallback adListener) {
        isTimeDelay = false;
        isTimeout = false;
        if (!isNetworkConnected(context) || !AdsConsentManager.getConsentResult(context)) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (adListener != null) {
                    adListener.onAdClosed();
                    adListener.onNextAction();
                }
                return;
            }, 3000);
        } else {
            if (logTimeLoadAdsSplash) {
                currentTime = System.currentTimeMillis();
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                //check delay show ad splash
                if (mInterstitialSplash != null) {
                    Log.d(TAG, "loadSplashInterAds:show ad on delay ");
                    onShowSplash((Activity) context, adListener);
                    return;
                }
                Log.d(TAG, "loadSplashInterAds: delay validate");
                isTimeDelay = true;
            }, timeDelay);
            if (timeOut > 0) {
                handlerTimeout = new Handler(Looper.getMainLooper());
                rdTimeout = () -> {
                    Log.e(TAG, "loadSplashInterstitialAds: on timeout");
                    isTimeout = true;
                    if (mInterstitialSplash != null) {
                        Log.i(TAG, "loadSplashInterstitialAds:show ad on timeout ");
                        onShowSplash((Activity) context, adListener);
                        return;
                    }
                    if (adListener != null) {
                        adListener.onAdClosed();
                        adListener.onNextAction();
                        isShowLoadingSplash = false;
                    }
                };
                handlerTimeout.postDelayed(rdTimeout, timeOut);
            }

            isShowLoadingSplash = true;
            loadInterAds(context, id, new InterCallback() {
                @Override
                public void onAdLoadSuccess(InterstitialAd interstitialAd) {
                    super.onAdLoadSuccess(interstitialAd);
                    Log.e(TAG, "loadSplashInterstitialAds  end time loading success: " + Calendar.getInstance().getTimeInMillis() + "     time limit:" + isTimeout);
                    if (isTimeout) return;
                    if (interstitialAd != null) {
                        mInterstitialSplash = interstitialAd;
                        if (isTimeDelay) {
                            onShowSplash((Activity) context, adListener);
                            Log.i(TAG, "loadSplashInterstitialAds: show ad on loaded ");
                        }
                    }
                    if (interstitialAd != null) {
                        interstitialAd.setOnPaidEventListener(adValue -> {
                            trackRevenue(interstitialAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                            Log.d(TAG, "OnPaidEvent Interstitial:" + adValue.getValueMicros());
                            AdmobEvent.logPaidAdImpression(adValue, interstitialAd.getAdUnitId(), AdType.BANNER);
                        });
                    }
                }

                @Override
                public void onAdFailedToLoad(LoadAdError i) {
                    super.onAdFailedToLoad(i);
                    Log.e(TAG, "loadSplashInterstitialAds end time loading error:" + Calendar.getInstance().getTimeInMillis() + "     time limit:" + isTimeout);
                    if (isTimeout) return;
                    if (adListener != null) {
                        if (handlerTimeout != null && rdTimeout != null) {
                            handlerTimeout.removeCallbacks(rdTimeout);
                        }
                        if (i != null) Log.e(TAG, "loadSplashInterstitialAds: load fail " + i.getMessage());
                        adListener.onAdFailedToLoad(i);
                        adListener.onNextAction();
                    }
                }

                @Override
                public void onAdClicked() {
                    if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                    super.onAdClicked();
                    if (timeLimitAds > 1000) setTimeLimitInter();
                }
            });

        }
    }

    public void loadSplashInterAds2(final Context context, String id, long timeDelay, final InterCallback adListener) {
        if (!isNetworkConnected(context) || !isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (adListener != null) {
                    adListener.onAdClosed();
                    adListener.onNextAction();
                }
                return;
            }, 3000);
        } else {
            mInterstitialSplash = null;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    InterstitialAd.load(context, id, getAdRequest(), new InterstitialAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                            super.onAdLoaded(interstitialAd);
                            mInterstitialSplash = interstitialAd;
                            AppOpenManager.getInstance().disableAppResume();
                            onShowSplash((Activity) context, adListener);
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            super.onAdFailedToLoad(loadAdError);
                            mInterstitialSplash = null;
                            adListener.onAdFailedToLoad(loadAdError);
                            adListener.onNextAction();
                        }

                    });
                }
            }, timeDelay);
        }
    }

    public void loadSplashInterAds3(Context context, String idInter, int timeDelay, int timeOut, InterCallback callback, boolean isNextActionWhenFailedInter) {
        if (handlerTimeOutSplash == null) {
            timeStartSplash = System.currentTimeMillis();
            handlerTimeOutSplash = new Handler(Looper.getMainLooper());
            Runnable runnableTimeOutSplash = () -> {
                Log.d(TAG, "handlerTimeOutSplash: timeout");
                callback.onAdClosed();
                callback.onNextAction();
                Log.d(TAG, "loadSplashInterAds3: 1");
                handlerTimeOutSplash = null;
            };
            handlerTimeOutSplash.postDelayed(runnableTimeOutSplash, timeOut);
        }
        if (!isNetworkConnected(context) || Strings.isNullOrEmpty(idInter) || !AdsConsentManager.getConsentResult(context)) {
            handlerTimeOutSplash.removeCallbacksAndMessages(null);
            handlerTimeOutSplash.postDelayed(() -> {
                Log.d(TAG, "handlerTimeOutSplash: size 0");
                callback.onNextAction();
                Log.d(TAG, "loadSplashInterAds3: 2");
                handlerTimeOutSplash = null;
            }, timeDelay);
        } else {
            Log.d(TAG, "loadSplashInterAds3: " + idInter);
            InterstitialAd.load(context, idInter, getAdRequest(), new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    super.onAdLoaded(interstitialAd);
                    Log.d(TAG, "loadSplashInterAds3 - onAdLoaded:");
                    mInterstitialSplash = interstitialAd;
                    handlerTimeOutSplash.removeCallbacksAndMessages(null);
                    handlerTimeOutSplash = null;
                    AppOpenManager.getInstance().disableAppResume();
                    onShowSplash((Activity) context, callback);
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    Log.d(TAG, "loadSplashInterAds3 - onAdFailedToLoad:");
                    mInterstitialSplash = null;
                    callback.onAdFailedToLoad(loadAdError);
                    if (!isNextActionWhenFailedInter) return;
//                    if (idInter.isEmpty()) {
//                        callback.onAdFailedToLoad(loadAdError);
//                        if (!isNextActionWhenFailedInter) return;
//                    }
//                    Log.d(TAG, "loadSplashInterAds3 - onAdFailedToLoad: ");
//                    if (System.currentTimeMillis() - timeStartSplash < timeOut) {
//                        loadSplashInterAds3(context, idInter, timeDelay, timeOut, callback, isNextActionWhenFailedInter);
//                    }
                }
            });
        }
    }

    public void onShowSplash(Activity activity, InterstitialAd interSplash, InterCallback adListener) {
        AppOpenManager.getInstance().disableAppResume();
        isShowLoadingSplash = true;
        mInterstitialSplash = interSplash;
        if (!isNetworkConnected(activity)) {
            adListener.onAdClosed();
        } else {
            if (mInterstitialSplash == null) {
                adListener.onAdClosed();
                adListener.onNextAction();
                Log.d(TAG, "loadSplashInterAds3: 3");
            } else {
                mInterstitialSplash.setOnPaidEventListener(adValue -> {
                    trackRevenue(mInterstitialSplash.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                    Log.d(TAG, "OnPaidEvent splash:" + adValue.getValueMicros());
                    AdmobEvent.logPaidAdImpression(adValue, mInterstitialSplash.getAdUnitId(), AdType.INTERSTITIAL);
                    adListener.onEarnRevenue((double) adValue.getValueMicros());
                });

                if (handlerTimeout != null && rdTimeout != null) {
                    handlerTimeout.removeCallbacks(rdTimeout);
                }

                if (adListener != null) {
                    adListener.onAdLoaded();
                }

                mInterstitialSplash.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdShowedFullScreenContent() {
                        if (AppOpenManager.getInstance().isInitialized) {
                            AppOpenManager.getInstance().disableAppResume();
                        }
                        isShowLoadingSplash = true;
                        if (logTimeLoadAdsSplash) {
                            long timeLoad = System.currentTimeMillis() - currentTime;
                            Log.e(TAG, "load ads time: " + timeLoad);
                            FirebaseAnalyticsUtil.logTimeLoadAdsSplash(round1000(timeLoad));
                        }
                    }

                    @Override
                    public void onAdDismissedFullScreenContent() {
                        Log.e(TAG, "DismissedFullScreenContent Splash");
                        if (AppOpenManager.getInstance().isInitialized) {
                            AppOpenManager.getInstance().enableAppResume();
                        }
                        if (adListener != null) {
                            adListener.onInterDismiss();
                            if (!openActivityAfterShowInterAds) {
                                adListener.onAdClosed();
                                adListener.onNextAction();
                                Log.d(TAG, "loadSplashInterAds3: 4");
                            } else {
                                adListener.onAdClosedByUser();
                                Log.d(TAG, "loadSplashInterAds3: 5");
                            }

                            if (dialog != null) {
                                dialog.dismiss();
                            }

                        }
                        mInterstitialSplash = null;
                        isShowLoadingSplash = true;
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                        Log.e(TAG, "onAdFailedToShowFullScreenContent: " + adError);
                        //  mInterstitialSplash = null;
                        if (adError.getCode() == 1) {
                            mInterstitialSplash = null;
                            if (adListener != null) adListener.onAdClosed();
                            Log.d(TAG, "loadSplashInterAds3: 6");
                        }
                        isShowLoadingSplash = false;
                        if (adListener != null) {
                            adListener.onAdFailedToShow(adError);

                            if (dialog != null) {
                                dialog.dismiss();
                            }
                        }
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                        if (timeLimitAds > 1000) {
                            setTimeLimitInter();
                        }
                        AdmobEvent.logClickAdsEvent(mInterstitialSplash.getAdUnitId());
                    }
                });
                if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                    try {
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        dialog = new LoadingAdsDialog(activity);
                        try {
                            dialog.show();
                        } catch (Exception e) {
                            if (adListener != null) {
                                adListener.onAdClosed();
                                adListener.onNextAction();
                            }
                            Log.d(TAG, "loadSplashInterAds3: 7");
                            return;
                        }
                    } catch (Exception e) {
                        dialog = null;
                        e.printStackTrace();
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (AppOpenManager.getInstance().isInitialized) {
                            AppOpenManager.getInstance().disableAppResume();
                        }

                        if (openActivityAfterShowInterAds && adListener != null) {
                            adListener.onAdClosed();
                            adListener.onNextAction();
                            Log.d(TAG, "loadSplashInterAds3: 8");
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (dialog != null && dialog.isShowing() && !activity.isDestroyed()) dialog.dismiss();
                            }, 1500);
                        }

                        if (activity != null) {
                            mInterstitialSplash.show(activity);
                            Log.e(TAG, "onShowSplash: mInterstitialSplash.show");
                            isShowLoadingSplash = false;
                        } else if (adListener != null) {
                            if (dialog != null) {
                                dialog.dismiss();
                            }
                            adListener.onAdClosed();
                            adListener.onNextAction();
                            Log.d(TAG, "loadSplashInterAds3: 9");
                            isShowLoadingSplash = false;
                        }
                    }, 300);
                } else {
                    isShowLoadingSplash = false;
                    Log.e(TAG, "onShowSplash: fail on background");
                }
            }

        }

    }

    private void onShowSplash(Activity activity, InterCallback adListener) {
        isShowLoadingSplash = true;
        if (mInterstitialSplash == null) {
            Log.d(TAG, "loadSplashInterAds3: ");
            AppOpenManager.getInstance().enableAppResume();
            adListener.onAdClosed();
            adListener.onNextAction();
            Log.d(TAG, "loadSplashInterAds3: 10");
            return;
        }
        mInterstitialSplash.setOnPaidEventListener(adValue -> {
            trackRevenue(mInterstitialSplash.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
            Log.d(TAG, "OnPaidEvent splash:" + adValue.getValueMicros());
            AdmobEvent.logPaidAdImpression(adValue, mInterstitialSplash.getAdUnitId(), AdType.INTERSTITIAL);
            adListener.onEarnRevenue((double) adValue.getValueMicros());
        });

        if (handlerTimeout != null && rdTimeout != null) {
            handlerTimeout.removeCallbacks(rdTimeout);
        }

        if (adListener != null) {
            adListener.onAdLoaded();
        }

        mInterstitialSplash.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                isShowLoadingSplash = false;
                if (logTimeLoadAdsSplash) {
                    long timeLoad = System.currentTimeMillis() - currentTime;
                    Log.e(TAG, "load ads time: " + timeLoad);
                    FirebaseAnalyticsUtil.logTimeLoadAdsSplash(round1000(timeLoad));
                }
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                Log.e(TAG, "DismissedFullScreenContent Splash");
                if (AppOpenManager.getInstance().isInitialized) {
                    AppOpenManager.getInstance().enableAppResume();
                }
                if (adListener != null) {
                    adListener.onInterDismiss();
                    adListener.onAdClosed();
                    if (!openActivityAfterShowInterAds) {
                        adListener.onNextAction();
                    }

                    if (dialog != null) {
                        dialog.dismiss();
                    }

                }
                mInterstitialSplash = null;
                isShowLoadingSplash = false;
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                Log.e(TAG, "onAdFailedToShowFullScreenContent : " + adError);
                mInterstitialSplash = null;
                isShowLoadingSplash = false;
                if (adListener != null) {
                    adListener.onAdFailedToShow(adError);
                    if (!openActivityAfterShowInterAds) {
                        adListener.onNextAction();
                        Log.d(TAG, "loadSplashInterAds3: 13");
                    }

                    if (dialog != null) {
                        dialog.dismiss();
                    }
                }
            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                if (timeLimitAds > 1000) {
                    setTimeLimitInter();
                }
                AdmobEvent.logClickAdsEvent(mInterstitialSplash.getAdUnitId());
            }
        });
        if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            try {
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
                dialog = new LoadingAdsDialog(activity);
                try {
                    dialog.show();
                } catch (Exception e) {
                    adListener.onNextAction();
                    Log.d(TAG, "loadSplashInterAds3: 14");
                    return;
                }
            } catch (Exception e) {
                dialog = null;
                Log.e(TAG, e.getMessage(), e);
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (AppOpenManager.getInstance().isInitialized) {
                    AppOpenManager.getInstance().disableAppResume();
                }

                if (openActivityAfterShowInterAds && adListener != null) {
                    adListener.onAdClosed();
                    adListener.onNextAction();
                    Log.d(TAG, "loadSplashInterAds3: 15");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (dialog != null && dialog.isShowing() && !activity.isDestroyed()) dialog.dismiss();
                    }, 1500);
                }

                if (activity != null && mInterstitialSplash != null) {
                    mInterstitialSplash.show(activity);
                    Log.e(TAG, "onShowSplash: mInterstitialSplash.show");
                    isShowLoadingSplash = false;
                } else if (adListener != null) {
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                    adListener.onAdClosed();
                    adListener.onNextAction();
                    Log.d(TAG, "loadSplashInterAds3: 16");
                    isShowLoadingSplash = false;
                }
            }, 500);
        } else {
            isShowLoadingSplash = false;
            Log.e(TAG, "onShowSplash: fail on background");
        }
    }

    /* ============================== END Inter Splash ============================== */
    /* ============================== START Inter ============================== */

    public void loadInterAds(Context context, String id, InterCallback adCallback) {
        Log.d(TAG, "loadInterAds: ");
        if (!isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            adCallback.onNextAction();
            adCallback.onAdFailedToLoad(null);
            return;
        }
        adCallback.onAdLoaded();
        if (isShowInter) {
            isTimeout = false;
            InterstitialAd.load(context, id, getAdRequest(), new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    adCallback.onAdLoadSuccess(interstitialAd);
                    //tracking adjust
                    interstitialAd.setOnPaidEventListener(adValue -> {
                        trackRevenue(interstitialAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                        Log.d(TAG, "OnPaidEvent Interstitial:" + adValue.getValueMicros());
                        AdmobEvent.logPaidAdImpression(adValue, interstitialAd.getAdUnitId(), AdType.INTERSTITIAL);
                        adCallback.onEarnRevenue((double) adValue.getValueMicros());
                    });
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    // Handle the error
                    Log.i(TAG, loadAdError.getMessage());
                    adCallback.onAdFailedToLoad(loadAdError);
                    adCallback.onNextAction();
                }
            });
        }
    }

    public void loadInterAdsFloor(Context context, String idInter, InterCallback adCallback) {
        Log.d(TAG, "loadInterAdsFloor: " + idInter);
        if (Strings.isNullOrEmpty(idInter) || !AdsConsentManager.getConsentResult(context) || !Admob.isShowAllAds) {
            adCallback.onAdFailedToLoad(null);
            adCallback.onNextAction();
            return;
        }
        loadInterAdsFloorById(context, idInter, adCallback);
    }

    private void loadInterAdsFloorById(Context context, String idInter, InterCallback adCallback) {
        Log.d(TAG, "loadInterAdsFloorById: " + idInter);
        if (Strings.isNullOrEmpty(idInter) || !isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            adCallback.onNextAction();
            adCallback.onAdFailedToLoad(null);
            return;
        }
        Log.e("Admob", "Load Inter id: " + idInter);

        adCallback.onAdLoaded();

        if (isShowInter) {
            isTimeout = false;
            InterstitialAd.load(context, idInter, getAdRequest(), new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    adCallback.onAdLoadSuccess(interstitialAd);
                    //tracking adjust
                    interstitialAd.setOnPaidEventListener(adValue -> {
                        trackRevenue(interstitialAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                        Log.d(TAG, "OnPaidEvent Interstitial:" + adValue.getValueMicros());
                        AdmobEvent.logPaidAdImpression(adValue, interstitialAd.getAdUnitId(), AdType.INTERSTITIAL);
                        adCallback.onEarnRevenue((double) adValue.getValueMicros());
                    });
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    adCallback.onAdFailedToLoad(loadAdError);
//                    listID.remove(0);
//                    if (listID.isEmpty()) {
//                        // Log event admob
//                        adCallback.onAdFailedToLoad(loadAdError);
//                    } else {
//                        //end log
//                        loadInterAdsFloorByList(context, listID, adCallback);
//                    }
                }

            });
        }
    }

    public void showInterAds(Context context, InterstitialAd mInterstitialAd, final InterCallback callback) {
        Log.d(TAG, "time: " + (System.currentTimeMillis() - lastTimeDismissInter) + " - stateInter: " + stateInter);
        if ((System.currentTimeMillis() - lastTimeDismissInter > timeInterval && stateInter == StateInter.DISMISS) || !AdsConsentManager.getConsentResult(context)) {
            showInterAds(context, mInterstitialAd, callback, false);
        } else {
            callback.onNextAction();
        }
    }

    private void showInterAds(Context context, InterstitialAd mInterstitialAd, final InterCallback callback, boolean shouldReload) {
        currentClicked = numShowAds;
        showInterAdByTimes(context, mInterstitialAd, callback, shouldReload);
    }

    private void showInterAdByTimes(final Context context, InterstitialAd mInterstitialAd, final InterCallback callback, final boolean shouldReloadAds) {
        if (logLogTimeShowAds) {
            currentTimeShowAds = System.currentTimeMillis();
        }
        Helper.setupAdmobData(context);
        if (!isShowAllAds) {
            callback.onNextAction();
            return;
        }
        if (mInterstitialAd == null) {
            if (callback != null) {
                callback.onNextAction();
                callback.onLoadInter();
            }
            return;
        }
        stateInter = StateInter.SHOWING;
        mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent();
                callback.onInterDismiss();
                // Called when fullscreen content is dismissed.
                if (AppOpenManager.getInstance().isInitialized) {
                    AppOpenManager.getInstance().enableAppResume();
                }
                Log.d(TAG, "onAdDismissedFullScreenContent: stateInter = " + stateInter);
                if (stateInter == StateInter.SHOWED) lastTimeDismissInter = System.currentTimeMillis();
                stateInter = StateInter.DISMISS;
                if (!openActivityAfterShowInterAds) {
                    callback.onNextAction();
                }
                callback.onLoadInter();

                if (dialog != null) {
                    dialog.dismiss();
                }
                Log.e(TAG, "onAdDismissedFullScreenContent");
            }


            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                super.onAdFailedToShowFullScreenContent(adError);
                Log.e(TAG, "onAdFailedToShowFullScreenContent: " + adError.getMessage());
                stateInter = StateInter.DISMISS;

                if (dialog != null) {
                    dialog.dismiss();
                }

                // Called when fullscreen content failed to show.
                callback.onNextAction();
                callback.onAdFailedToShow(adError);
                callback.onLoadInter();
            }

            @Override
            public void onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent();
                // Called when fullscreen content is shown.
                callback.onAdImpression();
                stateInter = StateInter.SHOWED;
                if (logLogTimeShowAds) {
                    long timeLoad = System.currentTimeMillis() - currentTimeShowAds;
                    Log.e(TAG, "show ads time: " + timeLoad);
                    FirebaseAnalyticsUtil.logTimeLoadShowAdsInter((double) timeLoad / 1000);
                }
            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                callback.onAdClicked();
                if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                if (timeLimitAds > 1000) setTimeLimitInter();
                AdmobEvent.logClickAdsEvent(mInterstitialAd.getAdUnitId());
            }
        });

        if (Helper.getNumClickAdsPerDay(context, mInterstitialAd.getAdUnitId()) < maxClickAds) {
            showInterstitialAd(context, mInterstitialAd, callback);
            return;
        }
        if (callback != null) {
            callback.onNextAction();
        }
    }

    private void showInterstitialAd(Context context, InterstitialAd mInterstitialAd, InterCallback callback) {
        if (!isShowInter || !isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            callback.onAdClosed();
            callback.onNextAction();
            return;
        }
        if (!isNetworkConnected(context) || mInterstitialAd == null) {
            callback.onNextAction();
            return;
        }
        currentClicked++;
        if (currentClicked >= numShowAds) {
            if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                try {
                    if (dialog != null && dialog.isShowing()) dialog.dismiss();
                    dialog = new LoadingAdsDialog(context);
                    try {
                        dialog.show();
                    } catch (Exception e) {
                        callback.onAdClosed();
                        callback.onNextAction();
                        return;
                    }
                } catch (Exception e) {
                    dialog = null;
                    Log.e(TAG, e.getMessage(), e);
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (AppOpenManager.getInstance().isInitialized) {
                        AppOpenManager.getInstance().disableAppResume();
                    }

                    if (openActivityAfterShowInterAds && callback != null) {
                        callback.onAdClosed();
                        callback.onNextAction();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (dialog != null && dialog.isShowing() && !((Activity) context).isDestroyed()) dialog.dismiss();
                        }, 1500);
                    }
                    mInterstitialAd.show((Activity) context);

                }, 800);

            }
            currentClicked = 0;
        } else if (callback != null) {
            if (dialog != null) {
                dialog.dismiss();
            }
            callback.onAdClosed();
            callback.onNextAction();
        }
    }

    public void loadAndShowInter(AppCompatActivity activity, String idInter, int timeDelay, int timeOut, InterCallback callback) {
        if (!isNetworkConnected(activity) || !AdsConsentManager.getConsentResult(activity)) {
            callback.onAdClosed();
            callback.onNextAction();
            return;
        }
        if (!isShowAllAds && !isShowInter) {
            callback.onAdClosed();
            callback.onNextAction();
            return;
        }
        Log.d(TAG, "time: " + (System.currentTimeMillis() - lastTimeDismissInter) + " - stateInter: " + stateInter);
        if (System.currentTimeMillis() - lastTimeDismissInter < timeInterval || stateInter != StateInter.DISMISS) {
            callback.onAdClosed();
            callback.onNextAction();
            return;
        }

        if (AppOpenManager.getInstance().isInitialized) {
            AppOpenManager.getInstance().disableAppResumeWithActivity(activity.getClass());
        }

        Dialog dialog2 = new LoadingAdsDialog(activity);
        dialog2.show();
        InterstitialAd.load(activity, idInter, getAdRequestTimeOut(timeOut), new InterstitialAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                dialog2.dismiss();
                callback.onAdFailedToLoad(loadAdError);
                if (AppOpenManager.getInstance().isInitialized) {
                    AppOpenManager.getInstance().enableAppResumeWithActivity(activity.getClass());
                }
            }

            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                super.onAdLoaded(interstitialAd);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    stateInter = StateInter.SHOWING;
                    interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            dialog2.dismiss();
                            callback.onInterDismiss();
                            callback.onAdClosed();
                            callback.onNextAction();
                            callback.onLoadInter();
                            Log.d(TAG, "onAdDismissedFullScreenContent: stateInter = " + stateInter);
                            if (stateInter == StateInter.SHOWED) lastTimeDismissInter = System.currentTimeMillis();
                            stateInter = StateInter.DISMISS;
                            if (AppOpenManager.getInstance().isInitialized) {
                                AppOpenManager.getInstance().enableAppResumeWithActivity(activity.getClass());
                            }
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                            dialog2.dismiss();
                            callback.onAdClosed();
                            callback.onNextAction();
                            callback.onLoadInter();
                            stateInter = StateInter.DISMISS;
                            Log.d(TAG, "onAdFailedToShowFullScreenContent: " + adError.getMessage());
                            if (AppOpenManager.getInstance().isInitialized) {
                                AppOpenManager.getInstance().enableAppResumeWithActivity(activity.getClass());
                            }
                        }

                        @Override
                        public void onAdShowedFullScreenContent() {
                            stateInter = StateInter.SHOWED;
                            Log.d("TAG", "The ad was shown.");
                        }

                        @Override
                        public void onAdClicked() {
                            super.onAdClicked();
                            if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                            if (timeLimitAds > 1000) {
                                setTimeLimitInter();
                            }
                            AdmobEvent.logClickAdsEvent(mInterstitialSplash.getAdUnitId());
                        }
                    });
                    if (activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                        interstitialAd.show(activity);
                    } else {
                        if (AppOpenManager.getInstance().isInitialized) {
                            AppOpenManager.getInstance().enableAppResumeWithActivity(activity.getClass());
                            dialog2.dismiss();
                        }
                        // dialog.dismiss();
                    }
                }, timeDelay);
            }
        });
    }

    /* ============================== END Inter ============================== */
    /* ============================== START Rewarded Ads ============================== */

    public RewardedAd getRewardedAd() {
        return this.rewardedAd;
    }

    public void loadAndShowReward(Context context, Activity activity, String id, RewardCallback adCallback) {
        if (!isShowAllAds || !isNetworkConnected(activity) || !AdsConsentManager.getConsentResult(context)) {
            adCallback.onAdClosed();
            return;
        }
        dialogLoadingLoadAndShowReward = new LoadingAdsDialog(context);
        dialogLoadingLoadAndShowReward.show();
        RewardedAd.load(context, id, getAdRequest(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                //Track revenue
                rewardedAd.setOnPaidEventListener(adValue -> {
                    trackRevenue(rewardedAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                    Log.d(TAG, "OnPaidEvent Reward:" + adValue.getValueMicros());
                    AdmobEvent.logPaidAdImpression(adValue, rewardedAd.getAdUnitId(), AdType.REWARDED);
                });
                //Show reward
                showReward(activity, rewardedAd, adCallback);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                Log.e(TAG, "RewardedAd onAdFailedToLoad: " + loadAdError.getMessage());
                adCallback.onAdClosed();
            }
        });
    }

    private void showReward(Activity activity, RewardedAd rewardedAd, RewardCallback adCallback) {
        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent();
                if (adCallback != null) adCallback.onAdClosed();
                if (AppOpenManager.getInstance().isInitialized) {
                    AppOpenManager.getInstance().enableAppResume();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                super.onAdFailedToShowFullScreenContent(adError);
                Log.e(TAG, "onAdFailedToShowFullScreenContent: " + adError);
                if (dialogLoadingLoadAndShowReward != null && dialogLoadingLoadAndShowReward.isShowing()) {
                    dialogLoadingLoadAndShowReward.dismiss();
                }
                if (adCallback != null) adCallback.onAdFailedToShow(adError.getCode());
            }

            @Override
            public void onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent();
                if (dialogLoadingLoadAndShowReward != null && dialogLoadingLoadAndShowReward.isShowing()) {
                    dialogLoadingLoadAndShowReward.dismiss();
                }
                if (AppOpenManager.getInstance().isInitialized) {
                    AppOpenManager.getInstance().disableAppResume();
                }
                adCallback.onAdImpression();
            }

            public void onAdClicked() {
                super.onAdClicked();
                if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                AdmobEvent.logClickAdsEvent(rewardedAd.getAdUnitId());
            }
        });
        rewardedAd.show(activity, rewardItem -> {
            if (adCallback != null) {
                adCallback.onEarnedReward(rewardItem);
            }
        });
    }

    public void showRewardAds(final Activity mActivity, final RewardCallback adCallback) {
        if (!isShowAllAds || !isNetworkConnected(mActivity) || !AdsConsentManager.getConsentResult(mActivity)) {
            adCallback.onAdClosed();
            return;
        }
        if (rewardedAd == null) {
            initRewardAds(mActivity, rewardedId);
            adCallback.onAdFailedToShow(0);
            return;
        } else {
            Admob.this.rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent();
                    if (adCallback != null) adCallback.onAdClosed();

                    if (AppOpenManager.getInstance().isInitialized) {
                        AppOpenManager.getInstance().enableAppResume();
                    }
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    super.onAdFailedToShowFullScreenContent(adError);
                    Log.e(TAG, "onAdFailedToShowFullScreenContent: " + adError);
                    if (adCallback != null) adCallback.onAdFailedToShow(adError.getCode());
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    super.onAdShowedFullScreenContent();
                    if (AppOpenManager.getInstance().isInitialized) {
                        AppOpenManager.getInstance().disableAppResume();
                    }
                    rewardedAd = null;
                    initRewardAds(mActivity, rewardedId);
                    adCallback.onAdImpression();
                }

                public void onAdClicked() {
                    super.onAdClicked();
                    if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                    AdmobEvent.logClickAdsEvent(rewardedAd.getAdUnitId());
                }
            });
            rewardedAd.show(mActivity, rewardItem -> {
                if (adCallback != null) {
                    adCallback.onEarnedReward(rewardItem);

                }
            });
        }
    }

    public void initRewardAds(Context context, String id) {
        if (!isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            return;
        }
        this.rewardedId = id;
        RewardedAd.load(context, id, getAdRequest(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                Admob.this.rewardedAd = rewardedAd;
                Admob.this.rewardedAd.setOnPaidEventListener(adValue -> {
                    trackRevenue(Admob.this.rewardedAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                    Log.d(TAG, "OnPaidEvent Reward:" + adValue.getValueMicros());
                    AdmobEvent.logPaidAdImpression(adValue, rewardedAd.getAdUnitId(), AdType.REWARDED);
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                Log.e(TAG, "RewardedAd onAdFailedToLoad: " + loadAdError.getMessage());
            }
        });
    }

    public RewardedAd getRewardedAdLoaded() {
        return Admob.this.rewardedAd;
    }

    /* ============================== END Rewarded Ads ============================== */
    /* ============================== START New Rewarded Ads ============================== */

    private final List<RewardAdModel> listReward = new ArrayList<>();

    public void loadNewReward(Context context, String idAd, RewardAdCallback callback) {
        if (!isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            callback.onAdLoaded(false);
            return;
        }
        RewardAdModel rewardAdModel = null;
        for (RewardAdModel item : listReward) {
            if (item.idAd.equals(idAd)) {
                rewardAdModel = item;
            }
        }
        if (rewardAdModel == null) {
            rewardAdModel = new RewardAdModel(idAd);
            listReward.add(rewardAdModel);
        }
        dialogLoadingLoadAndShowReward = new LoadingAdsDialog(context);
        dialogLoadingLoadAndShowReward.show();
        rewardAdModel.loadReward(context, new RewardAdCallback() {
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                callback.onAdFailedToLoad(loadAdError);
            }

            public void onAdLoaded(Boolean isSuccessful) {
                if (dialogLoadingLoadAndShowReward.isShowing()) dialogLoadingLoadAndShowReward.dismiss();
                callback.onAdLoaded(isSuccessful);
            }
        });
    }

    public void showNewReward(Context context, String idAd, RewardAdCallback callback) {
        if (!isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            callback.onNextAction();
            return;
        }
        AppOpenManager.getInstance().disableAppResume();
        RewardAdModel rewardAdModel = null;
        for (RewardAdModel item : listReward) {
            if (item.idAd.equals(idAd)) {
                rewardAdModel = item;
            }
        }
        if (rewardAdModel == null) {
            rewardAdModel = new RewardAdModel(idAd);
            listReward.add(rewardAdModel);
        }
        dialogLoadingLoadAndShowReward = new LoadingAdsDialog(context);
        dialogLoadingLoadAndShowReward.show();
        rewardAdModel.showReward(context, new RewardAdCallback() {
            public void onAdDismissed() {
                callback.onAdDismissed();
                AppOpenManager.getInstance().enableAppResume();
            }

            public void onAdFailedToShow(@NonNull AdError adError) {
                callback.onAdFailedToShow(adError);
                AppOpenManager.getInstance().enableAppResume();
            }

            public void onAdShowed() {
                callback.onAdShowed();
            }

            public void onAdClicked() {
                callback.onAdClicked();
            }

            public void onNextAction() {
                callback.onNextAction();
            }

            public void onAdImpression() {
                callback.onAdImpression();
            }

            public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                callback.onUserEarnedReward(rewardItem);

            }
        });
    }

    public void loadAndShowReward(Context context, String idAd, RewardAdCallback callback) {
        if (!isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            callback.onNextAction();
            return;
        }
        AppOpenManager.getInstance().disableAppResume();
        RewardAdModel rewardAdModel = null;
        for (RewardAdModel item : listReward) {
            if (item.idAd.equals(idAd)) {
                rewardAdModel = item;
            }
        }
        if (rewardAdModel == null) {
            rewardAdModel = new RewardAdModel(idAd);
            listReward.add(rewardAdModel);
        }
        dialogLoadingLoadAndShowReward = new LoadingAdsDialog(context);
        dialogLoadingLoadAndShowReward.setCancelable(false);
        dialogLoadingLoadAndShowReward.show();
        rewardAdModel.loadAndShowReward(context, new RewardAdCallback() {
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                callback.onAdFailedToLoad(loadAdError);
                AppOpenManager.getInstance().enableAppResume();
            }

            public void onAdLoaded(Boolean isSuccessful) {
                if (!isSuccessful) {
                    if (dialogLoadingLoadAndShowReward.isShowing()) dialogLoadingLoadAndShowReward.dismiss();
                }
                callback.onAdLoaded(isSuccessful);
            }

            public void onAdDismissed() {
                if (dialogLoadingLoadAndShowReward.isShowing()) dialogLoadingLoadAndShowReward.dismiss();
                callback.onAdDismissed();
                AppOpenManager.getInstance().enableAppResume();
            }

            public void onAdFailedToShow(@NonNull AdError adError) {
                if (dialogLoadingLoadAndShowReward.isShowing()) dialogLoadingLoadAndShowReward.dismiss();
                callback.onAdFailedToShow(adError);
            }

            public void onAdShowed() {
                callback.onAdShowed();
            }

            public void onAdClicked() {
                callback.onAdClicked();
            }

            public void onNextAction() {
                callback.onNextAction();
            }

            public void onAdImpression() {
                callback.onAdImpression();
            }

            public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                callback.onUserEarnedReward(rewardItem);
            }
        });
    }

    /* ============================== END New Rewarded Ads ============================== */
    /* ============================== START Native Ads ============================== */

    public void loadNativeAd(Context context, String id, final NativeCallback callback) {
        Log.e(TAG, "Load Native id: " + id);
        if (!isShowAllAds || !isNetworkConnected(context) || !AdsConsentManager.getConsentResult(context)) {
            callback.onAdFailedToLoad();
        } else {
            if (isShowNative) {
                if (isNetworkConnected(context)) {
                    VideoOptions videoOptions = new VideoOptions.Builder().setStartMuted(true).build();

                    com.google.android.gms.ads.nativead.NativeAdOptions adOptions = new com.google.android.gms.ads.nativead.NativeAdOptions.Builder().setVideoOptions(videoOptions).build();
                    AdLoader adLoader = new AdLoader.Builder(context, id).forNativeAd(nativeAd -> {
                        callback.onNativeAdLoaded(nativeAd);
                        nativeAd.setOnPaidEventListener(adValue -> {
                            if (nativeAd.getResponseInfo() != null) {
                                trackRevenue(nativeAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                            }
                            Log.d(TAG, "OnPaidEvent Native:" + adValue.getValueMicros());
                            AdmobEvent.logPaidAdImpression(adValue, id, AdType.NATIVE);
                            callback.onEarnRevenue((double) adValue.getValueMicros());
                        });
                    }).withAdListener(new AdListener() {
                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError error) {
                            Log.e(TAG, "NativeAd onAdFailedToLoad: " + error.getMessage());
                            callback.onAdFailedToLoad();
                        }

                        @Override
                        public void onAdClicked() {
                            super.onAdClicked();
                            Log.e(TAG, "NativeAd onAdClicked: ");
                            callback.onAdClicked();
                            if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                            AdmobEvent.logClickAdsEvent(id);
                        }
                    }).withNativeAdOptions(adOptions).build();
                    adLoader.loadAd(getAdRequest());
                } else {
                    callback.onAdFailedToLoad();
                }
            } else {
                callback.onAdFailedToLoad();
            }
        }

    }

    public void loadNativeAd(Context context, String id, FrameLayout frameLayout, int shimmerLayout, int layoutNative) {
        frameLayout.removeAllViews();
        if (isShowNative && isNetworkConnected(context) && isShowNative && AdsConsentManager.getConsentResult(context)) {
            View shimmerFrameLayout = LayoutInflater.from(context).inflate(shimmerLayout, null);
            frameLayout.addView(shimmerFrameLayout);
            VideoOptions videoOptions = new VideoOptions.Builder().setStartMuted(true).build();

            com.google.android.gms.ads.nativead.NativeAdOptions adOptions = new com.google.android.gms.ads.nativead.NativeAdOptions.Builder().setVideoOptions(videoOptions).build();
            AdLoader adLoader = new AdLoader.Builder(context, id).forNativeAd(nativeAd -> {
                NativeAdView adView = (NativeAdView) LayoutInflater.from(context).inflate(layoutNative, null);
                frameLayout.removeAllViews();
                frameLayout.addView(adView);
                Admob.getInstance().pushAdsToViewCustom(nativeAd, adView);
                nativeAd.setOnPaidEventListener(adValue -> {
                    if (nativeAd.getResponseInfo() != null)
                        trackRevenue(nativeAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                    Log.d(TAG, "OnPaidEvent Native:" + adValue.getValueMicros());
                    AdmobEvent.logPaidAdImpression(adValue, id, AdType.NATIVE);
                });
            }).withAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    Log.e(TAG, "NativeAd onAdFailedToLoad: " + error.getMessage());
                    frameLayout.removeAllViews();
                }

                @Override
                public void onAdClicked() {
                    super.onAdClicked();
                    if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                    AdmobEvent.logClickAdsEvent(id);
                    if (timeLimitAds > 1000) {
                        setTimeLimitNative();
                    }
                }
            }).withNativeAdOptions(adOptions).build();
            adLoader.loadAd(getAdRequest());
        }
    }

    public void loadNativeAd(Context context, String id, FrameLayout frameLayout, int layoutNative) {
        if (!isShowAllAds || !isNetworkConnected(context) || !AdsConsentManager.getConsentResult(context)) {
            frameLayout.removeAllViews();
            return;
        }
        if (isShowNative) {
            if (isNetworkConnected(context)) {
                VideoOptions videoOptions = new VideoOptions.Builder().setStartMuted(true).build();

                com.google.android.gms.ads.nativead.NativeAdOptions adOptions = new com.google.android.gms.ads.nativead.NativeAdOptions.Builder().setVideoOptions(videoOptions).build();
                AdLoader adLoader = new AdLoader.Builder(context, id).forNativeAd(nativeAd -> {
                    NativeAdView adView = (NativeAdView) LayoutInflater.from(context).inflate(layoutNative, null);
                    frameLayout.removeAllViews();
                    frameLayout.addView(adView);
                    Admob.getInstance().pushAdsToViewCustom(nativeAd, adView);
                    nativeAd.setOnPaidEventListener(adValue -> {
                        if (nativeAd.getResponseInfo() != null)
                            trackRevenue(nativeAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                        Log.d(TAG, "OnPaidEvent Native:" + adValue.getValueMicros());
                        AdmobEvent.logPaidAdImpression(adValue, id, AdType.NATIVE);
                    });
                }).withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        Log.e(TAG, "NativeAd onAdFailedToLoad: " + error.getMessage());
                        frameLayout.removeAllViews();
                    }

                    @Override
                    public void onAdClicked() {
                        super.onAdClicked();
                        if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                        AdmobEvent.logClickAdsEvent(id);
                        if (timeLimitAds > 1000) {
                            setTimeLimitNative();
                        }
                    }
                }).withNativeAdOptions(adOptions).build();
                adLoader.loadAd(getAdRequest());
            } else {
                frameLayout.removeAllViews();
            }
        } else {
            frameLayout.removeAllViews();
        }
    }

    /* ============================== Native Ads Floor ============================== */

    public void loadNativeAd(Context context, List<String> listID, final NativeCallback callback) {
        if (listID == null || !AdsConsentManager.getConsentResult(context)) {
            callback.onAdFailedToLoad();
        } else if (listID.isEmpty()) {
            callback.onAdFailedToLoad();
        } else {
            List<String> listIDNew = new ArrayList<>(listID);
            Log.e(TAG, "listID: " + listID);
            Log.e(TAG, "listIDNew: " + listID);
            Log.e(TAG, listIDNew + listID.get(0));

            loadNativeAd(context, listIDNew.get(0), new NativeCallback() {
                @Override
                public void onNativeAdLoaded(NativeAd nativeAd) {
                    super.onNativeAdLoaded(nativeAd);
                    callback.onNativeAdLoaded(nativeAd);
                }

                @Override
                public void onAdClicked() {
                    super.onAdClicked();
                    callback.onAdClicked();
                }

                @Override
                public void onAdFailedToLoad() {
                    super.onAdFailedToLoad();
                    if (listIDNew.size() > 1) {
                        listIDNew.remove(0);
                        loadNativeAd(context, listIDNew, callback);
                    } else {
                        callback.onAdFailedToLoad();
                    }

                }
            });
        }
    }

    private void loadNativeAdFloor(Context context, List<String> listID, final NativeCallback callback) {
        if (listID == null || listID.isEmpty() || !AdsConsentManager.getConsentResult(context)) {
            callback.onAdFailedToLoad();
        } else {
            if (!isShowAllAds || !isNetworkConnected(context)) {
                callback.onAdFailedToLoad();
                return;
            }
            if (!listID.isEmpty()) {
                int position = 0;
                Log.e(TAG, "Load Native ID :" + listID.get(position));
                loadNativeAd(context, listID.get(position), callback);
            } else {
                callback.onAdFailedToLoad();
            }
        }
    }

    public void loadNativeAdFloor(Context context, List<String> listID, FrameLayout frameLayout, int layoutNative) {
        if (listID == null || listID.isEmpty() || !AdsConsentManager.getConsentResult(context)) {
            frameLayout.removeAllViews();
        } else {
            if (!isNetworkConnected(context) || !isShowAllAds) {
                frameLayout.removeAllViews();
                return;
            }
            NativeCallback callback1 = new NativeCallback() {
                @Override
                public void onNativeAdLoaded(NativeAd nativeAd) {
                    super.onNativeAdLoaded(nativeAd);
                    NativeAdView adView = (NativeAdView) LayoutInflater.from(context).inflate(layoutNative, null);
                    frameLayout.removeAllViews();
                    frameLayout.addView(adView);
                    Admob.getInstance().pushAdsToViewCustom(nativeAd, adView);
                    nativeAd.setOnPaidEventListener(adValue -> {
                        if (nativeAd.getResponseInfo() != null)
                            trackRevenue(nativeAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                        Log.d(TAG, "OnPaidEvent Native:" + adValue.getValueMicros());
                        AdmobEvent.logPaidAdImpression(adValue, listID.get(0), AdType.NATIVE);
                    });
                }

                @Override
                public void onAdFailedToLoad() {
                    super.onAdFailedToLoad();
                    if (!listID.isEmpty()) {
                        listID.remove(0);
                        loadNativeAdFloor(context, listID, frameLayout, layoutNative);
                    }
                }
            };
            if (!listID.isEmpty()) {
                int position = 0;
                Log.e(TAG, "Load Native ID :" + listID.get(position));
                loadNativeAd(context, listID.get(position), callback1);
            } else {
                frameLayout.removeAllViews();
            }
        }
    }

    public void pushAdsToViewCustom(NativeAd nativeAd, NativeAdView adView) {
        Log.d(TAG, "pushAdsToViewCustom: " + nativeAd.getResponseInfo().getMediationAdapterClassName());
        adView.setMediaView(adView.findViewById(R.id.ad_media));
        if (adView.getMediaView() != null) {
//            adView.getMediaView().setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
//                @Override
//                public void onChildViewAdded(View parent, View child) {
//                    float scale = adView.getContext().getResources().getDisplayMetrics().density;
//
//                    int maxHeightPixels = 175;
//                    int maxHeightDp = (int) (maxHeightPixels * scale + 0.5f);
//
//                    if (child instanceof ImageView imageView) { //Images
//                        imageView.setAdjustViewBounds(true);
//                        imageView.setMaxHeight(maxHeightDp);
//
//                    } else { //Videos
//                        ViewGroup.LayoutParams params = child.getLayoutParams();
//                        params.height = maxHeightDp;
//                        child.setLayoutParams(params);
//                    }
//                }
//
//                @Override
//                public void onChildViewRemoved(View parent, View child) {
//                }
//            });

            adView.getMediaView().postDelayed(() -> {
                if (BuildConfig.DEBUG) {
                    float sizeMin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120, adView.getContext().getResources().getDisplayMetrics());
                    Log.e(TAG, "Native sizeMin: " + sizeMin);
                    Log.e(TAG, "Native w/h media : " + adView.getMediaView().getWidth() + "/" + adView.getMediaView().getHeight());
                    if (adView.getMediaView().getWidth() < sizeMin || adView.getMediaView().getHeight() < sizeMin) {
                        Toast.makeText(adView.getContext(), "Size media native not valid", Toast.LENGTH_SHORT).show();
                    }
                }
            }, 1000);
        }
        // Set other ad assets.
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.ad_app_icon));
        adView.setAdvertiserView(adView.findViewById(R.id.ad_advertiser));
        try {
            adView.setPriceView(adView.findViewById(R.id.ad_price));
        } catch (Exception e) {
            Log.e(TAG, "pushAdsToViewCustom: cannot find id \"ad_price\"", e);
        }

        // The headline is guaranteed to be in every UnifiedNativeAd.
        try {
            ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());
        } catch (Exception e) {
            Log.e(TAG, "pushAdsToViewCustom 1: getHeadlineView", e);
        }

        // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
        // check before trying to display them.
        try {
            if (nativeAd.getBody() == null) {
                adView.getBodyView().setVisibility(View.INVISIBLE);
            } else {
                adView.getBodyView().setVisibility(View.VISIBLE);
                ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
            }
        } catch (Exception e) {
            Log.e(TAG, "pushAdsToViewCustom 2: getBodyView", e);
        }

        try {
            if (nativeAd.getCallToAction() == null) {
                adView.getCallToActionView().setVisibility(View.INVISIBLE);
            } else {
                adView.getCallToActionView().setVisibility(View.VISIBLE);
                ((TextView) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
            }
        } catch (Exception e) {
            Log.e(TAG, "pushAdsToViewCustom 3: getCallToAction", e);
        }

        try {
            if (nativeAd.getIcon() == null) {
                adView.getIconView().setVisibility(View.GONE);
            } else {
                ((ImageView) adView.getIconView()).setImageDrawable(nativeAd.getIcon().getDrawable());
                adView.getIconView().setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "pushAdsToViewCustom 4: getIconView", e);
        }

        /*try {
            if (nativeAd.getPrice() == null) {
                Objects.requireNonNull(adView.getPriceView()).setVisibility(View.INVISIBLE);
            } else {
                Objects.requireNonNull(adView.getPriceView()).setVisibility(View.VISIBLE);
                ((TextView) adView.getPriceView()).setText(nativeAd.getPrice());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "pushAdsToViewCustom 5: ", e);
        }*/

        try {
            if (nativeAd.getStore() == null) {
                adView.getStoreView().setVisibility(View.INVISIBLE);
            } else {
                adView.getStoreView().setVisibility(View.VISIBLE);
                ((TextView) adView.getStoreView()).setText(nativeAd.getStore());
            }
        } catch (Exception e) {
            Log.e(TAG, "pushAdsToViewCustom 6: getStoreView", e);
        }

        try {
            if (nativeAd.getStarRating() == null) {
                adView.getStarRatingView().setVisibility(View.INVISIBLE);
            } else {
                adView.getStarRatingView().setVisibility(View.VISIBLE);
                ((RatingBar) adView.getStarRatingView()).setRating(nativeAd.getStarRating().floatValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "pushAdsToViewCustom 7: getStarRatingView", e);
        }

        try {
            if (nativeAd.getAdvertiser() == null) {
                adView.getAdvertiserView().setVisibility(View.INVISIBLE);
            } else {
                adView.getAdvertiserView().setVisibility(View.VISIBLE);
                ((TextView) adView.getAdvertiserView()).setText(nativeAd.getAdvertiser());
            }
        } catch (Exception e) {
            Log.e(TAG, "pushAdsToViewCustom 8: getAdvertiserView", e);
        }

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad. The SDK will populate the adView's MediaView
        // with the media content from this native ad.
        adView.setNativeAd(nativeAd);

    }

    public void loadNativeFragment(final Activity mActivity, String id, View parent) {
        final FrameLayout frameLayout = parent.findViewById(R.id.fl_load_native);
        final ShimmerFrameLayout containerShimmer = parent.findViewById(R.id.shimmer_container_native);
        loadNative(mActivity, containerShimmer, frameLayout, id, R.layout.native_admob_ad);
    }

    private void loadNative(final Context context, final ShimmerFrameLayout containerShimmer, final FrameLayout frameLayout, final String id, final int layout) {
        if (!isNetworkConnected(context) || !isShowAllAds || !AdsConsentManager.getConsentResult(context)) {
            containerShimmer.setVisibility(View.GONE);
            return;
        }
        frameLayout.removeAllViews();
        frameLayout.setVisibility(View.GONE);
        containerShimmer.setVisibility(View.VISIBLE);
        containerShimmer.startShimmer();

        VideoOptions videoOptions = new VideoOptions.Builder().setStartMuted(true).build();

        com.google.android.gms.ads.nativead.NativeAdOptions adOptions = new com.google.android.gms.ads.nativead.NativeAdOptions.Builder().setVideoOptions(videoOptions).build();


        AdLoader adLoader = new AdLoader.Builder(context, id).forNativeAd(nativeAd -> {
            containerShimmer.stopShimmer();
            containerShimmer.setVisibility(View.GONE);
            frameLayout.setVisibility(View.VISIBLE);
            @SuppressLint("InflateParams") NativeAdView adView = (NativeAdView) LayoutInflater.from(context).inflate(layout, null);
            pushAdsToViewCustom(nativeAd, adView);
            frameLayout.removeAllViews();
            frameLayout.addView(adView);
            nativeAd.setOnPaidEventListener(adValue -> {
                if (nativeAd.getResponseInfo() != null)
                    trackRevenue(nativeAd.getResponseInfo().getLoadedAdapterResponseInfo(), adValue);
                Log.d(TAG, "OnPaidEvent Interstitial:" + adValue.getValueMicros());
                AdmobEvent.logPaidAdImpression(adValue, id, AdType.NATIVE);
            });
        }).withAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                Log.e(TAG, "onAdFailedToLoad: " + error.getMessage());
                containerShimmer.stopShimmer();
                containerShimmer.setVisibility(View.GONE);
                frameLayout.setVisibility(View.GONE);
            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                if (disableAdResumeWhenClickAds) AppOpenManager.getInstance().disableAdResumeByClickAction();
                AdmobEvent.logClickAdsEvent(id);
            }

        }).withNativeAdOptions(adOptions).build();

        adLoader.loadAd(getAdRequest());
    }

    /* ============================== END Native Ads ============================== */

    public AdRequest getAdRequest() {
        AdRequest.Builder builder = new AdRequest.Builder();
        return builder.build();
    }

    private AdRequest getAdRequestTimeOut(int timeOut) {
        if (timeOut < 5000) timeOut = 5000;
        return new AdRequest.Builder().setHttpTimeoutMillis(timeOut).build();
    }

    public void setOpenActivityAfterShowInterAds(boolean openActivityAfterShowInterAds) {
        this.openActivityAfterShowInterAds = openActivityAfterShowInterAds;
    }

    /* ============================== GET INFO DEVICE ============================== */
    @SuppressLint("HardwareIds")
    public String getDeviceId(Activity activity) {
        String android_id = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID);
        return md5(android_id).toUpperCase();
    }

    private String md5(final String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                StringBuilder h = new StringBuilder(Integer.toHexString(0xFF & b));
                while (h.length() < 2) h.insert(0, "0");
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException ignored) {
        }
        return "";
    }

    /* ============================== END GET INFO DEVICE ============================== */

    private boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }

    private void setTimeLimitInter() {
        if (timeLimitAds > 1000) {
            isShowInter = false;
            new Handler().postDelayed(() -> isShowInter = true, timeLimitAds);
        }
    }

    private void setTimeLimitBanner() {
        if (timeLimitAds > 1000) {
            isShowBanner = false;
            new Handler().postDelayed(() -> isShowBanner = true, timeLimitAds);
        }
    }

    private void setTimeLimitNative() {
        if (timeLimitAds > 1000) {
            isShowNative = false;
            new Handler().postDelayed(() -> isShowNative = true, timeLimitAds);
        }
    }

    public void onCheckShowSplashWhenFail(final AppCompatActivity activity, final InterCallback callback, int timeDelay) {
        if (isNetworkConnected(activity)) {
            (new Handler(Looper.getMainLooper())).postDelayed(() -> {
                if (Admob.this.interstitialSplashLoaded() && !Admob.this.isShowLoadingSplash) {
                    Log.i("Admob", "show ad splash when show fail in background");
                    Admob.getInstance().onShowSplash(activity, callback);
                }

            }, timeDelay);
        }
    }

    public void onCheckShowSplashWhenFailClickButton(final AppCompatActivity activity, InterstitialAd interstitialAd, final InterCallback callback, int timeDelay) {
        if (interstitialAd != null) {
            if (isNetworkConnected(activity)) {
                (new Handler(Looper.getMainLooper())).postDelayed(() -> {
                    if (Admob.this.interstitialSplashLoaded() && !Admob.this.isShowLoadingSplash) {
                        Log.i("Admob", "show ad splash when show fail in background");
                        Admob.getInstance().onShowSplash(activity, interstitialAd, callback);
                    }

                }, timeDelay);
            }
        }
    }

    public int round1000(long time) {
        return Math.round((float) time / 1000);
    }

    public void setTimeInterval(long timeInterval) {
        this.lastTimeDismissInter = 0L;
        stateInter = StateInter.DISMISS;
        this.timeInterval = timeInterval;
    }

    private String tokenEventAdjust = "";

    public void setTokenEventAdjust(String tokenEventAdjust) {
        this.tokenEventAdjust = tokenEventAdjust;
    }

    //push adjust
    private void trackRevenue(@Nullable AdapterResponseInfo loadedAdapterResponseInfo, AdValue adValue) {
        String adName = "";
        if (loadedAdapterResponseInfo != null) adName = loadedAdapterResponseInfo.getAdSourceName();
        double valueMicros = adValue.getValueMicros() / 1000000d;
        Log.d("AdjustRevenue", "adName: " + adName + " - valueMicros: " + valueMicros);
        // send ad revenue info to Adjust
        AdjustAdRevenue adRevenue = new AdjustAdRevenue(AdjustConfig.AD_REVENUE_ADMOB);
        adRevenue.setRevenue(valueMicros, adValue.getCurrencyCode());
        adRevenue.setAdRevenueNetwork(adName);
        if (!tokenEventAdjust.isEmpty()) {
            AdjustEvent event = new AdjustEvent(tokenEventAdjust);
            adRevenue.setRevenue(valueMicros, adValue.getCurrencyCode());
            Adjust.trackEvent(event);
        }
        Adjust.trackAdRevenue(adRevenue);
    }
}