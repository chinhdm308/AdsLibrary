package com.chinchin.ads.iap

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.common.collect.ImmutableList
import kotlin.concurrent.Volatile

class IAPManager {
    private var billingClient: BillingClient? = null
    private var purchaseCallback: PurchaseCallback? = null
    private val listInAppProduct = ArrayList<QueryProductDetailsParams.Product>()
    private val listSubProduct = ArrayList<QueryProductDetailsParams.Product>()
    private var productDetailsListInApp: List<ProductDetails> = ArrayList()
    private var productDetailsListSub: List<ProductDetails> = ArrayList()
    private val productDetailsInAppMap: MutableMap<String, ProductDetails> = HashMap()
    private val productDetailsSubsMap: MutableMap<String, ProductDetails> = HashMap()
    private var isPurchase: Boolean = false
    private var isPurchaseTest = false
    private var isVerifyInApp = false
    private var isVerifySub = false

    fun isPurchase(): Boolean = isPurchase

    fun setIsPurchase(isPurchase: Boolean) {
        this.isPurchase = isPurchase
    }

    fun setPurchaseTest(isPurchaseTest: Boolean) {
        this.isPurchaseTest = isPurchaseTest
    }

    fun setPurchaseListener(purchaseCallback: PurchaseCallback?) {
        this.purchaseCallback = purchaseCallback
    }

    /**
     * Initializes the Google Play Billing client and sets up the necessary listeners.
     *
     * @param context                  The application context.
     * @param listProductDetailCustoms A list of custom product details.
     * @param billingCallback          A callback interface to handle billing-related events.
     */
    fun initBilling(context: Context?, listProductDetailCustoms: ArrayList<ProductDetailCustom>, billingCallback: BillingCallback) {
        setListProductDetails(listProductDetailCustoms)
        // Handle an error caused by a user cancelling the purchase flow.
        // Handle any other error codes.
        val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, list ->
            // Hàm này sẽ trả về kết quả khi người dùng thực hiện mua hàng.
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
                for (purchase in list) {
                    handlePurchase(purchase)
                }
                Log.d(TAG, "onPurchasesUpdated OK")
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                // Handle an error caused by a user cancelling the purchase flow.
                if (purchaseCallback != null) {
                    purchaseCallback!!.onUserCancelBilling()
                }
                Log.d(TAG, "user cancelling the purchase flow")
            } else {
                // Handle any other error codes.
                Log.d(TAG, "onPurchasesUpdated:... ")
            }
        }
        billingClient = BillingClient.newBuilder(context!!)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        // Connect ứng dụng của bạn với Google Billing
        connectToGooglePlay(billingCallback)
    }

    private fun setListProductDetails(listProductDetailCustoms: ArrayList<ProductDetailCustom>) {
        //check case purchase test -> auto add id product test to list
        if (isPurchaseTest) {
            listInAppProduct.add(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID_TEST).setProductType(INAPP).build())
            listSubProduct.add(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID_TEST).setProductType(SUBS).build())
        }
        for (productDetailCustom in listProductDetailCustoms) {
            if (productDetailCustom.productType == INAPP) {
                listInAppProduct.add(
                    QueryProductDetailsParams.Product.newBuilder().setProductId(productDetailCustom.productId)
                        .setProductType(productDetailCustom.productType).build()
                )
            } else if (productDetailCustom.productType == SUBS) {
                listSubProduct.add(
                    QueryProductDetailsParams.Product.newBuilder().setProductId(productDetailCustom.productId)
                        .setProductType(productDetailCustom.productType).build()
                )
            }
        }
    }

    /**
     * Connects to the Google Play Billing service and performs necessary setup.
     *
     * @param billingCallback A callback interface to handle billing-related events.
     */
    private fun connectToGooglePlay(billingCallback: BillingCallback) {
        billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                // Connect tới Google Play thành công. Bạn đã có thể lấy những sản phẩm mà người dùng đã mua
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    // The connection to the Google Play Billing service was successful
                    // Verify the user's purchases
                    verifyPurchased(billingCallback)
                    // Retrieve the details of the available in-app purchase and subscription products
                    showProductsAvailableToBuy(listInAppProduct, listSubProduct)
                    Log.d(TAG, "onBillingSetupFinished OK: " + billingResult.responseCode)
                } else {
                    // The connection to the Google Play Billing service was not successful
                    // Notify the billing callback of the error
                    billingCallback.onBillingSetupFinished(billingResult.responseCode)
                    Log.d(TAG, "onBillingSetupFinished NOT OK: " + billingResult.responseCode + ": " + billingResult.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                billingCallback.onBillingServiceDisconnected()
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                Log.d(TAG, "onBillingServiceDisconnected")
            }
        })
    }

    /**
     * Retrieves the details of the in-app purchase and subscription products available to the user.
     *
     * @param listIAPProduct The list of in-app purchase product IDs.
     * @param listSubProduct The list of subscription product IDs.
     */
    private fun showProductsAvailableToBuy(
        listIAPProduct: ArrayList<QueryProductDetailsParams.Product>,
        listSubProduct: ArrayList<QueryProductDetailsParams.Product>,
    ) {
        if (!listIAPProduct.isEmpty()) {
            val queryProductDetailsParamsIAP = QueryProductDetailsParams.newBuilder().setProductList(listIAPProduct).build()
            billingClient!!.queryProductDetailsAsync(queryProductDetailsParamsIAP) { billingResult: BillingResult?, productDetailsList: List<ProductDetails> ->
                // check billingResult
                // process returned productDetailsList
                productDetailsListInApp = productDetailsList
                addProductDetailsINAPToMap(productDetailsList)
            }
        }
        if (!listSubProduct.isEmpty()) {
            val queryProductDetailsParamsSub = QueryProductDetailsParams.newBuilder().setProductList(listSubProduct).build()
            billingClient!!.queryProductDetailsAsync(queryProductDetailsParamsSub) { billingResult: BillingResult?, productDetailsList: List<ProductDetails> ->
                // check billingResult
                // process returned productDetailsList
                productDetailsListSub = productDetailsList
                addProductDetailsSubsToMap(productDetailsList)
            }
        }
    }

    private fun addProductDetailsINAPToMap(productDetailsList: List<ProductDetails>) {
        for (productDetails in productDetailsList) {
            productDetailsInAppMap[productDetails.productId] = productDetails
        }
    }

    private fun addProductDetailsSubsToMap(productDetailsList: List<ProductDetails>) {
        for (productDetails in productDetailsList) {
            productDetailsSubsMap[productDetails.productId] = productDetails
        }
    }

    fun purchase(activity: Activity, productId: String): String {
        val productDetails = productDetailsInAppMap[productId]
        if (isPurchaseTest) {
            val purchaseTestBottomSheet = PurchaseTestBottomSheet(
                context = activity,
                typeIap = INAPP,
                productDetails = productDetails,
                purchaseCallback = purchaseCallback
            )
            purchaseTestBottomSheet.show()
            return "Purchase Test BottomSheet"
        }
        if (productDetails == null) {
            return "Product id invalid"
        }
        val productDetailsParamsList = ImmutableList.of(ProductDetailsParams.newBuilder().setProductDetails(productDetails).build())

        val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build()

        // Launch the billing flow
        val billingResult = billingClient!!.launchBillingFlow(activity, billingFlowParams)
        return when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing not supported for type of request"
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> ""
            BillingClient.BillingResponseCode.ERROR -> "Error completing request"
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Error processing request."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "Selected item is already owned"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Item not available"
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Play Store service is not connected now"
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> "Timeout"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Network Connection down"
            BillingClient.BillingResponseCode.USER_CANCELED -> "Request Canceled"
            BillingClient.BillingResponseCode.OK -> "Subscribed Successfully"
            else -> ""
        }
    }

    fun subscribe(activity: Activity, productId: String): String {
        if (isPurchaseTest) {
            purchase(activity, PRODUCT_ID_TEST)
        }
        val productDetails = productDetailsSubsMap[productId] ?: return "Product id invalid"
        val subsDetail = productDetails.subscriptionOfferDetails ?: return "Get Subscription Offer Details fail"
        val offerToken = subsDetail[subsDetail.size - 1].offerToken
        val productDetailsParamsList =
            ImmutableList.of(ProductDetailsParams.newBuilder().setProductDetails(productDetails).setOfferToken(offerToken).build())

        val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build()

        // Launch the billing flow
        val billingResult = billingClient!!.launchBillingFlow(activity, billingFlowParams)
        return when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing not supported for type of request"
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> ""
            BillingClient.BillingResponseCode.ERROR -> "Error completing request"
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Error processing request."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "Selected item is already owned"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Item not available"
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Play Store service is not connected now"
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> "Timeout"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Network Connection down"
            BillingClient.BillingResponseCode.USER_CANCELED -> "Request Canceled"
            BillingClient.BillingResponseCode.OK -> "Subscribed Successfully"
            else -> ""
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        isPurchase = true
        if (purchaseCallback != null) {
            purchaseCallback!!.onProductPurchased(purchase.orderId, purchase.originalJson)
        }
    }

    /**
     * Verifies the user's purchases for both in-app purchases and subscriptions.
     *
     * @param billingCallback A callback interface that will be notified when the billing setup is finished.
     */
    private fun verifyPurchased(billingCallback: BillingCallback) {
        if (listInAppProduct.isNotEmpty()) {
            billingClient!!.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(INAPP).build()
            ) { billingResult: BillingResult, list: List<Purchase> ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in list) {
                        for (id in listInAppProduct) {
                            if (purchase.products.contains(id.zza())) {
                                isPurchase = true
                            }
                        }
                    }
                }
                isVerifyInApp = true
                if (!listSubProduct.isEmpty()) {
                    if (isVerifySub) {
                        billingCallback.onBillingSetupFinished(billingResult.responseCode)
                    }
                } else {
                    billingCallback.onBillingSetupFinished(billingResult.responseCode)
                }
            }
        }
        if (!listSubProduct.isEmpty()) {
            billingClient!!.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            ) { billingResult: BillingResult, list: List<Purchase> ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in list) {
                        for (id in listSubProduct) {
                            if (purchase.products.contains(id.zza())) {
                                isPurchase = true
                            }
                        }
                    }
                }
                isVerifySub = true
                if (!listInAppProduct.isEmpty()) {
                    if (isVerifyInApp) {
                        billingCallback.onBillingSetupFinished(billingResult.responseCode)
                    }
                } else {
                    billingCallback.onBillingSetupFinished(billingResult.responseCode)
                }
            }
        }
    }

    /**
     * Retrieves the formatted price of the one-time purchase offer for a given product.
     *
     * @param productId The unique identifier of the product.
     * @return The formatted price of the one-time purchase offer, or an empty string if the product details are not found or the one-time purchase offer details are not available.
     */
    fun getPrice(productId: String): String {
        val productDetails = productDetailsInAppMap[productId] ?: return ""
        if (productDetails.oneTimePurchaseOfferDetails == null) return ""
        Log.e(TAG, "getPrice: " + productDetails.oneTimePurchaseOfferDetails!!.formattedPrice)
        return productDetails.oneTimePurchaseOfferDetails!!.formattedPrice
    }

    /**
     * Retrieves the formatted price of the last subscription offer for a given product.
     *
     * @param productId The unique identifier of the product.
     * @return The formatted price of the last subscription offer, or an empty string if the product details are not found.
     */
    fun getPriceSub(productId: String): String {
        val productDetails = productDetailsSubsMap[productId] ?: return ""
        val subsDetail = productDetails.subscriptionOfferDetails ?: return ""
        val pricingPhaseList = subsDetail[subsDetail.size - 1].pricingPhases.pricingPhaseList
        Log.e(TAG, "getPriceSub: " + pricingPhaseList[pricingPhaseList.size - 1].formattedPrice)
        return pricingPhaseList[pricingPhaseList.size - 1].formattedPrice
    }

    /**
     * Retrieves the currency code of a product (in-app purchase or subscription).
     *
     * @param productId The unique identifier of the product.
     * @param type      The type of the product, either "in-app purchase" or "subscription".
     * @return The currency code of the product, or an empty string if the product details are not found.
     */
    fun getCurrency(productId: String, type: String): String {
        val productDetails = if (type == INAPP) productDetailsInAppMap[productId] else productDetailsSubsMap[productId]
        if (productDetails == null) return ""
        if (type == INAPP) {
            if (productDetails.oneTimePurchaseOfferDetails == null) return ""
            return productDetails.oneTimePurchaseOfferDetails!!.priceCurrencyCode
        } else {
            val subsDetail = productDetails.subscriptionOfferDetails ?: return ""
            val pricingPhaseList = subsDetail[subsDetail.size - 1].pricingPhases.pricingPhaseList
            return pricingPhaseList[pricingPhaseList.size - 1].priceCurrencyCode
        }
    }

    /**
     * Retrieves the price of a product (in-app purchase or subscription) without the currency symbol.
     *
     * @param productId The unique identifier of the product.
     * @param type      The type of the product, either "in-app purchase" or "subscription".
     * @return The price of the product in micros (1/1,000,000 of the currency unit), or 0 if the product details are not found.
     */
    fun getPriceWithoutCurrency(productId: String, type: String): Double {
        val productDetails = if (type == INAPP) productDetailsInAppMap[productId] else productDetailsSubsMap[productId]
        if (productDetails == null) return 0.toDouble()
        if (type == INAPP) {
            if (productDetails.oneTimePurchaseOfferDetails == null) return 0.toDouble()
            return productDetails.oneTimePurchaseOfferDetails!!.priceAmountMicros.toDouble()
        } else {
            val subsDetail = productDetails.subscriptionOfferDetails ?: return 0.toDouble()
            val pricingPhaseList = subsDetail[subsDetail.size - 1].pricingPhases.pricingPhaseList
            return pricingPhaseList[pricingPhaseList.size - 1].priceAmountMicros.toDouble()
        }
    }

    companion object {
        private const val TAG = "IAPManager"
        const val PRODUCT_ID_TEST: String = "android.test.purchased"

        const val INAPP: String = BillingClient.ProductType.INAPP
        const val SUBS: String = BillingClient.ProductType.SUBS

        @Volatile
        private var INSTANCE: IAPManager? = null

        @JvmStatic
        fun getInstance(): IAPManager {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = IAPManager()
                }
            }
            return INSTANCE!!
        }
    }
}