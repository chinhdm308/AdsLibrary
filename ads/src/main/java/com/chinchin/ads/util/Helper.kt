package com.chinchin.ads.util

import android.content.Context

object Helper {
    private const val FILE_SETTING = "setting.pref"
    private const val FILE_SETTING_ADMOB = "setting_admob.pref"
    private const val IS_FIRST_OPEN = "IS_FIRST_OPEN"
    private const val KEY_FIRST_TIME = "KEY_FIRST_TIME"


    /**
     * Trả về số click của 1 ads nào đó
     *
     * @param context: Context
     * @param idAds:   id ads
     * @return num of click ads per day
     */
    @JvmStatic
    fun getNumClickAdsPerDay(context: Context, idAds: String?): Int {
        return context.getSharedPreferences(FILE_SETTING_ADMOB, Context.MODE_PRIVATE).getInt(idAds, 0)
    }


    /**
     * nếu lần đầu mở app lưu thời gian đầu tiên vào SharedPreferences
     * nếu thời gian hiện tại so với thời gian đầu được 1 ngày thì reset lại data của admob.
     *
     * @param context: Context
     */
    @JvmStatic
    fun setupAdmobData(context: Context) {
        if (isFirstOpenApp(context)) {
            context.getSharedPreferences(FILE_SETTING_ADMOB, Context.MODE_PRIVATE).edit().putLong(KEY_FIRST_TIME, System.currentTimeMillis()).apply()
            context.getSharedPreferences(FILE_SETTING, Context.MODE_PRIVATE).edit().putBoolean(IS_FIRST_OPEN, true).apply()
            return
        }
        val firstTime = context.getSharedPreferences(FILE_SETTING_ADMOB, Context.MODE_PRIVATE).getLong(KEY_FIRST_TIME, System.currentTimeMillis())
        val rs = System.currentTimeMillis() - firstTime
        /*
       qua q ngày reset lại data
        */
        if (rs >= 24 * 60 * 60 * 1000) {
            resetAdmobData(context)
        }
    }

    @JvmStatic
    private fun resetAdmobData(context: Context) {
        context.getSharedPreferences(FILE_SETTING_ADMOB, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(FILE_SETTING_ADMOB, Context.MODE_PRIVATE).edit().putLong(KEY_FIRST_TIME, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    private fun isFirstOpenApp(context: Context): Boolean {
        return context.getSharedPreferences(FILE_SETTING, Context.MODE_PRIVATE).getBoolean(IS_FIRST_OPEN, false)
    }
}