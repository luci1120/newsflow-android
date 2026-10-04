package com.newsflow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import com.newsflow.app.billing.PremiumProducts

/**
 * 付费页（订阅去广告）。
 *
 * 价格优先用 Play 返回的本地化价格；拉不到时用兜底值。
 * 会员状态下显示「已订阅」而非购买按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(
    isPremium: Boolean,
    products: List<ProductDetails>,
    billingConnected: Boolean,
    message: String?,
    onBack: () -> Unit,
    onSubscribe: (String) -> Unit,
    onRestore: () -> Unit,
) {
    // 默认选最划算的季付
    var selected by remember { mutableStateOf(PremiumProducts.QUARTERLY) }

    // 系统返回键回到设置页，而不是退出 App
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPremium) "Premium" else "Remove Ads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // ---------- 顶部图形 ----------
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2B7BF3), Color(0xFF0A4DBF))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPremium) Icons.Default.CheckCircle else Icons.Default.Block,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                if (isPremium) "You're Premium" else "Ad-free learning",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                if (isPremium) "Ads are off. Thanks for supporting NewsFlow."
                else "Remove all banner ads and keep the practice flow uninterrupted.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(24.dp))

            if (isPremium) {
                // ---------- 已订阅状态 ----------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Active subscription", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Manage or cancel anytime in Google Play → Subscriptions.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // ---------- 权益列表 ----------
                BenefitRow("No banner ads", "Clean, distraction-free practice")
                BenefitRow("Every source, every day", "CNN 10 · CBS · PBS · ABC")
                BenefitRow("Cancel anytime", "Managed by Google Play")

                Spacer(Modifier.height(20.dp))

                // ---------- 方案选择 ----------
                PremiumProducts.all.forEach { productId ->
                    PlanCard(
                        productId = productId,
                        price = priceOf(products, productId),
                        selected = selected == productId,
                        onClick = { selected = productId },
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(12.dp))

                // ---------- 购买按钮 ----------
                Button(
                    onClick = { onSubscribe(selected) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = billingConnected,
                ) {
                    Text(
                        if (billingConnected) "Subscribe" else "Play Store unavailable",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (!billingConnected) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Subscriptions need the app installed from Google Play.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(6.dp))

                TextButton(onClick = onRestore) {
                    Text("Restore purchase")
                }
            }

            // ---------- 提示信息 ----------
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Payment is charged to your Google Play account. The subscription " +
                    "renews automatically unless cancelled at least 24 hours before " +
                    "the end of the current period.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BenefitRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanCard(
    productId: String,
    price: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        PremiumProducts.title(productId),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (PremiumProducts.isBestValue(productId)) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                "BEST VALUE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    PremiumProducts.period(productId),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                price,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 取 Play 返回的本地化价格；没有就用兜底值 */
private fun priceOf(products: List<ProductDetails>, productId: String): String {
    val details = products.firstOrNull { it.productId == productId }
    val phase = details
        ?.subscriptionOfferDetails
        ?.firstOrNull()
        ?.pricingPhases
        ?.pricingPhaseList
        ?.firstOrNull()
    return phase?.formattedPrice ?: PremiumProducts.fallbackPrice(productId)
}
