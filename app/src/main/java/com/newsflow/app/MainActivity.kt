package com.newsflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.newsflow.app.ads.AdsConsent
import com.newsflow.app.ui.AppRoot
import com.newsflow.app.ui.theme.NewsFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // AdMob：先跑同意流程（GDPR/CCPA），再初始化广告 SDK。
        // 幂等，重复启动不会重复弹窗。
        AdsConsent.requestAndInit(this)

        setContent {
            NewsFlowTheme {
                AppRoot()
            }
        }
    }
}
