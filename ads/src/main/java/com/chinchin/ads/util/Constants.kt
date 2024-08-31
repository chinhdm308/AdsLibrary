package com.chinchin.ads.util

import com.google.android.gms.ads.LoadAdError

object Constants {
    var BUILD_DEBUG: Boolean = true

    @JvmField
    var currentTotalRevenue001Ad: Float = 0f

    @JvmField
    val NO_INTERNET_ERROR: LoadAdError = LoadAdError(-100, "No Internet", "local", null, null)

    @JvmField
    val AD_NOT_AVAILABLE_ERROR: LoadAdError = LoadAdError(-200, "Ad Not Available", "local", null, null)

    @JvmField
    val AD_NOT_HAVE_ID: LoadAdError = LoadAdError(-300, "Not have id", "local", null, null)

    @JvmField
    val CONVERT_ACTIVITY_ERROR: LoadAdError = LoadAdError(-400, "Cannot convert to Activity", "local", null, null)
}
