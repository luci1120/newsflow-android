package com.newsflow.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play 订阅封装。
 *
 * 流程：
 *   启动 → startConnection → queryProducts（拿价格）+ refreshEntitlement（恢复已有订阅）
 *   购买 → launchPurchase → Play 弹窗 → onPurchasesUpdated → acknowledge → 标记会员
 *
 * ⚠️ 购买流程无法在本地模拟器验证：Play Billing 要求 App 从 Play 安装
 *    （需上传到内部测试轨道 + 配置许可测试账号）。代码已做降级处理，
 *    连不上时不会崩溃，只是无法购买。
 */
class BillingService(private val context: Context) {

    private val store = PremiumStore(context)
    private var client: BillingClient? = null

    /** Play 返回的商品详情（含本地化价格） */
    var products: List<ProductDetails> = emptyList()
        private set

    /** 是否已连上 Play 计费服务 */
    var isConnected: Boolean = false
        private set

    /** 购买成功/失败回调（由 UI 设置） */
    var onPurchaseFinished: ((success: Boolean, message: String?) -> Unit)? = null

    /** 会员状态变化回调（由 UI 设置） */
    var onEntitlementChanged: ((Boolean) -> Unit)? = null

    /** 商品列表加载完成回调（由 UI 设置） */
    var onProductsLoaded: ((List<ProductDetails>) -> Unit)? = null

    private val purchaseListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
                onPurchaseFinished?.invoke(true, null)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                onPurchaseFinished?.invoke(false, null)   // 用户主动取消，不算错误
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // 已经订阅过 —— 直接刷新权益
                refreshEntitlement { }
                onPurchaseFinished?.invoke(true, null)
            }
            else -> {
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
                onPurchaseFinished?.invoke(false, result.debugMessage)
            }
        }
    }

    /** 连接 Play 计费服务，并拉取商品 + 恢复已有订阅 */
    fun start(onReady: (Boolean) -> Unit = {}) {
        if (client != null) {
            onReady(isConnected)
            return
        }

        val c = BillingClient.newBuilder(context)
            .setListener(purchaseListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        client = c

        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    Log.i(TAG, "Billing connected")
                    queryProducts { }
                    refreshEntitlement { }
                    onReady(true)
                } else {
                    isConnected = false
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    onReady(false)
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    /** 查询订阅商品（拿到本地化价格） */
    fun queryProducts(onDone: (List<ProductDetails>) -> Unit) {
        val productList = PremiumProducts.all.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client?.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                products = detailsResult.productDetailsList ?: emptyList()
                Log.i(TAG, "Loaded ${products.size} product(s)")
            } else {
                // 商品不存在 / 未激活 / App 非 Play 安装 —— 都会走到这里
                Log.w(TAG, "queryProductDetails failed: ${result.responseCode} ${result.debugMessage}")
            }
            onProductsLoaded?.invoke(products)
            onDone(products)
        } ?: onDone(emptyList())
    }

    /** 发起购买 */
    fun launchPurchase(activity: Activity, productId: String) {
        val details = products.firstOrNull { it.productId == productId }
        if (details == null) {
            onPurchaseFinished?.invoke(false, "Product not available. Check Play Console setup.")
            return
        }

        // 订阅必须带 offerToken
        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken

        if (offerToken.isNullOrBlank()) {
            onPurchaseFinished?.invoke(false, "No active offer for this subscription.")
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = client?.launchBillingFlow(activity, flowParams)
        if (result != null && result.responseCode != BillingClient.BillingResponseCode.OK) {
            onPurchaseFinished?.invoke(false, result.debugMessage)
        }
    }

    /** 用 Play 的真实数据刷新会员状态（同时用于「恢复购买」） */
    fun refreshEntitlement(onDone: (Boolean) -> Unit) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client?.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                // queryPurchasesAsync(SUBS) 只返回当前有效的订阅（已过期的不会返回）
                val active = purchases.firstOrNull { p ->
                    p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        p.products.any { it in PremiumProducts.all }
                }
                store.setPremium(active != null, active?.products?.firstOrNull())
                onEntitlementChanged?.invoke(active != null)
                onDone(active != null)
            } else {
                // 查询失败时保留本地状态，避免误把会员降级
                Log.w(TAG, "queryPurchases failed: ${result.debugMessage}")
                onDone(store.isPremium())
            }
        } ?: onDone(store.isPremium())
    }

    /** 处理单笔购买：确认 + 标记会员 */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // 必须确认，否则 3 天后 Play 会自动退款
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            client?.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Purchase acknowledged")
                    store.setPremium(true, purchase.products.firstOrNull())
                    onEntitlementChanged?.invoke(true)
                } else {
                    Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
                }
            }
        } else {
            store.setPremium(true, purchase.products.firstOrNull())
            onEntitlementChanged?.invoke(true)
        }
    }

    fun end() {
        client?.endConnection()
        client = null
        isConnected = false
    }

    /** 本地记录的会员状态（离线可用） */
    fun isPremiumLocally(): Boolean = store.isPremium()

    fun currentPlan(): String? = store.plan()

    private companion object {
        const val TAG = "BillingService"
    }
}
