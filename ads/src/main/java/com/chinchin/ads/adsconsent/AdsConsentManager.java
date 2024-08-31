package com.chinchin.ads.adsconsent;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The UMP writes its output to some attributes in SharedPreferences, outlined here. You can write some helper methods to query these attributes to find out what level of ad consent the user has given or whether the user is EEA or not, but you will need to look at more than just the VendorConsents string.
 * <p>
 * There are generally 5 attributes you will want to look for to determine whether ads will be served:
 * <p>
 * - IABTCF_gdprApplies - An integer (0 or 1) indicating whether the user is in the EEA
 * <p>
 * - IABTCF_PurposeConsents - A string of 0's and 1's up to 10 entries long indicating whether the user provided consent for the 10 different purposes
 * <p>
 * - IABTCF_PurposeLegitimateInterests - A string of 0's and 1's up to 10 entries long indicating whether the app has legitimate interest for the 10 different purposes
 * <p>
 * - IABTCF_VendorConsents - A string of 0s and 1s that is arbitrarily long, indicating whether a given vendor has been given consent for the previously mentioned purposes. Each vendor has an ID indicating their position in the string. For example Google's ID is 755, so if Google has been given consent then the 755th character in this string would be a "1". The full vendor list is available here.
 * <p>
 * - IABTCF_VendorLegitimateInterests - Similar to the vendor consent string, except that it indicates if the vendor has legitimate interest for the previously indicated purposes.
 */
public class AdsConsentManager {
    private static final String TAG = "AdsConsentManager";
    private final Activity activity;
    private final AtomicBoolean auAtomicBoolean;
    private final ConsentInformation consentInformation;

    public interface UMPResultListener {
        void onCheckUMPSuccess(boolean result);
    }

    public AdsConsentManager(Activity activity) {
        this.activity = activity;
        this.auAtomicBoolean = new AtomicBoolean(false);
        consentInformation = UserMessagingPlatform.getConsentInformation(activity);
    }

    /**
     * Helper variable to determine if the app can request ads.
     */
    public boolean canRequestAds() {
        return consentInformation.canRequestAds();
    }

    /**
     * Helper variable to determine if the privacy options form is required.
     */
    public boolean isPrivacyOptionsRequired() {
        return consentInformation.getPrivacyOptionsRequirementStatus() ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public void requestUMP(UMPResultListener umpResultListener) {
        this.requestUMP(false, "", false, umpResultListener);
    }

    public static boolean getConsentResult(Context context) {
        String consentResult = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
                .getString("IABTCF_PurposeConsents", "");
        return consentResult.isEmpty() || String.valueOf(consentResult.charAt(0)).equals("1");
    }

    /**
     * Helper method to call the UMP SDK methods to request consent information and load/show a
     * consent form if necessary.
     */
    public void requestUMP(Boolean enableDebug, String testDevice, Boolean resetData, UMPResultListener umpResultListener) {
        // For testing purposes, you can force a DebugGeography of EEA or NOT_EEA.
        ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                // Check your logcat output for the hashed device ID e.g.
                .addTestDeviceHashedId(testDevice)
                .build();
        // Set tag for under age of consent. false means users are not under age of consent.
        ConsentRequestParameters.Builder params = new ConsentRequestParameters.Builder();
        params.setTagForUnderAgeOfConsent(false);
        if (enableDebug) {
            params.setConsentDebugSettings(debugSettings);
        }
        ConsentRequestParameters consentRequestParameters = params.build();
        //ConsentInformation consentInformation = UserMessagingPlatform.getConsentInformation(activity);
        if (resetData) {
            consentInformation.reset();
        }

        ConsentInformation.OnConsentInfoUpdateSuccessListener onConsentInfoUpdateSuccessListener = () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                activity,
                loadAndShowError -> {
                    if (loadAndShowError != null)
                        Log.e(TAG, "onConsentInfoUpdateSuccess: " + loadAndShowError.getMessage());
                    if (!auAtomicBoolean.getAndSet(true)) {
                        umpResultListener.onCheckUMPSuccess(getConsentResult(activity));
                    }
                }
        );

        ConsentInformation.OnConsentInfoUpdateFailureListener onConsentInfoUpdateFailureListener = formError -> {
            if (!auAtomicBoolean.getAndSet(true)) {
                Log.e(TAG, "onConsentInfoUpdateFailure: " + formError.getMessage());
                umpResultListener.onCheckUMPSuccess(getConsentResult(activity));
            }
        };

        // Requesting an update to consent information should be called on every app launch.
        consentInformation.requestConsentInfoUpdate(
                activity,
                consentRequestParameters,
                onConsentInfoUpdateSuccessListener,
                onConsentInfoUpdateFailureListener
        );

        // Check if you can initialize the Google Mobile Ads SDK in parallel
        // while checking for new consent information. Consent obtained in
        // the previous session can be used to request ads.
        /*if (consentInformation.canRequestAds() && !auAtomicBoolean.getAndSet(true)) {
            umpResultListener.onCheckUMPSuccess(getConsentResult(activity));
            Log.d(TAG, "requestUMP: ");
        }*/
    }

    public void showPrivacyOption(Activity activity, UMPResultListener umpResultListener) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, (formError) -> {
            Log.d(TAG, "showPrivacyOption: " + getConsentResult(activity));

            UMPResultListener var10000 = umpResultListener;
            var10000.onCheckUMPSuccess(getConsentResult(activity));
        });
    }
}