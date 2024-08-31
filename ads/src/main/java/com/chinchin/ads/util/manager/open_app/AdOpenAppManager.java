package com.chinchin.ads.util.manager.open_app;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.chinchin.ads.util.NetworkUtil;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.common.base.Strings;

public class AdOpenAppManager {

    enum State {LOADING, LOADED, SHOWING, DISMISS}

    private static final String TAG = "OpenAppManager";

    OpenAppBuilder builder;
    static AppOpenAd myAppOpenAd;
    private String idAd;
    State state = State.DISMISS;

    public void setBuilder(OpenAppBuilder builder) {
        this.builder = builder;
    }

    public void setId(String idAd) {
        builder.setId(idAd);
    }

    private void loadOpenAdWithIdAd() {
        if (!NetworkUtil.isNetworkActive(builder.currentActivity)) {
            LoadAdError error = new LoadAdError(-1, "network isn't active", "local", null, null);
            builder.getCallback().onAdFailedToLoad(error);
            return;
        }
        if (Strings.isNullOrEmpty(idAd)) {
            LoadAdError error = new LoadAdError(-2, "can't load ad", "local", null, null);
            builder.getCallback().onAdFailedToLoad(error);
            return;
        }
        Log.d(TAG, "loadOpenAdWithList: " + idAd);
        if (myAppOpenAd == null)
            AppOpenAd.load(builder.currentActivity, idAd, builder.getAdNewRequest(), new AppOpenAd.AppOpenAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    idAd = "";
                    if (Strings.isNullOrEmpty(idAd)) {
                        Log.d(TAG, "onAdFailedToLoad: " + loadAdError);
                        builder.getCallback().onAdFailedToLoad(loadAdError);
                        builder.getCallback().onAdLoaded();
                        state = State.LOADED;
                    } else {
                        loadOpenAdWithIdAd();
                    }
                }

                @Override
                public void onAdLoaded(@NonNull AppOpenAd appOpenAd) {
                    super.onAdLoaded(appOpenAd);
                    Log.d(TAG, "onAdLoaded: ");
                    builder.getCallback().onAdLoaded();
                    myAppOpenAd = appOpenAd;
                    state = State.LOADED;
                }
            });
    }

    public void loadAd() {
        if (state != State.LOADING && myAppOpenAd == null) {
            state = State.LOADING;
            idAd = builder.getIdAd();
            loadOpenAdWithIdAd();
        }
    }

    public void showAd(Activity activity) {
        if (myAppOpenAd == null || builder.getCallback() == null) {
            builder.getCallback().onNextAction();
            return;
        }
        if (!NetworkUtil.isNetworkActive(builder.currentActivity)) {
            LoadAdError error = new LoadAdError(-1, "network isn't active", "local", null, null);
            builder.getCallback().onAdFailedToLoad(error);
            builder.getCallback().onNextAction();
            return;
        }
        builder.showLoading();
        myAppOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdClicked() {
                super.onAdClicked();
                Log.d(TAG, "onAdClicked:");
                builder.getCallback().onAdClicked();
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent();
                state = State.DISMISS;
                Log.d(TAG, "onAdDismissedFullScreenContent:");
                builder.dismissLoading();
                builder.getCallback().onAdClosed();
                builder.getCallback().onNextAction();
                myAppOpenAd = null;
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                super.onAdFailedToShowFullScreenContent(adError);
                state = State.DISMISS;
                Log.d(TAG, "onAdFailedToShowFullScreenContent: " + adError.getMessage());
                builder.getCallback().onAdFailedToShow(adError);
                builder.getCallback().onNextAction();
            }

            @Override
            public void onAdImpression() {
                super.onAdImpression();
                state = State.SHOWING;
                Log.d(TAG, "onAdImpression: ");
                builder.getCallback().onAdImpression();
            }

            @Override
            public void onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent();
                state = State.SHOWING;
                Log.d(TAG, "onAdShowedFullScreenContent: ");
                builder.getCallback().onAdShowed();
            }
        });
        myAppOpenAd.show(activity);
    }
}
