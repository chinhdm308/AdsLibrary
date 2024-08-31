package com.chinchin.ads.util.remote_config

import android.content.Context

object SharePreRemoteConfig {
    private const val NAME_SHARE_PRE_REMOTE_CONFIG = "NAME_SHARE_PRE_REMOTE_CONFIG"

    fun setConfig(context: Context, key: String?, value: String?) {
        val pre = context.getSharedPreferences(NAME_SHARE_PRE_REMOTE_CONFIG, Context.MODE_PRIVATE)
        val editor = pre.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun getConfigString(context: Context, key: String?): String? {
        val pre = context.getSharedPreferences(NAME_SHARE_PRE_REMOTE_CONFIG, Context.MODE_PRIVATE)
        return pre.getString(key, "")
    }

    fun getConfigBoolean(context: Context, key: String?): Boolean {
        return getConfigString(context, key).toBoolean()
    }

    fun getConfigFloat(context: Context, key: String?): Float {
        return try {
            getConfigString(context, key)!!.toFloat()
        } catch (e: NumberFormatException) {
            0.0f
        }
    }

    fun getConfigInt(context: Context, key: String?): Int {
        try {
            val value = getConfigString(context, key)
            return if (value!!.contains(".")) Math.round(value.toFloat())
            else value.toInt()
        } catch (e: NumberFormatException) {
            return 0
        }
    }
}