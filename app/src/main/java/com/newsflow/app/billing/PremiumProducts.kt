package com.newsflow.app.billing

/**
 * 订阅商品定义。
 *
 * ⚠️ 下面两个 ID 必须与 Google Play Console 里创建的「订阅」商品 ID 完全一致，
 *    否则 queryProductDetails 会返回空列表。
 *
 * Play Console 创建步骤：
 *   1. Play Console → 你的应用 → 创收 → 商品 → 订阅
 *   2. 新建订阅 ID：newsflow_premium_monthly
 *      基础方案：月付，价格 $0.99
 *   3. 新建订阅 ID：newsflow_premium_quarterly
 *      基础方案：3 个月，价格 $1.99
 *   4. 两个订阅都要「激活」
 *
 * 价格在 Play Console 里设，代码里不写死价格（Play 会返回本地化价格）。
 */
object PremiumProducts {

    /** 月付 $0.99 */
    const val MONTHLY = "newsflow_premium_monthly"

    /** 3 个月 $1.99 */
    const val QUARTERLY = "newsflow_premium_quarterly"

    val all: List<String> = listOf(MONTHLY, QUARTERLY)

    /** Play 价格拉不到时的兜底显示（真实价格以 Play Console 为准） */
    const val FALLBACK_MONTHLY_PRICE = "$0.99"
    const val FALLBACK_QUARTERLY_PRICE = "$1.99"

    fun title(productId: String): String = when (productId) {
        MONTHLY -> "1 Month"
        QUARTERLY -> "3 Months"
        else -> productId
    }

    fun period(productId: String): String = when (productId) {
        MONTHLY -> "per month"
        QUARTERLY -> "every 3 months"
        else -> ""
    }

    fun fallbackPrice(productId: String): String = when (productId) {
        MONTHLY -> FALLBACK_MONTHLY_PRICE
        QUARTERLY -> FALLBACK_QUARTERLY_PRICE
        else -> ""
    }

    /** 是否是最划算的方案（UI 上打角标） */
    fun isBestValue(productId: String): Boolean = productId == QUARTERLY
}
