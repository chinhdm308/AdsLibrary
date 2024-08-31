package com.chinchin.ads.billing

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.IntDef
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.PricingPhase
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.chinchin.ads.callback.BillingListener
import com.chinchin.ads.callback.PurchaseListener
import com.chinchin.ads.callback.UpdatePurchaseListener
import com.chinchin.ads.util.AppOpenManager
import com.chinchin.ads.util.Security.verifyPurchase
import com.google.common.collect.ImmutableList
import java.text.NumberFormat
import java.util.Currency

class AppPurchase private constructor() {
    private var isPurchaseTest = false
    val price: String
        get() = getPrice(productId)
    private var oldPrice = "2.99$"
    private val productId: String? = null
    private var listSubsId: ArrayList<QueryProductDetailsParams.Product>? = null
    private var listInAppId: ArrayList<QueryProductDetailsParams.Product>? = null
    private var purchaseListener: PurchaseListener? = null
    private var updatePurchaseListener: UpdatePurchaseListener? = null
    private var billingListener: BillingListener? = null
    private var initBillingFinish: Boolean = false
    private var billingClient: BillingClient? = null
    private var skuListInAppFromStore: List<ProductDetails>? = null
    private var skuListSubsFromStore: List<ProductDetails>? = null
    private val skuDetailsInAppMap: MutableMap<String?, ProductDetails> = HashMap()
    private val skuDetailsSubsMap: MutableMap<String, ProductDetails> = HashMap()
    private var isAvailable: Boolean = false
    private var isListGot = false
    private var isConsumePurchase = false

    private val countReconnectBilling = 0
    private val countMaxReconnectBilling = 4

    //tracking purchase adjust
    private var idPurchaseCurrent = ""
    private var typeIAP = 0

    // status verify purchase INAPP & SUBS
    private var verifyFinish = false

    private var isVerifyInApp = false
    private var isVerifySubs = false
    private var isUpdateInApp = false
    private var isUpdateSubs = false

    private var isPurchased: Boolean = false //state purchase on app
    val idPurchased: String = "" //id purchased
    private val listOwnerIdSubs: MutableList<PurchaseResult> = ArrayList() //id sub
    private val listOwnerIdInApp: MutableList<String> = ArrayList() //id inapp

    private var discount: Double = 1.0

    private var handlerTimeout: Handler? = null
    private var rdTimeout: Runnable? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, list ->
        Log.e(TAG, "onPurchasesUpdated code: " + billingResult.responseCode)
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
            for (purchase in list) {
                val sku: List<String> = purchase.skus
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            if (purchaseListener != null) purchaseListener!!.onUserCancelBilling()
            Log.d(TAG, "onPurchasesUpdated:USER_CANCELED")
        } else {
            Log.d(TAG, "onPurchasesUpdated:...")
        }
    }

    private val purchaseClientStateListener: BillingClientStateListener = object : BillingClientStateListener {
        override fun onBillingServiceDisconnected() {
            isAvailable = false
        }

        override fun onBillingSetupFinished(billingResult: BillingResult) {
            Log.d(TAG, "onBillingSetupFinished: " + billingResult.responseCode)

            if (!initBillingFinish) {
                verifyPurchased(true)
            }

            initBillingFinish = true
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                isAvailable = true
                // check product detail INAPP
                if (listInAppId!!.isNotEmpty()) {
                    val paramsINAPP = QueryProductDetailsParams.newBuilder()
                        .setProductList(listInAppId!!)
                        .build()

                    billingClient!!.queryProductDetailsAsync(paramsINAPP) { billingResult, productDetailsList ->
                        Log.d(TAG, "onSkuINAPPDetailsResponse: " + productDetailsList.size)
                        skuListInAppFromStore = productDetailsList
                        isListGot = true
                        addSkuInAppToMap(productDetailsList)
                    }
                }
                // check product detail SUBS
                if (listSubsId!!.isNotEmpty()) {
                    val paramsSUBS = QueryProductDetailsParams.newBuilder()
                        .setProductList(listSubsId!!)
                        .build()

                    billingClient!!.queryProductDetailsAsync(paramsSUBS) { billingResult, productDetailsList ->
                        Log.d(TAG, "onSkuSubsDetailsResponse: " + productDetailsList.size)
                        skuListSubsFromStore = productDetailsList
                        isListGot = true
                        addSkuSubsToMap(productDetailsList)
                    }
                }
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE || billingResult.responseCode == BillingClient.BillingResponseCode.ERROR) {
                Log.e(TAG, "onBillingSetupFinished:ERROR")
            }
        }
    }

    fun setPurchaseListener(purchaseListener: PurchaseListener?) {
        this.purchaseListener = purchaseListener
    }

    fun setUpdatePurchaseListener(listener: UpdatePurchaseListener?) {
        this.updatePurchaseListener = listener
    }

    fun setPurchaseTest(purchaseTest: Boolean) {
        this.isPurchaseTest = purchaseTest
    }

    /**
     * Listener init billing app
     * When init available auto call onInitBillingFinish with resultCode = 0
     *
     * @param billingListener The BillingListener object that will be notified about the state of the billing system.
     */
    fun setBillingListener(billingListener: BillingListener) {
        this.billingListener = billingListener
        if (isAvailable) {
            billingListener.onInitBillingFinished(0)
            initBillingFinish = true
        }
    }

    fun setEventConsumePurchaseTest(view: View) {
        view.setOnClickListener {
            if (isPurchaseTest) {
                Log.d(TAG, "setEventConsumePurchaseTest: success")
                consumePurchase(PRODUCT_ID_TEST)
            }
        }
    }

    /**
     * Listener init billing app with timeout
     * When init available auto call onInitBillingFinish with resultCode = 0
     *
     * @param billingListener The BillingListener object that will be notified about the state of the billing system.
     * @param timeout         The maximum time (in milliseconds) that the billing system initialization should take before timing out.
     */
    fun setBillingListener(billingListener: BillingListener, timeout: Int) {
        Log.d(TAG, "setBillingListener: timeout $timeout")
        this.billingListener = billingListener
        if (isAvailable) {
            Log.d(TAG, "setBillingListener: finish")
            billingListener.onInitBillingFinished(0)
            initBillingFinish = true
            return
        }
        handlerTimeout = Handler()
        rdTimeout = Runnable {
            Log.d(TAG, "setBillingListener: timeout run")
            initBillingFinish = true
            billingListener.onInitBillingFinished(BillingClient.BillingResponseCode.ERROR)
        }
        handlerTimeout!!.postDelayed(rdTimeout!!, timeout.toLong())
    }

    fun setConsumePurchase(consumePurchase: Boolean) {
        isConsumePurchase = consumePurchase
    }

    fun setOldPrice(oldPrice: String) {
        this.oldPrice = oldPrice
    }

    fun getListOwnerIdSubs(): List<PurchaseResult> {
        return listOwnerIdSubs
    }

    fun getListOwnerIdInApp(): List<String> {
        return listOwnerIdInApp
    }

    /**
     * Initialize the billing system for the Android application.
     *
     * @param application The application object for the Android app.
     * @param listInAppId A list of in-app purchase product IDs.
     * @param listSubsId  A list of subscription product IDs.
     */
    fun initBilling(application: Application?, listInAppId: MutableList<String?>, listSubsId: List<String?>) {
        if (isPurchaseTest) {
            // auto add purchase test when dev
            listInAppId.add(PRODUCT_ID_TEST)
        }
        this.listSubsId = listIdToListProduct(listSubsId, BillingClient.ProductType.SUBS)
        this.listInAppId = listIdToListProduct(listInAppId, BillingClient.ProductType.INAPP)

        billingClient = BillingClient.newBuilder(application!!)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient!!.startConnection(purchaseClientStateListener)
    }

    private fun addSkuSubsToMap(skuList: List<ProductDetails>) {
        for (skuDetails in skuList) {
            skuDetailsSubsMap[skuDetails.productId] = skuDetails
        }
    }

    private fun addSkuInAppToMap(skuList: List<ProductDetails>) {
        for (skuDetails in skuList) {
            skuDetailsInAppMap[skuDetails.productId] = skuDetails
        }
    }

    fun setPurchase(purchase: Boolean) {
        isPurchased = purchase
    }

    private fun addOrUpdateOwnerIdSub(purchaseResult: PurchaseResult, id: String) {
        var isExistId = false
        for (p in listOwnerIdSubs) {
            if (p.productId.contains(id)) {
                isExistId = true
                listOwnerIdSubs.remove(p)
                listOwnerIdSubs.add(purchaseResult)
                break
            }
        }
        if (!isExistId) {
            listOwnerIdSubs.add(purchaseResult)
        }
    }

    // kiểm tra trạng thái purchase
    fun verifyPurchased(isCallback: Boolean) {
        Log.d(TAG, "isPurchased: " + listSubsId!!.size)
        verifyFinish = false
        if (listInAppId != null) {
            billingClient!!.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { billingResult, list ->
                Log.d(TAG, "verifyPurchased INAPP code:" + billingResult.responseCode + " === size:" + list.size)
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in list) {
                        for (id in listInAppId!!) {
                            if (purchase.products.contains(id.zza())) {
                                Log.d(TAG, "verifyPurchased INAPP: true")
                                listOwnerIdInApp.add(id.zza())
                                isPurchased = true
                            }
                        }
                    }
                    isVerifyInApp = true
                    if (isVerifySubs) {
                        if (billingListener != null && isCallback) {
                            billingListener!!.onInitBillingFinished(billingResult.responseCode)
                            if (handlerTimeout != null && rdTimeout != null) {
                                handlerTimeout!!.removeCallbacks(rdTimeout!!)
                            }
                        }
                        verifyFinish = true
                    }
                } else {
                    isVerifyInApp = true
                    if (isVerifySubs) {
                        // chưa mua subs và IAP
                        billingListener!!.onInitBillingFinished(billingResult.responseCode)
                        if (handlerTimeout != null && rdTimeout != null) {
                            handlerTimeout!!.removeCallbacks(rdTimeout!!)
                        }
                        verifyFinish = true
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

        if (listSubsId != null) {
            billingClient!!.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                object : PurchasesResponseListener {
                    override fun onQueryPurchasesResponse(billingResult: BillingResult, list: List<Purchase>) {
                        Log.d(TAG, "verifyPurchased SUBS code:" + billingResult.responseCode + " === size:" + list.size)
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            for (purchase in list) {
                                for (id in listSubsId!!) {
                                    if (purchase.products.contains(id.zza())) {
                                        val purchaseResult = PurchaseResult(
                                            purchase.packageName,
                                            purchase.products,
                                            purchase.purchaseState,
                                            purchase.isAutoRenewing
                                        )
                                        addOrUpdateOwnerIdSub(purchaseResult, id.zza())
                                        Log.d(TAG, "verifyPurchased SUBS: true")
                                        isPurchased = true
                                    }
                                }
                            }
                            isVerifySubs = true
                            if (isVerifyInApp) {
                                if (billingListener != null && isCallback) {
                                    billingListener!!.onInitBillingFinished(billingResult.responseCode)
                                    if (handlerTimeout != null && rdTimeout != null) {
                                        handlerTimeout!!.removeCallbacks(rdTimeout!!)
                                    }
                                }
                                verifyFinish = true
                            }
                        } else {
                            isVerifySubs = true
                            if (isVerifyInApp) {
                                // chưa mua subs và IAP
                                if (billingListener != null && isCallback) {
                                    billingListener!!.onInitBillingFinished(billingResult.responseCode)
                                    if (handlerTimeout != null && rdTimeout != null) {
                                        handlerTimeout!!.removeCallbacks(rdTimeout!!)
                                    }
                                    verifyFinish = true
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
            )
        }
    }

    fun updatePurchaseStatus() {
        if (listInAppId != null) {
            billingClient!!.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { billingResult: BillingResult, list: List<Purchase> ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in list) {
                        for (id in listInAppId!!) {
                            if (purchase.products.contains(id.zza())) {
                                if (!listOwnerIdInApp.contains(id.zza())) {
                                    listOwnerIdInApp.add(id.zza())
                                }
                            }
                        }
                    }
                }
                isUpdateInApp = true
                if (isUpdateSubs) {
                    if (updatePurchaseListener != null) {
                        updatePurchaseListener!!.onUpdateFinished()
                    }
                }
            }
        }

        if (listSubsId != null) {
            billingClient!!.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            ) { billingResult: BillingResult, list: List<Purchase> ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in list) {
                        for (id in listSubsId!!) {
                            if (purchase.products.contains(id.zza())) {
                                val purchaseResult = PurchaseResult(
                                    purchase.packageName,
                                    purchase.products,
                                    purchase.purchaseState,
                                    purchase.isAutoRenewing
                                )
                                addOrUpdateOwnerIdSub(purchaseResult, id.zza())
                            }
                        }
                    }
                }
                isUpdateSubs = true
                if (isUpdateInApp) {
                    if (updatePurchaseListener != null) {
                        updatePurchaseListener!!.onUpdateFinished()
                    }
                }
            }
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
    fun purchase(activity: Activity?) {
        if (productId == null) {
            Log.e(TAG, "Purchase false:productId null")
            Toast.makeText(activity, "Product id must not be empty!", Toast.LENGTH_SHORT).show()
            return
        }
        purchase(activity, productId)
    }

    fun purchase(activity: Activity?, productId: String): String {
        var productId = productId
        if (skuListInAppFromStore == null) {
            if (purchaseListener != null) {
                purchaseListener!!.displayErrorMessage("Billing error init")
            }
            return ""
        }
        val productDetails = skuDetailsInAppMap[productId] ?: return "Product ID invalid"
        Log.d(TAG, "purchase: $productDetails")
        //ProductDetails{jsonString='{"productId":"android.test.purchased","type":"inapp","title":"Tiêu đề mẫu","description":"Mô tả mẫu về sản phẩm: android.test.purchased.","skuDetailsToken":"AEuhp4Izz50wTvd7YM9wWjPLp8hZY7jRPhBEcM9GAbTYSdUM_v2QX85e8UYklstgqaRC","oneTimePurchaseOfferDetails":{"priceAmountMicros":23207002450,"priceCurrencyCode":"VND","formattedPrice":"23.207 ₫"}}', parsedJson={"productId":"android.test.purchased","type":"inapp","title":"Tiêu đề mẫu","description":"Mô tả mẫu về sản phẩm: android.test.purchased.","skuDetailsToken":"AEuhp4Izz50wTvd7YM9wWjPLp8hZY7jRPhBEcM9GAbTYSdUM_v2QX85e8UYklstgqaRC","oneTimePurchaseOfferDetails":{"priceAmountMicros":23207002450,"priceCurrencyCode":"VND","formattedPrice":"23.207 ₫"}}, productId='android.test.purchased', productType='inapp', title='Tiêu đề mẫu', productDetailsToken='AEuhp4Izz50wTvd7YM9wWjPLp8hZY7jRPhBEcM9GAbTYSdUM_v2QX85e8UYklstgqaRC', subscriptionOfferDetails=null}
        if (isPurchaseTest) {
            // Auto using id purchase test in variant dev
            productId = PRODUCT_ID_TEST
            val purchaseDevBottomSheet = PurchaseDevBottomSheet(activity!!, TYPE_IAP.PURCHASE, productDetails, purchaseListener)
            purchaseDevBottomSheet.show()
            return ""
        }

        idPurchaseCurrent = productId
        typeIAP = TYPE_IAP.PURCHASE

        val productDetailsParamsList =
            ImmutableList.of(
                ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient!!.launchBillingFlow(activity!!, billingFlowParams)

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                if (purchaseListener != null) purchaseListener!!.displayErrorMessage("Billing not supported for type of request")
                return "Billing not supported for type of request"
            }

            BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> return ""

            BillingClient.BillingResponseCode.ERROR -> {
                if (purchaseListener != null) purchaseListener!!.displayErrorMessage("Error completing request")
                return "Error completing request"
            }

            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                return "Error processing request."
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                return "Selected item is already owned"
            }

            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                return "Item not available"
            }

            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                return "Play Store service is not connected now"
            }

            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {
                return "Timeout"
            }

            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                if (purchaseListener != null) purchaseListener!!.displayErrorMessage("Network error.")
                return "Network Connection down"
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                if (purchaseListener != null) purchaseListener!!.displayErrorMessage("Request Canceled")
                return "Request Canceled"
            }

            BillingClient.BillingResponseCode.OK -> {
                return "Subscribed Successfully"
            }

            else -> {
                return ""
            }
        }
    }

    fun subscribe(activity: Activity?, subsId: String): String {
        if (skuListSubsFromStore == null) {
            if (purchaseListener != null) purchaseListener!!.displayErrorMessage("Billing error init")
            return ""
        }

        if (isPurchaseTest) {
            // sử dụng ID Purchase test
            purchase(activity, PRODUCT_ID_TEST)
            return "Billing test"
        }
        val productDetails = skuDetailsSubsMap[subsId] ?: return "Product ID invalid"
        val skuDetails = skuDetailsSubsMap[subsId]
        val subsDetail = if (skuDetails != null) skuDetails.subscriptionOfferDetails else ArrayList()
        val offerToken = subsDetail?.get(subsDetail.size - 1)?.offerToken ?: ""
        val productDetailsParams = ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
        val productDetailsParamsList = ImmutableList.of(productDetailsParams)

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient!!.launchBillingFlow(activity!!, billingFlowParams)

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                if (purchaseListener != null) purchaseListener!!.displayErrorMessage("Billing not supported for type of request")
                return "Billing not supported for type of request"
            }

            BillingClient.BillingResponseCode.ITEM_NOT_OWNED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> return ""
            BillingClient.BillingResponseCode.ERROR -> {
                if (purchaseListener != null) purchaseListener!!.displayErrorMessage("Error completing request")
                return "Error completing request"
            }

            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> return "Error processing request."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> return "Selected item is already owned"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> return "Item not available"
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> return "Play Store service is not connected now"
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> return "Timeout"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                if (purchaseListener != null) purchaseListener!!.displayErrorMessage("Network error.")
                return "Network Connection down"
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                if (purchaseListener != null) purchaseListener!!.displayErrorMessage("Request Canceled")
                return "Request Canceled"
            }

            BillingClient.BillingResponseCode.OK -> return "Subscribed Successfully"
            else -> return ""
        }
    }

    fun consumePurchase() {
        if (productId == null) {
            Log.e(TAG, "Consume Purchase false:productId null ")
            return
        }
        consumePurchase(productId)
    }

    /**
     * Consumes the purchase for the given product ID.
     *
     * @param productId the unique identifier of the product
     */
    fun consumePurchase(productId: String?) {
        billingClient!!.queryPurchasesAsync(BillingClient.ProductType.INAPP) { billingResult: BillingResult, list: List<Purchase> ->
            var pc: Purchase? = null
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in list) {
                    if (purchase.skus.contains(productId)) {
                        pc = purchase
                    }
                }
            }
            if (pc == null) return@queryPurchasesAsync
            try {
                val consumeParams =
                    ConsumeParams.newBuilder()
                        .setPurchaseToken(pc.purchaseToken)
                        .build()

                val listener = ConsumeResponseListener { billingResult, purchaseToken ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.e(TAG, "onConsumeResponse: OK")
                        verifyPurchased(false)
                    }
                }

                billingClient!!.consumeAsync(consumeParams, listener)
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
            }
        }
    }

    private fun getListInAppId(): List<String> {
        val list: MutableList<String> = ArrayList()
        for (product in listInAppId!!) {
            list.add(product.zza())
        }
        return list
    }

    private fun getListSubsId(): List<String> {
        val list: MutableList<String> = ArrayList()
        for (product in listSubsId!!) {
            list.add(product.zza())
        }
        return list
    }

    /**
     * Handles the processing of a purchase made by the user.
     *
     * @param purchase the purchase object obtained from the Google Play Billing API
     */
    private fun handlePurchase(purchase: Purchase) {
        // tracking adjust
        val price = getPriceWithoutCurrency(idPurchaseCurrent, typeIAP)
        val currency = getCurrency(idPurchaseCurrent, typeIAP)

        if (purchaseListener != null) {
            isPurchased = true
            purchaseListener!!.onProductPurchased(purchase.orderId, purchase.originalJson)
        }
        if (isConsumePurchase) {
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            val listener = ConsumeResponseListener { billingResult, purchaseToken -> Log.d(TAG, "onConsumeResponse: " + billingResult.debugMessage) }

            billingClient!!.consumeAsync(consumeParams, listener)
        } else {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!verifyValidSignature(purchase.originalJson, purchase.signature)) {
                    Log.d(TAG, "Error: invalid Purchase")
                    return
                }
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                if (!purchase.isAcknowledged) {
                    billingClient!!.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                        Log.d(
                            TAG,
                            "onAcknowledgePurchaseResponse: " + billingResult.debugMessage
                        )
                    }
                }
            }
        }
    }

    /**
     * Retrieves the price of the one-time purchase for the given product ID.
     *
     * @param productId the unique identifier of the product
     * @return the formatted price of the one-time purchase, or an empty string if the product does not have a one-time purchase or its details cannot be found
     */
    fun getPrice(productId: String?): String {
        val skuDetails = skuDetailsInAppMap[productId] ?: return ""
        if (skuDetails.oneTimePurchaseOfferDetails == null) return ""
        Log.e(TAG, "getPrice: " + skuDetails.oneTimePurchaseOfferDetails!!.formattedPrice)
        return skuDetails.oneTimePurchaseOfferDetails!!.formattedPrice
    }

    /**
     * Retrieves the price of the subscription for the given product ID.
     *
     * @param productId the unique identifier of the product
     * @return the formatted price of the subscription's last pricing phase, or an empty string if the product does not have a subscription or its details cannot be found
     */
    fun getPriceSub(productId: String): String {
        val skuDetails = skuDetailsSubsMap[productId] ?: return ""
        if (skuDetails.subscriptionOfferDetails == null) return ""
        val subsDetail = skuDetails.subscriptionOfferDetails
        val pricingPhaseList = subsDetail!![subsDetail.size - 1].pricingPhases.pricingPhaseList
        Log.e(TAG, "getPriceSub: " + pricingPhaseList[pricingPhaseList.size - 1].formattedPrice)
        return pricingPhaseList[pricingPhaseList.size - 1].formattedPrice
    }

    /**
     * Get Price Pricing Phase List Subs
     *
     * @param productId The unique identifier of the subscription product.
     * @return A list of ProductDetails.PricingPhase objects, which represent the pricing phases for the subscription.
     */
    fun getPricePricingPhaseList(productId: String): List<PricingPhase>? {
        val skuDetails = skuDetailsSubsMap[productId] ?: return null
        if (skuDetails.subscriptionOfferDetails == null) return null
        val subsDetail = skuDetails.subscriptionOfferDetails
        return subsDetail!![subsDetail.size - 1].pricingPhases.pricingPhaseList
    }

    /**
     * Get Formatted Price by country
     * Get final price with id
     *
     * @param productId The unique identifier of the product or subscription.
     * @return The formatted price of the product or subscription (e.g. "$9.99", "€7.99", "¥980", etc.).
     */
    fun getIntroductorySubPrice(productId: String): String {
        val skuDetails = skuDetailsSubsMap[productId] ?: return ""
        if (skuDetails.oneTimePurchaseOfferDetails != null) return skuDetails.oneTimePurchaseOfferDetails!!.formattedPrice
        else if (skuDetails.subscriptionOfferDetails != null) {
            val subsDetail = skuDetails.subscriptionOfferDetails
            val pricingPhaseList = subsDetail!![subsDetail.size - 1].pricingPhases.pricingPhaseList
            return pricingPhaseList[pricingPhaseList.size - 1].formattedPrice
        } else {
            return ""
        }
    }

    /**
     * Get Currency subs or IAP by country
     *
     * @param productId The unique identifier of the product or subscription.
     * @param typeIAP   The type of in-app purchase, either TYPE_IAP.PURCHASE (one-time purchase) or TYPE_IAP.SUBSCRIPTION.
     * @return The currency code of the product or subscription (e.g. "USD", "EUR", "JPY"...)
     */
    fun getCurrency(productId: String, typeIAP: Int): String {
        val skuDetails = if (typeIAP == TYPE_IAP.PURCHASE) skuDetailsInAppMap[productId] else skuDetailsSubsMap[productId]
        if (skuDetails == null) return ""

        if (typeIAP == TYPE_IAP.PURCHASE) {
            if (skuDetails.oneTimePurchaseOfferDetails == null) return ""
            return skuDetails.oneTimePurchaseOfferDetails!!.priceCurrencyCode
        } else {
            val subsDetail = skuDetails.subscriptionOfferDetails ?: return ""
            val pricingPhaseList = subsDetail[subsDetail.size - 1].pricingPhases.pricingPhaseList
            return pricingPhaseList[pricingPhaseList.size - 1].priceCurrencyCode
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
    fun getPriceWithoutCurrency(productId: String, typeIAP: Int): Double {
        val skuDetails = if (typeIAP == TYPE_IAP.PURCHASE) skuDetailsInAppMap[productId] else skuDetailsSubsMap[productId]
        if (skuDetails == null) {
            return 0.toDouble()
        }
        if (typeIAP == TYPE_IAP.PURCHASE) {
            if (skuDetails.oneTimePurchaseOfferDetails == null) return 0.toDouble()
            return skuDetails.oneTimePurchaseOfferDetails!!.priceAmountMicros.toDouble()
        } else {
            val subsDetail = skuDetails.subscriptionOfferDetails ?: return 0.toDouble()
            val pricingPhaseList = subsDetail[subsDetail.size - 1].pricingPhases.pricingPhaseList
            return pricingPhaseList[pricingPhaseList.size - 1].priceAmountMicros.toDouble()
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
    private fun formatCurrency(price: Double, currency: String): String {
        val format = NumberFormat.getCurrencyInstance()
        format.maximumFractionDigits = 0
        format.currency = Currency.getInstance(currency)
        return format.format(price)
    }

    private fun listIdToListProduct(listId: List<String?>, styleBilling: String): ArrayList<QueryProductDetailsParams.Product> {
        val listProduct = ArrayList<QueryProductDetailsParams.Product>()
        for (id in listId) {
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id!!)
                .setProductType(styleBilling)
                .build()
            listProduct.add(product)
        }
        return listProduct
    }

    private fun verifyValidSignature(signedData: String, signature: String): Boolean {
        val base64Key = ""
        return verifyPurchase(base64Key, signedData, signature)
    }

    @IntDef(TYPE_IAP.PURCHASE, TYPE_IAP.SUBSCRIPTION)
    annotation class TYPE_IAP {
        companion object {
            const val PURCHASE: Int = 1
            const val SUBSCRIPTION: Int = 2
        }
    }

    companion object {
        private const val TAG = "AppPurchase"

        private const val LICENSE_KEY: String = ""
        private const val MERCHANT_ID: String = ""
        private const val PRODUCT_ID_TEST: String = "android.test.purchased"

        @Volatile
        private var INSTANCE: AppPurchase? = null

        @JvmStatic
        fun getInstance(): AppPurchase {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = AppPurchase()
                }
            }
            return INSTANCE!!
        }
    }
}