package com.chinchin.ads.billing

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.billingclient.api.ProductDetails
import com.chinchin.ads.R
import com.chinchin.ads.callback.PurchaseListener
import com.google.android.material.bottomsheet.BottomSheetDialog

class PurchaseDevBottomSheet(
    context: Context,
    private val typeIap: Int,
    private val productDetails: ProductDetails?,
    private val purchaseListener: PurchaseListener?,
) : BottomSheetDialog(context) {

    private var txtTitle: TextView? = null
    private var txtDescription: TextView? = null
    private var txtId: TextView? = null
    private var txtPrice: TextView? = null
    private var txtContinuePurchase: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_billing_test)
        txtTitle = findViewById(R.id.txtTitle)
        txtDescription = findViewById(R.id.txtDescription)
        txtId = findViewById(R.id.txtId)
        txtPrice = findViewById(R.id.txtPrice)
        txtContinuePurchase = findViewById(R.id.txtContinuePurchase)
        if (productDetails == null) {
        } else {
            txtTitle!!.text = productDetails.title
            txtDescription!!.text = productDetails.description
            txtId!!.text = productDetails.productId
            if (typeIap == AppPurchase.TYPE_IAP.PURCHASE) txtPrice!!.text = productDetails.oneTimePurchaseOfferDetails!!.formattedPrice
            else txtPrice!!.text = productDetails.subscriptionOfferDetails!![0].pricingPhases.pricingPhaseList[0].formattedPrice

            txtContinuePurchase!!.setOnClickListener { v: View? ->
                if (purchaseListener != null) {
                    AppPurchase.getInstance().setPurchase(true)
                    purchaseListener.onProductPurchased(
                        productDetails.productId,
                        "{\"productId\":\"android.test.purchased\",\"type\":\"inapp\",\"title\":\"Tiêu đề mẫu\",\"description\":\"Mô tả mẫu về sản phẩm: android.test.purchased.\",\"skuDetailsToken\":\"AEuhp4Izz50wTvd7YM9wWjPLp8hZY7jRPhBEcM9GAbTYSdUM_v2QX85e8UYklstgqaRC\",\"oneTimePurchaseOfferDetails\":{\"priceAmountMicros\":23207002450,\"priceCurrencyCode\":\"VND\",\"formattedPrice\":\"23.207 ₫\"}}', parsedJson={\"productId\":\"android.test.purchased\",\"type\":\"inapp\",\"title\":\"Tiêu đề mẫu\",\"description\":\"Mô tả mẫu về sản phẩm: android.test.purchased.\",\"skuDetailsToken\":\"AEuhp4Izz50wTvd7YM9wWjPLp8hZY7jRPhBEcM9GAbTYSdUM_v2QX85e8UYklstgqaRC\",\"oneTimePurchaseOfferDetails\":{\"priceAmountMicros\":23207002450,\"priceCurrencyCode\":\"VND\",\"formattedPrice\":\"23.207 ₫\"}}, productId='android.test.purchased', productType='inapp', title='Tiêu đề mẫu', productDetailsToken='AEuhp4Izz50wTvd7YM9wWjPLp8hZY7jRPhBEcM9GAbTYSdUM_v2QX85e8UYklstgqaRC', subscriptionOfferDetails=null}"
                    )
                }
                dismiss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val w = ViewGroup.LayoutParams.MATCH_PARENT
        val h = ViewGroup.LayoutParams.WRAP_CONTENT
        window?.setLayout(w, h)
    }
}
