package com.chinchin.ads.util.manager.banner

import com.chinchin.ads.callback.BannerCallBack

class BannerBuilder {

    var callBack: BannerCallBack = BannerCallBack()
        private set

    var idAd: String? = null
        private set

    fun setIdAd(idAd: String?): BannerBuilder {
        this.idAd = idAd
        return this
    }

    fun setCallBack(callBack: BannerCallBack): BannerBuilder {
        this.callBack = callBack
        return this
    }
}