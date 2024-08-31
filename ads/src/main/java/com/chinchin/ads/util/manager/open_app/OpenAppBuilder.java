package com.chinchin.ads.util.manager.open_app;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.chinchin.ads.dialog.LoadingAdsDialog;
import com.google.android.gms.ads.AdRequest;

public class OpenAppBuilder {
    private String idAd;
    OpenAppCallback openAppCallback = new OpenAppCallback();
    LoadingAdsDialog dialog;
    Activity currentActivity;

    public OpenAppBuilder(@NonNull Activity activity) {
        this.currentActivity = activity;
        dialog = new LoadingAdsDialog(activity);
    }


    public String getIdAd() {
        return idAd;
    }

    public OpenAppBuilder setId(String idAd) {
        this.idAd = idAd;
        return this;
    }

    public OpenAppBuilder setCallback(OpenAppCallback callback) {
        this.openAppCallback = callback;
        return this;
    }

    public OpenAppCallback getCallback() {
        return openAppCallback;
    }

    public AdRequest getAdNewRequest() {
        return new AdRequest.Builder().build();
    }


    public void showLoading() {
        if (dialog != null && !dialog.isShowing())
            dialog.show();
    }

    public void dismissLoading() {
        if (dialog != null && dialog.isShowing())
            dialog.dismiss();
    }
}
