package com.yamahamx.editor.data.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*
import com.yamahamx.editor.R
import com.yamahamx.editor.data.repository.PerformanceStorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play Billing lifecycle, product queries, purchases and acknowledgements
 * for the lifetime Pro license ("mxcontrol_full_license").
 */
class BillingManager(
    private val context: Context,
    private val storageRepository: PerformanceStorageRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_ID_PRO = "mxcontrol_full_license"
        const val DEFAULT_PRICE_FALLBACK = "R$ 29,90"

        // 01/11/2026 00:00:00 BRT (03:00:00 UTC)
        const val PROMO_START_TIMESTAMP = PerformanceStorageRepository.PROMO_START_TIMESTAMP
        // 08/11/2026 00:00:00 BRT (03:00:00 UTC)
        const val PROMO_END_TIMESTAMP   = PerformanceStorageRepository.PROMO_END_TIMESTAMP
    }

    val isLicensePurchased: Boolean
        get() = storageRepository.isLicensePurchased

    fun isPromoActive(): Boolean = storageRepository.isPromoActive()

    private val _isProUnlocked = MutableStateFlow(storageRepository.isProUnlocked())
    val isProUnlocked: StateFlow<Boolean> = _isProUnlocked.asStateFlow()

    fun refreshProStatus() {
        val currentStatus = storageRepository.isProUnlocked()
        if (_isProUnlocked.value != currentStatus) {
            _isProUnlocked.value = currentStatus
            Log.i(TAG, "[MX_BILLING] Pro status refreshed: isProUnlocked=$currentStatus")
        }
    }

    private val _formattedPrice = MutableStateFlow(DEFAULT_PRICE_FALLBACK)
    val formattedPrice: StateFlow<String> = _formattedPrice.asStateFlow()

    private val _purchaseStatusMessage = MutableStateFlow<String?>(null)
    val purchaseStatusMessage: StateFlow<String?> = _purchaseStatusMessage.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .build()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(pendingPurchasesParams)
        .build()

    init {
        Log.i(TAG, "[MX_BILLING] Initializing BillingManager for product '$PRODUCT_ID_PRO'...")
        startBillingConnection()
    }

    fun startBillingConnection() {
        if (!billingClient.isReady) {
            Log.i(TAG, "[MX_BILLING] Starting connection to Google Play Billing Service...")
            billingClient.startConnection(this)
        } else {
            Log.i(TAG, "[MX_BILLING] BillingClient is already ready.")
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val code = billingResult.responseCode
        val msg = billingResult.debugMessage
        Log.i(TAG, "[MX_BILLING] onBillingSetupFinished: ResponseCode=$code, Message='$msg'")
        if (code == BillingClient.BillingResponseCode.OK) {
            queryProductDetails()
            queryPurchases(isManualRestore = false)
        } else {
            Log.w(TAG, "[MX_BILLING] Billing setup failed: ResponseCode=$code - $msg")
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.w(TAG, "[MX_BILLING] onBillingServiceDisconnected: Service disconnected. Will reconnect on demand.")
    }

    fun queryProductDetails() {
        Log.i(TAG, "[MX_BILLING] queryProductDetails: Requesting details for ProductID='$PRODUCT_ID_PRO' (Type: INAPP)...")
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            val code = billingResult.responseCode
            val msg = billingResult.debugMessage
            Log.i(TAG, "[MX_BILLING] queryProductDetails response: ResponseCode=$code ($msg)")
            if (code == BillingClient.BillingResponseCode.OK) {
                val list = queryProductDetailsResult.productDetailsList
                Log.i(TAG, "[MX_BILLING] Products returned from Google Play: ${list.size}")
                if (list.isNotEmpty()) {
                    list.forEach { prod ->
                        Log.i(TAG, "[MX_BILLING] >> Found Product: ID='${prod.productId}', Type='${prod.productType}', Title='${prod.title}', FormattedPrice='${prod.oneTimePurchaseOfferDetails?.formattedPrice}'")
                    }
                    val proProduct = list.firstOrNull { it.productId == PRODUCT_ID_PRO }
                    if (proProduct != null) {
                        productDetails = proProduct
                        val price = proProduct.oneTimePurchaseOfferDetails?.formattedPrice
                        if (!price.isNullOrBlank()) {
                            _formattedPrice.value = price
                            Log.i(TAG, "[MX_BILLING] Formatted price updated to: $price")
                        }
                    }
                } else {
                    Log.w(TAG, "[MX_BILLING] Product list is EMPTY! Ensure '$PRODUCT_ID_PRO' is Active in Play Console, the internal test track is released, and tester account is enrolled.")
                }
            } else {
                Log.w(TAG, "[MX_BILLING] Query product details failed: ResponseCode=$code - $msg")
            }
        }
    }

    fun queryPurchases(isManualRestore: Boolean = false) {
        Log.i(TAG, "[MX_BILLING] queryPurchases: Querying active INAPP purchases (isManualRestore=$isManualRestore)...")
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            val code = billingResult.responseCode
            val msg = billingResult.debugMessage
            Log.i(TAG, "[MX_BILLING] queryPurchases result: ResponseCode=$code, purchasesFound=${purchases.size}")
            if (code == BillingClient.BillingResponseCode.OK) {
                val hasPro = processPurchases(purchases)
                if (!hasPro) {
                    refreshProStatus()
                }
                if (isManualRestore) {
                    if (hasPro) {
                        showToast(context.getString(R.string.toast_billing_pro_restored))
                    } else {
                        showToast(context.getString(R.string.toast_billing_no_purchases))
                    }
                }
            } else {
                Log.w(TAG, "[MX_BILLING] Query purchases failed: ResponseCode=$code - $msg")
                if (isManualRestore) {
                    showToast(context.getString(R.string.toast_billing_check_error, code))
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        val code = billingResult.responseCode
        val msg = billingResult.debugMessage
        Log.i(TAG, "[MX_BILLING] onPurchasesUpdated: ResponseCode=$code, purchasesCount=${purchases?.size ?: 0}")
        when (code) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    val hasPro = processPurchases(purchases)
                    if (hasPro) {
                        showToast(context.getString(R.string.toast_billing_pro_activated))
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "[MX_BILLING] User canceled the purchase flow.")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.i(TAG, "[MX_BILLING] Item already owned. Unlocking Pro.")
                unlockPro(true)
                showToast(context.getString(R.string.toast_billing_already_owned))
                queryPurchases(isManualRestore = false)
            }
            else -> {
                Log.e(TAG, "[MX_BILLING] Purchase failed: ResponseCode=$code - $msg")
                _purchaseStatusMessage.value = context.getString(R.string.toast_billing_payment_error, msg)
                showToast(context.getString(R.string.toast_billing_purchase_failed, msg.ifBlank { "Código $code" }))
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>): Boolean {
        var hasPro = false
        for (purchase in purchases) {
            Log.i(TAG, "[MX_BILLING] Inspecting purchase: products=${purchase.products}, state=${purchase.purchaseState}, acknowledged=${purchase.isAcknowledged}")
            if (purchase.products.contains(PRODUCT_ID_PRO)) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    hasPro = true
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
            }
        }

        if (hasPro) {
            unlockPro(true)
        }
        return hasPro
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        Log.i(TAG, "[MX_BILLING] Acknowledging purchase token: ${purchase.purchaseToken.take(15)}...")
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "[MX_BILLING] Purchase acknowledged successfully.")
                unlockPro(true)
            } else {
                Log.e(TAG, "[MX_BILLING] Failed to acknowledge purchase: ${billingResult.responseCode} - ${billingResult.debugMessage}")
            }
        }
    }

    fun launchBillingFlow(activity: Activity): Boolean {
        Log.i(TAG, "[MX_BILLING] launchBillingFlow requested. isReady=${billingClient.isReady}, hasProductDetails=${productDetails != null}")
        if (!billingClient.isReady) {
            Log.w(TAG, "[MX_BILLING] BillingClient is not ready. Attempting connection...")
            showToast(context.getString(R.string.toast_billing_connecting))
            startBillingConnection()
            return false
        }

        val details = productDetails
        if (details == null) {
            Log.w(TAG, "[MX_BILLING] Product details not loaded yet. Retrying queryProductDetails...")
            showToast(context.getString(R.string.toast_billing_product_not_synced))
            queryProductDetails()
            return false
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        Log.i(TAG, "[MX_BILLING] Launching Google Play Billing Flow for '${details.productId}'...")
        val billingResult = billingClient.launchBillingFlow(activity, flowParams)
        Log.i(TAG, "[MX_BILLING] launchBillingFlow result: ResponseCode=${billingResult.responseCode} - ${billingResult.debugMessage}")
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            showToast(context.getString(R.string.toast_billing_flow_start_failed, billingResult.debugMessage.ifBlank { "Código ${billingResult.responseCode}" }))
            return false
        }
        return true
    }

    fun restorePurchases() {
        Log.i(TAG, "[MX_BILLING] Manual restorePurchases requested by user.")
        if (!billingClient.isReady) {
            showToast(context.getString(R.string.toast_billing_connecting))
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryPurchases(isManualRestore = true)
                    } else {
                        showToast(context.getString(R.string.toast_billing_connect_error, billingResult.responseCode))
                    }
                }
                override fun onBillingServiceDisconnected() {}
            })
            return
        }
        queryPurchases(isManualRestore = true)
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun unlockPro(unlocked: Boolean) {
        storageRepository.setProUnlocked(unlocked)
        _isProUnlocked.value = storageRepository.isProUnlocked()
        Log.i(TAG, "[MX_BILLING] MX Control Pro status updated: isProUnlocked=${_isProUnlocked.value}")
    }

    fun clearStatusMessage() {
        _purchaseStatusMessage.value = null
    }

    fun destroy() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
