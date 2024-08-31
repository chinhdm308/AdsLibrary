package com.chinchin.ads.billing

class PurchaseResult {

    @JvmField
    var productId: List<String>

    private var orderId: String? = null
    private var packageName: String
    private var purchaseTime: Long = 0
    private var purchaseState: Int
    private var purchaseToken: String? = null
    private var quantity = 0
    private var isAutoRenewing: Boolean
    private var acknowledged = false

    constructor(packageName: String, productId: List<String>, purchaseState: Int, autoRenewing: Boolean) {
        this.packageName = packageName
        this.productId = productId
        this.purchaseState = purchaseState
        this.isAutoRenewing = autoRenewing
    }

    constructor(
        orderId: String?,
        packageName: String,
        productId: List<String>,
        purchaseTime: Long,
        purchaseState: Int,
        purchaseToken: String?,
        quantity: Int,
        autoRenewing: Boolean,
        acknowledged: Boolean
    ) {
        this.orderId = orderId
        this.packageName = packageName
        this.productId = productId
        this.purchaseTime = purchaseTime
        this.purchaseState = purchaseState
        this.purchaseToken = purchaseToken
        this.quantity = quantity
        this.isAutoRenewing = autoRenewing
        this.acknowledged = acknowledged
    }
}
