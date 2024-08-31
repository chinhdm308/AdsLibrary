package com.chinchin.ads.iap;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IAPManager {
    private static final String TAG = "IAPManager";
    public static final String PRODUCT_ID_TEST = "android.test.purchased";
    private static volatile IAPManager instance;
    private BillingClient billingClient;
    private PurchaseCallback purchaseCallback;
    public static String typeINAPP = BillingClient.ProductType.INAPP, typeSUBS = BillingClient.ProductType.SUBS;
    private final ArrayList<QueryProductDetailsParams.Product> listInAppProduct = new ArrayList<>();
    private final ArrayList<QueryProductDetailsParams.Product> listSubProduct = new ArrayList<>();
    private List<ProductDetails> productDetailsListInApp = new ArrayList<>();
    private List<ProductDetails> productDetailsListSub = new ArrayList<>();
    private final Map<String, ProductDetails> productDetailsInAppMap = new HashMap<>();
    private final Map<String, ProductDetails> productDetailsSubsMap = new HashMap<>();
    private boolean isPurchase = false;
    private boolean isPurchaseTest = false;
    private boolean isVerifyInApp = false;
    private boolean isVerifySub = false;

    public static IAPManager getInstance() {
        if (instance == null) {
            synchronized (IAPManager.class) {
                if (instance == null) instance = new IAPManager();
            }
        }
        return instance;
    }

    public boolean isPurchase() {
        return this.isPurchase;
    }

    public void setPurchase(boolean isPurchase) {
        this.isPurchase = isPurchase;
    }

    public void setPurchaseTest(boolean isPurchaseTest) {
        this.isPurchaseTest = isPurchaseTest;
    }

    public void setPurchaseListener(PurchaseCallback purchaseCallback) {
        this.purchaseCallback = purchaseCallback;
    }

    /**
     * Initializes the Google Play Billing client and sets up the necessary listeners.
     *
     * @param context                  The application context.
     * @param listProductDetailCustoms A list of custom product details.
     * @param billingCallback          A callback interface to handle billing-related events.
     */
    public void initBilling(Context context, ArrayList<ProductDetailCustom> listProductDetailCustoms, BillingCallback billingCallback) {
        setListProductDetails(listProductDetailCustoms);
        // Handle an error caused by a user cancelling the purchase flow.
        // Handle any other error codes.
        PurchasesUpdatedListener purchasesUpdatedListener = new PurchasesUpdatedListener() {
            @Override
            public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
                // Hàm này sẽ trả về kết quả khi người dùng thực hiện mua hàng.
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
                    for (Purchase purchase : list) {
                        handlePurchase(purchase);
                    }
                    Log.d(TAG, "onPurchasesUpdated OK");
                } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                    // Handle an error caused by a user cancelling the purchase flow.
                    if (purchaseCallback != null) {
                        purchaseCallback.onUserCancelBilling();
                    }
                    Log.d(TAG, "user cancelling the purchase flow");
                } else {
                    // Handle any other error codes.
                    Log.d(TAG, "onPurchasesUpdated:... ");
                }
            }
        };
        billingClient = BillingClient.newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build();

        // Connect ứng dụng của bạn với Google Billing
        connectToGooglePlay(billingCallback);
    }

    private void setListProductDetails(ArrayList<ProductDetailCustom> listProductDetailCustoms) {
        //check case purchase test -> auto add id product test to list
        if (isPurchaseTest) {
            listInAppProduct.add(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID_TEST).setProductType(typeINAPP).build());
            listSubProduct.add(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID_TEST).setProductType(typeSUBS).build());
        }
        for (ProductDetailCustom productDetailCustom : listProductDetailCustoms) {
            if (productDetailCustom.getProductType().equals(typeINAPP)) {
                listInAppProduct.add(QueryProductDetailsParams.Product.newBuilder().setProductId(productDetailCustom.getProductId()).setProductType(productDetailCustom.getProductType()).build());
            } else if (productDetailCustom.getProductType().equals(typeSUBS)) {
                listSubProduct.add(QueryProductDetailsParams.Product.newBuilder().setProductId(productDetailCustom.getProductId()).setProductType(productDetailCustom.getProductType()).build());
            }
        }
    }

    /**
     * Connects to the Google Play Billing service and performs necessary setup.
     *
     * @param billingCallback A callback interface to handle billing-related events.
     */
    private void connectToGooglePlay(BillingCallback billingCallback) {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                // Connect tới Google Play thành công. Bạn đã có thể lấy những sản phẩm mà người dùng đã mua
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    // The connection to the Google Play Billing service was successful
                    // Verify the user's purchases
                    verifyPurchased(billingCallback);
                    // Retrieve the details of the available in-app purchase and subscription products
                    showProductsAvailableToBuy(listInAppProduct, listSubProduct);
                    Log.d(TAG, "onBillingSetupFinished OK: " + billingResult.getResponseCode());
                } else {
                    // The connection to the Google Play Billing service was not successful
                    // Notify the billing callback of the error
                    billingCallback.onBillingSetupFinished(billingResult.getResponseCode());
                    Log.d(TAG, "onBillingSetupFinished NOT OK: " + billingResult.getResponseCode() + ": " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                billingCallback.onBillingServiceDisconnected();
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                Log.d(TAG, "onBillingServiceDisconnected");
            }
        });
    }

    /**
     * Retrieves the details of the in-app purchase and subscription products available to the user.
     *
     * @param listIAPProduct The list of in-app purchase product IDs.
     * @param listSubProduct The list of subscription product IDs.
     */
    private void showProductsAvailableToBuy(ArrayList<QueryProductDetailsParams.Product> listIAPProduct, ArrayList<QueryProductDetailsParams.Product> listSubProduct) {
        if (!listIAPProduct.isEmpty()) {
            QueryProductDetailsParams queryProductDetailsParamsIAP = QueryProductDetailsParams.newBuilder().setProductList(listIAPProduct).build();
            billingClient.queryProductDetailsAsync(queryProductDetailsParamsIAP, (billingResult, productDetailsList) -> {
                // check billingResult
                // process returned productDetailsList
                productDetailsListInApp = productDetailsList;
                addProductDetailsINAPToMap(productDetailsList);
            });
        }
        if (!listSubProduct.isEmpty()) {
            QueryProductDetailsParams queryProductDetailsParamsSub = QueryProductDetailsParams.newBuilder().setProductList(listSubProduct).build();
            billingClient.queryProductDetailsAsync(queryProductDetailsParamsSub, (billingResult, productDetailsList) -> {
                // check billingResult
                // process returned productDetailsList
                productDetailsListSub = productDetailsList;
                addProductDetailsSubsToMap(productDetailsList);
            });
        }
    }

    private void addProductDetailsINAPToMap(List<ProductDetails> productDetailsList) {
        for (ProductDetails productDetails : productDetailsList) {
            productDetailsInAppMap.put(productDetails.getProductId(), productDetails);
        }
    }

    private void addProductDetailsSubsToMap(List<ProductDetails> productDetailsList) {
        for (ProductDetails productDetails : productDetailsList) {
            productDetailsSubsMap.put(productDetails.getProductId(), productDetails);
        }
    }

    public String purchase(Activity activity, String productId) {
        ProductDetails productDetails = productDetailsInAppMap.get(productId);
        if (isPurchaseTest) {
            PurchaseTestBottomSheet purchaseTestBottomSheet = new PurchaseTestBottomSheet(typeINAPP, productDetails, activity, purchaseCallback);
            purchaseTestBottomSheet.show();
            return "Purchase Test BottomSheet";
        }
        if (productDetails == null) {
            return "Product id invalid";
        }
        ImmutableList<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = ImmutableList.of(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails).build());

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build();

        // Launch the billing flow
        BillingResult billingResult = billingClient.launchBillingFlow(activity, billingFlowParams);
        return switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing not supported for type of request";
            case BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "";
            case BillingClient.BillingResponseCode.ERROR -> "Error completing request";
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Error processing request.";
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "Selected item is already owned";
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Item not available";
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Play Store service is not connected now";
            case BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> "Timeout";
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Network Connection down";
            case BillingClient.BillingResponseCode.USER_CANCELED -> "Request Canceled";
            case BillingClient.BillingResponseCode.OK -> "Subscribed Successfully";
            default -> "";
        };
    }

    public String subscribe(Activity activity, String productId) {
        if (isPurchaseTest) {
            purchase(activity, PRODUCT_ID_TEST);
        }
        ProductDetails productDetails = productDetailsSubsMap.get(productId);
        if (productDetails == null) {
            return "Product id invalid";
        }
        List<ProductDetails.SubscriptionOfferDetails> subsDetail = productDetails.getSubscriptionOfferDetails();
        if (subsDetail == null) {
            return "Get Subscription Offer Details fail";
        }
        String offerToken = subsDetail.get(subsDetail.size() - 1).getOfferToken();
        ImmutableList<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = ImmutableList.of(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails).setOfferToken(offerToken).build());

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build();

        // Launch the billing flow
        BillingResult billingResult = billingClient.launchBillingFlow(activity, billingFlowParams);
        return switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing not supported for type of request";
            case BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "";
            case BillingClient.BillingResponseCode.ERROR -> "Error completing request";
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Error processing request.";
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "Selected item is already owned";
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Item not available";
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Play Store service is not connected now";
            case BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> "Timeout";
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Network Connection down";
            case BillingClient.BillingResponseCode.USER_CANCELED -> "Request Canceled";
            case BillingClient.BillingResponseCode.OK -> "Subscribed Successfully";
            default -> "";
        };
    }

    private void handlePurchase(Purchase purchase) {
        isPurchase = true;
        if (purchaseCallback != null) {
            purchaseCallback.onProductPurchased(purchase.getOrderId(), purchase.getOriginalJson());
        }
    }

    /**
     * Verifies the user's purchases for both in-app purchases and subscriptions.
     *
     * @param billingCallback A callback interface that will be notified when the billing setup is finished.
     */
    private void verifyPurchased(BillingCallback billingCallback) {
        if (!listInAppProduct.isEmpty()) {
            billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(typeINAPP).build(), (billingResult, list) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    for (Purchase purchase : list) {
                        for (QueryProductDetailsParams.Product id : listInAppProduct) {
                            if (purchase.getProducts().contains(id.zza())) {
                                isPurchase = true;
                            }
                        }
                    }
                }
                isVerifyInApp = true;
                if (!listSubProduct.isEmpty()) {
                    if (isVerifySub) {
                        billingCallback.onBillingSetupFinished(billingResult.getResponseCode());
                    }
                } else {
                    billingCallback.onBillingSetupFinished(billingResult.getResponseCode());
                }
            });
        }
        if (!listSubProduct.isEmpty()) {
            billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(), (billingResult, list) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    for (Purchase purchase : list) {
                        for (QueryProductDetailsParams.Product id : listSubProduct) {
                            if (purchase.getProducts().contains(id.zza())) {
                                isPurchase = true;
                            }
                        }
                    }
                }
                isVerifySub = true;
                if (!listInAppProduct.isEmpty()) {
                    if (isVerifyInApp) {
                        billingCallback.onBillingSetupFinished(billingResult.getResponseCode());
                    }
                } else {
                    billingCallback.onBillingSetupFinished(billingResult.getResponseCode());
                }
            });
        }
    }

    /**
     * Retrieves the formatted price of the one-time purchase offer for a given product.
     *
     * @param productId The unique identifier of the product.
     * @return The formatted price of the one-time purchase offer, or an empty string if the product details are not found or the one-time purchase offer details are not available.
     */
    public String getPrice(String productId) {
        ProductDetails productDetails = productDetailsInAppMap.get(productId);
        if (productDetails == null) return "";
        if (productDetails.getOneTimePurchaseOfferDetails() == null) return "";
        Log.e(TAG, "getPrice: " + productDetails.getOneTimePurchaseOfferDetails().getFormattedPrice());
        return productDetails.getOneTimePurchaseOfferDetails().getFormattedPrice();
    }

    /**
     * Retrieves the formatted price of the last subscription offer for a given product.
     *
     * @param productId The unique identifier of the product.
     * @return The formatted price of the last subscription offer, or an empty string if the product details are not found.
     */
    public String getPriceSub(String productId) {
        ProductDetails productDetails = productDetailsSubsMap.get(productId);
        if (productDetails == null) return "";
        List<ProductDetails.SubscriptionOfferDetails> subsDetail = productDetails.getSubscriptionOfferDetails();
        if (subsDetail == null) return "";
        List<ProductDetails.PricingPhase> pricingPhaseList = subsDetail.get(subsDetail.size() - 1).getPricingPhases().getPricingPhaseList();
        Log.e(TAG, "getPriceSub: " + pricingPhaseList.get(pricingPhaseList.size() - 1).getFormattedPrice());
        return pricingPhaseList.get(pricingPhaseList.size() - 1).getFormattedPrice();
    }

    /**
     * Retrieves the currency code of a product (in-app purchase or subscription).
     *
     * @param productId The unique identifier of the product.
     * @param type      The type of the product, either "in-app purchase" or "subscription".
     * @return The currency code of the product, or an empty string if the product details are not found.
     */
    public String getCurrency(String productId, String type) {
        ProductDetails productDetails = type.equals(typeINAPP) ? productDetailsInAppMap.get(productId) : productDetailsSubsMap.get(productId);
        if (productDetails == null) return "";
        if (type.equals(typeINAPP)) {
            if (productDetails.getOneTimePurchaseOfferDetails() == null) return "";
            return productDetails.getOneTimePurchaseOfferDetails().getPriceCurrencyCode();
        } else {
            List<ProductDetails.SubscriptionOfferDetails> subsDetail = productDetails.getSubscriptionOfferDetails();
            if (subsDetail == null) return "";
            List<ProductDetails.PricingPhase> pricingPhaseList = subsDetail.get(subsDetail.size() - 1).getPricingPhases().getPricingPhaseList();
            return pricingPhaseList.get(pricingPhaseList.size() - 1).getPriceCurrencyCode();
        }
    }

    /**
     * Retrieves the price of a product (in-app purchase or subscription) without the currency symbol.
     *
     * @param productId The unique identifier of the product.
     * @param type      The type of the product, either "in-app purchase" or "subscription".
     * @return The price of the product in micros (1/1,000,000 of the currency unit), or 0 if the product details are not found.
     */
    public double getPriceWithoutCurrency(String productId, String type) {
        ProductDetails productDetails = type.equals(typeINAPP) ? productDetailsInAppMap.get(productId) : productDetailsSubsMap.get(productId);
        if (productDetails == null) return 0;
        if (type.equals(typeINAPP)) {
            if (productDetails.getOneTimePurchaseOfferDetails() == null) return 0;
            return productDetails.getOneTimePurchaseOfferDetails().getPriceAmountMicros();
        } else {
            List<ProductDetails.SubscriptionOfferDetails> subsDetail = productDetails.getSubscriptionOfferDetails();
            if (subsDetail == null) return 0;
            List<ProductDetails.PricingPhase> pricingPhaseList = subsDetail.get(subsDetail.size() - 1).getPricingPhases().getPricingPhaseList();
            return pricingPhaseList.get(pricingPhaseList.size() - 1).getPriceAmountMicros();
        }
    }
}