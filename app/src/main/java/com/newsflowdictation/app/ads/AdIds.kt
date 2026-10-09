package com.newsflowdictation.app.ads

import com.newsflowdictation.app.BuildConfig

/**
 * 广告单元 ID 解析。
 *
 * 真实 ID 配置在 app/build.gradle.kts 顶部（ADMOB 配置块），
 * 通过 BuildConfig 注入到这里，避免硬编码散落在业务代码里。
 *
 * ⚠️ USE_TEST_ADS = true 时一律返回 Google 官方测试单元，
 *    这样开发期不会产生无效流量、不会导致 AdMob 封号。
 */
object AdIds {

    /** Google 官方测试横幅单元（长期有效，可放心使用） */
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"

    /** 是否处于测试广告模式 */
    val isTestMode: Boolean
        get() = BuildConfig.USE_TEST_ADS

    /** 新闻列表页底部横幅 */
    val bannerList: String
        get() = if (isTestMode) TEST_BANNER else BuildConfig.ADMOB_BANNER_LIST

    /** 播放页底部横幅 */
    val bannerPlayer: String
        get() = if (isTestMode) TEST_BANNER else BuildConfig.ADMOB_BANNER_PLAYER
}
