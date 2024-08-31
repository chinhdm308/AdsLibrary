package com.chinchin.ads.util

import android.content.Context
import android.net.ConnectivityManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object NetworkUtil {

    @JvmStatic
    fun isConnectedNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetworkInfo
        return network != null && network.isConnected
    }

    private fun isActiveInternetConnection(context: Context): Boolean {
        if (isConnectedNetwork(context)) try {
            val urlConnection =
                URL("https://www.google.com").openConnection() as HttpURLConnection
            urlConnection.setRequestProperty("User-Agent", "Test")
            urlConnection.setRequestProperty("Connection", "close")
            urlConnection.connectTimeout = 2000
            urlConnection.connect()
            return urlConnection.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    @JvmStatic
    fun isNetworkActive(context: Context?): Boolean {
        return context != null && isConnectedNetwork(context)
    }
}
