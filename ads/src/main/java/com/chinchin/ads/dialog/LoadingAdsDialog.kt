package com.chinchin.ads.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.annotation.LayoutRes
import com.chinchin.ads.R

class LoadingAdsDialog(context: Context) : Dialog(context, R.style.AppTheme) {

    private var layoutLoadingAdsView = -1

    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        if (layoutLoadingAdsView == -1) setContentView(R.layout.dialog_loading_ads)
        else setContentView(layoutLoadingAdsView)
    }

    fun setLayoutLoadingAdsView(@LayoutRes layoutLoadingAdsView: Int) {
        this.layoutLoadingAdsView = layoutLoadingAdsView
    }

    fun resetLayoutLoadingAdsView() {
        layoutLoadingAdsView = -1
    }
}
