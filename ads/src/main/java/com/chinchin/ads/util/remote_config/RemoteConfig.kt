package com.chinchin.ads.util.remote_config

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.util.concurrent.atomic.AtomicBoolean


object RemoteConfig {
    private const val TAG = "RemoteConfigLog"
    private val isFinishedCallRemote = MutableLiveData(false)

    fun initFirebaseConfig(context: Context, isSetUp: Boolean) {
        Log.d(TAG, "isSetUp: $isSetUp")
        if (isSetUp) {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            remoteConfig.reset()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.fetchAndActivate().addOnCompleteListener { task: Task<Boolean?> ->
                Log.d(TAG, "initFirebaseConfig:")
                if (task.result != null && task.result!!) {
                    isFinishedCallRemote.value = false
                    fetchDataRemote(context)
                }
                isFinishedCallRemote.postValue(true)
            }
        } else {
            isFinishedCallRemote.postValue(true)
        }
    }

    private fun fetchDataRemote(context: Context) {
        Log.d(TAG, "fetchDataRemote:")
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val allValues = remoteConfig.all
        for (key in allValues.keys) {
            val value = allValues[key]
            if (value != null) {
                SharePreRemoteConfig.setConfig(context, key, value.asString())
            }
        }
    }

    fun onRemoteConfigFetched(owner: LifecycleOwner, listener: OnCompleteListener) {
        val action = AtomicBoolean(true)
        isFinishedCallRemote.observe(owner) { finished: Boolean ->
            if (finished && action.get()) {
                action.set(false)
                listener.onComplete()
            }
        }
    }

    fun getRemoteConfigStringSingleParam(adUnitId: String?): String {
        val mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
        return mFirebaseRemoteConfig.getString(adUnitId!!)
    }

    interface OnCompleteListener {
        fun onComplete()
    }
}