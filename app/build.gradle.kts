import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Load signing credentials from keystore.properties (gitignored).
// If the file is missing (e.g. fresh clone / CI), release falls back to unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
val hasSigningConfig = keystorePropertiesFile.exists() &&
        keystoreProperties.getProperty("storeFile") != null

// ============================================================================
// AdMob 配置 —— 换真实 ID 时只改这一块
// ============================================================================
// ⚠️ 开发/测试期间必须保持 useTestAds = true。
//    用真实广告单元做测试 = 无效流量 = AdMob 封号，这是最常见的封号原因。
//
// 上线前要做的：
//   1. AdMob 后台 → 应用 → 添加应用 → 拿到「应用 ID」(格式 ca-app-pub-4841187795675033~1234567890)
//   2. AdMob 后台 → 广告单元 → 创建「横幅」→ 拿到「广告单元 ID」(格式 ca-app-pub-4841187795675033/1234567890)
//   3. 把下面 3 个测试 ID 替换成你自己的真实 ID
//   4. 把 useTestAds 改成 false
// ============================================================================
val admobAppId = "ca-app-pub-3940256099942544~3347511713"          // 测试 App ID
val admobBannerList = "ca-app-pub-3940256099942544/6300978111"     // 测试横幅（新闻列表页）
val admobBannerPlayer = "ca-app-pub-3940256099942544/6300978111"   // 测试横幅（播放页）
val useTestAds = true

android {
    namespace = "com.newsflow.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.newsflow.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AdMob：App ID 注入到 AndroidManifest 的 meta-data
        manifestPlaceholders["admobAppId"] = admobAppId
        // AdMob：广告单元 ID 注入到代码里（BuildConfig）
        buildConfigField("String", "ADMOB_BANNER_LIST", "\"$admobBannerList\"")
        buildConfigField("String", "ADMOB_BANNER_PLAYER", "\"$admobBannerPlayer\"")
        buildConfigField("boolean", "USE_TEST_ADS", "$useTestAds")
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // WebView for YouTube IFrame
    implementation("androidx.webkit:webkit:1.12.1")

    // DataStore for settings
    implementation("androidx.datastore:datastore-preferences:1.1.5")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // AdMob
    // 注意：25.x / 24.9+ 用 Kotlin 2.1+ 元数据编译，与本项目的 Kotlin 2.0.20 不兼容。
    // 24.0.0 是最后一个纯 Java 版本，横幅功能完全够用。
    implementation("com.google.android.gms:play-services-ads:24.0.0")
    // GDPR/CCPA 同意流程（欧盟/加州用户必须）
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")

    // Google Play 计费（订阅去广告）
    // 基础库是纯 Java，无 Kotlin 元数据兼容问题
    implementation("com.android.billingclient:billing:8.3.0")

    // Testing
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")
}
