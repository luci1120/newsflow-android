package com.newsflow.app.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * 自适应横幅广告。
 *
 * 设计要点：
 *  - 高度自适应：加载成功才展开，失败/未加载时高度为 0，**不会留下空白条**
 *  - 宽度撑满容器：用「锚定自适应横幅」，比固定 320x50 效果好
 *  - 生命周期：离开界面时销毁 AdView，避免内存泄漏
 *
 * 用法：BannerAd(adUnitId = AdIds.bannerList)
 */
@Composable
fun BannerAd(
    adUnitId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    // 0 = 还没加载出来（不占位）；加载成功后设为真实高度
    var adHeightDp by remember(adUnitId) { mutableIntStateOf(0) }
    var adView by remember(adUnitId) { mutableStateOf<AdView?>(null) }

    DisposableEffect(adUnitId) {
        onDispose {
            adView?.destroy()
            adView = null
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(adHeightDp.dp),
        factory = { ctx ->
            AdView(ctx).apply {
                val size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    ctx, screenWidthDp
                )
                setAdSize(size)
                this.adUnitId = adUnitId
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        adHeightDp = size.height
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // 加载失败就收起来，不给用户看空白
                        adHeightDp = 0
                    }
                }
                adView = this
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
