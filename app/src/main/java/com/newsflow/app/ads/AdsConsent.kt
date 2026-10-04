package com.newsflow.app.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * AdMob 同意流程 + SDK 初始化。
 *
 * 顺序很重要（Google 官方要求）：
 *   1. 查询用户所在地区是否需要同意
 *   2. 需要则弹出同意表单（UMP）
 *   3. 之后才初始化 MobileAds SDK
 *
 * 不做的后果：
 *   - 欧盟用户看不到同意表单 → 违反 GDPR → Play 审核可能拒绝
 *   - 未同意就请求个性化广告 → AdMob 会限制投放
 *
 * 幂等：重复调用是安全的，已同意时不会重复弹窗。
 */
object AdsConsent {

    private const val TAG = "AdsConsent"

    @Volatile
    private var initialized = false

    /**
     * 请求同意并初始化 SDK。可在每次启动时调用。
     *
     * @param activity 当前 Activity（弹窗需要）
     * @param onReady  初始化完成回调（主线程）
     */
    fun requestAndInit(activity: Activity, onReady: () -> Unit = {}) {
        if (initialized) {
            onReady()
            return
        }

        val params = ConsentRequestParameters.Builder()
            // 本应用面向普通学习者，不针对儿童；如后续定位儿童需改为 true
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation: ConsentInformation =
            UserMessagingPlatform.getConsentInformation(activity)

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // 同意信息已更新 → 需要就展示表单
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    initSdk(activity, onReady)
                }
            },
            { requestError ->
                // 查询失败（例如离线）—— 仍然初始化，SDK 会只请求非个性化广告
                Log.w(TAG, "Consent update failed: ${requestError.message}")
                initSdk(activity, onReady)
            },
        )
    }

    private fun initSdk(activity: Activity, onReady: () -> Unit) {
        MobileAds.initialize(activity) {
            Log.i(TAG, "MobileAds initialized")
            initialized = true
            onReady()
        }
    }

    /** 当前是否允许请求广告（同意流程已完成） */
    fun canRequestAds(activity: Activity): Boolean =
        UserMessagingPlatform.getConsentInformation(activity).canRequestAds()

    /** 调试用：强制在测试设备上显示同意表单（上线前删掉调用） */
    fun debugForceEea(activity: Activity): ConsentRequestParameters =
        ConsentRequestParameters.Builder()
            .setConsentDebugSettings(
                com.google.android.ump.ConsentDebugSettings.Builder(activity)
                    .addTestDeviceHashedId("TEST_DEVICE_HASHED_ID")
                    .build()
            )
            .build()
}
