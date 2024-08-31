package com.chinchin.ads.callback

interface PurchaseListener {
    fun onProductPurchased(productId: String?, transactionDetails: String?)

    fun displayErrorMessage(errorMsg: String?)

    fun onUserCancelBilling()
}
