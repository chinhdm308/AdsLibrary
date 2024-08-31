package com.chinchin.ads.billing;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.chinchin.ads.callback.BillingListener;
import com.chinchin.ads.callback.PurchaseListener;
import com.chinchin.ads.callback.UpdatePurchaseListener;
import com.chinchin.ads.util.Security;
import com.google.common.collect.ImmutableList;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppPurchase {
    private static final String LICENSE_KEY = null;
    private static final String MERCHANT_ID = null;
    private static final String TAG = "PurchaseEG";
    private boolean isPurchaseTest = false;
    public static final String PRODUCT_ID_TEST = "android.test.purchased";
    private static AppPurchase instance;

    private String price = "1.49$";
    private String oldPrice = "2.99$";

    private String productId;
    private ArrayList<QueryProductDetailsParams.Product> listSubsId;
    private ArrayList<QueryProductDetailsParams.Product> listInAppId;
    private PurchaseListener purchaseListener;
    private UpdatePurchaseListener updatePurchaseListener;
    private BillingListener billingListener;
    private Boolean isInitBillingFinish = false;
    private BillingClient billingClient;
    private List<ProductDetails> skuListInAppFromStore;
    private List<ProductDetails> skuListSubsFromStore;
    final private Map<String, ProductDetails> skuDetailsInAppMap = new HashMap<>();
    final private Map<String, ProductDetails> skuDetailsSubsMap = new HashMap<>();
    private boolean isAvailable;
    private boolean isListGot;
    private boolean isConsumePurchase = false;

    private int countReconnectBilling = 0;
    private int countMaxReconnectBilling = 4;
    //tracking purchase adjust
    private String idPurchaseCurrent = "";
    private int typeIAP;
    // status verify purchase INAPP & SUBS
    private boolean verifyFinish = false;

    private boolean isVerifyInApp = false;
    private boolean isVerifySubs = false;
    private boolean isUpdateInApp = false;
    private boolean isUpdateSubs = false;

    private boolean isPurchase = false;//state purchase on app
    private String idPurchased = "";//id purchased
    private final List<PurchaseResult> listOwnerIdSubs = new ArrayList<>();//id sub
    private final List<String> listOwnerIdInApp = new ArrayList<>();//id inapp

    private double discount = 1;

    private Handler handlerTimeout;
    private Runnable rdTimeout;

    private final PurchasesUpdatedListener purchasesUpdatedListener = new PurchasesUpdatedListener() {
        @Override
        public void onPurchasesUpdated(@NonNull BillingResult billingResult, List<Purchase> list) {
            Log.e(TAG, "onPurchasesUpdated code: " + billingResult.getResponseCode());
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
                for (Purchase purchase : list) {
                    List<String> sku = purchase.getSkus();
                    handlePurchase(purchase);
                }
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                if (purchaseListener != null)
                    purchaseListener.onUserCancelBilling();
                Log.d(TAG, "onPurchasesUpdated:USER_CANCELED");
            } else {
                Log.d(TAG, "onPurchasesUpdated:...");
            }
        }
    };

    private final BillingClientStateListener purchaseClientStateListener = new BillingClientStateListener() {
        @Override
        public void onBillingServiceDisconnected() {
            isAvailable = false;
        }

        @Override
        public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
            Log.d(TAG, "onBillingSetupFinished: " + billingResult.getResponseCode());

            if (!isInitBillingFinish) {
                verifyPurchased(true);
            }

            isInitBillingFinish = true;
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                isAvailable = true;
                // check product detail INAPP
                if (!listInAppId.isEmpty()) {
                    QueryProductDetailsParams paramsINAPP = QueryProductDetailsParams.newBuilder()
                            .setProductList(listInAppId)
                            .build();

                    billingClient.queryProductDetailsAsync(
                            paramsINAPP,
                            new ProductDetailsResponseListener() {
                                public void onProductDetailsResponse(@NonNull BillingResult billingResult, @NonNull List<ProductDetails> productDetailsList) {
                                    Log.d(TAG, "onSkuINAPPDetailsResponse: " + productDetailsList.size());
                                    skuListInAppFromStore = productDetailsList;
                                    isListGot = true;
                                    addSkuInAppToMap(productDetailsList);
                                }
                            });
                }
                // check product detail SUBS
                if (!listSubsId.isEmpty()) {
                    QueryProductDetailsParams paramsSUBS = QueryProductDetailsParams.newBuilder()
                            .setProductList(listSubsId)
                            .build();

                    billingClient.queryProductDetailsAsync(
                            paramsSUBS,
                            new ProductDetailsResponseListener() {
                                public void onProductDetailsResponse(@NonNull BillingResult billingResult, @NonNull List<ProductDetails> productDetailsList) {
                                    Log.d(TAG, "onSkuSubsDetailsResponse: " + productDetailsList.size());
                                    skuListSubsFromStore = productDetailsList;
                                    isListGot = true;
                                    addSkuSubsToMap(productDetailsList);
                                }
                            });
                }
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE || billingResult.getResponseCode() == BillingClient.BillingResponseCode.ERROR) {
                Log.e(TAG, "onBillingSetupFinished:ERROR");
            }
        }
    };

    private AppPurchase() {
    }

    public static AppPurchase getInstance() {
        if (instance == null) {
            instance = new AppPurchase();
        }
        return instance;
    }

    public void setPurchaseListener(PurchaseListener purchaseListener) {
        this.purchaseListener = purchaseListener;
    }

    public void setUpdatePurchaseListener(UpdatePurchaseListener listener) {
        this.updatePurchaseListener = listener;
    }

    public void setPurchaseTest(Boolean purchaseTest) {
        this.isPurchaseTest = purchaseTest;
    }

    /**
     * Listener init billing app
     * When init available auto call onInitBillingFinish with resultCode = 0
     *
     * @param billingListener The BillingListener object that will be notified about the state of the billing system.
     */
    public void setBillingListener(BillingListener billingListener) {
        this.billingListener = billingListener;
        if (isAvailable) {
            billingListener.onInitBillingFinished(0);
            isInitBillingFinish = true;
        }
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public Boolean getInitBillingFinish() {
        return isInitBillingFinish;
    }

    public void setEventConsumePurchaseTest(View view) {
        view.setOnClickListener(v -> {
            if (isPurchaseTest) {
                Log.d(TAG, "setEventConsumePurchaseTest: success");
                AppPurchase.getInstance().consumePurchase(PRODUCT_ID_TEST);
            }
        });
    }

    /**
     * Listener init billing app with timeout
     * When init available auto call onInitBillingFinish with resultCode = 0
     *
     * @param billingListener The BillingListener object that will be notified about the state of the billing system.
     * @param timeout         The maximum time (in milliseconds) that the billing system initialization should take before timing out.
     */
    public void setBillingListener(BillingListener billingListener, int timeout) {
        Log.d(TAG, "setBillingListener: timeout " + timeout);
        this.billingListener = billingListener;
        if (isAvailable) {
            Log.d(TAG, "setBillingListener: finish");
            billingListener.onInitBillingFinished(0);
            isInitBillingFinish = true;
            return;
        }
        handlerTimeout = new Handler();
        rdTimeout = () -> {
            Log.d(TAG, "setBillingListener: timeout run");
            isInitBillingFinish = true;
            billingListener.onInitBillingFinished(BillingClient.BillingResponseCode.ERROR);
        };
        handlerTimeout.postDelayed(rdTimeout, timeout);
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public void setConsumePurchase(boolean consumePurchase) {
        isConsumePurchase = consumePurchase;
    }

    public void setOldPrice(String oldPrice) {
        this.oldPrice = oldPrice;
    }

    public List<PurchaseResult> getListOwnerIdSubs() {
        return listOwnerIdSubs;
    }

    public List<String> getListOwnerIdInApp() {
        return listOwnerIdInApp;
    }

    /**
     * Initialize the billing system for the Android application.
     *
     * @param application The application object for the Android app.
     * @param listInAppId A list of in-app purchase product IDs.
     * @param listSubsId  A list of subscription product IDs.
     */
    public void initBilling(final Application application, List<String> listInAppId, List<String> listSubsId) {
        if (isPurchaseTest) {
            // auto add purchase test when dev
            listInAppId.add(PRODUCT_ID_TEST);
        }
        this.listSubsId = listIdToListProduct(listSubsId, BillingClient.ProductType.SUBS);
        this.listInAppId = listIdToListProduct(listInAppId, BillingClient.ProductType.INAPP);

        billingClient = BillingClient.newBuilder(application)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(purchaseClientStateListener);
    }

    private void addSkuSubsToMap(List<ProductDetails> skuList) {
        for (ProductDetails skuDetails : skuList) {
            skuDetailsSubsMap.put(skuDetails.getProductId(), skuDetails);
        }
    }

    private void addSkuInAppToMap(List<ProductDetails> skuList) {
        for (ProductDetails skuDetails : skuList) {
            skuDetailsInAppMap.put(skuDetails.getProductId(), skuDetails);
        }
    }

    public void setPurchase(boolean purchase) {
        isPurchase = purchase;
    }

    public boolean isPurchased() {
        return isPurchase;
    }

    public String getIdPurchased() {
        return idPurchased;
    }

    private void addOrUpdateOwnerIdSub(PurchaseResult purchaseResult, String id) {
        boolean isExistId = false;
        for (PurchaseResult p : listOwnerIdSubs) {
            if (p.getProductId().contains(id)) {
                isExistId = true;
                listOwnerIdSubs.remove(p);
                listOwnerIdSubs.add(purchaseResult);
                break;
            }
        }
        if (!isExistId) {
            listOwnerIdSubs.add(purchaseResult);
        }
    }

    // kiểm tra trạng thái purchase
    public void verifyPurchased(boolean isCallback) {
        Log.d(TAG, "isPurchased: " + listSubsId.size());
        verifyFinish = false;
        if (listInAppId != null) {
            billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                    new PurchasesResponseListener() {
                        public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list) {
                            Log.d(TAG, "verifyPurchased INAPP code:" + billingResult.getResponseCode() + " === size:" + list.size());
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                for (Purchase purchase : list) {
                                    for (QueryProductDetailsParams.Product id : listInAppId) {
                                        if (purchase.getProducts().contains(id.zza())) {
                                            Log.d(TAG, "verifyPurchased INAPP: true");
                                            listOwnerIdInApp.add(id.zza());
                                            isPurchase = true;
                                        }
                                    }
                                }
                                isVerifyInApp = true;
                                if (isVerifySubs) {
                                    if (billingListener != null && isCallback) {
                                        billingListener.onInitBillingFinished(billingResult.getResponseCode());
                                        if (handlerTimeout != null && rdTimeout != null) {
                                            handlerTimeout.removeCallbacks(rdTimeout);
                                        }
                                    }
                                    verifyFinish = true;
                                }
                            } else {
                                isVerifyInApp = true;
                                if (isVerifySubs) {
                                    // chưa mua subs và IAP
                                    billingListener.onInitBillingFinished(billingResult.getResponseCode());
                                    if (handlerTimeout != null && rdTimeout != null) {
                                        handlerTimeout.removeCallbacks(rdTimeout);
                                    }
                                    verifyFinish = true;
                                }
                            }
                            /*if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED && !verifyFinish) {
                                Log.e(TAG, "onQueryPurchasesResponse INAPP: SERVICE_DISCONNECTED  === count reconnect:" + countReconnectBilling);
                                verifyFinish = true;
                                if (countReconnectBilling >= countMaxReconnectBilling) {
                                    billingListener.onInitBillingFinished(billingResult.getResponseCode());
                                    return;
                                }

                                billingClient.startConnection(purchaseClientStateListener);
                                countReconnectBilling++;
                                return;
                            }*/
                        }
                    }
            );
        }

        if (listSubsId != null) {
            billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                    new PurchasesResponseListener() {
                        public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list) {
                            Log.d(TAG, "verifyPurchased SUBS code:" + billingResult.getResponseCode() + " === size:" + list.size());
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                for (Purchase purchase : list) {
                                    for (QueryProductDetailsParams.Product id : listSubsId) {
                                        if (purchase.getProducts().contains(id.zza())) {
                                            PurchaseResult purchaseResult = new PurchaseResult(
                                                    purchase.getPackageName(),
                                                    purchase.getProducts(),
                                                    purchase.getPurchaseState(),
                                                    purchase.isAutoRenewing()
                                            );
                                            addOrUpdateOwnerIdSub(purchaseResult, id.zza());
                                            Log.d(TAG, "verifyPurchased SUBS: true");
                                            isPurchase = true;
                                        }
                                    }
                                }
                                isVerifySubs = true;
                                if (isVerifyInApp) {
                                    if (billingListener != null && isCallback) {
                                        billingListener.onInitBillingFinished(billingResult.getResponseCode());
                                        if (handlerTimeout != null && rdTimeout != null) {
                                            handlerTimeout.removeCallbacks(rdTimeout);
                                        }
                                    }
                                    verifyFinish = true;
                                }
                            } else {
                                isVerifySubs = true;
                                if (isVerifyInApp) {
                                    // chưa mua subs và IAP
                                    if (billingListener != null && isCallback) {
                                        billingListener.onInitBillingFinished(billingResult.getResponseCode());
                                        if (handlerTimeout != null && rdTimeout != null) {
                                            handlerTimeout.removeCallbacks(rdTimeout);
                                        }
                                        verifyFinish = true;
                                    }
                                }
                            }
                            /*if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED && !verifyFinish) {
                                Log.e(TAG, "onQueryPurchasesResponse SUBS: SERVICE_DISCONNECTED  === count reconnect:" + countReconnectBilling);
                                verifyFinish = true;
                                if (countReconnectBilling >= countMaxReconnectBilling) {
                                    billingListener.onInitBillingFinished(billingResult.getResponseCode());
                                    return;
                                }
                                billingClient.startConnection(purchaseClientStateListener);
                                countReconnectBilling++;
                                return;
                            }*/
                        }
                    }
            );
        }
    }

    public void updatePurchaseStatus() {
        if (listInAppId != null) {
            billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                    (billingResult, list) -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            for (Purchase purchase : list) {
                                for (QueryProductDetailsParams.Product id : listInAppId) {
                                    if (purchase.getProducts().contains(id.zza())) {
                                        if (!listOwnerIdInApp.contains(id.zza())) {
                                            listOwnerIdInApp.add(id.zza());
                                        }
                                    }
                                }
                            }
                        }
                        isUpdateInApp = true;
                        if (isUpdateSubs) {
                            if (updatePurchaseListener != null) {
                                updatePurchaseListener.onUpdateFinished();
                            }
                        }
                    }
            );
        }

        if (listSubsId != null) {
            billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                    (billingResult, list) -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            for (Purchase purchase : list) {
                                for (QueryProductDetailsParams.Product id : listSubsId) {
                                    if (purchase.getProducts().contains(id.zza())) {
                                        PurchaseResult purchaseResult = new PurchaseResult(
                                                purchase.getPackageName(),
                                                purchase.getProducts(),
                                                purchase.getPurchaseState(),
                                                purchase.isAutoRenewing()
                                        );
                                        addOrUpdateOwnerIdSub(purchaseResult, id.zza());
                                    }
                                }
                            }
                        }
                        isUpdateSubs = true;
                        if (isUpdateInApp) {
                            if (updatePurchaseListener != null) {
                                updatePurchaseListener.onUpdateFinished();
                            }
                        }
                    }
            );
        }
    }

    /*private String logResultBilling(Purchase.PurchasesResult result) {
        if (result == null || result.getPurchasesList() == null)
            return "null";
        StringBuilder log = new StringBuilder();
        for (Purchase purchase : result.getPurchasesList()) {
            for (String s : purchase.getSkus()) {
                log.append(s).append(",");
            }
        }
        return log.toString();
    }*/

    public void purchase(Activity activity) {
        if (productId == null) {
            Log.e(TAG, "Purchase false:productId null");
            Toast.makeText(activity, "Product id must not be empty!", Toast.LENGTH_SHORT).show();
            return;
        }
        purchase(activity, productId);
    }

    public String purchase(Activity activity, String productId) {
        if (skuListInAppFromStore == null) {
            if (purchaseListener != null) {
                purchaseListener.displayErrorMessage("Billing error init");
            }
            return "";
        }
        ProductDetails productDetails = skuDetailsInAppMap.get(productId);
        if (productDetails == null) {
            return "Product ID invalid";
        }
        Log.d(TAG, "purchase: " + productDetails);
        //ProductDetails{jsonString='{"productId":"android.test.purchased","type":"inapp","title":"Tiêu đề mẫu","description":"Mô tả mẫu về sản phẩm: android.test.purchased.","skuDetailsToken":"AEuhp4Izz50wTvd7YM9wWjPLp8hZY7jRPhBEcM9GAbTYSdUM_v2QX85e8UYklstgqaRC","oneTimePurchaseOfferDetails":{"priceAmountMicros":23207002450,"priceCurrencyCode":"VND","formattedPrice":"23.207 ₫"}}', parsedJson={"productId":"android.test.purchased","type":"inapp","title":"Tiêu đề mẫu","description":"Mô tả mẫu về sản phẩm: android.test.purchased.","skuDetailsToken":"AEuhp4Izz50wTvd7YM9wWjPLp8hZY7jRPhBEcM9GAbTYSdUM_v2QX85e8UYklstgqaRC","oneTimePurchaseOfferDetails":{"priceAmountMicros":23207002450,"priceCurrencyCode":"VND","formattedPrice":"23.207 ₫"}}, productId='android.test.purchased', productType='inapp', title='Tiêu đề mẫu', productDetailsToken='AEuhp4Izz50wTvd7YM9wWjPLp8hZY7jRPhBEcM9GAbTYSdUM_v2QX85e8UYklstgqaRC', subscriptionOfferDetails=null}
        if (isPurchaseTest) {
            // Auto using id purchase test in variant dev
            productId = PRODUCT_ID_TEST;
            PurchaseDevBottomSheet purchaseDevBottomSheet = new PurchaseDevBottomSheet(TYPE_IAP.PURCHASE, productDetails, activity, purchaseListener);
            purchaseDevBottomSheet.show();
            return "";
        }

        idPurchaseCurrent = productId;
        typeIAP = TYPE_IAP.PURCHASE;

        ImmutableList<BillingFlowParams.ProductDetailsParams> productDetailsParamsList =
                ImmutableList.of(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                );

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build();

        BillingResult billingResult = billingClient.launchBillingFlow(activity, billingFlowParams);

        return switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                if (purchaseListener != null)
                    purchaseListener.displayErrorMessage("Billing not supported for type of request");
                yield "Billing not supported for type of request";
            }
            case BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "";
            case BillingClient.BillingResponseCode.ERROR -> {
                if (purchaseListener != null)
                    purchaseListener.displayErrorMessage("Error completing request");
                yield "Error completing request";
            }
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Error processing request.";
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "Selected item is already owned";
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Item not available";
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Play Store service is not connected now";
            case BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> "Timeout";
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                if (purchaseListener != null)
                    purchaseListener.displayErrorMessage("Network error.");
                yield "Network Connection down";
            }
            case BillingClient.BillingResponseCode.USER_CANCELED -> {
                if (purchaseListener != null)
                    purchaseListener.displayErrorMessage("Request Canceled");
                yield "Request Canceled";
            }
            case BillingClient.BillingResponseCode.OK -> "Subscribed Successfully";
            default -> "";
        };
    }

    public String subscribe(Activity activity, String subsId) {
        if (skuListSubsFromStore == null) {
            if (purchaseListener != null)
                purchaseListener.displayErrorMessage("Billing error init");
            return "";
        }

        if (isPurchaseTest) {
            // sử dụng ID Purchase test
            purchase(activity, PRODUCT_ID_TEST);
            return "Billing test";
        }
        ProductDetails productDetails = skuDetailsSubsMap.get(subsId);
        if (productDetails == null) {
            return "Product ID invalid";
        }
        ProductDetails skuDetails = skuDetailsSubsMap.get(subsId);
        List<ProductDetails.SubscriptionOfferDetails> subsDetail = skuDetails != null ? skuDetails.getSubscriptionOfferDetails() : new ArrayList<>();
        String offerToken = subsDetail != null ? subsDetail.get(subsDetail.size() - 1).getOfferToken() : "";
        BillingFlowParams.ProductDetailsParams productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build();
        ImmutableList<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = ImmutableList.of(productDetailsParams);

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build();

        BillingResult billingResult = billingClient.launchBillingFlow(activity, billingFlowParams);

        return switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                if (purchaseListener != null)
                    purchaseListener.displayErrorMessage("Billing not supported for type of request");
                yield "Billing not supported for type of request";
            }
            case BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "";
            case BillingClient.BillingResponseCode.ERROR -> {
                if (purchaseListener != null)
                    purchaseListener.displayErrorMessage("Error completing request");
                yield "Error completing request";
            }
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Error processing request.";
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "Selected item is already owned";
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Item not available";
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Play Store service is not connected now";
            case BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> "Timeout";
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                if (purchaseListener != null)
                    purchaseListener.displayErrorMessage("Network error.");
                yield "Network Connection down";
            }
            case BillingClient.BillingResponseCode.USER_CANCELED -> {
                if (purchaseListener != null)
                    purchaseListener.displayErrorMessage("Request Canceled");
                yield "Request Canceled";
            }
            case BillingClient.BillingResponseCode.OK -> "Subscribed Successfully";
            default -> "";
        };
    }

    public void consumePurchase() {
        if (productId == null) {
            Log.e(TAG, "Consume Purchase false:productId null ");
            return;
        }
        consumePurchase(productId);
    }

    /**
     * Consumes the purchase for the given product ID.
     *
     * @param productId the unique identifier of the product
     */
    public void consumePurchase(String productId) {
        billingClient.queryPurchasesAsync(BillingClient.ProductType.INAPP, (billingResult, list) -> {
            Purchase pc = null;
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (Purchase purchase : list) {
                    if (purchase.getSkus().contains(productId)) {
                        pc = purchase;
                    }
                }
            }
            if (pc == null) return;
            try {
                ConsumeParams consumeParams =
                        ConsumeParams.newBuilder()
                                .setPurchaseToken(pc.getPurchaseToken())
                                .build();

                ConsumeResponseListener listener = new ConsumeResponseListener() {
                    @Override
                    public void onConsumeResponse(BillingResult billingResult, @NonNull String purchaseToken) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            Log.e(TAG, "onConsumeResponse: OK");
                            verifyPurchased(false);
                        }
                    }
                };

                billingClient.consumeAsync(consumeParams, listener);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        });

    }

    private List<String> getListInAppId() {
        List<String> list = new ArrayList<>();
        for (QueryProductDetailsParams.Product product : listInAppId) {
            list.add(product.zza());
        }
        return list;
    }

    private List<String> getListSubsId() {
        List<String> list = new ArrayList<>();
        for (QueryProductDetailsParams.Product product : listSubsId) {
            list.add(product.zza());
        }
        return list;
    }

    /**
     * Handles the processing of a purchase made by the user.
     *
     * @param purchase the purchase object obtained from the Google Play Billing API
     */
    private void handlePurchase(Purchase purchase) {
        //tracking adjust
        double price = getPriceWithoutCurrency(idPurchaseCurrent, typeIAP);
        String currency = getCurrency(idPurchaseCurrent, typeIAP);

        if (purchaseListener != null) {
            isPurchase = true;
            purchaseListener.onProductPurchased(purchase.getOrderId(), purchase.getOriginalJson());
        }
        if (isConsumePurchase) {
            ConsumeParams consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();

            ConsumeResponseListener listener = new ConsumeResponseListener() {
                @Override
                public void onConsumeResponse(BillingResult billingResult, @NonNull String purchaseToken) {
                    Log.d(TAG, "onConsumeResponse: " + billingResult.getDebugMessage());
                }
            };

            billingClient.consumeAsync(consumeParams, listener);
        } else {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                if (!verifyValidSignature(purchase.getOriginalJson(), purchase.getSignature())) {
                    Log.d(TAG, "Error: invalid Purchase");
                    return;
                }
                AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
                if (!purchase.isAcknowledged()) {
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
                        @Override
                        public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                            Log.d(TAG, "onAcknowledgePurchaseResponse: " + billingResult.getDebugMessage());
                        }
                    });
                }
            }
        }
    }

    public String getPrice() {
        return getPrice(productId);
    }

    /**
     * Retrieves the price of the one-time purchase for the given product ID.
     *
     * @param productId the unique identifier of the product
     * @return the formatted price of the one-time purchase, or an empty string if the product does not have a one-time purchase or its details cannot be found
     */
    public String getPrice(String productId) {
        ProductDetails skuDetails = skuDetailsInAppMap.get(productId);
        if (skuDetails == null) return "";
        if (skuDetails.getOneTimePurchaseOfferDetails() == null) return "";
        Log.e(TAG, "getPrice: " + skuDetails.getOneTimePurchaseOfferDetails().getFormattedPrice());
        return skuDetails.getOneTimePurchaseOfferDetails().getFormattedPrice();
    }

    /**
     * Retrieves the price of the subscription for the given product ID.
     *
     * @param productId the unique identifier of the product
     * @return the formatted price of the subscription's last pricing phase, or an empty string if the product does not have a subscription or its details cannot be found
     */
    public String getPriceSub(String productId) {
        ProductDetails skuDetails = skuDetailsSubsMap.get(productId);
        if (skuDetails == null) return "";
        if (skuDetails.getSubscriptionOfferDetails() == null) return "";
        List<ProductDetails.SubscriptionOfferDetails> subsDetail = skuDetails.getSubscriptionOfferDetails();
        List<ProductDetails.PricingPhase> pricingPhaseList = subsDetail.get(subsDetail.size() - 1).getPricingPhases().getPricingPhaseList();
        Log.e(TAG, "getPriceSub: " + pricingPhaseList.get(pricingPhaseList.size() - 1).getFormattedPrice());
        return pricingPhaseList.get(pricingPhaseList.size() - 1).getFormattedPrice();
    }

    /**
     * Get Price Pricing Phase List Subs
     *
     * @param productId The unique identifier of the subscription product.
     * @return A list of ProductDetails.PricingPhase objects, which represent the pricing phases for the subscription.
     */
    public List<ProductDetails.PricingPhase> getPricePricingPhaseList(String productId) {
        ProductDetails skuDetails = skuDetailsSubsMap.get(productId);
        if (skuDetails == null) return null;
        if (skuDetails.getSubscriptionOfferDetails() == null) return null;
        List<ProductDetails.SubscriptionOfferDetails> subsDetail = skuDetails.getSubscriptionOfferDetails();
        return subsDetail.get(subsDetail.size() - 1).getPricingPhases().getPricingPhaseList();
    }

    /**
     * Get Formatted Price by country
     * Get final price with id
     *
     * @param productId The unique identifier of the product or subscription.
     * @return The formatted price of the product or subscription (e.g. "$9.99", "€7.99", "¥980", etc.).
     */
    public String getIntroductorySubPrice(String productId) {
        ProductDetails skuDetails = skuDetailsSubsMap.get(productId);
        if (skuDetails == null) return "";
        if (skuDetails.getOneTimePurchaseOfferDetails() != null)
            return skuDetails.getOneTimePurchaseOfferDetails().getFormattedPrice();
        else if (skuDetails.getSubscriptionOfferDetails() != null) {
            List<ProductDetails.SubscriptionOfferDetails> subsDetail = skuDetails.getSubscriptionOfferDetails();
            List<ProductDetails.PricingPhase> pricingPhaseList = subsDetail.get(subsDetail.size() - 1).getPricingPhases().getPricingPhaseList();
            return pricingPhaseList.get(pricingPhaseList.size() - 1).getFormattedPrice();
        } else {
            return "";
        }
    }

    /**
     * Get Currency subs or IAP by country
     *
     * @param productId The unique identifier of the product or subscription.
     * @param typeIAP   The type of in-app purchase, either TYPE_IAP.PURCHASE (one-time purchase) or TYPE_IAP.SUBSCRIPTION.
     * @return The currency code of the product or subscription (e.g. "USD", "EUR", "JPY"...)
     */
    public String getCurrency(String productId, int typeIAP) {
        ProductDetails skuDetails = typeIAP == TYPE_IAP.PURCHASE ? skuDetailsInAppMap.get(productId) : skuDetailsSubsMap.get(productId);
        if (skuDetails == null) return "";

        if (typeIAP == TYPE_IAP.PURCHASE) {
            if (skuDetails.getOneTimePurchaseOfferDetails() == null) return "";
            return skuDetails.getOneTimePurchaseOfferDetails().getPriceCurrencyCode();
        } else {
            List<ProductDetails.SubscriptionOfferDetails> subsDetail = skuDetails.getSubscriptionOfferDetails();
            if (subsDetail == null) return "";
            List<ProductDetails.PricingPhase> pricingPhaseList = subsDetail.get(subsDetail.size() - 1).getPricingPhases().getPricingPhaseList();
            return pricingPhaseList.get(pricingPhaseList.size() - 1).getPriceCurrencyCode();
        }
    }

    /**
     * Get Price Amount Micros subs or IAP
     * Get final price with id
     *
     * @param productId The unique identifier of the product or subscription.
     * @param typeIAP   The type of in-app purchase, either TYPE_IAP.PURCHASE (one-time purchase) or TYPE_IAP.SUBSCRIPTION.
     * @return The final price amount in micros (one-millionth of the base currency unit) for the given product and IAP type.
     */
    public double getPriceWithoutCurrency(String productId, int typeIAP) {
        ProductDetails skuDetails = typeIAP == TYPE_IAP.PURCHASE ? skuDetailsInAppMap.get(productId) : skuDetailsSubsMap.get(productId);
        if (skuDetails == null) {
            return 0;
        }
        if (typeIAP == TYPE_IAP.PURCHASE) {
            if (skuDetails.getOneTimePurchaseOfferDetails() == null) return 0;
            return skuDetails.getOneTimePurchaseOfferDetails().getPriceAmountMicros();
        } else {
            List<ProductDetails.SubscriptionOfferDetails> subsDetail = skuDetails.getSubscriptionOfferDetails();
            if (subsDetail == null) return 0;
            List<ProductDetails.PricingPhase> pricingPhaseList = subsDetail.get(subsDetail.size() - 1).getPricingPhases().getPricingPhaseList();
            return pricingPhaseList.get(pricingPhaseList.size() - 1).getPriceAmountMicros();
        }
    }

//    public String getOldPrice() {
//        SkuDetails skuDetails = bp.getPurchaseListingDetails(productId);
//        if (skuDetails == null)
//            return "";
//        return formatCurrency(skuDetails.priceValue / discount, skuDetails.currency);
//    }

    /**
     * Format currency and price by country
     *
     * @param price    The value of the product or service to be formatted.
     * @param currency The currency code of the country to be formatted (e.g. "USD", "EUR", "JPY"...)
     * @return Returns the value formatted as a currency string, e.g. "$10", "€10", "¥10".
     */
    private String formatCurrency(double price, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance();
        format.setMaximumFractionDigits(0);
        format.setCurrency(Currency.getInstance(currency));
        return format.format(price);
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    public double getDiscount() {
        return discount;
    }

    private ArrayList<QueryProductDetailsParams.Product> listIdToListProduct(List<String> listId, String styleBilling) {
        ArrayList<QueryProductDetailsParams.Product> listProduct = new ArrayList<>();
        for (String id : listId) {
            QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(styleBilling)
                    .build();
            listProduct.add(product);
        }
        return listProduct;
    }

    private boolean verifyValidSignature(String signedData, String signature) {
        String base64Key = "";
        return Security.verifyPurchase(base64Key, signedData, signature);
    }

    @IntDef({TYPE_IAP.PURCHASE, TYPE_IAP.SUBSCRIPTION})
    public @interface TYPE_IAP {
        int PURCHASE = 1;
        int SUBSCRIPTION = 2;
    }
}