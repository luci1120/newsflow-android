package com.newsflow.app.billing

import android.content.Context

/**
 * 会员状态本地存储。
 *
 * 为什么本地存一份：
 *   - 离线时也能立刻知道该不该显示广告（不用等 Play 查询回来）
 *   - 每次启动仍会用 Play 的真实查询结果覆盖，防止被篡改
 *
 * 说明：这是客户端存储，理论上可被 root 用户篡改。对「去广告」这种
 * 低价值权益来说，加服务端校验性价比不高；如果以后权益变重（比如解锁
 * 付费课程），应改为服务端校验（Play Developer API）。
 */
class PremiumStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("newsflow_premium", Context.MODE_PRIVATE)

    /** 是否是会员（无广告） */
    fun isPremium(): Boolean = prefs.getBoolean(KEY_PREMIUM, false)

    /** 当前订阅的商品 ID（可能为 null） */
    fun plan(): String? = prefs.getString(KEY_PLAN, null)

    /** 上次从 Play 校验通过的时间戳 */
    fun lastVerifiedAt(): Long = prefs.getLong(KEY_VERIFIED_AT, 0L)

    fun setPremium(value: Boolean, plan: String? = null) {
        prefs.edit()
            .putBoolean(KEY_PREMIUM, value)
            .apply {
                if (plan != null) putString(KEY_PLAN, plan)
                if (!value) remove(KEY_PLAN)
            }
            .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
            .apply()
    }

    /** 仅调试用：直接改状态，不经过 Play */
    fun debugOverride(value: Boolean) = setPremium(value, if (value) "debug" else null)

    private companion object {
        const val KEY_PREMIUM = "is_premium"
        const val KEY_PLAN = "plan"
        const val KEY_VERIFIED_AT = "verified_at"
    }
}
