package com.chinchin.ads.util.manager.native_ad;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;

import com.chinchin.ads.R;
import com.chinchin.ads.callback.NativeCallback;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.ads.nativead.NativeAdView;

public class NativeBuilder {
    private static final String TAG = "NativeBuilder";
    private NativeCallback callback = new NativeCallback();
    private String idAd;
    private NativeAdView nativeAdView;
    private NativeAdView nativeMetaAdView;
    private ShimmerFrameLayout shimmerFrameLayout;

    public NativeBuilder(Context context, FrameLayout flAd, @LayoutRes int idLayoutShimmer, @LayoutRes int idLayoutNative, @LayoutRes int idLayoutNativeMeta) {
        setLayoutAds(context, flAd, idLayoutShimmer, idLayoutNative, idLayoutNativeMeta);
    }

    private void setLayoutAds(Context context, FrameLayout flAd, @LayoutRes int idLayoutShimmer, @LayoutRes int idLayoutNative, @LayoutRes int idLayoutNativeMeta) {
        View _nativeAdView = LayoutInflater.from(context).inflate(idLayoutNative, null);
        View _nativeMetaAdView = LayoutInflater.from(context).inflate(idLayoutNativeMeta, null);
        View _shimmerFrameLayout = LayoutInflater.from(context).inflate(idLayoutShimmer, null);

        if (_nativeAdView instanceof NativeAdView) nativeAdView = (NativeAdView) _nativeAdView;
        else nativeAdView = (NativeAdView) LayoutInflater.from(context).inflate(R.layout.ads_native_large_btn_ads_bot2, null);
        if (_nativeMetaAdView instanceof NativeAdView) nativeMetaAdView = (NativeAdView) _nativeMetaAdView;
        else nativeMetaAdView = (NativeAdView) LayoutInflater.from(context).inflate(R.layout.ads_native_meta_large, null);
        if (_shimmerFrameLayout instanceof ShimmerFrameLayout) shimmerFrameLayout = (ShimmerFrameLayout) _shimmerFrameLayout;
        else
            shimmerFrameLayout = (ShimmerFrameLayout) LayoutInflater.from(context).inflate(R.layout.shimmer_native_large_btn_ads_bot2, null);

        flAd.removeAllViews();
        flAd.addView(nativeMetaAdView);
        flAd.addView(nativeAdView);
        flAd.addView(shimmerFrameLayout);
        showLoading();
    }

    public void setIdAd(String idAd) {
        this.idAd = idAd;
    }

    public String getIdAd() {
        return idAd;
    }

    public NativeAdView getNativeAdView() {
        return nativeAdView;
    }

    public NativeAdView getNativeMetaAdView() {
        return nativeMetaAdView;
    }

    public NativeCallback getCallback() {
        return callback;
    }

    public void setCallback(NativeCallback callback) {
        this.callback = callback;
    }

    public void showAd() {
        nativeAdView.setVisibility(View.VISIBLE);
        shimmerFrameLayout.setVisibility(View.GONE);
        nativeMetaAdView.setVisibility(View.GONE);
    }

    public void showAdMeta() {
        nativeMetaAdView.setVisibility(View.VISIBLE);
        nativeAdView.setVisibility(View.GONE);
        shimmerFrameLayout.setVisibility(View.GONE);
    }

    public void showLoading() {
        shimmerFrameLayout.setVisibility(View.VISIBLE);
        nativeAdView.setVisibility(View.GONE);
        nativeMetaAdView.setVisibility(View.GONE);
    }

    public void hideAd() {
        shimmerFrameLayout.setVisibility(View.GONE);
        nativeAdView.setVisibility(View.GONE);
    }
}
